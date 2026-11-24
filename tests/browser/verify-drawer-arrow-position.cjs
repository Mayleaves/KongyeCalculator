const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { pathToFileURL } = require('node:url');
const html = path.resolve(__dirname, '../../app/src/main/assets/calculator.html');
const executable = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const source = fs.readFileSync(html,'utf8');
new Function(source.split('<script>')[1].split('</script>')[0]);
assert(!/<(?:script|link|img)\b[^>]*(?:src|href)=["']https?:/i.test(source));
const profile = fs.mkdtempSync(path.join(__dirname,'browser-profile-'));
const chrome = spawn(executable,[
  '--headless=new','--no-first-run','--disable-background-networking','--disable-gpu',
  '--remote-debugging-port=0','--user-data-dir='+profile,'about:blank'
],{windowsHide:true,stdio:'ignore'});
const pause = ms => new Promise(resolve => setTimeout(resolve,ms));
let socket;
async function run() {
  const portFile = path.join(profile,'DevToolsActivePort');
  for (let i = 0; i < 80 && !fs.existsSync(portFile); i++) await pause(125);
  const port = fs.readFileSync(portFile,'utf8').split('\n')[0];
  const [target] = await fetch('http://127.0.0.1:'+port+'/json/list').then(r=>r.json());
  const pending = new Map();
  let id = 0;
  socket = new WebSocket(target.webSocketDebuggerUrl);
  socket.addEventListener('message',event => {
    const data = JSON.parse(event.data);
    if(data.method==='Log.entryAdded')console.log(data.params.entry.text);
    const command = pending.get(data.id);
    if (!command) return;
    pending.delete(data.id);
    if (data.error) command.reject(new Error(data.error.message));
    else command.resolve(data.result);
  });
  await new Promise((resolve,reject) => {
    socket.addEventListener('open',resolve,{once:true});
    socket.addEventListener('error',reject,{once:true});
  });
  function send(method,params = {}) {
    return new Promise((resolve,reject) => {
      pending.set(++id,{resolve,reject});
      socket.send(JSON.stringify({id,method,params}));
    });
  }
  async function js(expression) {
    const data = await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});
    if (data.exceptionDetails) throw new Error(data.exceptionDetails.exception?.description || data.exceptionDetails.text);
    return data.result.value;
  }
  const click = selector => js('document.querySelector('+JSON.stringify(selector)+').click()');
  const key = name => click('#keyboard section:first-child button[aria-label="'+name+'"]');
  const named = (selector,label) => js('[...document.querySelectorAll('+JSON.stringify(selector)+')].find(node=>node.textContent.trim()==='+JSON.stringify(label)+').click()');
  const sectionName = () => js('document.querySelector("#tools [aria-pressed=true]").ariaLabel');
  const heading = () => js('document.querySelector("#utility-title").textContent');
  const answer = () => js('document.querySelector("#answer").textContent');
  const drawer = () => click('#open-drawer');
  const page = async title => {
    await drawer();
    await js('[...document.querySelectorAll(".menu-row")].find(node=>node.lastChild.textContent==='+JSON.stringify(title)+').click()');
    assert.equal(await heading(),title);
  };
  async function shot(name) {
    const {data} = await send('Page.captureScreenshot',{format:'png'});
    fs.writeFileSync(path.join(__dirname,name),Buffer.from(data,'base64'));
  }
  await send('Log.enable');
  await send('Emulation.setDeviceMetricsOverride',{width:412,height:880,deviceScaleFactor:1,mobile:true});
  await send('Emulation.setFocusEmulationEnabled',{enabled:true});
  await send('Page.navigate',{url:pathToFileURL(html).href});
  for (let i=0;i<60 && await js('document.documentElement.dataset.ready') !== 'true';i++) await pause(100);
  assert.equal(await js('document.documentElement.dataset.ready'),'true');






await click('#skip-brand-splash');await pause(250);
const sampleNav=async action=>js('new Promise(resolve=>{'+action+';const samples=[];let start=performance.now();function frame(){samples.push(document.getElementById("open-drawer").getBoundingClientRect().left-document.getElementById("stage").getBoundingClientRect().left);if(performance.now()-start<210)requestAnimationFrame(frame);else resolve(samples);}frame();})');
const geometry=()=>js('(()=>{const s=document.getElementById("stage").getBoundingClientRect(),b=document.getElementById("open-drawer").getBoundingClientRect(),home=document.querySelector(".home-card-shell").getBoundingClientRect();return {x:b.left-s.left,y:b.top-s.top,homeX:home.left-s.left,width:b.width,hit:document.elementFromPoint(b.left+b.width/2,b.top+b.height/2)?.closest("#open-drawer")?.id||null};})()');
for(const width of [320,360,412,480,720]){
 await send('Emulation.setDeviceMetricsOverride',{width,height:880,deviceScaleFactor:1,mobile:true});
 for(const theme of ['light','dark']){
  await js('setTheme('+JSON.stringify(theme)+');toggleDrawer(false)');await pause(270);
  const closed=await geometry();
  await click('#open-drawer');await pause(80);
  const middle=await geometry();assert(Math.abs((middle.x-closed.x)-middle.homeX)<2,JSON.stringify({width,theme,middle,closed}));
  await pause(200);const open=await geometry();
  assert(Math.abs(open.x-open.homeX-closed.x)<1);assert.equal(open.hit,'open-drawer');
  assert(open.homeX>200);assert.equal(await js('document.querySelector("#open-drawer").dataset.back'),'true');
  if(width===412)await shot('drawer-arrow-fixed-'+theme+'.png');
  const enter=await sampleNav('openPage("计算设置")');assert(enter.every(x=>Math.abs(x-closed.x)<1),JSON.stringify({width,theme,enter}));const tool=await geometry();assert.equal(tool.x,closed.x);assert.equal(tool.hit,'open-drawer');
  const leave=await sampleNav('document.getElementById("open-drawer").click()');assert(leave.every(x=>Math.abs(x-open.x)<1),JSON.stringify({width,theme,leave}));const back=await geometry();assert(Math.abs(back.x-open.x)<1);assert.equal(back.hit,'open-drawer');
  await click('#open-drawer');await pause(270);assert(Math.abs((await geometry()).x-closed.x)<1);
 }
}
console.log('PASS drawer arrow follows calculator throughout animation at five widths in both themes, remains clickable, and aligns on tool-page return');
}
run().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{socket?.close();chrome.kill();});
