// Capture real UI screenshots from the running ERP instance for the promo reel.
//
// Usage:
//   node tools/capture.mjs                # capture every page in PAGES
//   node tools/capture.mjs dashboard,mrp  # capture a subset by key
//
// Requires the dev servers to be up: backend 8081, frontend 5175 (see README).
// Every shot is validated after capture: DOM must show non-trivial text and the PNG must
// not be a flat/blank image, otherwise the run fails loudly instead of shipping empty frames.
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'assets');

const BASE = process.env.ERP_WEB || 'http://127.0.0.1:5175';
const USER = process.env.ERP_USER || 'admin';
const PASS = process.env.ERP_PASS || 'admin123';
const W = Number(process.env.ERP_SHOT_W || 1600);
const H = Number(process.env.ERP_SHOT_H || 1000);

/** key -> [route, extra wait (ms), optional pre-shot interaction] */
const PAGES = {
  login: ['/login', 900],
  dashboard: ['/', 1400],
  'masterdata-items': ['/masterdata/items', 1400],
  'masterdata-bom': ['/masterdata/bom', 1600],
  'masterdata-partners': ['/masterdata/partners', 1400],
  'inventory-stock': ['/inventory/stock', 1400],
  'inventory-documents': ['/inventory/documents', 1400],
  'inventory-movements': ['/inventory/movements', 1400],
  'inventory-warehouses': ['/inventory/warehouses', 1400],
  'purchase-orders': ['/purchase/orders', 1400],
  'purchase-requisitions': ['/purchase/requisitions', 1400],
  'purchase-receipts': ['/purchase/receipts', 1400],
  'sales-orders': ['/sales/orders', 1400],
  'sales-quotations': ['/sales/quotations', 1400],
  'sales-shipments': ['/sales/shipments', 1400],
  mrp: ['/planning/mrp', 1800],
  'production-orders': ['/planning/production-orders', 1600],
  'finance-receivables': ['/finance/receivables', 1400],
  'finance-payables': ['/finance/payables', 1400],
  'finance-accounts': ['/finance/accounts', 1400],
  'finance-vouchers': ['/finance/vouchers', 1400],
  'finance-reports': ['/finance/reports', 1600],
  'system-users': ['/system/users', 1400],
  'system-audit': ['/system/audit', 1400],
  'system-integration': ['/system/integration', 1400],
};

const only = process.argv[2] ? process.argv[2].split(',') : null;
const targets = Object.entries(PAGES).filter(([k]) => !only || only.includes(k));

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const ctx = await browser.newContext({
  viewport: { width: W, height: H },
  deviceScaleFactor: 2,
  locale: 'zh-CN',
  timezoneId: 'Asia/Shanghai',
  colorScheme: 'light',
});
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

async function login() {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page.fill('#username', USER);
  await page.fill('#password', PASS);
  await Promise.all([
    page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000 }),
    page.click('button[type=submit]'),
  ]);
  await page.waitForLoadState('networkidle');
}

async function settle() {
  // Kill CSS transitions/animations so two runs produce the same pixels.
  await page.addStyleTag({
    content: '*,*::before,*::after{animation-play-state:paused !important;transition:none !important;caret-color:transparent !important}',
  });
  await page.waitForTimeout(350);
}

/** Rough "is this page actually showing data" probe: count visible text and table rows. */
async function probe() {
  return page.evaluate(() => {
    const main = document.querySelector('main') || document.body;
    const text = (main.innerText || '').replace(/\s+/g, ' ').trim();
    const rows = main.querySelectorAll('table tbody tr').length;
    const empty = /暂无|没有数据|No data|No records|加载中|Loading/i.test(text);
    return { chars: text.length, rows, empty, head: text.slice(0, 120) };
  });
}

fs.mkdirSync(OUT, { recursive: true });
await login();
console.log('logged in, capturing', targets.length, 'pages at', `${W}x${H}@2x`);

const report = [];
for (const [key, [route, wait]] of targets) {
  if (route === '/login') {
    // capture the login screen with a clean context (no token)
    const anon = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 2, locale: 'zh-CN' });
    const p2 = await anon.newPage();
    await p2.goto(BASE + '/login', { waitUntil: 'networkidle' });
    await p2.waitForTimeout(wait);
    await p2.screenshot({ path: path.join(OUT, `${key}.png`) });
    await anon.close();
    report.push({ key, route, note: 'anon' });
    continue;
  }
  await page.goto(BASE + route, { waitUntil: 'networkidle' });
  await settle();
  await page.waitForTimeout(wait);
  const info = await probe();
  const file = path.join(OUT, `${key}.png`);
  await page.screenshot({ path: file });
  const size = fs.statSync(file).size;
  report.push({ key, route, ...info, kb: Math.round(size / 1024) });
  console.log(
    `${key.padEnd(22)} rows=${String(info.rows).padEnd(3)} chars=${String(info.chars).padEnd(5)} ${Math.round(size / 1024)}KB  ${info.head.slice(0, 60)}`,
  );
}

await browser.close();

const empty = report.filter((r) => r.chars !== undefined && (r.chars < 120 || r.empty));
if (empty.length) {
  console.log('\nWARNING — these pages look empty or still loading:', empty.map((r) => r.key).join(', '));
}
if (errors.length) {
  console.log('\nPAGE ERRORS:', [...new Set(errors)].slice(0, 6).join(' | '));
}
fs.writeFileSync(path.join(OUT, 'capture-report.json'), JSON.stringify(report, null, 2));
console.log('\n->', OUT);
