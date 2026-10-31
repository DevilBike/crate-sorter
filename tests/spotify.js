const { chromium } = require('playwright'); const fs=require('fs');
(async()=>{ const b=await chromium.launch(); const ctx=await b.newContext({viewport:{width:400,height:850}}); const p=await ctx.newPage(); const errs=[]; p.on('pageerror',e=>errs.push(e.message));
 const map={'react.production.min.js':'node_modules/react/umd/react.production.min.js','react-dom.production.min.js':'node_modules/react-dom/umd/react-dom.production.min.js','babel.min.js':'node_modules/@babel/standalone/babel.min.js'};
 const counts={}; let n429=0, nAbort=0, failItems=1;
 const J=(r,o,st=200,h={})=>r.fulfill({status:st,body:JSON.stringify(o),contentType:'application/json',headers:{'access-control-allow-origin':'*',...h}});
 const tracks=Array.from({length:230},(_,i)=>({item:{id:'t'+i,uri:'spotify:track:t'+i,name:'Song '+i,type:'track',album:{name:'A',release_date:String(1960+i%60)},artists:[{id:'a'+(i%25),name:'Artist '+(i%25)},{id:'feat'+i,name:'F'}]}}));
 const G=['house','metal','folk','classical','hip hop','rock','pop','reggaeton','lo-fi','disco'];
 await p.route('**/*',async r=>{const u=r.request().url(), m=r.request().method(); if(u.startsWith('file:'))return r.continue(); const k=Object.keys(map).find(k=>u.endsWith(k)); if(k)return r.fulfill({path:map[k],contentType:'text/javascript'}); if(u.includes('tailwindcss'))return r.fulfill({body:'window.tailwind={}',contentType:'text/javascript'});
  if(u.includes('musicbrainz.org')){ counts.mb=(counts.mb||0)+1; if(counts.mb===3) return r.fulfill({status:503,body:'',headers:{'access-control-allow-origin':'*'}}); const q=decodeURIComponent(new URL(u).searchParams.get('query')).match(/"(.*)"/)[1]; const id=+q.split(' ')[1]||0; return J(r,{artists:[{name:q,score:100,tags:[{name:G[id%10],count:2}]}]}); }
  if(u.includes('audioscrobbler')){ counts.lfm=(counts.lfm||0)+1; const sp=new URL(u).searchParams; if(sp.get('api_key')!=='0123456789abcdef0123456789abcdef') return J(r,{error:10,message:'Invalid API key'},403); if(counts.lfm===4) return J(r,{error:29,message:'Rate'}); const art=sp.get('artist'); const n=+(art.split(' ')[1])||0;
   if(sp.get('method')==='artist.getinfo') return J(r,{artist:{name:art,tags:{tag:[{name:['chillout','metal','folk','classical','Hip-Hop'][n%5]},{name:'seen live'}]},similar:{artist:[{name:'Artist '+((n+5)%25)},{name:'Artist '+((n+10)%25)}]}}});
   return J(r,{toptags:{tag:[{name:'mellow',count:100},{name:'noise',count:2}]}}); }
  if(!u.includes('api.spotify.com'))return r.abort(); const path=new URL(u).pathname.replace('/v1',''); const key=m+' '+path.replace(/\/[a-z]+\d+/g,'/:id'); counts[key]=(counts[key]||0)+1;
  if(path==='/me')return J(r,{id:'bike',display_name:'Bike'});
  if(path==='/me/playlists'&&m==='GET')return J(r,{items:[{id:'p1',name:'Mine',owner:{id:'bike',display_name:'Bike'},public:false,items:{total:3}},{id:'p2',name:'Road trip',collaborative:true,owner:{id:'bike'},public:false,items:{total:2}},{id:'p3',name:'Their mix',owner:{id:'sam',display_name:'Sam'},public:true,items:{total:40}}],next:null,total:3});
  if(path.endsWith('/items')&&m==='GET'){ const pid=path.split('/')[2]; if(pid==='p3') return J(r,{error:{message:'forbidden'}},403); const rows=pid==='p1'?[{item:{id:'x1',uri:'spotify:track:x1',name:'Own 1',type:'track',album:{name:'A',release_date:'1999'},artists:[{id:'a1',name:'Artist 1'}]}},tracks[0]]:[{item:{id:'x2',uri:'spotify:track:x2',name:'Own 2',type:'track',album:{name:'A',release_date:'2005'},artists:[{id:'a2',name:'Artist 2'}]}}]; return J(r,{items:rows,total:rows.length,next:null}); }
  if(path==='/me/tracks'){const off=+new URL(u).searchParams.get('offset')||0; return J(r,{items:tracks.slice(off,off+50),total:230,next:off+50<230?'https://api.spotify.com/v1/me/tracks?limit=50&offset='+(off+50):null});}
  if(path.startsWith('/artists/')){ if(n429++===2) return J(r,{error:{message:'rate'}},429,{'retry-after':'1'}); if(nAbort++===5) return r.abort(); const id=+path.split('a')[2]||0; return J(r,{genres:[G[id%10]]});}
  if(path==='/me/playlists'&&m==='POST')return J(r,{id:'new'+counts[key]},201);
  if(path.endsWith('/items')&&m==='POST'){ if(counts[key]===4 && failItems-->0) return J(r,{error:{message:'boom'}},400); return J(r,{snapshot_id:'x'},201);}
  return J(r,{},404);});
 await p.addInitScript(()=>{ window.__calls=[]; window.__nat={appVersion:()=>'1.4',sleep:(id,ms)=>setTimeout(()=>window.__crateWake(id),ms/20),setWorking:on=>window.__calls.push(on),copy:t=>window.__calls.push('copy'),batteryUnrestricted:()=>false,askBattery:()=>window.__calls.push('bat'), mbStart:j=>{ const a=JSON.parse(j); window.__q=(window.__q||[]).concat(a); return window.__q.length; }, mbPoll:()=>{ const r={}; (window.__q||[]).splice(0,10).forEach(([id])=>r[id]=['rock']); window.__res=Object.assign(window.__res||{},r); return JSON.stringify({running:(window.__q||[]).length>0,left:(window.__q||[]).length,results:window.__res,log:['x']}); }}; if(!sessionStorage.x){sessionStorage.x=1; localStorage.clear(); localStorage.setItem('cs.blockedUntil', String(Date.now()+1500)); localStorage.setItem('cs.clientId','"cid"'); localStorage.setItem('cs.tok',JSON.stringify({access:'A',refresh:'R',exp:Date.now()+3e6}));}});
 let html=fs.readFileSync('../index.html','utf8').replace('const MIN_GAP = 600','const MIN_GAP = 5').replaceAll('seconds * 1000 + 500','seconds * 20').replace('mbLast + mbGap','mbLast + 5').replace('lfmLast + lfmGap','lfmLast + 5').replace('sleep(10000 * (tries + 1))','sleep(20)').replace('sleep(5000 * (tries + 1))','sleep(50)');
 fs.writeFileSync('t.html','<!doctype html><meta charset=utf-8><style>'+fs.readFileSync('tw.css','utf8')+'</style>'+html); await p.goto('file://'+process.cwd()+'/t.html');
 await p.waitForSelector('text=Start Full Automation',{timeout:30000});

 const txt=async()=>(await p.textContent('#root')).replace(/\s+/g,' ');
 const bucketN=async key=>p.evaluate(k=>{const el=document.querySelector('#on'+k); return el.closest('li').querySelector('.num').textContent;},key);
 const allN=async()=>{const o={}; for(const k of ['chill','energy','acoustic','focus','electronic','hiphop','rock','pop','global','throwback']) o[k]=await bucketN(k); return JSON.stringify(o);};
 console.log('before', await allN());
 await p.fill('input[aria-label="Last.fm API key"]','badkey'); console.log('save disabled', await p.isDisabled('button:has-text("Save key")'));
 await p.fill('input[aria-label="Last.fm API key"]','ffffffffffffffffffffffffffffffff'); await p.click('button:has-text("Save key")');
 await p.click('button:has-text("Look up tags")'); await p.waitForSelector('text=Back to the plan',{timeout:30000}); console.log('bad key:', (await txt()).match(/Last.fm did not accept[^.]*\./)?.[0]);
 await p.click('text=Back to the plan'); await p.click('text=Change key'); await p.fill('input[aria-label="Last.fm API key"]','0123456789abcdef0123456789abcdef'); await p.click('button:has-text("Save key")');
 await p.click('button:has-text("Look up")'); await p.waitForSelector('text=/Done\\. The plan now uses/',{timeout:60000});
 console.log('lfm requests', counts.lfm, (await txt()).match(/Looking up[^.]*\./)?.[0]);
 await p.click('text=Back to the plan'); console.log('after', await allN()); console.log((await txt()).match(/Tagged:[^.]*\./)?.[0]);

 // custom playlist
 await p.click('button:has-text("Add your own")'); await p.fill('#cname','My Deathcore'); await p.fill('#cwords','metal, Artist 2'); await p.click('button:text-is("Save")');
 console.log('with custom', await allN(), await p.evaluate(()=>{const el=[...document.querySelectorAll('li')].find(l=>l.textContent.includes('My Deathcore')); return el&&el.querySelector('.num').textContent;}));
 await p.click('button:has-text("Sort by hand")'); await p.fill('input[type=search]','Song 4'); await p.waitForTimeout(100);
 console.log('why', (await txt()).match(/Song 4Artist 4.{0,120}/)?.[0]);
 await p.fill('input[type=search]','Song 2'); console.log('why2', (await txt()).match(/Song 2Artist 2.{0,80}/)?.[0]);
 await p.fill('input[type=search]',''); await p.click('button:has-text("Done")');
 await p.click('li span:text-is("Edit")'); await p.click('text=Remove this playlist'); await p.click('button:has-text("Yes, remove")'); console.log('removed', !(await txt()).includes('My Deathcore'));
 // hand sort two artists then look at suggestions
 await p.click('button:has-text("Sort by hand")'); await p.click('button:text-is("Artists")');
 await p.click('li button:has-text("Artist 0")'); console.log('open?', await p.locator('li:has-text("Artist 0") div button:has-text("Chill")').count(), await p.locator('li button:has-text("Artist 0")').count()); await p.click('li:has-text("Artist 0") div button:has-text("Chill")');
 await p.fill('input[type=search]','Artist 3'); await p.click('li button:has-text("Artist 3")'); await p.click('li:has-text("Artist 3") div button:has-text("Focus")'); await p.fill('input[type=search]','');
 console.log('manual', await p.evaluate(()=>localStorage.getItem('cs.manual'))); await p.click('button:has-text("Suggested")'); await p.waitForTimeout(200);
 console.log('sugg', (await txt()).match(/Suggested \d+/)?.[0], '|', (await txt()).match(/artists? with a suggestion.{0,300}/)?.[0]);
 await p.screenshot({path:'shot-spotify.png'});
 await p.click('li button:text-is("Accept") >> nth=0'); console.log('after accept', (await txt()).match(/Suggested \d+/)?.[0]);
 await p.click('button:has-text("Accept all")'); console.log('after all', (await txt()).match(/Suggested ?\d*/)?.[0], await p.evaluate(()=>Object.keys(JSON.parse(localStorage.getItem('cs.manual')).artists).length));
 console.log('mbgenres polluted?', await p.evaluate(()=>Object.keys(JSON.parse(localStorage.getItem('cs.mbgenres')||'{}')).some(k=>k.startsWith('lf'))));
 console.log(errs); await b.close();})();
