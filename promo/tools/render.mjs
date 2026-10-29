// Frame renderer for the promo storyboard.
// Usage:
//   node tools/render.mjs stills 6,20,45        # spot-check frames (audits layout too)
//   node tools/render.mjs frames [from] [to]    # render every frame at 30 fps
//   node tools/render.mjs audit                 # scan the whole timeline for layout defects
//
// Requires: npm i playwright  (uses the locally installed Google Chrome channel)
import { chromium } from 'playwright';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const URL_PAGE = 'file://' + path.join(ROOT, 'storyboard.html');
const FPS = 30;

const mode = process.argv[2] || 'stills';
const OUT = mode === 'frames'
  ? (process.env.FRAME_DIR || path.join(os.tmpdir(), 'genisource-promo-frames'))
  : path.join(os.tmpdir(), 'genisource-promo-stills');

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
page.on('console', m => { if (m.type() === 'error') console.log('PAGE ERR:', m.text()); });
await page.goto(URL_PAGE, { waitUntil: 'load' });
await page.evaluate(() => window.__ready);

const DUR = await page.evaluate(() => window.DURATION);

async function audit(t) {
  return page.evaluate((tt) => {
    window.seek(tt);
    const out = { t: tt, overflow: [], outside: [] };
    const visible = [...document.querySelectorAll('.scene')].filter(s => s.style.visibility === 'visible');
    for (const sc of visible) {
      for (const el of sc.querySelectorAll('*')) {
        if (el.classList.contains('grid') || el.classList.contains('grid-dark')) continue;
        if (el.classList.contains('cutX')) continue;
        // Decorative layers that are deliberately larger than the frame: the full-bleed
        // screenshot is 1920x1200 and gets cropped by its own overflow:hidden parent.
        if (el.classList.contains('scrim')) continue;
        if (el.closest('.bleed')) continue;
        const cs = getComputedStyle(el);
        if (cs.visibility === 'hidden' || cs.display === 'none') continue;
        if (el.classList.contains('anim') && parseFloat(cs.opacity) < 0.05) continue;
        if (el.children.length === 0 && el.textContent.trim() &&
            el.scrollWidth > el.clientWidth + 2 && cs.overflow !== 'hidden' && el.clientWidth > 0) {
          out.overflow.push({ text: el.textContent.trim().slice(0, 30), sw: el.scrollWidth, cw: el.clientWidth });
        }
        const r = el.getBoundingClientRect();
        if (r.width > 0 && r.height > 0 && (r.right > 1922 || r.left < -2 || r.bottom > 1084 || r.top < -30)) {
          out.outside.push({ text: el.textContent.trim().slice(0, 26), rect: [r.left, r.top, r.right, r.bottom].map(Math.round) });
        }
      }
    }
    return out;
  }, t);
}

if (mode === 'stills') {
  const times = (process.argv[3] || '1,6,13,20,28,36,45,53,61,69,78,87').split(',').map(Number);
  fs.mkdirSync(OUT, { recursive: true });
  for (const t of times) {
    const res = await audit(t);
    if (res.overflow.length) console.log('OVERFLOW', JSON.stringify(res.overflow));
    if (res.outside.length) console.log('OUTSIDE', JSON.stringify(res.outside));
    await page.evaluate(tt => window.seek(tt), t);
    await page.screenshot({ path: path.join(OUT, `still_f${String(Math.round(t * FPS)).padStart(4, '0')}.png`) });
  }
  console.log('stills ->', OUT);
} else if (mode === 'audit') {
  const bad = [];
  for (let t = 0; t < DUR; t += 0.5) {
    const res = await audit(t);
    if (res.overflow.length || res.outside.length) bad.push(res);
  }
  console.log(bad.length ? JSON.stringify(bad.slice(0, 8), null, 1) : `clean: 0 defects across ${Math.ceil(DUR / 0.5)} samples`);
} else {
  fs.mkdirSync(OUT, { recursive: true });
  const total = Math.round(DUR * FPS);
  const from = Number(process.argv[3] || 0);
  const to = Number(process.argv[4] || total);
  const t0 = Date.now();
  for (let f = from; f < to; f++) {
    await page.evaluate(tt => window.seek(tt), f / FPS);
    await page.screenshot({ path: path.join(OUT, `f_${String(f).padStart(5, '0')}.png`) });
    if (f % 300 === 0) console.log(`frame ${f}/${to} (${((Date.now() - t0) / 1000).toFixed(0)}s)`);
  }
  console.log(`rendered ${to - from} frames -> ${OUT}`);
}

await browser.close();
