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





await click('#skip-brand-splash');await pause(430);await send('Emulation.setTouchEmulationEnabled',{enabled:true});
await js('openPage("键位布局管理")');await pause(150);
assert.equal(await js('document.querySelectorAll(".manage-grid button").length'),80);
assert.equal(await js('JSON.stringify([...document.querySelectorAll(".manage-grid button")].map(n=>n.innerHTML))'),await js('JSON.stringify([...document.querySelectorAll("#keyboard .key-grid button")].map(n=>n.innerHTML))'),'Every management key must render exactly like its keyboard key');
assert.ok(await js('document.querySelectorAll(".manage-grid .kslot").length')>20);
assert.equal(await js('document.querySelectorAll(".manage-grid .stats-key").length'),1);
assert.equal(await js('document.querySelector(".manage-grid [aria-label=叉积] .math-cross")!==null'),true);
await shot('keyboard-manager-matched-light.png');await js('setTheme("dark")');await shot('keyboard-manager-matched-dark.png');
const original=await js('JSON.stringify(tokens)');
await click('.manage-grid button[data-drag-position="2:0"]');await click('.manage-grid button[data-drag-position="2:1"]');assert.equal(await js('keyLayout[2][0]'),'b');assert.equal(await js('JSON.stringify(tokens)'),original);
await js('document.querySelector("#utility-body .scroll-body").scrollTo({top:0,behavior:"instant"})');await pause(70);
const before=await js('keyLayout[0].slice(0,2)');const positions=await js('[...document.querySelector(".manage-grid").children].slice(0,2).map(n=>{const r=n.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})');
await send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[positions[0]]});await pause(450);assert.equal(await js('document.querySelectorAll(".key-drag-ghost").length'),1);
await send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[positions[1]]});await send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});assert.deepEqual(await js('keyLayout[0].slice(0,2)'),before.reverse());
assert.equal(await js('JSON.stringify(tokens)'),original);
await js('openPage("单位转换器")');await pause(150);assert.equal(await js('document.querySelectorAll(".manage-grid").length'),0);
await js('history=[{date:"2026/11/24",expression:"log_4956(92)",rawTokens:[{key:"logₐ",fields:["4956","92"]}],result:"0.5314527872"},{date:"2026/11/24",expression:"(5)/(2)",rawTokens:[{key:"a/b",fields:["5","2"]}],result:"2.5",exact:"5/2"},{date:"2026/11/24",expression:"(5)^(8)",rawTokens:[{key:"xʸ",fields:["5","8"]}],result:"390625"},{date:"2026/11/23",expression:"7+8",rawTokens:["7","+","8"],result:"15"}];openPage("历史记录")');await pause(160);
assert.equal(await js('document.querySelectorAll(".history-day").length'),2);assert.equal(await js('document.querySelector(".history-day-count").textContent'),'3 条记录');
assert.equal(await js('getComputedStyle(document.querySelector(".history-date")).backgroundColor'),'rgba(0, 0, 0, 0)');
assert.equal(await js('getComputedStyle(document.querySelector(".list-row .history-math")).fontSize'),'16px');
assert.equal(await js('getComputedStyle(document.querySelector(".list-row .history-math-result")).fontSize'),'16px');
assert.ok(await js('document.querySelectorAll(".history-math .fraction, .history-math mfrac").length')>0);
await js('setTheme("light")');await shot('history-clean-light.png');await js('setTheme("dark")');await shot('history-clean-dark.png');
await named('#utility-actions button','导出');assert.match(await js('document.querySelector("#utility-body textarea").value'),/log_4956/);
await click('.list-row');await pause(160);assert.equal(await js('tokens[0].key'),'logₐ');assert.equal(await js('document.querySelector("#utility-body").classList.contains("history-page")'),true);
await js('replaceExpression(String());insertFormulaPreset("k*x+b",true);document.querySelectorAll("#expression input").forEach((n,i)=>{n.value=["7","+9"][i];n.dispatchEvent(new Event("input"))});document.activeElement.blur();formulaField=null;');
const formula=await js('JSON.stringify(tokens)');
await shot('formula-actions-bottom-dark.png');await js('setTheme("light")');await shot('formula-actions-bottom-light.png');
assert.ok(await js('(()=>{const a=document.querySelector("#formula-actions").getBoundingClientRect(),b=document.querySelector(".edit-tools").getBoundingClientRect();return b.top-a.bottom>=0&&b.top-a.bottom<20})()'));
await named('#formula-actions button','绘制图像');await pause(150);assert.equal(await heading(),'函数图像');
assert.equal(await js('document.querySelector("#utility-body").classList.contains("history-page")'),false);
assert.ok(await js('document.querySelector(".function-graph .curve").getAttribute("d").length')>100);
assert.equal(await js('document.querySelector(".function-graph").dataset.method'),'explicit');
assert.ok(await js('document.querySelector(".graph-viewport").getBoundingClientRect().height')>500);
assert.equal(await js('document.querySelectorAll(".tool-equation .nested-empty").length'),0);
assert.equal(await js('document.querySelector("#workbench").hidden'),true);
await shot('function-graph-light.png');await js('setTheme("dark")');await shot('function-graph-dark.png');
await click('[aria-label="放大图像"]');await pause(70);assert.equal(await js('document.querySelector(".function-graph").dataset.zoom'),'1.5');
const graph=await js('(()=>{const r=document.querySelector(".graph-viewport").getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()');
await send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[graph]});await send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:graph.x+40,y:graph.y+30}]});await send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await pause(70);assert.notEqual(await js('document.querySelector(".function-graph").dataset.center'),'0,0');
await named('.graph-controls button','重置');await pause(70);assert.equal(await js('document.querySelector(".function-graph").dataset.center'),'0,0');
await click('.graph-visible');assert.equal(await js('document.querySelector(".function-graph .curve").getAttribute("d")'),'');await click('.graph-visible');
await click('#back');await pause(170);assert.equal(await js('JSON.stringify(tokens)'),formula);assert.equal(await js('document.querySelector("#utility").hidden'),true);
await named('#formula-actions button','求解方程');await pause(600);assert.equal(await heading(),'求解方程');
assert.ok(await js('document.querySelectorAll(".solve-step").length')>=1);
assert.doesNotMatch(await js('document.querySelector(".solve-result").textContent'),/无法|计算中/);
const symbolic=await js('document.querySelector(".solve-result .solve-math").textContent');assert.match(symbolic,/x/);assert.match(symbolic,/y/);
await shot('equation-solver-dark.png');
await click('.solve-substitution input');await pause(450);assert.match(await js('document.querySelector(".solve-result").textContent'),/1\.285714/);
await click('.solve-target:nth-child(2)');await pause(450);assert.match(await js('document.querySelector(".solve-result").textContent'),/9/);
await js('setTheme("light")');await shot('equation-solver-light.png');
await click('#back');await pause(170);assert.equal(await js('JSON.stringify(tokens)'),formula);
await js('replaceExpression(String());insertFormulaPreset("x²/a²+y²/b²",false);document.querySelectorAll("#expression input").forEach((n,i)=>{n.value=["x²","4","y²","9"][i];n.dispatchEvent(new Event("input"))})');
await named('#formula-actions button','绘制图像');await pause(180);assert.equal(await js('document.querySelector(".function-graph").dataset.method'),'implicit');assert.ok(await js('document.querySelector(".function-graph .curve").getAttribute("d").length')>100);await shot('conic-graph-light.png');
await click('#back');await pause(160);assert.equal(await js('document.querySelector("#utility").hidden'),true);
for(const mode of ['light','dark']){await js('setTheme('+JSON.stringify(mode)+')');assert.equal(await js('getComputedStyle(document.querySelector("#keyboard section:nth-child(2)")).borderTopColor'),await js('getComputedStyle(document.querySelector("#tools")).borderRightColor'));}
console.log('PASS manager shares live renderer, swap/drag without expression edits, compact structured history/export/recall, matching divider colours, bottom actions, independent graph/pan/zoom/reset/visibility, symbolic/numeric solver and conics, preserved expression on return');
}
run().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{socket?.close();chrome.kill();});
