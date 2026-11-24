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
const waitSolved=async()=>{for(let i=0;i<100;i++){if(await js('document.querySelector(".solve-result")?.getAttribute("aria-busy")==="false"'))return;await pause(60);}throw Error('Solver timeout');};
await js('replaceExpression(String());insertFormulaPreset("k*x+b",true);insertFormulaPreset("a*x^2+b*x+c",true);insertFormulaPreset("(x−a)²+(y−b)²=r²",false)');
const before=await js('JSON.stringify(tokens)');await named('#formula-actions button','绘制图像');await pause(150);
assert.equal(await js('mathToolState.curves.length'),3);assert.equal(await js('document.querySelectorAll(".function-graph .curve").length'),3);
assert.equal(await js('document.querySelectorAll(".graph-legend-row").length'),3);
assert.equal(await js('new Set([...document.querySelectorAll(".curve")].map(p=>p.style.stroke)).size'),3);
assert.equal(await js('[...document.querySelectorAll(".curve")].every(p=>p.getAttribute("d").length>100)'),true);

assert.equal(await js('document.querySelectorAll(".graph-legend-row .graph-visible").length'),3);
const paths=await js('[...document.querySelectorAll(".curve")].map(n=>n.getAttribute("d"))');
await click('.graph-legend-row:nth-child(2) .graph-visible');
assert.equal(await js('document.querySelectorAll(".curve")[1].getAttribute("d")'),'');
assert.equal(await js('document.querySelectorAll(".curve")[0].getAttribute("d")'),paths[0]);
assert.equal(await js('document.querySelectorAll(".curve")[2].getAttribute("d")'),paths[2]);
await click('[aria-label="放大图像"]');await pause(70);
assert.equal(await js('document.querySelectorAll(".curve")[1].getAttribute("d")'),'');
await click('.graph-reset');await pause(70);
await click('.graph-legend-row:nth-child(2) .graph-visible');
assert.equal(await js('document.querySelectorAll(".curve")[1].getAttribute("d")'),paths[1]);
for(let i=1;i<=3;i++)await click('.graph-legend-row:nth-child('+i+') .graph-visible');
assert.equal(await js('[...document.querySelectorAll(".curve")].every(n=>!n.getAttribute("d"))'),true);
assert.equal(await js('document.querySelector(".graph-status").textContent'),'');
await shot('grouped-curves-all-hidden.png');
await click('.graph-legend-row:nth-child(3) .graph-visible');
assert.equal(await js('document.querySelector(".function-graph").dataset.visibleCurves'),'001');
await shot('grouped-curves-circle-only.png');
await click('.graph-legend-row:nth-child(1) .graph-visible');await click('.graph-legend-row:nth-child(2) .graph-visible');
await click('[aria-label="放大图像"]');await pause(70);assert.equal(await js('document.querySelector(".function-graph").dataset.curves'),'3');
for(const theme of ['light','dark']){await js('setTheme('+JSON.stringify(theme)+')');await shot('grouped-graphs-'+theme+'.png');}
await click('#open-drawer');await pause(260);assert.equal(await js('JSON.stringify(tokens)'),before);assert.equal(await js('document.querySelector("#open-drawer").dataset.back'),'false');
await js('replaceExpression(String());insertFormulaPreset("a*x^2+b*x+c",true)');await named('#formula-actions button','求解方程');await waitSolved();
const timing=await js('(()=>{const start=performance.now();document.querySelector(".solve-substitution input").click();return {ms:performance.now()-start,visible:!document.querySelector(".solve-known").hidden,busy:document.querySelector(".solve-result").getAttribute("aria-busy")};})()');
assert.equal(timing.visible,true);assert(timing.ms<30);await waitSolved();
await js('window.originalNerdamer=nerdamer;nerdamer=()=>{throw Error("No symbolic calculations allowed on UI thread")};void 0;');
for(let i=0;i<6;i++){await click('.solve-substitution input');await pause(12);}await waitSolved();
assert.doesNotMatch(await js('document.querySelector(".solve-result").textContent'),/无法|计算中/);
await js('const n=document.querySelector(".solve-known");n.value="2";n.dispatchEvent(new Event("input"));n.value="6";n.dispatchEvent(new Event("input"));');await pause(180);await waitSolved();
assert.doesNotMatch(await js('document.querySelector(".solve-result").textContent'),/无法|计算中/);
await js('nerdamer=window.originalNerdamer;void 0;');console.log('Checkbox synchronous time',timing.ms);
await click('#open-drawer');await pause(280);
await js('window.morphPaths=[...document.querySelectorAll("#open-drawer path")];toggleDrawer(true);');await pause(95);
const middle=await js('getComputedStyle(document.querySelector("#open-drawer path")).transform');await pause(200);
const end=await js('getComputedStyle(document.querySelector("#open-drawer path")).transform');assert.notEqual(middle,end);assert.notEqual(middle,'none');
assert.equal(await js('window.morphPaths.every((n,i)=>n===document.querySelectorAll("#open-drawer path")[i])'),true);
await click('#open-drawer');await pause(280);assert.equal(await js('getComputedStyle(document.querySelector("#open-drawer path")).transform'),'none');
console.log('PASS all grouped functions plotted with distinct legends, immediate checkbox feedback, worker-only solving, rapid input cancellation and reversible SVG path morph');
}
run().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{socket?.close();chrome.kill();});
