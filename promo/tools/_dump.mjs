import { chromium } from 'playwright';
const BASE='http://127.0.0.1:5175';
const b = await chromium.launch({channel:'chrome',headless:true});
const ctx = await b.newContext({viewport:{width:1600,height:1000},locale:'zh-CN'});
const p = await ctx.newPage();
await p.goto(BASE+'/login',{waitUntil:'networkidle'});
await p.fill('#username','admin'); await p.fill('#password','admin123');
await Promise.all([p.waitForURL(u=>!u.pathname.startsWith('/login')), p.click('button[type=submit]')]);
for (const r of ['/','/planning/mrp','/sales/orders','/finance/reports','/planning/production-orders','/system/integration']) {
  await p.goto(BASE+r,{waitUntil:'networkidle'}); await p.waitForTimeout(1200);
  const t = await p.evaluate(()=> (document.querySelector('main')||document.body).innerText.replace(/\n{2,}/g,'\n'));
  console.log('\n========== '+r+' ==========');
  console.log(t.slice(0,1800));
}
await b.close();
