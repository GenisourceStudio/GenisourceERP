import { chromium } from 'playwright';
const BASE='http://127.0.0.1:5175';
const b = await chromium.launch({channel:'chrome',headless:true});
const ctx = await b.newContext({viewport:{width:1600,height:1000},locale:'zh-CN'});
const p = await ctx.newPage();
await p.goto(BASE+'/login',{waitUntil:'networkidle'});
await p.fill('#username','admin'); await p.fill('#password','admin123');
await Promise.all([p.waitForURL(u=>!u.pathname.startsWith('/login')), p.click('button[type=submit]')]);
for (const r of ['/planning/mrp','/finance/reports']) {
  await p.goto(BASE+r,{waitUntil:'networkidle'}); await p.waitForTimeout(1500);
  const info = await p.evaluate(() => {
    const out=[];
    document.querySelectorAll('.content > *').forEach((el,i)=>{
      const r=el.getBoundingClientRect();
      out.push({i, tag:el.tagName, cls:el.className, box:[Math.round(r.x),Math.round(r.y),Math.round(r.width),Math.round(r.height)], text:(el.innerText||'').slice(0,40).replace(/\n/g,'|')});
    });
    return out;
  });
  console.log('\n===',r);
  for (const x of info) console.log(x.i, x.tag, '.'+String(x.cls).replace(/ /g,'.'), JSON.stringify(x.box), x.text);
}
await b.close();
