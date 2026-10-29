// Capture wide "data strip" crops: a single panel/table, no page chrome, at 2x.
// Used for the two hero scenes where legibility matters more than context.
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(__dirname, '..', 'assets');
const BASE = process.env.ERP_WEB || 'http://127.0.0.1:5175';

const STRIPS = {
  // key -> [route, selector, nth match]
  'mrp-plans': ['/planning/mrp', '.content > .table-wrap', 0],
  'finance-trial': ['/finance/reports', '.content > .table-wrap', 0],
  'finance-balance': ['/finance/reports', '.content > .grid--2', 0],
};

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 2, locale: 'zh-CN', colorScheme: 'light' });
const page = await ctx.newPage();
await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
await page.fill('#username', 'admin');
await page.fill('#password', 'admin123');
await Promise.all([page.waitForURL(u => !u.pathname.startsWith('/login')), page.click('button[type=submit]')]);

for (const [key, [route, sel, nth]] of Object.entries(STRIPS)) {
  await page.goto(BASE + route, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  await page.addStyleTag({ content: '*,*::before,*::after{animation:none !important;transition:none !important;caret-color:transparent}' });
  const loc = page.locator(sel).nth(nth);
  const box = await loc.boundingBox();
  const file = path.join(OUT, `${key}.png`);
  await loc.screenshot({ path: file });
  console.log(key.padEnd(16), JSON.stringify(box && Object.fromEntries(Object.entries(box).map(([k, v]) => [k, Math.round(v)]))), Math.round(fs.statSync(file).size / 1024) + 'KB');
}
await browser.close();
