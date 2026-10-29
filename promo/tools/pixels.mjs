// Pixel statistics for rendered stills — a machine-checkable stand-in for "does it look right".
// Chrome decodes the PNGs, so no extra image library is needed.
//   node tools/pixels.mjs <dir> [region x,y,w,h]
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const dir = process.argv[2];
const region = process.argv[3] ? process.argv[3].split(',').map(Number) : null;
const files = fs.readdirSync(dir).filter(f => f.endsWith('.png')).sort();

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage();
await page.goto('about:blank');

for (const f of files) {
  const b64 = fs.readFileSync(path.join(dir, f)).toString('base64');
  const stat = await page.evaluate(async ({ b64, region }) => {
    const img = new Image();
    img.src = 'data:image/png;base64,' + b64;
    await img.decode();
    const c = document.createElement('canvas');
    c.width = img.width; c.height = img.height;
    const ctx = c.getContext('2d');
    ctx.drawImage(img, 0, 0);
    const box = region ? { x: region[0], y: region[1], w: region[2], h: region[3] }
                       : { x: 0, y: 0, w: img.width, h: img.height };
    const data = ctx.getImageData(box.x, box.y, box.w, box.h).data;
    let sum = 0, sum2 = 0, n = 0, dark = 0;
    const hist = new Array(8).fill(0);
    for (let i = 0; i < data.length; i += 4) {
      const l = 0.2126 * data[i] + 0.7152 * data[i + 1] + 0.0722 * data[i + 2];
      sum += l; sum2 += l * l; n++;
      if (l < 40) dark++;
      hist[Math.min(7, Math.floor(l / 32))]++;
    }
    const mean = sum / n;
    const sd = Math.sqrt(Math.max(0, sum2 / n - mean * mean));
    return { w: img.width, h: img.height, mean: +mean.toFixed(1), sd: +sd.toFixed(1),
             darkPct: +(100 * dark / n).toFixed(1), hist: hist.map(v => +(100 * v / n).toFixed(0)) };
  }, { b64, region });
  console.log(f.padEnd(18), `${stat.w}x${stat.h}`, 'mean', String(stat.mean).padStart(6), 'sd', String(stat.sd).padStart(5), 'dark%', String(stat.darkPct).padStart(5), 'hist', stat.hist.join(' '));
}
await browser.close();
