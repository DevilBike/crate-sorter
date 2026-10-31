const { chromium } = require('playwright'); const fs=require('fs');
(async()=>{
 const b=await chromium.launch(); const p=await b.newPage({viewport:{width:400,height:850}});
 const errs=[]; p.on('pageerror',e=>errs.push(e.message)); p.on('console',m=>{if(m.type()==='error')errs.push(m.text())});
 const map={'react.production.min.js':'node_modules/react/umd/react.production.min.js','react-dom.production.min.js':'node_modules/react-dom/umd/react-dom.production.min.js','babel.min.js':'node_modules/@babel/standalone/babel.min.js'};
 await p.route('**/*',r=>{const u=r.request().url(); if(u.startsWith('file:'))return r.continue(); const k=Object.keys(map).find(k=>u.endsWith(k)); if(k)return r.fulfill({path:map[k],contentType:'text/javascript'}); if(u.includes('tailwindcss'))return r.fulfill({body:'window.tailwind={}',contentType:'text/javascript'}); r.abort();});
 const html='<!doctype html><meta charset=utf-8><style>'+fs.readFileSync('tw.css','utf8')+'</style>'+fs.readFileSync('../index.html','utf8');
 fs.writeFileSync('t.html',html); await p.goto('file://'+process.cwd()+'/t.html'); await p.waitForTimeout(1500);
 await p.click('text=Try it with a sample library'); await p.waitForTimeout(1800);
 const txt=async()=>(await p.textContent('#root')).replace(/\s+/g,' ');
 const count=async name=>(await txt()).match(new RegExp(name.replace(/[&]/g,'.')+'[^0-9]*?(\\d+)Icon|'))&&0;
 const bucketN=async key=>p.evaluate(k=>{const el=document.querySelector('#on'+k); return el.closest('li').querySelector('.num').textContent;},key);

 const names=async()=>p.evaluate(()=>[...document.querySelectorAll('input[id^=on]')].map(i=>{const li=i.closest('li'); return li.querySelector('.font-semibold').textContent+'='+li.querySelector('.num').textContent;}).join(' | '));
 console.log('genres', (await txt()).match(/Biggest genres in your library.{0,400}/)?.[0]);
 await p.click('li:has-text("hip hop") button:has-text("Make playlist")'); await p.waitForTimeout(100);
 console.log('after make', await names());
 await p.click('button:has-text("Combine two")'); await p.selectOption('#cb','chill'); await p.selectOption('#ca','focus'); 
 console.log('default name', await p.inputValue('#cn')); await p.click('button:text-is("Combine")');
 console.log('after combine', await names()); console.log((await txt()).match(/Combined with[^|]{0,30}/)?.[0], (await txt()).match(/Combined into[^|]{0,20}/)?.[0]);
 await p.click('button:has-text("Sort by hand")'); console.log('sorter chips', await p.evaluate(()=>[...document.querySelectorAll('.sticky .overflow-x-auto button')].map(b=>b.textContent).join(',')));
 await p.click('button:has-text("Done")');
 await p.screenshot({path:'shot-demo.png', fullPage:true});
 await p.click('button:has-text("Remove…")'); await p.click('li:has-text("Throwbacks") span:text-is("Remove")'); await p.click('li:has-text("Hip Hop") span:text-is("Remove")'); await p.click('button:text-is("Done")'); console.log('after remove', await names()); console.log((await txt()).match(/Bring back removed playlists[^)]*\)/)?.[0]); console.log('genres now', (await txt()).match(/Biggest genres.{0,300}/)?.[0]); await p.click('text=Bring back removed'); console.log('restored', await names());
 await p.click('span:text-is("Separate")'); console.log('after separate', await names());
 await p.click('text=Start Full Automation'); await p.click('text=Run preview'); await p.waitForSelector('text=Back to the plan',{timeout:20000}); console.log('dry', (await txt()).match(/Would create[^’]*’[^’]*’/g)?.join(' ; '));
 await p.reload(); await p.waitForTimeout(1200); console.log('errs', errs); await b.close();
})();
