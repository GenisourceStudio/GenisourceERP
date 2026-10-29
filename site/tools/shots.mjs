#!/usr/bin/env node
/**
 * 官网截图：用本机 Chrome + CDP 抓真实运行界面。
 *
 * 用法：node tools/shots.mjs [--only=mrp,stock]
 * 前置：后端 8081、前端 5175 都在跑；演示数据已灌（seed-demo.mjs）。
 * 输出：assets/img/<name>-{800,1200}.webp（原图留在 .tmp/shots 里）
 */

import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'

const APP = process.env.ERP_WEB || 'http://127.0.0.1:5175'
const PORT = 9333
const CHROME = process.env.CHROME_BIN ||
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const ROOT = path.resolve(import.meta.dirname, '..')
const RAW = path.join(ROOT, '.tmp', 'shots')
const OUT = path.join(ROOT, 'assets', 'img')

const VIEWPORT = { width: 1440, height: 1000, dpr: 2 }

/** 每张图：路径、文件名、等待（毫秒）、说明 */
const SHOTS = [
  { name: 'dashboard', path: '/', wait: 2600, alt: '经营概览' },
  { name: 'sales-orders', path: '/sales/orders', wait: 2200, alt: '销售订单' },
  { name: 'mrp', path: '/planning/mrp', wait: 3000, alt: 'MRP 运算' },
  { name: 'production', path: '/planning/production-orders', wait: 2400, alt: '生产订单' },
  { name: 'stock', path: '/inventory/stock', wait: 2200, alt: '库存余额' },
  { name: 'purchase-orders', path: '/purchase/orders', wait: 2200, alt: '采购订单' },
  { name: 'receivables', path: '/finance/receivables', wait: 2200, alt: '应收台账' },
  { name: 'reports', path: '/finance/reports', wait: 2600, alt: '财务报表' },
  { name: 'bom', path: '/masterdata/bom', wait: 2400, alt: 'BOM 清单' },
  { name: 'vouchers', path: '/finance/vouchers', wait: 2200, alt: '记账凭证' },
]

const only = (process.argv.find((a) => a.startsWith('--only=')) || '').split('=')[1]
const list = only ? SHOTS.filter((s) => only.split(',').includes(s.name)) : SHOTS

/* ------------------------------ CDP 客户端 ------------------------------ */

class Cdp {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      const p = msg.id && this.pending.get(msg.id)
      if (!p) return
      this.pending.delete(msg.id)
      msg.error ? p.reject(new Error(msg.error.message)) : p.resolve(msg.result)
    })
  }

  send(method, params = {}) {
    const id = ++this.id
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => {
        if (this.pending.delete(id)) reject(new Error('CDP timeout: ' + method))
      }, 30000)
    })
  }

  async eval(expression) {
    const r = await this.send('Runtime.evaluate', {
      expression, awaitPromise: true, returnByValue: true,
    })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.text)
    return r.result.value
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function waitForEndpoint(url, tries = 60) {
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(url)
      if (res.ok) return await res.json()
    } catch { /* 还没起来 */ }
    await sleep(250)
  }
  throw new Error('DevTools 端点没起来：' + url)
}

async function main() {
  await mkdir(RAW, { recursive: true })
  await mkdir(OUT, { recursive: true })

  const chrome = spawn(CHROME, [
    '--headless=new',
    `--remote-debugging-port=${PORT}`,
    '--remote-allow-origins=*',
    '--hide-scrollbars',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-extensions',
    '--force-device-scale-factor=' + VIEWPORT.dpr,
    `--window-size=${VIEWPORT.width},${VIEWPORT.height}`,
    '--user-data-dir=/tmp/erp-shot-profile',
    'about:blank',
  ], { stdio: 'ignore' })

  try {
    await waitForEndpoint(`http://127.0.0.1:${PORT}/json/version`)
    const pages = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
    let target = pages.find((t) => t.type === 'page')
    if (!target) {
      target = await (await fetch(`http://127.0.0.1:${PORT}/json/new?url=about:blank`, { method: 'PUT' })).json()
    }
    const ws = new WebSocket(target.webSocketDebuggerUrl)
    await new Promise((res, rej) => {
      ws.addEventListener('open', res)
      ws.addEventListener('error', rej)
    })
    const cdp = new Cdp(ws)

    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: VIEWPORT.width, height: VIEWPORT.height,
      deviceScaleFactor: VIEWPORT.dpr, mobile: false,
    })

    /* 登录：直接打接口拿令牌，按 zustand persist 的结构写进 localStorage */
    console.log('登录 …')
    await cdp.send('Page.navigate', { url: APP + '/login' })
    await sleep(2500)

    const token = await cdp.eval(`(async () => {
      const res = await fetch('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: 'admin123' }),
      })
      if (!res.ok) return null
      const t = await res.json()
      const state = { accessToken: t.accessToken, refreshToken: t.refreshToken, user: t.user }
      localStorage.setItem('genisource.erp.auth', JSON.stringify({ state, version: 0 }))
      localStorage.setItem('genisource.erp.locale', 'zh-CN')
      return t.accessToken ? 'ok' : null
    })()`)
    if (!token) throw new Error('登录失败')
    console.log('  令牌已写入 localStorage')

    for (const shot of list) {
      const url = APP + shot.path
      await cdp.send('Page.navigate', { url })
      await sleep(600)
      // 等 SPA 路由与首屏数据落定
      for (let i = 0; i < 40; i++) {
        const ready = await cdp.eval(`document.readyState === 'complete' && !!document.querySelector('main, .content, .shell')`)
        if (ready) break
        await sleep(200)
      }
      await sleep(shot.wait)

      const check = await cdp.eval(`location.pathname`)
      if (check.includes('/login')) throw new Error(`${shot.name} 掉回登录页`)

      const info = await cdp.eval(`(() => {
        const text = (document.body.innerText || '').replace(/\\s+/g, ' ').trim()
        return {
          rows: document.querySelectorAll('table tbody tr').length,
          chars: text.length,
          head: text.slice(0, 420),
          alerts: [...document.querySelectorAll('[role=alert], .form-error')].map((e) => e.innerText.trim()).slice(0, 2),
        }
      })()`)
      console.log('     rows=%d chars=%d %s', info.rows, info.chars, info.alerts.length ? '⚠ ' + info.alerts.join(' | ') : '')
      console.log('     ', info.head)

      const { data } = await cdp.send('Page.captureScreenshot', {
        format: 'png', captureBeyondViewport: false, fromSurface: true,
      })
      const file = path.join(RAW, shot.name + '.png')
      await writeFile(file, Buffer.from(data, 'base64'))
      console.log('  截', shot.name, '→', path.relative(ROOT, file))
    }

    ws.close()
  } finally {
    chrome.kill('SIGKILL')
  }

  /* 转 webp：1200 与 800 两档 */
  console.log('\n转 webp …')
  for (const shot of list) {
    const src = path.join(RAW, shot.name + '.png')
    if (!existsSync(src)) continue
    for (const w of [1200, 800]) {
      const dst = path.join(OUT, `${shot.name}-${w}.webp`)
      await new Promise((resolve, reject) => {
        const p = spawn('cwebp', ['-quiet', '-q', '82', '-resize', String(w), '0', src, '-o', dst])
        p.on('exit', (code) => (code === 0 ? resolve() : reject(new Error('cwebp ' + code))))
      })
      console.log('  ', path.relative(ROOT, dst))
    }
  }
  console.log('\n完成。')
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
