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





await click('#skip-brand-splash');await pause(350);
for(const theme of ['light','dark']) {
 await js('setTheme('+JSON.stringify(theme)+');tabs[3].click()');
 const colors=await js("({key:getComputedStyle(document.querySelector('#keyboard section:nth-child(3) button')).backgroundColor,formula:getComputedStyle(document.querySelector('.preset-catalog .formula-choices button')).backgroundColor,selected:getComputedStyle(document.documentElement).getPropertyValue('--selected').trim()})");
 assert.equal(colors.formula,colors.key);
 if(theme==='dark')assert.equal(colors.selected,'#FFFFFF');
 await shot('white-catalog-'+theme+'.png');
}
assert.equal(await js('buildKongyeAppIconSVG("dark").includes("#F2B200")'),false);
const summaries=[];
for(const [name,rows] of await js('classicFormulas')) {
 for(const [label,code] of rows) {
  for(const blank of [false,true]) {
   await js('replaceExpression(String());insertFormulaPreset('+JSON.stringify(code)+','+label.startsWith('y=')+')');
   if(blank)await js("document.querySelectorAll('#expression input').forEach(n=>{n.value='';n.dispatchEvent(new Event('input'))})");
   const before=await js('JSON.stringify(tokens)');
   const equation=await js('preparePresetEquation().displayEquation');
   await named('#formula-actions button','绘制图像');await pause(90);
   assert.equal(await heading(),'函数图像');
   assert.equal(await js('mathToolState.error||null'),null,name+':'+equation);
   assert((await js("document.querySelector('.function-graph .curve').getAttribute('d').length"))>30,name+':'+equation);
   await click('#back');await pause(150);
   assert.equal(await js('JSON.stringify(tokens)'),before);
   await named('#formula-actions button','求解方程');
   for(let i=0;i<70&&await js("!!document.querySelector('.solve-progress')");i++)await pause(100);
   const output=await js("document.querySelector('.solve-result').textContent");
   assert(!/无法|计算中|请检查/.test(output),name+':'+equation+' '+output);
   assert((await js("document.querySelectorAll('.solve-result .solver-mathml').length"))>0,name+' formatted roots');
   if(code==='a*x^2+b*x+c'&&!blank) {
    assert((await js("document.querySelectorAll('.solve-result msqrt').length"))>0);
    assert((await js("document.querySelectorAll('.solve-result mfrac').length"))>0);
    for(const theme of ['light','dark']){await js('setTheme('+JSON.stringify(theme)+')');await shot('flat-solver-'+theme+'.png');}
    const sizes=await js("[...document.querySelectorAll('.solve-math')].map(n=>getComputedStyle(n).fontSize)");
    assert(sizes.every(s=>s==='20px'));
   }
   await click('#back');await pause(150);
   assert.equal(await js('JSON.stringify(tokens)'),before);
   summaries.push([code,blank?'blank':'default',equation]);
  }
 }
}
await js("replaceExpression('');press('a/b');document.querySelectorAll('#expression input').forEach((n,i)=>{n.value=['7','9'][i];n.dispatchEvent(new Event('input'))})");
assert((await js("[...document.querySelectorAll('#expression input')].every(n=>getComputedStyle(n).borderTopStyle==='dashed')")));
await shot('uniform-template-slots.png');
console.log('PASS 14 presets with default and blank values, graph and solver for all 28 cases, shared background, white theme, root/fraction typography, preserved editor',summaries);
}
run().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{socket?.close();chrome.kill();});
