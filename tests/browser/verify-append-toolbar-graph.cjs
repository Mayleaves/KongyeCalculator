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
await js("replaceExpression('');insertFormulaPreset('k*x+b',true)");
const first=await js('JSON.stringify(tokens)');
await js("insertFormulaPreset('a*x^2+b*x+c',true)");
assert.equal(await js("document.querySelectorAll('#expression .group-row').length"),2);
assert.equal(await js('JSON.stringify(tokens.slice(0,tokens.indexOf("\\n")))'),first);
assert.equal(await js("document.activeElement.closest('.group-row')===document.querySelector('#expression .group-row:last-child')"),true);
assert.equal(await js('preparePresetEquation().displayEquation'),'y=x+x^2');
await click('#undo');assert.equal(await js('JSON.stringify(tokens)'),first);
await click('#redo');assert.equal(await js("document.querySelectorAll('#expression .group-row').length"),2);
await js("replaceExpression('')");await click('#group');
await js("insertFormulaPreset('k*x+b',true);insertFormulaPreset('a*x^2+b*x+c',true)");
assert.equal(await js('tokens.filter(t=>t==="\\n").length'),1);
await click('#group');
await js("insertFormulaPreset('log_a_x',true)");
assert.equal(await js("document.querySelectorAll('#expression .group-row').length"),3);
assert.equal(await js('tokens.filter(t=>t==="\\n").length'),2);
await js("document.querySelector('#expression input').focus()");
assert.equal(await js('preparePresetEquation().displayEquation'),'y=x');
await js("document.querySelectorAll('#expression .group-row')[1].querySelector('input').focus()");
assert.equal(await js('preparePresetEquation().displayEquation'),'y=x+x^2');
await named('#formula-actions button','绘制图像');await pause(150);
assert.equal(await js('mathToolState.displayEquation'),'y=x+x^2');
assert((await js("document.querySelector('.function-graph .curve').getAttribute('d').length"))>100);
assert.equal(await js("(()=>{const f=document.querySelector('.graph-footer').getBoundingClientRect(),v=document.querySelector('.graph-viewport').getBoundingClientRect(),c=document.querySelector('.graph-page>.graph-controls').getBoundingClientRect();return f.bottom<=v.top+1&&c.top>=v.bottom-1})()"),true);
await click('[aria-label="放大图像"]');await pause(80);
assert.equal(await js("document.querySelector('.function-graph').dataset.zoom"),'1.5');
await named('.graph-controls button','重置');await pause(80);
assert.equal(await js("document.querySelector('.function-graph').dataset.zoom"),'1');
assert.equal(await js("(()=>{const chart=document.querySelector('.function-graph'),w=chart.viewBox.baseVal.width,h=chart.viewBox.baseVal.height;return [...chart.querySelectorAll('text')].every(n=>{const r=n.getBBox();return r.x>=0&&r.x+r.width<=w&&r.y>=0&&r.y+r.height<=h})})()"),true);
for(const theme of ['light','dark']){await js('setTheme('+JSON.stringify(theme)+')');await shot('refined-graph-'+theme+'.png');}
await click('#back');await pause(160);
for(const theme of ['light','dark']){
 await js('setTheme('+JSON.stringify(theme)+')');
 await js("savedResults=[];drawSavedResults()");
 const emptyPositions=await js("['clear','group','previous','next','delete'].map(id=>{const r=document.getElementById(id).getBoundingClientRect();return [r.x,r.width]})");
 assert(emptyPositions.every((r,i)=>Math.abs(r[0]-i*412/5)<1&&Math.abs(r[1]-412/5)<1));
 let filledPositions;
 for(const value of ['1','123456','449149123456789']){
  await js('savedResults=['+JSON.stringify(value)+'];drawSavedResults()');
  const positions=await js("['clear','group','previous','next','delete'].map(id=>{const r=document.getElementById(id).getBoundingClientRect();return [r.x,r.width]})");
  if(filledPositions)assert.deepEqual(positions,filledPositions);else {filledPositions=positions;assert.notDeepEqual(positions,emptyPositions);}
  assert(Math.abs(positions[0][1]-412/6)<1);
  assert.equal(await js("document.querySelector('#reuse').title"),value);
 }
 await shot('fixed-toolbar-and-group-'+theme+'.png');
 const colors=await js("[...document.querySelectorAll('#formula-actions button')].map(n=>getComputedStyle(n).color)");
 assert.equal(colors[0],colors[1]);
}
await js("replaceExpression('');savedResults=['449149123456789'];drawSavedResults()");
await click('#reuse');await click('.saved-results-list button');
assert.equal(await js('serializeTokens(tokens)'),'449149123456789');
await js('replaceExpression("(1)\\n(2)");bracketHints=[2,6];cursor=1;');
const previousHints=await js('[...bracketHints]');
await js("insertFormulaPreset('k*x+b',true)");
assert.equal(await js("tokens.slice(0,3).join('')"),'(1)');
assert.equal(await js("tokens.slice(-3).join('')"),'(2)');
assert.equal(await js('bracketHints[0]'),2);
await click('#undo');assert.deepEqual(await js('[...bracketHints]'),previousHints);
console.log('PASS cumulative formula insertion, existing blank row reuse, group key, undo/redo, active-row tools, graph controls/labels, fixed toolbar geometry and intact full stored values');
}
run().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{socket?.close();chrome.kill();});
