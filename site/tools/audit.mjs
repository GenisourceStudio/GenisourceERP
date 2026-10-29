#!/usr/bin/env node
/**
 * 官网站点运行时审计：真实渲染下检查
 *   1. 四档宽度横向溢出（1440 / 1024 / 768 / 390）
 *   2. 图片与字体是否真正加载
 *   3. 能力切换器、移动端菜单、FAQ 折叠是否可用
 *   4. 站内链接是否都指向存在的文件
 *
 * 用法：
 *   node tools/audit.mjs                                    # 审本地 http://127.0.0.1:8090
 *   SITE_BASE=https://genisource-erp.vercel.app \
 *     SITE_PROXY=http://127.0.0.1:7890 node tools/audit.mjs  # 审线上（大陆需走代理）
 */
import { spawn } from 'node:child_process'

const BASE = process.env.SITE_BASE || 'http://127.0.0.1:8090'
const PORT = 9336
const CHROME = process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const PAGES = ['/', '/demo.html', '/faq.html']
const WIDTHS = [1440, 1024, 768, 390]

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
let failures = 0
const ok = (m) => console.log('  ✓', m)
const bad = (m) => { failures++; console.log('  ✗', m) }

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
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.text + ' ' + JSON.stringify(r.exceptionDetails.exception?.description || ''))
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

const userDir = '/tmp/erp-audit-profile-' + Date.now()
const chrome = spawn(CHROME, [
  '--headless=new',
  '--no-sandbox',
  '--disable-setuid-sandbox',
  '--disable-gpu',
  '--disable-dev-shm-usage',
  // 本地预览直连；审计线上站点时用 SITE_PROXY=http://127.0.0.1:7890 走代理
  process.env.SITE_PROXY ? '--proxy-server=' + process.env.SITE_PROXY : '--no-proxy-server',
  '--disable-software-rasterizer',
  '--hide-scrollbars',
  '--no-first-run',
  '--no-default-browser-check',
  '--force-device-scale-factor=1',
  '--user-data-dir=' + userDir,
  '--remote-debugging-port=' + PORT,
  'about:blank',
], { stdio: 'ignore' })

try {
  await waitJson(`http://127.0.0.1:${PORT}/json/version`)
  const pages = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
  const target = pages.find((t) => t.type === 'page')
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej) })
  const cdp = new Cdp(ws)
  await cdp.send('Page.enable'); await cdp.send('Runtime.enable')

  /**
   * 等「整页解析完成 + 目标元素出现」再操作。
   * index.html 的 site.js 是 defer 的，解析完成才执行；线上首屏比本地慢得多，
   * 只等元素出现就 click 会在监听器绑定前点空，造成假失败。
   */
  const waitReady = async (expr) => {
    for (let i = 0; i < 100; i++) {
      if (await cdp.eval(`document.readyState === 'complete' && !!(${expr})`)) return true
      await sleep(150)
    }
    return false
  }

  for (const path of PAGES) {
    console.log('\n■ ' + path)

    for (const width of WIDTHS) {
      await cdp.send('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: width < 700 })
      await cdp.send('Page.navigate', { url: BASE + path })
      await sleep(1100)
      const res = await cdp.eval(`(() => {
        const de = document.documentElement
        const overflow = de.scrollWidth - de.clientWidth
        const wide = [...document.querySelectorAll('body *')]
          .filter((el) => el.getBoundingClientRect().right > de.clientWidth + 2)
          .slice(0, 3)
          .map((el) => el.tagName.toLowerCase() + '.' + (el.className || '').toString().split(' ')[0])
        return { overflow, wide }
      })()`)
      if (res.overflow > 2) bad(`${width}px 横向溢出 ${res.overflow}px  ${JSON.stringify(res.wide)}`)
      else ok(`${width}px 无横向溢出`)
    }

    // 回到桌面宽度做内容检查
    await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
    await cdp.send('Page.navigate', { url: BASE + path })
    await sleep(1400)

    // 懒加载图在视口外不会请求，先滚到底把它们叫起来再统计
    await cdp.eval(`(async () => {
      document.querySelectorAll('img[loading="lazy"]').forEach((i) => { i.loading = 'eager' })
      window.scrollTo(0, document.body.scrollHeight)
      await new Promise((r) => setTimeout(r, 900))
      window.scrollTo(0, 0)
      return true
    })()`)
    await sleep(500)

    const media = await cdp.eval(`(() => {
      const imgs = [...document.images]
      const broken = imgs.filter((i) => !i.complete || i.naturalWidth === 0).map((i) => i.currentSrc || i.src)
      return {
        imgs: imgs.length,
        broken,
        fonts: document.fonts ? document.fonts.check('16px ArchivoVF') : null,
        h1: (document.querySelector('h1') || {}).innerText || '',
      }
    })()`)
    media.broken.length ? bad(`图片未加载：${media.broken.join(', ')}`) : ok(`${media.imgs} 张图全部加载`)
    media.fonts ? ok('品牌字体已生效') : bad('品牌字体未加载')

    const links = await cdp.eval(`(async () => {
      const hrefs = [...new Set([...document.querySelectorAll('a[href]')]
        .map((a) => a.getAttribute('href'))
        .filter((h) => h && !h.startsWith('http') && !h.startsWith('mailto'))) ]
      const out = []
      for (const h of hrefs) {
        const url = h.split('#')[0]
        if (!url) continue
        const res = await fetch(url, { method: 'GET' })
        if (!res.ok) out.push(h + ' -> ' + res.status)
      }
      return out
    })()`)
    links.length ? bad(`死链：${links.join(', ')}`) : ok('站内链接全部可达')
  }

  /* 交互：能力切换器（首页） */
  console.log('\n■ 交互')
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await cdp.send('Page.navigate', { url: BASE + '/' })
  await waitReady(`document.getElementById('tab-3')`)
  const tabs = await cdp.eval(`(async () => {
    const tab = document.getElementById('tab-3')
    tab.click()
    await new Promise((r) => setTimeout(r, 120))
    const shown = !document.getElementById('panel-3').hidden
    const hidden = document.getElementById('panel-1').hidden
    const selected = tab.getAttribute('aria-selected') === 'true'
    const img = document.querySelector('#panel-3 img')
    await new Promise((r) => setTimeout(r, 800))
    return { shown, hidden, selected, imgOk: img ? img.complete && img.naturalWidth > 0 : null }
  })()`)
  tabs.shown && tabs.hidden && tabs.selected ? ok('能力切换器可用') : bad('能力切换器异常 ' + JSON.stringify(tabs))
  tabs.imgOk ? ok('切换后截图能加载') : bad('切换后截图未加载')

  await cdp.send('Page.navigate', { url: BASE + '/faq.html' })
  await waitReady(`document.querySelectorAll('.faq details').length > 0`)
  const faq = await cdp.eval(`(async () => {
    const d = [...document.querySelectorAll('.faq details')]
    const openCount = d.filter((x) => x.open).length
    d[1].querySelector('summary').click()
    await new Promise((r) => setTimeout(r, 80))
    return { total: d.length, openCount, toggled: d[1].open }
  })()`)
  faq.toggled ? ok(`FAQ 折叠可用（${faq.total} 条）`) : bad('FAQ 折叠不可用')

  /* 移动端菜单 */
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await cdp.send('Page.navigate', { url: BASE + '/' })
  await waitReady(`document.getElementById('burger')`)
  const menu = await cdp.eval(`(async () => {
    const b = document.getElementById('burger')
    b.click()
    await new Promise((r) => setTimeout(r, 120))
    const open = document.getElementById('mobile-nav').classList.contains('is-open')
    const expanded = b.getAttribute('aria-expanded') === 'true'
    b.click()
    await new Promise((r) => setTimeout(r, 120))
    const closed = !document.getElementById('mobile-nav').classList.contains('is-open')
    return { open, expanded, closed }
  })()`)
  menu.open && menu.expanded && menu.closed ? ok('移动端菜单可开可关') : bad('移动端菜单异常 ' + JSON.stringify(menu))

  ws.close()
} finally {
  chrome.kill('SIGKILL')
}

console.log('\n' + (failures ? `发现 ${failures} 个问题` : '全部通过'))
process.exit(failures ? 1 : 0)
