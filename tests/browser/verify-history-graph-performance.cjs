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






await click('#skip-brand-splash');await pause(200);
const parity=await js('(()=>{const sources=["y-x","x^2+y^2-4","((x-1)/2)^2-((y+2)/3)^2-1","y-(x^2+x)","y-2^x","y-logbase(2,x)","y-root(3,x)","y-sin(x)","y-(abs(x)+sqrt(x))","y-(-x^2)","y-2^-x","y-50%x","y-3!x","y-log10(x)","y-arctan(x)"];let count=0;for(const source of sources){const fn=compileGraphExpression(source);for(const x of [-3,-.5,0,.5,2,30])for(const y of [-2,0,1]){let expected;try{expected=evaluate(source,{x,y});}catch{continue;}const actual=fn(x,y);if(Math.abs(actual-expected)>1e-10*Math.max(1,Math.abs(expected)))throw Error(source+" expected "+expected+" got "+actual);count++;}}return count;})()');
assert(parity>200);console.log('Numeric compiler parity',parity);
await js('replaceExpression(String());insertFormulaPreset("k*x+b",true);insertFormulaPreset("a*x^2+b*x+c",true);insertFormulaPreset("a^x",true);history=[{date:"2026/11/25",expression:readableHistoryExpression(),rawTokens:clone(tokens),result:"0；0；1"},{date:"2026/11/25",expression:"1/2",rawTokens:[{key:"a/b",fields:["1","2"]}],result:"0.5",exact:"1/2"},{date:"2026/11/25",expression:"449149123456789",result:"4.4914912346e+14"}];openPage("历史记录");void 0;');
await pause(150);
const bounds=await js('(()=>{const group=document.querySelector(".history-math .expression-group"),rows=group.querySelector(".group-rows").getBoundingClientRect(),brace=group.querySelector(".group-brace").getBoundingClientRect(),equal=group.parentElement.querySelector(".history-equals").getBoundingClientRect();return {brace:[brace.x,brace.right,brace.top,brace.bottom],rows:[rows.x,rows.right,rows.top,rows.bottom],equal:[equal.x,equal.right]};})()');
assert(bounds.brace[1]<=bounds.rows[0]);assert(bounds.equal[0]>=bounds.rows[1]);
assert(Math.abs(bounds.brace[2]-bounds.rows[2])<2);assert(Math.abs(bounds.brace[3]-bounds.rows[3])<2);
for(const theme of ['light','dark']){await js('setTheme('+JSON.stringify(theme)+')');await shot('history-group-fixed-'+theme+'.png');}
await click('#back');
for(const width of [360,412,480]){
await send('Emulation.setDeviceMetricsOverride',{width,height:880,deviceScaleFactor:1,mobile:true});
for(const theme of ['light','dark']){await js('setTheme('+JSON.stringify(theme)+');savedResults=["449149123456789"];drawSavedResults()');
const geometry=await js('(()=>{const bar=document.querySelector(".edit-tools").getBoundingClientRect();return {height:bar.height,buttons:[...document.querySelectorAll(".edit-tools>.icon")].map(n=>{const r=n.getBoundingClientRect();return[r.top,r.height,r.top+r.height/2]})};})()');
assert.equal(geometry.height,48);assert(geometry.buttons.every(r=>r[1]===46&&Math.abs(r[2]-geometry.buttons[0][2])<.1));}}
await send('Emulation.setDeviceMetricsOverride',{width:412,height:880,deviceScaleFactor:1,mobile:true});await shot('toolbar-reference-dark.png');
await js('replaceExpression(String());insertFormulaPreset("k*x+b",true)');
await named('#formula-actions button','绘制图像');await pause(100);
await js('window.nativeCalls=0;window.originalEvaluate=evaluate;evaluate=()=>{nativeCalls++;throw Error("Sampling must not use general evaluator")};');
await click('[aria-label="放大图像"]');await pause(100);
assert.equal(await js('nativeCalls'),0);assert((await js('document.querySelector(".curve").getAttribute("d").length'))>100);
await js('evaluate=window.originalEvaluate;');
console.log('PASS grouped history braces, results, fractions, numeric fonts, shared 48px toolbar at three widths, and graph sampling without general evaluator');
}
run().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{socket?.close();chrome.kill();});
