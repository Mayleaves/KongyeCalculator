const projectGitHub='https://github.com/Mayleaves/KongyeCalculator';
let updateScreen=null,releaseScreen=null,releaseRequest=0;
let lastUpdateState={phase:'idle',currentVersion:'1.0.2',currentCode:36,downloaded:0,total:0};
window.onAppUpdateEvent=event=>{
  if(event.kind==='update'){lastUpdateState=event.state;updateScreen?.(event.state);}
  if(event.kind==='history')releaseScreen?.(event);
};
function openProjectGitHub(url=projectGitHub){
  if(nativeHost?.openGitHub)nativeHost.openGitHub(url);
  else window.open(url,'_blank','noopener,noreferrer');
}
function renderAbout(body){
  const wrap=element('div','scroll-body about-page'),list=element('div','about-menu');
  if(nativeHost?.getUpdateState){try{lastUpdateState=JSON.parse(nativeHost.getUpdateState());}catch{}}
  for(const title of ['检查更新','更新历史']){
    const row=button('',()=>title==='检查更新'?showCheckUpdate():openPage(title),'about-row');
    row.setAttribute('aria-label',title);
    const arrow=element('span');arrow.innerHTML=svgIcon('right');
    const trailing=element('span','about-row-trailing');
    if(title==='检查更新')trailing.append(element('span','about-version',lastUpdateState.currentVersion||'1.0.2'));
    trailing.append(arrow);row.append(element('span','',title),trailing);list.append(row);
  }
  wrap.append(list);body.append(wrap);
}
function updateBytes(value){
  const n=Number(value)||0;
  return n<1024*1024?(n/1024).toFixed(n?1:0)+' KB':(n/1024/1024).toFixed(1)+' MB';
}
function showCheckUpdate(){
  const existing=document.querySelector('.app-update-dialog');if(existing)return;
  const wrap=element('dialog','app-update-dialog');wrap.setAttribute('aria-label','检查更新');
  const card=element('section','update-card');
  const current=element('p','update-current'),title=element('h2','update-title'),message=element('p','update-message');
  const notes=element('div','update-notes');
  const progressBox=element('div','update-progress-box'),progress=element('progress','update-progress');
  progress.max=100;progress.setAttribute('aria-label','安装包下载进度');
  const details=element('div','update-progress-details'),amount=element('span'),percent=element('span');details.append(amount,percent);progressBox.append(progress,details);
  const actions=element('div','update-actions');
  card.append(current,title,message,progressBox,notes,actions);wrap.append(card);$('stage').append(wrap);
  const close=()=>{wrap.close();wrap.remove();};
  wrap.addEventListener('cancel',event=>{event.preventDefault();close();});wrap.showModal();
  const act=(label,run)=>actions.append(button(label,run,'update-action'));
  updateScreen=state=>{
    if(!wrap.isConnected)return;
    current.textContent='当前版本 '+(state.currentVersion||'1.0.2');
    const phase=state.phase||'idle';title.textContent={idle:'检查更新',checking:'正在检查更新',available:'发现新版本',current:'已是最新版本',downloading:'正在下载安装包',ready:'下载完成',cancelled:'下载已取消',error:'检查失败','download-error':'下载未完成'}[phase]||'检查更新';
    message.textContent=state.message||'';
    if(phase==='available')message.textContent='新版本 '+releaseDisplayVersion(state);
    notes.replaceChildren();
    const withNotes=['available','downloading','ready','cancelled','download-error'].includes(phase)&&state.notes;
    if(withNotes)notes.append(releaseNotesDOM(state.notes));notes.hidden=!withNotes;
    progressBox.hidden=!['downloading','ready','cancelled','download-error'].includes(phase)||!state.total;
    const ratio=state.total?Math.max(0,Math.min(100,(state.downloaded||0)/state.total*100)):0;
    progress.value=ratio;progress.setAttribute('aria-valuetext',Math.floor(ratio)+'%');
    percent.textContent=Math.floor(ratio)+'%';amount.textContent=updateBytes(state.downloaded)+' / '+updateBytes(state.total);
    actions.replaceChildren();
    act(phase==='available'?'稍后':phase==='current'?'确定':'关闭',close);
    if(phase==='error')act('重试',()=>nativeHost?.checkUpdates?.());
    if(['available','cancelled','download-error'].includes(phase))act(phase==='available'?'立即下载':'重新下载',()=>nativeHost?.downloadUpdate?.());
    if(phase==='downloading')act('取消下载',()=>nativeHost?.cancelUpdateDownload?.());
    if(phase==='ready')act('安装更新',()=>nativeHost?.installUpdate?.());
  };
  if(nativeHost?.getUpdateState){
    try{lastUpdateState=JSON.parse(nativeHost.getUpdateState());}catch{}
    updateScreen(lastUpdateState);
    if(['idle','current','error'].includes(lastUpdateState.phase))nativeHost.checkUpdates();
  }else{
    updateScreen({...lastUpdateState,phase:'error',message:'请在 Android 应用中检查更新'});actions.replaceChildren();act('关闭',close);
  }
}
// Kept for deep links and older callers; update checks never change the page.
function renderCheckUpdate(){openPage('关于');showCheckUpdate();}
function releaseDisplayVersion(release){
  const match=String(release.tag||release.version||release.name||'').match(/\d+\.\d+(?:\.\d+)?(?:[-+][\w.-]+)?/);
  return match?match[0]:release.name||release.tag||'版本更新';
}
function releaseNotesDOM(source){
  const content=element('div','formatted-release-notes');
  let list=null,paragraph=[];
  const inline=(node,text)=>{
    // Only create text and a few presentational nodes. Remote HTML never runs.
    const pieces=String(text).split(/(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]*\))/g);
    for(const part of pieces){
      if(part.startsWith('**')&&part.endsWith('**'))node.append(element('strong','',part.slice(2,-2)));
      else if(part.startsWith('`')&&part.endsWith('`'))node.append(element('code','',part.slice(1,-1)));
      else if(/^\[/.test(part)&&/\]\([^)]*\)$/.test(part))node.append(part.slice(1,part.indexOf(']')));
      else node.append(part);
    }
  };
  const flush=()=>{if(paragraph.length){const p=element('p');inline(p,paragraph.join(' '));content.append(p);paragraph=[];}};
  for(const raw of String(source||'').replace(/\r/g,'').split('\n')){
    const line=raw.trim();
    if(/^#{1,6}\s+.*(?:空叶计算器|\bv?\d+\.\d+)/i.test(line)||/^(?:版本名称|版本代码|包名)\s*[：:]/.test(line))continue;
    if(!line){flush();list=null;continue;}
    const bullet=/^(?:[-*+]\s+|\d+[.)]\s+)(.+)/.exec(line);
    if(bullet){flush();if(!list){list=element('ul');content.append(list);}const item=element('li');inline(item,bullet[1]);list.append(item);continue;}
    const heading=/^#{1,6}\s+(.+)/.exec(line);
    if(heading){flush();list=null;const h=element('h3');inline(h,heading[1]);content.append(h);continue;}
    list=null;paragraph.push(line);
  }
  flush();if(!content.childNodes.length)content.append(element('p','','本版本暂无更新说明'));return content;
}
function renderReleaseHistory(body){
  const wrap=element('div','scroll-body release-history-page');
  const github=button('',()=>openProjectGitHub(),'icon release-github');
  github.setAttribute('aria-label','在 GitHub 打开项目');github.title='GitHub';
  github.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 .8a11.2 11.2 0 0 0-3.54 21.83c.56.1.77-.24.77-.54v-2.08c-3.12.68-3.78-1.32-3.78-1.32-.51-1.3-1.24-1.65-1.24-1.65-1.02-.7.08-.69.08-.69 1.13.08 1.72 1.16 1.72 1.16 1 1.72 2.63 1.22 3.27.94.1-.73.4-1.22.72-1.5-2.49-.28-5.11-1.25-5.11-5.54 0-1.22.44-2.22 1.15-3-.12-.28-.5-1.42.11-2.96 0 0 .94-.3 3.08 1.15a10.74 10.74 0 0 1 5.6 0c2.14-1.45 3.08-1.15 3.08-1.15.61 1.54.23 2.68.11 2.96.72.78 1.15 1.78 1.15 3 0 4.3-2.62 5.26-5.12 5.54.4.35.76 1.03.76 2.08v3.06c0 .3.2.65.77.54A11.2 11.2 0 0 0 12 .8Z"/></svg>';
  $('utility-actions').append(github);
  const header=element('div','release-history-header');
  const search=element('input','release-search');search.type='search';search.placeholder='搜索版本号或更新内容';search.setAttribute('aria-label','搜索更新历史');
  header.append(search);
  const summary=element('div','release-summary'),entries=element('div','release-entries');
  const pager=element('nav','release-pager');pager.setAttribute('aria-label','更新历史分页');
  let page=1,pages=1,id=0,timer=0;
  const previous=button('上一页',()=>load(page-1),'release-page-button'),next=button('下一页',()=>load(page+1),'release-page-button'),pageLabel=element('span');
  pager.append(previous,pageLabel,next);wrap.append(header,summary,entries,pager);body.append(wrap);
  function load(requested){
    clearTimeout(timer);id=++releaseRequest;previous.disabled=next.disabled=true;summary.textContent='正在加载更新历史…';
    if(!nativeHost?.getReleaseHistory){summary.textContent='请在 Android 应用中查看更新历史';return;}
    nativeHost.getReleaseHistory(id,requested,search.value);
  }
  releaseScreen=event=>{
    if(!wrap.isConnected||event.id!==id)return;
    entries.replaceChildren();
    if(event.error){summary.textContent=event.error;entries.append(button('重试',()=>load(page),'update-action'));return;}
    page=event.page;pages=event.pages;pageLabel.textContent=page+' / '+pages;
    previous.disabled=page<=1;next.disabled=page>=pages;summary.textContent='共 '+event.total+' 个版本';
    if(!event.items.length){entries.append(element('div','release-empty',search.value?'没有匹配的更新记录':'暂无更新记录'));return;}
    for(const release of event.items){
      const item=element('article','release-entry'),heading=element('div','release-entry-heading');
      const date=release.date?new Date(release.date).toLocaleDateString('zh-CN'):'';
      heading.append(element('h2','',releaseDisplayVersion(release)),element('time','',date));
      item.append(heading,releaseNotesDOM(release.body),button('在 GitHub 查看',()=>openProjectGitHub(release.url),'release-link'));
      entries.append(item);
    }
  };
  search.oninput=()=>{clearTimeout(timer);id=++releaseRequest;previous.disabled=next.disabled=true;timer=setTimeout(()=>load(1),180);};
  search.onkeydown=event=>{if(event.key==='Enter'){event.preventDefault();load(1);}};
  pageLabel.textContent='1 / 1';load(1);
}
