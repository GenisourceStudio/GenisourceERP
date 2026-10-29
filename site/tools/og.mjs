#!/usr/bin/env node
/**
 * 生成社交分享卡片（OG image）：1200×630 JPG。
 *  源文件：tools/og-card.html（1200×630 的固定版式）
 *  输出：assets/img/og-cover.jpg
 *
 * 用法：先起本地预览 `python3 -m http.server 8090 --bind 127.0.0.1`，然后
 *   node tools/og.mjs
 *
 * 生成前会做版式自检：页面尺寸必须是 1200×630，任何元素不得越界，
 * 中英文字体必须真的生效（否则会拿系统兜底字体渲染出歪图）。
 */
import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const ORIGIN = process.env.SITE_BASE || 'http://127.0.0.1:8090'
const PAGE = ORIGIN + '/tools/og-card.html'
const PORT = 9337
const CHROME = process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const OUT_PNG = '/tmp/og-cover-2400.png'
const OUT_JPG = new URL('../assets/img/og-cover.jpg', import.meta.url).pathname

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

class Cdp {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data); const p = msg.id && this.pending.get(msg.id)
      if (!p) return; this.pending.delete(msg.id)
      msg.error ? p.reject(new Error(msg.error.message)) : p.resolve(msg.result)
    })
  }
  send(method, params = {}) {
    const id = ++this.id
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => { if (this.pending.delete(id)) reject(new Error('timeout ' + method)) }, 20000)
    })
  }
  async eval(expression) {
    const r = await this.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.text + ' ' + (r.exceptionDetails.exception?.description || ''))
    return r.result.value
  }
}

async function waitJson(url) {
  for (let i = 0; i < 80; i++) {
    try { const r = await fetch(url); if (r.ok) return r.json() } catch {}
    await sleep(250)
  }
  throw new Error('devtools not up')
}

const userDir = '/tmp/erp-og-profile-' + Date.now()
const chrome = spawn(CHROME, [
  '--headless=new',
  '--no-sandbox',
  '--disable-setuid-sandbox',
  '--disable-gpu',
  '--disable-dev-shm-usage',
  '--no-proxy-server',
  '--disable-software-rasterizer',
  '--hide-scrollbars',
  '--no-first-run',
  '--no-default-browser-check',
  '--force-device-scale-factor=2',
  '--user-data-dir=' + userDir,
  '--remote-debugging-port=' + PORT,
  'about:blank',
], { stdio: 'ignore' })

let failures = 0
const ok = (m) => console.log('  ✓', m)
const bad = (m) => { failures++; console.log('  ✗', m) }

try {
  await waitJson(`http://127.0.0.1:${PORT}/json/version`)
  const pages = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
  const target = pages.find((t) => t.type === 'page')
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej) })
  const cdp = new Cdp(ws)
  await cdp.send('Page.enable'); await cdp.send('Runtime.enable')

  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1200, height: 630, deviceScaleFactor: 2, mobile: false })
  await cdp.send('Page.navigate', { url: PAGE })
  for (let i = 0; i < 60; i++) {
    if (await cdp.eval(`document.readyState === 'complete'`)) break
    await sleep(200)
  }
  await cdp.eval(`document.fonts.ready.then(() => true)`)
  await sleep(500)

  console.log('■ 版式自检')
  const m = await cdp.eval(`(() => {
    const de = document.documentElement
    const urls = [...document.querySelectorAll('img')].map((i) => ({ src: i.currentSrc.split('/').pop(), ok: i.complete && i.naturalWidth > 0 }))
    const out = [...document.querySelectorAll('body *')]
      .map((el) => ({ el, r: el.getBoundingClientRect() }))
      .filter((x) => x.r.width > 0 && (x.r.right > 1200.5 || x.r.bottom > 630.5 || x.r.left < -0.5 || x.r.top < -0.5))
      .slice(0, 5)
      .map((x) => x.el.tagName.toLowerCase() + '.' + (x.el.className || '').toString().split(' ')[0] + ' ' + Math.round(x.r.right) + 'x' + Math.round(x.r.bottom))
    const archivo = document.fonts.check('800 58px Archivo')
    const mono = document.fonts.check('400 15px DMMono')
    const h1 = document.querySelector('h1').getBoundingClientRect()
    return { w: de.scrollWidth, h: de.scrollHeight, imgs: urls, out, archivo, mono, h1h: Math.round(h1.height) }
  })()`)

  m.w === 1200 && m.h === 630 ? ok('画布 1200×630') : bad(`画布 ${m.w}×${m.h}`)
  m.out.length ? bad('元素越界：' + m.out.join(', ')) : ok('无元素越界')
  m.imgs.every((i) => i.ok) ? ok(`图片全部加载（${m.imgs.length}）`) : bad('图片未加载：' + JSON.stringify(m.imgs.filter((i) => !i.ok)))
  m.archivo ? ok('Archivo 字体生效') : bad('Archivo 未生效（会退化渲染）')
  m.mono ? ok('DMMono 字体生效') : bad('DMMono 未生效（会退化渲染）')
  ok(`主标题高度 ${m.h1h}px`)

  if (failures) { console.log('\n自检未通过，不生成图片'); process.exitCode = 1 }
  else {
    const shot = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    writeFileSync(OUT_PNG, Buffer.from(shot.data, 'base64'))
    // 2× 降采样到 1200×630，转 JPG（微信/飞书对 WebP 支持不稳）
    execFileSync('sips', ['-s', 'format', 'jpeg', '-s', 'formatOptions', '88', '-z', '630', '1200', OUT_PNG, '--out', OUT_JPG], { stdio: 'ignore' })
    console.log('\n已生成 ' + OUT_JPG)
  }
} finally {
  chrome.kill('SIGKILL')
}
