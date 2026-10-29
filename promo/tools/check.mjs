// Deeper layout checks than render.mjs's audit: it catches text that collides with other
// text (the audit only knows about overflow and out-of-frame boxes). Run before rendering.
//
//   node tools/check.mjs            # all scenes, sampled every 0.5s
//   node tools/check.mjs 23,61      # specific times
import { chromium } from 'playwright';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const URL_PAGE = 'file://' + path.join(ROOT, 'storyboard.html');

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
await page.goto(URL_PAGE, { waitUntil: 'load' });
await page.evaluate(() => window.__ready);
const DUR = await page.evaluate(() => window.DURATION);

const probe = (t) => page.evaluate((tt) => {
  window.seek(tt);
  const visible = [...document.querySelectorAll('.scene')].filter(s => s.style.visibility === 'visible');
  const scene = visible[0];
  if (!scene) return { t: tt, none: true };

  // 1. collect leaf text boxes
  const boxes = [];
  for (const el of scene.querySelectorAll('*')) {
    const cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' || cs.opacity === '0') continue;
    const own = [...el.childNodes].filter(n => n.nodeType === 3 && n.textContent.trim()).map(n => n.textContent.trim()).join(' ');
    if (!own) continue;
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) continue;
    boxes.push({
      text: own.slice(0, 26),
      x: Math.round(r.left), y: Math.round(r.top),
      w: Math.round(r.width), h: Math.round(r.height),
      color: cs.color,
      fs: parseFloat(cs.fontSize),
      chrome: !!el.closest('.chrome'),   // browser-chrome micro labels are allowed to be small
    });
  }

  // 2. pairwise overlap (ignore nested: if one contains the other, it is the same text)
  const clashes = [];
  for (let i = 0; i < boxes.length; i++) {
    for (let j = i + 1; j < boxes.length; j++) {
      const a = boxes[i], b = boxes[j];
      const ox = Math.max(0, Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x));
      const oy = Math.max(0, Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y));
      const area = ox * oy;
      if (area < 150) continue;
      // a containing b (or vice versa) is nesting, not collision
      const aInB = a.x >= b.x - 2 && a.y >= b.y - 2 && a.x + a.w <= b.x + b.w + 2 && a.y + a.h <= b.y + b.h + 2;
      const bInA = b.x >= a.x - 2 && b.y >= a.y - 2 && b.x + b.w <= a.x + a.w + 2 && b.y + b.h <= a.y + a.h + 2;
      if (aInB || bInA) continue;
      clashes.push({ a: a.text, b: b.text, area, box: [a.x, a.y, a.w, a.h] });
    }
  }

  // 3. tiny type: below 10.5px after design scale is unreadable at 1080p
  const tiny = boxes.filter(b => b.fs < 10.5 && b.w > 8 && !b.chrome).map(b => ({ text: b.text, fs: b.fs }));

  return { t: tt, n: boxes.length, clashes: clashes.slice(0, 6), tiny: tiny.slice(0, 6) };
}, t);

const times = process.argv[2]
  ? process.argv[2].split(',').map(Number)
  : Array.from({ length: Math.ceil(DUR / 0.5) }, (_, i) => i * 0.5);

let bad = 0;
for (const t of times) {
  const r = await probe(t);
  if (r.none) continue;
  if (r.clashes.length || r.tiny.length) {
    bad++;
    if (bad <= 12) {
      console.log(`t=${t.toFixed(1)}`);
      for (const c of r.clashes) console.log('   clash', JSON.stringify(c));
      for (const x of r.tiny) console.log('   tiny ', JSON.stringify(x));
    }
  }
}
console.log(bad ? `\n${bad} sampled times have defects` : `clean: text sits on its own, no sub-10.5px type, ${times.length} samples`);
await browser.close();
