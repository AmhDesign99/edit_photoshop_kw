const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];

const displayCanvas = $('#displayCanvas');
const interactionCanvas = $('#interactionCanvas');
const displayCtx = displayCanvas.getContext('2d');
const interactionCtx = interactionCanvas.getContext('2d');
const canvasHolder = $('#canvasHolder');
const stageWrap = $('#stageWrap');

const state = {
  width: 1200,
  height: 800,
  zoom: 1,
  panX: 0,
  panY: 0,
  activeTool: 'move',
  activeLayerId: null,
  layers: [],
  history: [],
  historyIndex: -1,
  selection: null,
  isPointerDown: false,
  lastPoint: null,
  panOrigin: null,
  dragStart: null,
  movingLayer: false,
  currentShape: null,
  lassoPoints: null,
  currentText: null,
  projectDirty: false,
  brushSize: 24,
  brushOpacity: 100,
  brushHardness: 100,
  tolerance: 32,
  feather: 0,
  showRulers: false,
  gridVisible: false,
};

const toolDefs = [
  ['move','↖','Move Tool'], ['marquee','▭','Rectangular Marquee Tool'], ['lasso','⌁','Lasso Tool'], ['crop','⌗','Crop Tool'],
  ['eyedropper','⌁','Eyedropper Tool'], ['brush','✎','Brush Tool'], ['pencil','／','Pencil Tool'], ['eraser','⌫','Eraser Tool'],
  ['clone','◉','Clone Stamp Tool'], ['gradient','▨','Gradient Tool'], ['bucket','▰','Paint Bucket Tool'],
  ['blur','◌','Blur Tool'], ['sharpen','◆','Sharpen Tool'], ['smudge','◍','Smudge Tool'],
  ['dodge','☼','Dodge Tool'], ['burn','◒','Burn Tool'], ['sponge','◐','Sponge Tool'],
  ['pen','⌁','Pen Tool'], ['text','T','Text Tool'], ['rect','□','Rectangle Tool'], ['ellipse','○','Ellipse Tool'],
  ['line','╱','Line Tool'], ['hand','✋','Hand Tool'], ['zoom','＋','Zoom Tool'],
];

const menus = {
  File: [
    ['New','new'], ['Open Image','open'], ['Open Project','openProject'], ['Save Project','save'], ['Save Project As','saveAs'], ['separator'],
    ['Export PNG','exportPng'], ['Export JPG','exportJpg'], ['Export WebP','exportWebp'], ['separator'], ['Close','clear']
  ],
  Edit: [
    ['Undo','undo'], ['Redo','redo'], ['separator'], ['Cut','cut'], ['Copy','copy'], ['Paste','paste'], ['Delete','deleteLayer'],
  ],
  Image: [
    ['Image Size','imageSize'], ['Canvas Size','canvasSize'], ['Rotate 90° CW','rotateCW'], ['Rotate 90° CCW','rotateCCW'], ['Flip Horizontal','flipH'], ['Flip Vertical','flipV'],
  ],
  Layer: [
    ['New Layer','addLayer'], ['Duplicate Layer','duplicateLayer'], ['Delete Layer','deleteLayer'], ['separator'],
    ['Merge Visible','mergeVisible'], ['Flatten Image','flatten'],
  ],
  Select: [
    ['All','selectAll'], ['Deselect','deselect'], ['Inverse','inverse'], ['Feather…','feather'],
  ],
  View: [
    ['Zoom In','zoomIn'], ['Zoom Out','zoomOut'], ['Fit on Screen','fit'], ['separator'], ['Rulers','toggleRulers'], ['Grid','toggleGrid'],
  ],
  Help: [['Keyboard Shortcuts','shortcuts'], ['About','about']],
};

function makeId(prefix='id') { return prefix + '_' + Math.random().toString(36).slice(2, 9); }
function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
function deepClone(obj) { return JSON.parse(JSON.stringify(obj)); }
function activeLayer() { return state.layers.find(l => l.id === state.activeLayerId) || null; }
function ensureCanvasSize() { displayCanvas.width = interactionCanvas.width = state.width; displayCanvas.height = interactionCanvas.height = state.height; }
function createLayer(name='Layer', fill=null) {
  const c = document.createElement('canvas'); c.width = state.width; c.height = state.height;
  const ctx = c.getContext('2d');
  if (fill) { ctx.fillStyle = fill; ctx.fillRect(0,0,state.width,state.height); }
  const layer = { id: makeId('layer'), name, visible: true, opacity: 1, blendMode: 'source-over', canvas: c };
  state.layers.push(layer); state.activeLayerId = layer.id; return layer;
}
function resetDocument(w=1200,h=800,name='Layer 1') {
  state.width = Math.max(1, Math.round(w)); state.height = Math.max(1, Math.round(h));
  state.layers = []; state.history = []; state.historyIndex = -1; state.selection = null;
  ensureCanvasSize(); createLayer(name); state.panX = 0; state.panY = 0; fitToScreen();
  state.projectDirty = false; recordHistory('New document'); renderAll();
}

function snapshotState(label='Edit') {
  const layers = state.layers.map(l => ({ id:l.id, name:l.name, visible:l.visible, opacity:l.opacity, blendMode:l.blendMode, data:l.canvas.toDataURL('image/png') }));
  return { label, width:state.width, height:state.height, activeLayerId:state.activeLayerId, layers };
}
async function restoreSnapshot(snap) {
  state.width=snap.width; state.height=snap.height; ensureCanvasSize(); state.layers=[];
  for (const sl of snap.layers) {
    const layer = createLayer(sl.name); layer.visible=sl.visible; layer.opacity=sl.opacity; layer.blendMode=sl.blendMode;
    await drawDataUrlToCanvas(sl.data, layer.canvas);
  }
  state.activeLayerId = snap.activeLayerId && state.layers.some(x=>x.id===snap.activeLayerId) ? snap.activeLayerId : state.layers.at(-1)?.id;
  fitToScreen(); renderAll();
}
function recordHistory(label='Edit') {
  const snap = snapshotState(label);
  state.history = state.history.slice(0, state.historyIndex+1);
  state.history.push(snap); state.historyIndex = state.history.length-1;
  if (state.history.length > 40) { state.history.shift(); state.historyIndex--; }
  state.projectDirty = true; updateHistoryUI();
}
async function undo() {
  if (state.historyIndex <= 0) return;
  state.historyIndex--; await restoreSnapshot(state.history[state.historyIndex]); updateHistoryUI();
}
async function redo() {
  if (state.historyIndex >= state.history.length-1) return;
  state.historyIndex++; await restoreSnapshot(state.history[state.historyIndex]); updateHistoryUI();
}
function updateHistoryUI() {
  $('#historyCount').textContent = String(Math.max(0,state.history.length));
  $('#historyList').innerHTML = state.history.map((h,i)=>`<div class="history-item ${i===state.historyIndex?'active':''}">${i+1}. ${escapeHtml(h.label)}</div>`).join('');
}
function escapeHtml(s='') { return s.replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#039;','"':'&quot;'}[c])); }

function renderToolbar() {
  $('#toolbar').innerHTML = toolDefs.map((t,i)=> `${i && (i===4||i===8||i===11||i===17||i===22)?'<div class="tool-sep"></div>':''}<button class="tool-btn ${state.activeTool===t[0]?'active':''}" data-tool="${t[0]}" title="${t[2]}">${t[1]}</button>`).join('');
  $$('.tool-btn').forEach(b=>b.addEventListener('click',()=>setTool(b.dataset.tool)));
}
function setTool(tool) { state.activeTool=tool; renderToolbar(); renderOptions(); }

function renderMenus() {
  const bar = $('#menuBar');
  bar.innerHTML = Object.entries(menus).map(([name,items])=>`<div class="menu-item"><button class="menu-button" data-menu="${name}">${name}</button><div class="dropdown" data-dropdown="${name}" hidden>${items.map(([label,action])=> action==='separator' ? '<div class="separator"></div>' : `<button data-action="${action}">${label}</button>`).join('')}</div></div>`).join('');
  $$('.menu-button').forEach(btn=>btn.addEventListener('click',()=>toggleMenu(btn.dataset.menu)));
  $$('.dropdown button[data-action]').forEach(btn=>btn.addEventListener('click',()=>{ runAction(btn.dataset.action); closeMenus(); }));
  document.addEventListener('click',e=>{ if (!e.target.closest('.menu-item')) closeMenus(); });
}
function toggleMenu(name) { closeMenus(name); const dd=$(`[data-dropdown="${name}"]`); const btn=$(`[data-menu="${name}"]`); const open=dd.hidden; dd.hidden=!open; btn.classList.toggle('active',open); }
function closeMenus(except='') { $$('.dropdown').forEach(dd=>{ if (dd.dataset.dropdown!==except) dd.hidden=true; }); $$('.menu-button').forEach(b=>{ if(b.dataset.menu!==except) b.classList.remove('active'); }); }

function renderOptions() {
  const tool=state.activeTool; let html='';
  const common = `<div class="option-group"><label>Zoom</label><input id="zoomInput" type="number" min="5" max="800" step="5" value="${Math.round(state.zoom*100)}"></div>`;
  if (tool==='brush'||tool==='pencil'||tool==='eraser'||tool==='clone'||tool==='blur'||tool==='sharpen'||tool==='smudge'||tool==='dodge'||tool==='burn'||tool==='sponge') {
    html += `<div class="option-group"><label>Size</label><input id="brushSize" type="number" min="1" max="500" value="${state.brushSize}"></div>`;
    html += `<div class="option-group"><label>Opacity</label><input id="brushOpacity" type="range" min="1" max="100" value="${state.brushOpacity}"><span>${state.brushOpacity}%</span></div>`;
    html += `<div class="option-group"><label>Hardness</label><input id="brushHardness" type="range" min="0" max="100" value="${state.brushHardness}"><span>${state.brushHardness}%</span></div>`;
  }
  if (tool==='marquee') html += `<div class="option-group"><label>Feather</label><input id="featherInput" type="number" min="0" max="200" value="${state.feather}"></div>`;
  if (tool==='move') html += `<div class="option-group"><label>Mode</label><select id="moveMode"><option value="layer">Layer</option><option value="canvas">Canvas</option></select></div>`;
  if (tool==='text') html += `<div class="option-group"><label>Font</label><select id="fontSelect"><option>Arial</option><option>Georgia</option><option>Verdana</option><option>Courier New</option><option>Times New Roman</option></select></div><div class="option-group"><label>Size</label><input id="fontSize" type="number" min="6" max="300" value="64"></div>`;
  if (tool==='gradient') html += `<div class="option-group"><label>Type</label><select id="gradientType"><option value="linear">Linear</option><option value="radial">Radial</option></select></div>`;
  if (tool==='zoom') html += `<div class="option-group"><button id="zoomInOpt" class="small-btn">＋</button><button id="zoomOutOpt" class="small-btn">−</button><button id="fitOpt" class="small-btn">Fit</button></div>`;
  html += common;
  $('#optionsBar').innerHTML = html;
  const z=$('#zoomInput'); z?.addEventListener('change',()=>setZoom(clamp(Number(z.value)/100,0.05,8)));
  $('#brushSize')?.addEventListener('change',e=>state.brushSize=clamp(Number(e.target.value)||1,1,500));
  $('#brushOpacity')?.addEventListener('input',e=>{state.brushOpacity=Number(e.target.value); renderOptions();});
  $('#brushHardness')?.addEventListener('input',e=>{state.brushHardness=Number(e.target.value); renderOptions();});
  $('#featherInput')?.addEventListener('change',e=>state.feather=clamp(Number(e.target.value)||0,0,200));
  $('#zoomInOpt')?.addEventListener('click',()=>zoomIn()); $('#zoomOutOpt')?.addEventListener('click',()=>zoomOut()); $('#fitOpt')?.addEventListener('click',fitToScreen);
}

function setZoom(z, center=null) {
  z=clamp(z,0.05,8);
  const old=state.zoom;
  if (center) {
    const before = screenToDoc(center.x, center.y);
    state.zoom=z;
    const afterX = state.panX + before.x*z;
    const afterY = state.panY + before.y*z;
    state.panX += center.x - afterX; state.panY += center.y - afterY;
  } else state.zoom=z;
  renderAll();
}
function zoomIn(){setZoom(state.zoom*1.15,{x:stageWrap.clientWidth/2,y:stageWrap.clientHeight/2});}
function zoomOut(){setZoom(state.zoom/1.15,{x:stageWrap.clientWidth/2,y:stageWrap.clientHeight/2});}
function fitToScreen() {
  const pad=70; const w=stageWrap.clientWidth-pad; const h=stageWrap.clientHeight-pad;
  state.zoom = clamp(Math.min(w/state.width,h/state.height),0.05,2.5);
  state.panX=(stageWrap.clientWidth-state.width*state.zoom)/2; state.panY=(stageWrap.clientHeight-state.height*state.zoom)/2;
  renderAll();
}
function screenToDoc(sx,sy) { return { x:(sx-state.panX)/state.zoom, y:(sy-state.panY)/state.zoom }; }
function docToScreen(x,y) { return { x:state.panX+x*state.zoom, y:state.panY+y*state.zoom }; }
function updateCanvasTransform() {
  canvasHolder.style.width=state.width+'px'; canvasHolder.style.height=state.height+'px';
  canvasHolder.style.transform=`translate(${state.panX}px,${state.panY}px) scale(${state.zoom}) translateZ(0)`;
}

function composite() {
  displayCtx.clearRect(0,0,state.width,state.height);
  for (const layer of state.layers) {
    if (!layer.visible) continue;
    displayCtx.save(); displayCtx.globalAlpha=layer.opacity; displayCtx.globalCompositeOperation=layer.blendMode; displayCtx.drawImage(layer.canvas,0,0); displayCtx.restore();
  }
}
function renderInteraction() {
  interactionCtx.clearRect(0,0,state.width,state.height);
  if (state.selection) {
    const s=state.selection; interactionCtx.save(); interactionCtx.strokeStyle='#fff'; interactionCtx.lineWidth=1/state.zoom; interactionCtx.setLineDash([5/state.zoom,5/state.zoom]); interactionCtx.strokeRect(s.x,s.y,s.w,s.h); interactionCtx.restore();
  }
  if (state.currentShape) drawShapePreview(state.currentShape);
  if (state.gridVisible) {
    interactionCtx.save(); interactionCtx.strokeStyle='rgba(255,255,255,.08)'; interactionCtx.lineWidth=1/state.zoom;
    const step=50; for(let x=0;x<=state.width;x+=step){interactionCtx.beginPath();interactionCtx.moveTo(x,0);interactionCtx.lineTo(x,state.height);interactionCtx.stroke();}
    for(let y=0;y<=state.height;y+=step){interactionCtx.beginPath();interactionCtx.moveTo(0,y);interactionCtx.lineTo(state.width,y);interactionCtx.stroke();}
    interactionCtx.restore();
  }
}
function renderAll(){ composite(); renderInteraction(); updateCanvasTransform(); updateStatus(); renderLayers(); $('#emptyHint').classList.toggle('hidden', state.layers.some(l=>l.canvas && hasVisiblePixels(l.canvas))); }
function hasVisiblePixels(canvas){ return canvas.width>0 && canvas.height>0; }
function updateStatus(){ $('#statusBar').innerHTML=`<span>${state.width} × ${state.height}px</span><span>${Math.round(state.zoom*100)}%</span>`; }

function renderLayers(){
  $('#layerList').innerHTML = state.layers.slice().reverse().map(layer=>{
    const thumb=layer.canvas.toDataURL('image/png');
    return `<div class="layer-row ${layer.id===state.activeLayerId?'active':''}" data-layer="${layer.id}">
      <div class="visibility" data-vis="${layer.id}">${layer.visible?'◉':'○'}</div>
      <div class="layer-thumb"><img src="${thumb}"/></div>
      <div class="layer-name" title="${escapeHtml(layer.name)}">${escapeHtml(layer.name)}</div>
    </div>`;
  }).join('');
  $$('.layer-row').forEach(row=>row.addEventListener('click',e=>{
    const id=row.dataset.layer;
    if(e.target.dataset.vis){ const l=state.layers.find(x=>x.id===id); l.visible=!l.visible; recordHistory(l.visible?'Show layer':'Hide layer'); renderAll(); return; }
    state.activeLayerId=id; syncLayerControls(); renderLayers();
  }));
  syncLayerControls();
}
function syncLayerControls(){ const l=activeLayer(); if(!l) return; $('#opacitySlider').value=Math.round(l.opacity*100); $('#opacityValue').textContent=Math.round(l.opacity*100)+'%'; $('#blendMode').value=l.blendMode; }

function getPos(e){ const r=interactionCanvas.getBoundingClientRect(); return {x:(e.clientX-r.left)/state.zoom,y:(e.clientY-r.top)/state.zoom}; }
function pointerDown(e){
  if(e.button!==0 && state.activeTool!=='hand') return;
  state.isPointerDown=true; state.lastPoint=getPos(e); state.dragStart=state.lastPoint;
  const p=state.lastPoint; const tool=state.activeTool; const layer=activeLayer(); if(!layer) return;
  if(tool==='hand'){ state.panOrigin={x:e.clientX,y:e.clientY,panX:state.panX,panY:state.panY}; return; }
  if(tool==='zoom'){ const factor=e.altKey?.8:1.25; setZoom(state.zoom*factor,{x:e.clientX-stageWrap.getBoundingClientRect().left,y:e.clientY-stageWrap.getBoundingClientRect().top}); return; }
  if(tool==='eyedropper'){ const data=displayCtx.getImageData(clamp(Math.floor(p.x),0,state.width-1),clamp(Math.floor(p.y),0,state.height-1),1,1).data; $('#fgColor').value=rgbToHex(data[0],data[1],data[2]); return; }
  if(tool==='text'){ addTextAt(p.x,p.y); state.isPointerDown=false; return; }
  if(tool==='bucket'){ bucketFill(layer.canvas,p.x,p.y); recordHistory('Paint Bucket'); renderAll(); return; }
  if(tool==='gradient'){ state.currentShape={kind:'gradient',x:p.x,y:p.y,x2:p.x,y2:p.y}; return; }
  if(tool==='marquee'){ state.selection={x:p.x,y:p.y,w:0,h:0}; return; }
  if(tool==='lasso'){ state.lassoPoints=[p]; return; }
  if(tool==='crop'){ state.selection={x:p.x,y:p.y,w:0,h:0}; return; }
  if(['rect','ellipse','line'].includes(tool)){ state.currentShape={kind:tool,x:p.x,y:p.y,x2:p.x,y2:p.y}; return; }
  if(tool==='move'){ state.movingLayer=true; return; }
  if(tool==='brush'||tool==='pencil'||tool==='eraser'||tool==='clone'||tool==='blur'||tool==='sharpen'||tool==='smudge'||tool==='dodge'||tool==='burn'||tool==='sponge'){
    paintAt(layer,p.x,p.y,p.x,p.y); return;
  }
}
function pointerMove(e){ if(!state.isPointerDown) return; const p=getPos(e); const tool=state.activeTool;
  if(tool==='hand' && state.panOrigin){ state.panX=state.panOrigin.panX+(e.clientX-state.panOrigin.x); state.panY=state.panOrigin.panY+(e.clientY-state.panOrigin.y); renderAll(); return; }
  if(tool==='marquee'||tool==='crop'){ state.selection=normalizeRect(state.dragStart,p); renderInteraction(); return; }
  if(tool==='lasso' && state.lassoPoints){ state.lassoPoints.push(p); renderInteraction(); return; }
  if(['rect','ellipse','line','gradient'].includes(tool)){ state.currentShape={...(state.currentShape||{}),x2:p.x,y2:p.y}; renderInteraction(); return; }
  if(tool==='move' && state.movingLayer){ const dx=p.x-state.lastPoint.x, dy=p.y-state.lastPoint.y; translateCanvas(activeLayer().canvas,dx,dy); state.lastPoint=p; composite(); renderLayers(); return; }
  if(['brush','pencil','eraser','clone','blur','sharpen','smudge','dodge','burn','sponge'].includes(tool)){ paintAt(activeLayer(),p.x,p.y,state.lastPoint.x,state.lastPoint.y); state.lastPoint=p; composite(); renderInteraction(); renderLayers(); }
}
function pointerUp(){ if(!state.isPointerDown) return; const tool=state.activeTool;
  if(tool==='lasso' && state.lassoPoints?.length>2){ commitLassoSelection(state.lassoPoints); state.lassoPoints=null; renderAll(); }
  else if(tool==='crop' && state.selection?.w>2 && state.selection?.h>2){ cropDocument(state.selection); }
  else if(tool==='marquee' && state.selection){ state.selection=normalizeRect(state.selection,{x:state.selection.x+state.selection.w,y:state.selection.y+state.selection.h}); recordHistory('Selection'); renderAll(); }
  else if(['rect','ellipse','line'].includes(tool) && state.currentShape){ commitShape(state.currentShape); state.currentShape=null; recordHistory(capitalize(tool)); renderAll(); }
  else if(tool==='gradient' && state.currentShape){ commitGradient(state.currentShape); state.currentShape=null; recordHistory('Gradient'); renderAll(); }
  else if(['move','brush','pencil','eraser','clone','blur','sharpen','smudge','dodge','burn','sponge'].includes(tool)){ recordHistory(capitalize(tool)); renderAll(); }
  state.isPointerDown=false; state.panOrigin=null; state.movingLayer=false;
}
function pointerCancel(){ state.isPointerDown=false; state.panOrigin=null; state.currentShape=null; renderAll(); }
function commitLassoSelection(points){
  if(points.length<3)return;
  // Keep the polygon as metadata and expose a bounding rectangle for the current MVP.
  const xs=points.map(p=>p.x), ys=points.map(p=>p.y);
  state.selection={x:Math.max(0,Math.min(...xs)),y:Math.max(0,Math.min(...ys)),w:Math.max(...xs)-Math.min(...xs),h:Math.max(...ys)-Math.min(...ys),polygon:points};
  recordHistory('Lasso selection');
}

function normalizeRect(a,b){ const x=Math.min(a.x,b.x), y=Math.min(a.y,b.y), x2=Math.max(a.x,b.x), y2=Math.max(a.y,b.y); return {x,y,w:x2-x,h:y2-y}; }

function paintAt(layer,x,y,px,py){
  const ctx=layer.canvas.getContext('2d'); const t=state.activeTool;
  const size=state.brushSize; ctx.save();
  if(t==='eraser'){ ctx.globalCompositeOperation='destination-out'; }
  else if(t==='blur'){ applySoftEffect(layer.canvas,x,y,size,'blur'); ctx.restore(); return; }
  else if(t==='sharpen'){ applySoftEffect(layer.canvas,x,y,size,'sharpen'); ctx.restore(); return; }
  else if(t==='smudge'){ applySmudge(layer.canvas,x,y,px,py,size); ctx.restore(); return; }
  else if(t==='dodge'||t==='burn'||t==='sponge'){ applyTone(layer.canvas,x,y,size,t,state.brushOpacity/100); ctx.restore(); return; }
  else if(t==='clone'){ cloneStamp(layer.canvas,x,y,px,py,size); ctx.restore(); return; }
  ctx.globalAlpha=state.brushOpacity/100; ctx.strokeStyle=$('#fgColor').value; ctx.fillStyle=$('#fgColor').value; ctx.lineCap='round'; ctx.lineJoin='round';
  if(state.brushHardness>=95 || t==='pencil'){
    ctx.lineWidth=size; ctx.beginPath(); ctx.moveTo(px,py); ctx.lineTo(x,y); ctx.stroke();
  } else {
    const g=ctx.createRadialGradient(x,y,0,x,y,size/2); g.addColorStop(0,'rgba(0,0,0,1)'); g.addColorStop(state.brushHardness/100,'rgba(0,0,0,1)'); g.addColorStop(1,'rgba(0,0,0,0)');
    ctx.globalCompositeOperation=t==='eraser'?'destination-out':'source-over'; ctx.fillStyle=g; ctx.beginPath(); ctx.arc(x,y,size/2,0,Math.PI*2); ctx.fill();
  }
  ctx.restore();
}
function translateCanvas(canvas,dx,dy){ const tmp=document.createElement('canvas'); tmp.width=canvas.width; tmp.height=canvas.height; const tc=tmp.getContext('2d'); tc.drawImage(canvas,0,0); const c=canvas.getContext('2d'); c.clearRect(0,0,canvas.width,canvas.height); c.drawImage(tmp,dx,dy); }
function applySoftEffect(canvas,x,y,size,mode){ const ctx=canvas.getContext('2d'); ctx.save(); if(mode==='blur'){ ctx.filter=`blur(${Math.max(1,size/8)}px)`; const r=size; ctx.globalCompositeOperation='source-over'; ctx.drawImage(canvas,x-r,y-r,2*r,2*r,x-r,y-r,2*r,2*r); } else { ctx.globalAlpha=.08; ctx.fillStyle='#fff'; ctx.beginPath(); ctx.arc(x,y,size/2,0,Math.PI*2); ctx.fill(); } ctx.restore(); }
function applySmudge(canvas,x,y,px,py,size){ const ctx=canvas.getContext('2d'); const sw=Math.max(2,size); const sx=clamp(px-sw/2,0,canvas.width-sw), sy=clamp(py-sw/2,0,canvas.height-sw); const tmp=document.createElement('canvas'); tmp.width=sw; tmp.height=sw; tmp.getContext('2d').drawImage(canvas,sx,sy,sw,sw,0,0,sw,sw); ctx.globalAlpha=.25; ctx.drawImage(tmp,clamp(x-sw/2,0,canvas.width-sw),clamp(y-sw/2,0,canvas.height-sw)); }
function applyTone(canvas,x,y,size,type,alpha){ const ctx=canvas.getContext('2d'); ctx.save(); ctx.globalAlpha=alpha*.2; ctx.globalCompositeOperation=type==='dodge'?'screen':type==='burn'?'multiply':'saturation'; ctx.fillStyle=type==='dodge'?'#fff':type==='burn'?'#000':'#888'; ctx.beginPath(); ctx.arc(x,y,size/2,0,Math.PI*2); ctx.fill(); ctx.restore(); }
function cloneStamp(canvas,x,y,px,py,size){ const ctx=canvas.getContext('2d'); const dx=x-px,dy=y-py, sx=x-dx, sy=y-dy; ctx.save(); ctx.beginPath(); ctx.arc(x,y,size/2,0,Math.PI*2); ctx.clip(); ctx.drawImage(canvas,sx-size/2,sy-size/2,size,size,x-size/2,y-size/2,size,size); ctx.restore(); }
function bucketFill(canvas,x,y){ const ctx=canvas.getContext('2d'); const img=ctx.getImageData(0,0,state.width,state.height); const p=(Math.floor(y)*state.width+Math.floor(x))*4; const target=[img.data[p],img.data[p+1],img.data[p+2],img.data[p+3]]; const color=hexToRgba($('#fgColor').value); if(colorsClose(target,color,state.tolerance)) return; const q=[[Math.floor(x),Math.floor(y)]]; const seen=new Uint8Array(state.width*state.height); while(q.length){ const [cx,cy]=q.pop(); if(cx<0||cy<0||cx>=state.width||cy>=state.height) continue; const idx=cy*state.width+cx; if(seen[idx]) continue; seen[idx]=1; const i=idx*4; const cur=[img.data[i],img.data[i+1],img.data[i+2],img.data[i+3]]; if(!colorsClose(cur,target,state.tolerance)) continue; img.data[i]=color[0];img.data[i+1]=color[1];img.data[i+2]=color[2];img.data[i+3]=255; q.push([cx+1,cy],[cx-1,cy],[cx,cy+1],[cx,cy-1]); } ctx.putImageData(img,0,0); }
function colorsClose(a,b,t){ return Math.abs(a[0]-b[0])<=t && Math.abs(a[1]-b[1])<=t && Math.abs(a[2]-b[2])<=t && Math.abs(a[3]-b[3])<=t; }

function drawShapePreview(sh){ const ctx=interactionCtx; ctx.save(); ctx.strokeStyle=$('#fgColor').value; ctx.fillStyle='rgba(255,255,255,.08)'; ctx.lineWidth=2/state.zoom; const r=normalizeRect({x:sh.x,y:sh.y},{x:sh.x2,y:sh.y2}); if(sh.kind==='rect') ctx.strokeRect(r.x,r.y,r.w,r.h); else if(sh.kind==='ellipse'){ctx.beginPath();ctx.ellipse(r.x+r.w/2,r.y+r.h/2,Math.abs(r.w/2),Math.abs(r.h/2),0,0,Math.PI*2);ctx.stroke();} else if(sh.kind==='line'){ctx.beginPath();ctx.moveTo(sh.x,sh.y);ctx.lineTo(sh.x2,sh.y2);ctx.stroke();} else if(sh.kind==='gradient'){ctx.beginPath();ctx.moveTo(sh.x,sh.y);ctx.lineTo(sh.x2,sh.y2);ctx.stroke();} ctx.restore(); }
function commitShape(sh){ const ctx=activeLayer().canvas.getContext('2d'); const color=$('#fgColor').value; ctx.save(); ctx.strokeStyle=color; ctx.fillStyle=color; ctx.globalAlpha=state.brushOpacity/100; ctx.lineWidth=Math.max(1,state.brushSize/2); const r=normalizeRect({x:sh.x,y:sh.y},{x:sh.x2,y:sh.y2}); if(sh.kind==='rect') ctx.strokeRect(r.x,r.y,r.w,r.h); else if(sh.kind==='ellipse'){ctx.beginPath();ctx.ellipse(r.x+r.w/2,r.y+r.h/2,Math.abs(r.w/2),Math.abs(r.h/2),0,0,Math.PI*2);ctx.stroke();} else {ctx.beginPath();ctx.moveTo(sh.x,sh.y);ctx.lineTo(sh.x2,sh.y2);ctx.stroke();} ctx.restore(); }
function commitGradient(sh){ const ctx=activeLayer().canvas.getContext('2d'); let g; if($('#gradientType')?.value==='radial'){ g=ctx.createRadialGradient(sh.x,sh.y,0,sh.x2,sh.y2,Math.hypot(sh.x2-sh.x,sh.y2-sh.y)); } else {g=ctx.createLinearGradient(sh.x,sh.y,sh.x2,sh.y2);} g.addColorStop(0,$('#fgColor').value);g.addColorStop(1,$('#bgColor').value);ctx.save(); if(state.selection){ctx.beginPath();ctx.rect(state.selection.x,state.selection.y,state.selection.w,state.selection.h);ctx.clip();} ctx.fillStyle=g;ctx.fillRect(0,0,state.width,state.height);ctx.restore(); }

function addTextAt(x,y){ const font = $('#fontSelect')?.value || 'Arial'; const size=Number($('#fontSize')?.value)||64; const text=prompt('Text:', 'Double-click to edit'); if(!text) return; const layer=createLayer(text.slice(0,24) || 'Text'); const ctx=layer.canvas.getContext('2d'); ctx.fillStyle=$('#fgColor').value; ctx.font=`${size}px ${font}`; ctx.textBaseline='top'; ctx.fillText(text,x,y); recordHistory('Text'); renderAll(); }
function cropDocument(rect){ const r={x:clamp(Math.round(rect.x),0,state.width),y:clamp(Math.round(rect.y),0,state.height),w:clamp(Math.round(rect.w),1,state.width),h:clamp(Math.round(rect.h),1,state.height)}; if(!r.w||!r.h)return; for(const layer of state.layers){ const tmp=document.createElement('canvas'); tmp.width=r.w;tmp.height=r.h; tmp.getContext('2d').drawImage(layer.canvas,r.x,r.y,r.w,r.h,0,0,r.w,r.h); layer.canvas.width=r.w; layer.canvas.height=r.h; layer.canvas.getContext('2d').drawImage(tmp,0,0); } state.width=r.w;state.height=r.h; ensureCanvasSize(); state.selection=null; recordHistory('Crop'); fitToScreen(); }

async function openImage(file){ const url=URL.createObjectURL(file); await loadImageIntoDocument(url,file.name.replace(/\.[^.]+$/,'')); URL.revokeObjectURL(url); }
async function loadImageIntoDocument(url,name='Image') { const img=await loadImage(url); state.width=img.naturalWidth;state.height=img.naturalHeight;state.layers=[];state.history=[];state.historyIndex=-1;ensureCanvasSize(); const l=createLayer(name); l.canvas.getContext('2d').drawImage(img,0,0); fitToScreen();recordHistory('Open image');renderAll(); }
function loadImage(url){ return new Promise((resolve,reject)=>{const img=new Image();img.onload=()=>resolve(img);img.onerror=reject;img.src=url;}); }
function drawDataUrlToCanvas(url,canvas){ return new Promise((resolve,reject)=>{ const img=new Image(); img.onload=()=>{canvas.getContext('2d').drawImage(img,0,0); resolve();}; img.onerror=reject; img.src=url; }); }

function serializeProject(){ return {version:1, width:state.width,height:state.height,activeLayerId:state.activeLayerId,layers:state.layers.map(l=>({id:l.id,name:l.name,visible:l.visible,opacity:l.opacity,blendMode:l.blendMode,data:l.canvas.toDataURL('image/png')}))}; }
async function saveProject(as=false){ const data=JSON.stringify(serializeProject()); const blob=new Blob([data],{type:'application/json'}); downloadBlob(blob, 'web-photo-project.wpe'); state.projectDirty=false; }
async function loadProject(file){ const text=await file.text(); const p=JSON.parse(text); state.width=p.width;state.height=p.height;state.layers=[];ensureCanvasSize(); for(const sl of p.layers){const l=createLayer(sl.name);l.visible=sl.visible;l.opacity=sl.opacity;l.blendMode=sl.blendMode;await drawDataUrlToCanvas(sl.data,l.canvas);} state.activeLayerId=p.activeLayerId||state.layers.at(-1)?.id; state.history=[];state.historyIndex=-1;fitToScreen();recordHistory('Load project');state.projectDirty=false;renderAll(); }
function exportImage(type='png'){ composite(); let mime='image/png',ext='png'; if(type==='jpg'){mime='image/jpeg';ext='jpg';} if(type==='webp'){mime='image/webp';ext='webp';} displayCanvas.toBlob(blob=>downloadBlob(blob,`web-photo-editor.${ext}`),mime,0.92); }
function downloadBlob(blob,name){ const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(a.href),1000); }

function addLayer(){ createLayer(`Layer ${state.layers.length+1}`); recordHistory('New layer');renderAll(); }
function duplicateLayer(){ const l=activeLayer(); if(!l)return; const n=createLayer(l.name+' copy'); n.opacity=l.opacity;n.blendMode=l.blendMode;n.visible=l.visible;n.canvas.getContext('2d').drawImage(l.canvas,0,0); recordHistory('Duplicate layer');renderAll(); }
function deleteLayer(){ if(state.layers.length<=1)return; const i=state.layers.findIndex(l=>l.id===state.activeLayerId); state.layers.splice(i,1);state.activeLayerId=state.layers[Math.max(0,i-1)].id;recordHistory('Delete layer');renderAll(); }
function mergeVisible(){
  const vis=state.layers.filter(l=>l.visible); if(vis.length<2)return;
  const mergedCanvas=document.createElement('canvas'); mergedCanvas.width=state.width; mergedCanvas.height=state.height;
  const ctx=mergedCanvas.getContext('2d');
  for(const l of vis){ctx.save();ctx.globalAlpha=l.opacity;ctx.globalCompositeOperation=l.blendMode;ctx.drawImage(l.canvas,0,0);ctx.restore();}
  const merged={id:makeId('layer'),name:'Merged',visible:true,opacity:1,blendMode:'source-over',canvas:mergedCanvas};
  state.layers=state.layers.filter(l=>!vis.includes(l)); state.layers.push(merged); state.activeLayerId=merged.id;
  recordHistory('Merge visible');renderAll();
}
function flatten(){
  const mergedCanvas=document.createElement('canvas'); mergedCanvas.width=state.width; mergedCanvas.height=state.height;
  const ctx=mergedCanvas.getContext('2d');
  for(const l of state.layers){if(!l.visible)continue;ctx.save();ctx.globalAlpha=l.opacity;ctx.globalCompositeOperation=l.blendMode;ctx.drawImage(l.canvas,0,0);ctx.restore();}
  const merged={id:makeId('layer'),name:'Background',visible:true,opacity:1,blendMode:'source-over',canvas:mergedCanvas};
  state.layers=[merged];state.activeLayerId=merged.id;recordHistory('Flatten image');renderAll();
}
function selectAll(){state.selection={x:0,y:0,w:state.width,h:state.height};renderInteraction();}
function deselect(){state.selection=null;renderInteraction();}
function inverse(){ if(!state.selection){return;} const s=state.selection; state.selection={x:0,y:0,w:state.width,h:state.height}; state.selection.inverse=s; }
function imageSize(){showModal('Image Size', [{label:'Width',id:'w',value:state.width},{label:'Height',id:'h',value:state.height}], values=>{ const nw=Math.max(1,Number(values.w)); const nh=Math.max(1,Number(values.h)); const sx=nw/state.width, sy=nh/state.height; for(const l of state.layers){const t=document.createElement('canvas');t.width=nw;t.height=nh;t.getContext('2d').drawImage(l.canvas,0,0,nw,nh);l.canvas=t;} state.width=nw;state.height=nh;ensureCanvasSize();recordHistory('Image size');fitToScreen();renderAll();}); }
function canvasSize(){showModal('Canvas Size', [{label:'Width',id:'w',value:state.width},{label:'Height',id:'h',value:state.height}], values=>{ const nw=Math.max(1,Number(values.w)); const nh=Math.max(1,Number(values.h)); for(const l of state.layers){const t=document.createElement('canvas');t.width=nw;t.height=nh;t.getContext('2d').drawImage(l.canvas,0,0);l.canvas=t;} state.width=nw;state.height=nh;ensureCanvasSize();recordHistory('Canvas size');fitToScreen();renderAll();}); }
function rotateDocument(clockwise){ const nw=state.height,nh=state.width; for(const l of state.layers){const t=document.createElement('canvas');t.width=nw;t.height=nh;const c=t.getContext('2d'); if(clockwise){c.translate(nw,0);c.rotate(Math.PI/2);}else{c.translate(0,nh);c.rotate(-Math.PI/2);}c.drawImage(l.canvas,0,0);l.canvas=t;}state.width=nw;state.height=nh;ensureCanvasSize();recordHistory(clockwise?'Rotate CW':'Rotate CCW');fitToScreen();renderAll(); }
function flipDocument(horizontal){for(const l of state.layers){const t=document.createElement('canvas');t.width=state.width;t.height=state.height;const c=t.getContext('2d');c.translate(horizontal?state.width:0,horizontal?0:state.height);c.scale(horizontal?-1:1,horizontal?1:-1);c.drawImage(l.canvas,0,0);l.canvas=t;}recordHistory(horizontal?'Flip H':'Flip V');renderAll();}
function cut(){copy(); deleteSelectionPixels();recordHistory('Cut');renderAll();}
function copy(){ if(!state.selection)return; const s=state.selection; const t=document.createElement('canvas');t.width=s.w;t.height=s.h;t.getContext('2d').drawImage(displayCanvas,s.x,s.y,s.w,s.h,0,0,s.w,s.h); window.__clipboard=t; }
function paste(){ const t=window.__clipboard;if(!t)return;const l=createLayer('Pasted');l.canvas.getContext('2d').drawImage(t,20,20);recordHistory('Paste');renderAll(); }
function deleteSelectionPixels(){ if(!state.selection)return; const l=activeLayer(); const ctx=l.canvas.getContext('2d');ctx.save();ctx.globalCompositeOperation='destination-out';ctx.clearRect(state.selection.x,state.selection.y,state.selection.w,state.selection.h);ctx.restore(); }
function featherSelection(){ if(!state.selection)return; showModal('Feather Selection',[{label:'Radius',id:'r',value:state.feather}],v=>{state.feather=Math.max(0,Number(v.r)||0);renderInteraction();}); }
function shortcuts(){ alert('Ctrl/Cmd+Z Undo\nCtrl/Cmd+Shift+Z Redo\nCtrl/Cmd+S Save project\nCtrl/Cmd+O Open\nCtrl/Cmd+E Export\nB Brush\nE Eraser\nT Text\nM Marquee\nV Move\nC Crop\nI Eyedropper\nH Hand\nZ Zoom'); }
function about(){ alert('Web Photo Editor\nBrowser-based image editor for GitHub Pages.\nBuilt with HTML, CSS and JavaScript.'); }

function showModal(title,fields,onSubmit){ const root=$('#modalRoot');root.innerHTML=`<div class="modal"><div class="modal-header">${escapeHtml(title)}</div><div class="modal-body"><div class="modal-row">${fields.map(f=>`<div class="modal-field"><label>${escapeHtml(f.label)}</label><input id="modal-${f.id}" type="number" value="${f.value}"></div>`).join('')}</div></div><div class="modal-footer"><button id="modalCancel" class="ghost-btn">Cancel</button><button id="modalOk" class="primary-btn">OK</button></div></div>`;root.classList.remove('hidden');$('#modalCancel').onclick=()=>root.classList.add('hidden');$('#modalOk').onclick=()=>{const v={};fields.forEach(f=>v[f.id]=$(`#modal-${f.id}`).value);root.classList.add('hidden');onSubmit(v);}; }

function runAction(action){ switch(action){
  case'new': openNewDialog();break;case'open':$('#imageFileInput').click();break;case'openProject':$('#projectFileInput').click();break;case'save':saveProject();break;case'saveAs':saveProject(true);break;
  case'exportPng':exportImage('png');break;case'exportJpg':exportImage('jpg');break;case'exportWebp':exportImage('webp');break;case'clear':resetDocument();break;
  case'undo':undo();break;case'redo':redo();break;case'cut':cut();break;case'copy':copy();break;case'paste':paste();break;case'deleteLayer':deleteLayer();break;
  case'imageSize':imageSize();break;case'canvasSize':canvasSize();break;case'rotateCW':rotateDocument(true);break;case'rotateCCW':rotateDocument(false);break;case'flipH':flipDocument(true);break;case'flipV':flipDocument(false);break;
  case'addLayer':addLayer();break;case'duplicateLayer':duplicateLayer();break;case'mergeVisible':mergeVisible();break;case'flatten':flatten();break;
  case'selectAll':selectAll();break;case'deselect':deselect();break;case'inverse':inverse();break;case'feather':featherSelection();break;
  case'zoomIn':zoomIn();break;case'zoomOut':zoomOut();break;case'fit':fitToScreen();break;case'toggleRulers':state.showRulers=!state.showRulers;renderAll();break;case'toggleGrid':state.gridVisible=!state.gridVisible;renderAll();break;
  case'shortcuts':shortcuts();break;case'about':about();break;
 }}

function openNewDialog(){ showModal('New Document',[{label:'Width',id:'w',value:1200},{label:'Height',id:'h',value:800}],v=>resetDocument(Number(v.w)||1200,Number(v.h)||800)); }
function capitalize(s){return s.charAt(0).toUpperCase()+s.slice(1);}
function rgbToHex(r,g,b){return '#'+[r,g,b].map(x=>x.toString(16).padStart(2,'0')).join('');}
function hexToRgba(hex){const h=hex.replace('#','');return [parseInt(h.slice(0,2),16),parseInt(h.slice(2,4),16),parseInt(h.slice(4,6),16),255];}

$('#newBtn').onclick=openNewDialog; $('#openBtn').onclick=()=>$('#imageFileInput').click(); $('#saveBtn').onclick=()=>saveProject(); $('#exportBtn').onclick=()=>exportImage('png');
$('#imageFileInput').addEventListener('change',e=>{const f=e.target.files[0]; if(f)openImage(f);e.target.value='';});
$('#projectFileInput').addEventListener('change',e=>{const f=e.target.files[0]; if(f)loadProject(f);e.target.value='';});
$('#addLayerBtn').onclick=addLayer; $('#duplicateLayerBtn').onclick=duplicateLayer; $('#deleteLayerBtn').onclick=deleteLayer;
$('#opacitySlider').oninput=e=>{const l=activeLayer();if(l){l.opacity=Number(e.target.value)/100;$('#opacityValue').textContent=e.target.value+'%';composite();}};
$('#opacitySlider').onchange=()=>recordHistory('Layer opacity');
$('#blendMode').onchange=e=>{const l=activeLayer();if(l){l.blendMode=e.target.value;recordHistory('Blend mode');renderAll();}};
$('#swapColors').onclick=()=>{const a=$('#fgColor').value;$('#fgColor').value=$('#bgColor').value;$('#bgColor').value=a;};

interactionCanvas.addEventListener('pointerdown',pointerDown); interactionCanvas.addEventListener('pointermove',pointerMove); interactionCanvas.addEventListener('pointerup',pointerUp); interactionCanvas.addEventListener('pointercancel',pointerCancel);
stageWrap.addEventListener('wheel',e=>{e.preventDefault();const p={x:e.clientX-stageWrap.getBoundingClientRect().left,y:e.clientY-stageWrap.getBoundingClientRect().top};setZoom(state.zoom*(e.deltaY<0?1.1:.9),p);},{passive:false});
window.addEventListener('keydown',e=>{
  const mod=e.ctrlKey||e.metaKey;
  if(mod&&e.key.toLowerCase()==='z'){e.preventDefault();e.shiftKey?redo():undo();return;}
  if(mod&&e.key.toLowerCase()==='s'){e.preventDefault();saveProject();return;}
  if(mod&&e.key.toLowerCase()==='o'){e.preventDefault();$('#imageFileInput').click();return;}
  if(mod&&e.key.toLowerCase()==='e'){e.preventDefault();exportImage('png');return;}
  const map={v:'move',m:'marquee',c:'crop',i:'eyedropper',b:'brush',e:'eraser',t:'text',p:'pen',h:'hand',z:'zoom'}; if(!mod && map[e.key.toLowerCase()]) setTool(map[e.key.toLowerCase()]);
});

window.addEventListener('resize',()=>fitToScreen());

renderMenus(); renderToolbar(); renderOptions(); resetDocument();
