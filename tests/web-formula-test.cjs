// Logic and DOM-structure regression checks. This harness does not measure browser layout.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(root,'app/src/main/assets/calculator.html'),'utf8');
let document;
class Node {
  constructor(tag='span') {
    this.tagName=tag.toUpperCase(); this.children=[]; this.dataset={}; this.attributes={};
    this.className=''; this.style={setProperty(){}}; this.value=''; this.hidden=false;
    this.listeners={}; this.selectionStart=0; this.selectionEnd=0; this.scrollTop=0;
    this.classList={
      contains:n=>this.className.split(/\s+/).includes(n),
      toggle:(n,on)=>{const s=new Set(this.className.split(/\s+/).filter(Boolean));
        if(on ?? !s.has(n))s.add(n);else s.delete(n);this.className=[...s].join(' ');},
      add:(...names)=>names.forEach(n=>this.classList.toggle(n,true)),
      remove:(...names)=>names.forEach(n=>this.classList.toggle(n,false))
    };
  }
  set textContent(v){this._text=String(v); this.children=[];}
  get textContent(){return (this._text||'')+this.children.map(n=>n.textContent).join('');}
  set innerHTML(v){this._html=v;this.children=[];this._text='';}
  get innerHTML(){return this._html||'';}
  append(...nodes){for(let n of nodes){if(typeof n!=='object'){const t=new Node('#text');t.textContent=n;n=t;}n.parentNode=this;n.parentElement=this;this.children.push(n);}}
  appendChild(n){this.append(n);return n;}
  replaceChildren(...nodes){this._text='';this.children=[];this.append(...nodes);}
  get firstElementChild(){return this.children[0];}
  get lastChild(){return this.children.at(-1);}
  hasChildNodes(){return this.children.length>0;}
  setAttribute(k,v){this.attributes[k]=String(v);if(k==='class')this.className=v;}
  getAttribute(k){if(k.startsWith('data-')){const v=this.dataset[k.slice(5).replace(/-([a-z])/g,(_,x)=>x.toUpperCase())];return v===undefined?null:String(v);}return this.attributes[k]??null;}
  addEventListener(k,fn){(this.listeners[k]??=[]).push(fn);}
  dispatchEvent(e){this['on'+e.type]?.(e);for(const fn of this.listeners[e.type]||[])fn(e);}
  focus(){document.activeElement=this;this.onfocus?.();}
  blur(){document.activeElement=null;}
  click(){this.onclick?.({target:this});}
  matches(sel){return sel.split(',').some(s=>{
    s=s.trim();const attr=/\[([^=\]]+)(?:=["']?([^"'\]]+)["']?)?\]/.exec(s);
    if(attr && (attr[2]===undefined ? this.getAttribute(attr[1])===null : this.getAttribute(attr[1])!==attr[2])) return false;
    const cl=/\.([\w-]+)/.exec(s);if(cl&&!this.classList.contains(cl[1]))return false;
    const tag=/^[\w-]+/.exec(s);if(tag&&this.tagName!==tag[0].toUpperCase())return false;
    return true;
  });}
  querySelectorAll(sel){
    const parts=sel.trim().split(/\s+/);const found=[];
    const visit=n=>{for(const c of n.children){if(c.matches(parts.at(-1))){
      let parent=c.parentNode,good=true;
      for(let i=parts.length-2;i>=0;i--){while(parent&&!parent.matches(parts[i]))parent=parent.parentNode;if(!parent){good=false;break;}parent=parent.parentNode;}
      if(good)found.push(c);
    }visit(c);}};visit(this);return found;
  }
  querySelector(sel){return this.querySelectorAll(sel)[0]||null;}
  closest(sel){for(let n=this;n;n=n.parentNode)if(n.matches(sel))return n;return null;}
  contains(n){return n===this||this.children.some(c=>c.contains(n));}
  setSelectionRange(a,b){this.selectionStart=a;this.selectionEnd=b;}
  setRangeText(s,a,b,mode){this.value=this.value.slice(0,a)+s+this.value.slice(b);this.setSelectionRange(a+s.length,a+s.length);}
  scrollIntoView(){} scrollTo(){}
}
const body=new Node('body'), ids=new Map();
for(const match of html.matchAll(/<([\w-]+)\b[^>]*\bid="([^"]+)"[^>]*>/g)){
  const n=new Node(match[1]);n.id=match[2];n.hidden=/\bhidden\b/.test(match[0]);ids.set(n.id,n);body.append(n);
}
document={body,documentElement:new Node('html'),readyState:'loading',activeElement:null,
  getElementById:id=>ids.get(id),createElement:t=>new Node(t),createElementNS:(_,t)=>new Node(t),
  querySelectorAll:s=>body.querySelectorAll(s),querySelector:s=>body.querySelector(s),addEventListener(){}};
const local=new Map();
const context=vm.createContext({document,window:{addEventListener(){}},navigator:{},console,
  setTimeout,clearTimeout,setInterval,clearInterval,structuredClone,ResizeObserver:class{observe(){}},
  Event:class{constructor(type){this.type=type;}},
  localStorage:{getItem:k=>local.get(k)||null,setItem:(k,v)=>local.set(k,v)}});
const script=html.split('<script>')[1].split('</script>')[0];
new vm.Script(script).runInContext(context);
const run=s=>vm.runInContext(s,context);
let checks=0;
const check=(condition,message)=>{assert.ok(condition,message);checks++;};
const same=(actual,expected,message)=>{assert.equal(actual,expected,message);checks++;};
const reset=()=>run(`closeWorkbench();tokens=[];cursor=0;confirmed=false;formulaField=null;document.activeElement=null;drawExpression();`);
const press=k=>run(`press(${JSON.stringify(k)})`);
const cases=[
  ['a/b',['7','2'],3.5],['xʸ',['2','3'],8],['ⁿ√',['3','27'],3],['logₐ',['2','8'],3],
  ['A',['2','5'],20],['C',['2','5'],10],['abs',['-4'],4],['x!',['5'],120],['x!!',['6'],48],
  ['sin',['30'],.5],['cos',['60'],.5],['tan',['45'],1],['arcsin',['0.5'],30],['arccos',['0.5'],60],['arctan',['1'],45],
  ['ln',['e'],1],['log₁₀',['100'],2],['log₂',['32'],5],['mod',['17','5'],2],['公倍',['6','8'],24],['公约',['18','24'],6],
  ['∫ₐᵇ',['1','0','x^2'],1/3],['Σ',['4','k','1','k'],10],['Π',['4','k','1','k'],24],['lim',['x','0','sin(x)/x'],Math.PI/180]
];
for(const [key,fields,expected] of cases){
  reset();press(key);
  same(run('tokens.length'),1,key+' inserts one template');
  same(run('tokens[0].key'),key,key+' template key');
  same(ids.get('expression').querySelectorAll('input.formula-input').length,fields.length,key+' parameter count');
  check(run(`tokens[0].fields=${JSON.stringify(fields)};Math.abs(evaluate(serializeTokens(tokens))-${expected})<1e-7`),key+' result');
  run('drawExpression(true);');
  check(run('readableHistoryExpression().length>0'),key+' history label');
  same(run('history[0]?.rawTokens[0]?.key'),key,key+' history retains structure');
}
for(const key of ['∫','f′','ellipse']){
  reset();press(key);same(ids.get('expression').querySelectorAll('input').length,run('tokens[0].fields.length'),key+' DOM slots');
}
reset();press('log₁₀');same(ids.get('expression').querySelector('sub').textContent,'10','log base is semantic subscript');
reset();press('log₂');same(ids.get('expression').querySelector('sub').textContent,'2','log2 base is subscript');
reset();press('mod');
same(ids.get('expression').querySelector('.math-call').textContent,'mod,','mod name and argument separator');
press('1');press('7');run('moveCursor(1)');press('5');same(ids.get('answer').textContent,'= 2','two fields entered using keypad and next');
run('moveCursor(1)');same(run('formulaField'),null,'next exits template');
run('moveCursor(-1)');same(run('formulaField.value'),'5','previous enters last parameter');
run(`$('delete').click()`);same(run('tokens[0].fields[1]'),'','delete edits correct parameter');
run(`$('undo').click()`);same(run('tokens[0].fields[1]'),'5','undo restores parameter');
for(const text of ['mod(17,5)','log10(100)','log2(32)','ln(e)','lcm(6,8)','gcd(18,24)','sin(30)']){
  reset();run(`replaceExpression(${JSON.stringify(text)})`);
  same(ids.get('expression').querySelectorAll('.function-name').length,1,text+' old draft name grouped');
  same(run('serializeTokens(tokens)'),text,text+' old draft data unchanged');
  if(text.startsWith('log'))check(ids.get('expression').querySelector('sub').textContent.length>0,'old log subscript');
}
for(const [value,label] of [['1.5','1.5'],['123456','123456'],['1234567','123456...'],['-12.34','-12.34'],['0.12345678','0.1234...']]){
  reset();run(`savedResults=[${JSON.stringify(value)}];drawSavedResults();`);
  same(ids.get('reuse').textContent,label,'saved value six-character boundary');
  same(ids.get('reuse').title,value,'saved value full title');
  ids.get('saved-results-list').children[0].click();same(run('serializeTokens(tokens)'),value,'reuse retains complete value');
}
reset();run('toggleDrawer(true)');same(ids.get('open-drawer').getAttribute('aria-expanded'),'true','drawer open state');
check(ids.get('home').inert,'home disabled while drawer open');run('androidBack()');same(ids.get('stage').dataset.drawer,'closed','back closes drawer');
reset();press('(');press('5');press(')');same(run('serializeTokens(tokens)'),'(5)','paired parentheses');
reset();run(`tokens=[{key:'∫',fields:['x']}];drawExpression()`);check(ids.get('answer').textContent.includes('x²/2 + C'),'antiderivative display');
reset();run(`tokens=[{key:'f′',fields:['x^2']}];drawExpression()`);check(ids.get('answer').textContent.includes('2x'),'derivative display');
reset();run(`tokens=[{key:'ellipse',fields:['0','0','4','1']}];cursor=1;drawExpression();
  renderEllipseTool=(mode,token)=>{globalThis.ellipseAction=mode+':'+token.key;};`);
ids.get('formula-actions').children[0].click();same(run('ellipseAction'),'绘制图象:ellipse','ellipse graph action resolves current expression');
ids.get('formula-actions').children[1].click();same(run('ellipseAction'),'求解方程:ellipse','ellipse solve action resolves current expression');
for(let category=0;category<3;category++){
  const keys=run(`defaultKeys[${category}]`);
  for(const key of keys){const tile=run(`paintKey(${JSON.stringify(key)},${category})`);check(Boolean(tile),key+' key renderer');}
}
check(/\.stage\s*\{[^}]*width: 100%;/.test(html),'stage fills screen');
check(/\.home-card-shell\s*\{[^}]*padding: 0;/.test(html),'no outer home padding');
check(/\.page\s*\{[^}]*border-radius: 0;[^}]*border: 0;/.test(html),'no outer page frame');
check(/\.drawer-header\s*\{[^}]*padding: 2px 0 12px;/.test(html),'drawer title no close-button spacer');
check(/\.stage\[data-drawer="open"\] \.menu-toggle\s*\{[^}]*translateX\(var\(--push-offset\)\)/.test(html),'toggle follows foreground header');
check(!/text-overflow: ellipsis/.test(html.split('#reuse {')[1].split('}')[0]),'saved preview has no width-based ellipsis');
console.log(`PASS: ${checks} formula, serialization, keypad, history, saved-value and static style checks. Browser geometry and Android emulator were not tested.`);
