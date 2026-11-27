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







  async function ready() {
    for(let n=0;n<60&&await js("document.documentElement.dataset.ready")!=='true';n++)await pause(100);
    assert.equal(await js("document.documentElement.dataset.ready"),'true');
    await click('#skip-brand-splash');
  }
  const persistent={history:[{expression:'7+8',result:'15',date:'2026/11/27',rawTokens:['7','+','8']}],
    functions:{f:'x+1'},variables:{a:9},calculationOptions:{decimals:6,columns:5},mode:2};
  for(const draft of [['7','+','8'],['y','=',{math:'parameter',values:['2']},'x']]) {
    await js('window.removeEventListener("pagehide",retain);localStorage.setItem(storeKey,JSON.stringify('+JSON.stringify({...persistent,draft,cursor:999,bracketHints:[0],lastAnswer:'15',confirmed:true})+'))');
    await send('Page.reload');await pause(150);await ready();
    assert.equal(await js('JSON.stringify(tokens)'),'[]');
    assert.equal(await js('cursor'),0);assert.equal(await js('confirmed'),false);
    assert.equal(await js('bracketHints.length'),0);
    assert.equal(await js('document.querySelectorAll("#expression .token,#expression .expr-tpl,#expression .formula-input").length'),0);
    assert.equal(await js('document.querySelector("#answer").textContent.trim()'),'');
    assert.deepEqual(await js('history'),persistent.history);
    assert.deepEqual(await js('functions'),persistent.functions);
    assert.deepEqual(await js('variables'),persistent.variables);
    assert.equal(await js('calculationOptions.decimals'),6);
    assert.equal(await js('lastAnswer'),'15');
    await js("press('1');press('+');press('2');retain()");
    assert.equal(await js('serializeTokens(tokens)'),'1+2');
    const stored=await js('JSON.parse(localStorage.getItem(storeKey))');
    for(const field of ['draft','cursor','bracketHints','confirmed'])assert(!Object.hasOwn(stored,field));
    await send('Page.reload');await pause(150);await ready();
    assert.equal(await js('tokens.length'),0);
    assert.deepEqual(await js('history'),stored.history);
  }
  await shot('startup-empty.png');
  console.log('PASS empty startup for old numeric/formula drafts, fresh edits stay session-only, history/functions/variables/settings/Ans retained');
}
run().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{socket?.close();chrome.kill();});
