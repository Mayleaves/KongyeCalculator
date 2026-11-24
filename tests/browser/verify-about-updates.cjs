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
  await send('Page.enable');
  await send('Page.addScriptToEvaluateOnNewDocument',{source:"\nwindow.fakeUpdateState={phase:'idle',currentVersion:'1.0.1',currentCode:35,downloaded:0,total:0};\nwindow.fakeReleases=Array.from({length:25},(_,i)=>({name:'1.0.'+(25-i),tag:'v1.0.'+(25-i),date:'2026-11-24T08:00:00Z',body:i%2?'修复公式对齐':'优化函数绘图',url:'https://github.com/Mayleaves/KongyeCalculator/releases/tag/v1.0.'+(25-i)}));\nwindow.nativeCalls=[];\nwindow.CalculatorHost={setDark(){},calculate(){return '{}'},getUpdateState(){return JSON.stringify(fakeUpdateState)},\n checkUpdates(){nativeCalls.push('check');setTimeout(()=>{fakeUpdateState={...fakeUpdateState,phase:'available',name:'1.0.2',notes:'优化公式显示',total:10485760};window.onAppUpdateEvent({kind:'update',state:fakeUpdateState})},30)},\n downloadUpdate(){nativeCalls.push('download');fakeUpdateState={...fakeUpdateState,phase:'downloading',downloaded:5242880};onAppUpdateEvent({kind:'update',state:fakeUpdateState})},\n cancelUpdateDownload(){nativeCalls.push('cancel');fakeUpdateState={...fakeUpdateState,phase:'cancelled',message:'下载已取消'};onAppUpdateEvent({kind:'update',state:fakeUpdateState})},\n installUpdate(){nativeCalls.push('install')},openGitHub(url){nativeCalls.push(url)},\n getReleaseHistory(id,page,search){nativeCalls.push({id,page,search});const found=fakeReleases.filter(r=>(r.name+' '+r.body).includes(search));const pages=Math.max(1,Math.ceil(found.length/10));page=Math.max(1,Math.min(page,pages));setTimeout(()=>onAppUpdateEvent({kind:'history',id,page,pages,total:found.length,items:found.slice((page-1)*10,page*10)}),25)}\n};"});
  await send('Page.navigate',{url:pathToFileURL(html).href});
  for (let i=0;i<60 && await js('document.documentElement.dataset.ready') !== 'true';i++) await pause(100);
  assert.equal(await js('document.documentElement.dataset.ready'),'true');







await click('#skip-brand-splash');await pause(200);
await click('#open-drawer');await pause(200);await click('#drawer-about');await pause(200);
assert.equal(await heading(),'关于');assert.deepEqual(await js('[...document.querySelectorAll(".about-row")].map(n=>n.textContent)'),['检查更新','更新历史']);
assert.equal(await js('document.querySelector("#drawer-about").ariaLabel'),'关于');
await named('.about-row','检查更新');await pause(90);assert.equal(await heading(),'检查更新');assert.match(await js('document.querySelector(".update-title").textContent'),/发现新版本/);
assert.equal(await js('document.querySelector(".app-update-notice").open'),true);await named('.update-notice-actions button','稍后');assert.equal(await js('!!document.querySelector(".app-update-notice")'),false);await js('updateNoticeKey="";showUpdateNotice(fakeUpdateState);');await named('.update-notice-actions button','立即下载');assert.equal(await js('document.querySelector(".update-progress").value'),50);assert.match(await js('document.querySelector(".update-progress-details").textContent'),/5.0 MB.*10.0 MB.*50%/);
await click('#open-drawer');await pause(100);assert.equal(await heading(),'关于');await named('.about-row','检查更新');await pause(60);assert.equal(await js('document.querySelector(".update-progress").value'),50);
await named('.update-actions button','取消下载');assert.match(await js('document.querySelector(".update-title").textContent'),/下载已取消/);
await named('.update-actions button','重新下载');
await js('fakeUpdateState={...fakeUpdateState,phase:"ready",message:"下载完成，可以安装",downloaded:10485760};onAppUpdateEvent({kind:"update",state:fakeUpdateState});');
assert.equal(await js('document.querySelector(".update-progress").value'),100);await named('.update-actions button','安装更新');assert.equal(await js('nativeCalls.includes("install")'),true);
for(const theme of ['light','dark']){await js('setTheme('+JSON.stringify(theme)+')');await shot('app-update-'+theme+'.png');}
await click('#open-drawer');await pause(100);await named('.about-row','更新历史');await pause(80);
assert.equal(await heading(),'更新历史');assert.equal(await js('!!document.querySelector("#utility-actions .release-github svg")'),true);assert.equal(await js('document.querySelector(".release-history-header").children.length'),1);assert.equal(await js('document.querySelectorAll(".release-entry").length'),10);assert.equal(await js('document.querySelector(".release-pager span").textContent'),'1 / 3');
await named('.release-pager button','下一页');await pause(70);assert.equal(await js('document.querySelector(".release-pager span").textContent'),'2 / 3');
await named('.release-pager button','下一页');await pause(70);assert.equal(await js('document.querySelectorAll(".release-entry").length'),5);assert.equal(await js('document.querySelector(".release-pager button:last-child").disabled'),true);
await js('var search=document.querySelector(".release-search");search.value="绘图";search.dispatchEvent(new Event("input"));');await pause(280);assert.equal(await js('document.querySelector(".release-summary").textContent'),'共 13 个版本');
await named('.release-pager button','下一页');await pause(70);assert.equal(await js('document.querySelectorAll(".release-entry").length'),3);
await click('.release-github');assert.equal(await js('nativeCalls.includes("https://github.com/Mayleaves/KongyeCalculator")'),true);
for(const theme of ['light','dark']){await js('setTheme('+JSON.stringify(theme)+')');await shot('release-history-'+theme+'.png');}
await js('var search=document.querySelector(".release-search");search.value="不存在";search.dispatchEvent(new Event("input"));');await pause(280);assert.match(await js('document.querySelector(".release-empty").textContent'),/没有匹配/);
await click('#open-drawer');await pause(100);assert.equal(await heading(),'关于');
console.log('PASS two-entry About, nested back navigation, local progress/cancel/retry/install, persistent progress, searchable 25-release pagination, GitHub link and empty results in both themes');
}
run().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{socket?.close();chrome.kill();});

