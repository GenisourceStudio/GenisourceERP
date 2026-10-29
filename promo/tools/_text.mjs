import { chromium } from 'playwright';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const b = await chromium.launch({channel:'chrome',headless:true});
const p = await b.newPage({viewport:{width:1920,height:1080}});
await p.goto('file://'+path.join(ROOT,'storyboard.html'),{waitUntil:'load'});
await p.evaluate(()=>window.__ready);
const scenes = await p.evaluate(()=>[...document.querySelectorAll('.scene')].map(s=>({a:+s.dataset.start,b:+s.dataset.end})));
for (const s of scenes) {
  const t = (s.a + s.b)/2;
  const txt = await p.evaluate((tt)=>{
    window.seek(tt);
    const sc=[...document.querySelectorAll('.scene')].find(x=>x.style.visibility==='visible');
    if(!sc) return '(none)';
    const parts=[];
    for (const el of sc.querySelectorAll('*')) {
      const cs=getComputedStyle(el);
      if (cs.visibility==='hidden'||cs.display==='none') continue;
      const own=[...el.childNodes].filter(n=>n.nodeType===3&&n.textContent.trim()).map(n=>n.textContent.trim()).join(' ');
      if (own && parseFloat(cs.opacity)>0.05) parts.push(own);
    }
    return parts.join(' | ');
  }, t);
  console.log(`\n[${s.a}-${s.b}] t=${t.toFixed(1)}\n  ${txt}`);
}
await b.close();
