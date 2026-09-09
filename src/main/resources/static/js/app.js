const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const pageMeta = {
  dashboard: ['工作台', '掌握知识库的最新状态'],
  documents: ['文献管理', '导入并维护本地知识资料'],
  chat: ['智能问答', '基于文献内容获得有依据的回答'],
  history: ['问答记录', '回顾已经完成的知识查询']
};
let selectedFiles = [];

async function api(path, options = {}) {
  const response = await fetch(path, options);
  const type = response.headers.get('content-type') || '';
  const data = type.includes('json') ? await response.json() : await response.text();
  if (!response.ok) throw new Error(data.message || data || `请求失败 (${response.status})`);
  return data;
}

function showPage(name) {
  $$('.page').forEach(page => page.classList.toggle('active', page.id === `${name}Page`));
  $$('.nav-item').forEach(item => item.classList.toggle('active', item.dataset.page === name));
  $('#pageTitle').textContent = pageMeta[name][0];
  $('#pageSubtitle').textContent = pageMeta[name][1];
  $('#sidebar').classList.remove('open');
  if (name === 'dashboard') loadDashboard();
  if (name === 'documents') loadDocuments();
  if (name === 'history') loadHistory();
}

function toast(message, error = false) {
  const element = $('#toast');
  element.textContent = message;
  element.className = `toast show${error ? ' error' : ''}`;
  clearTimeout(element.timer);
  element.timer = setTimeout(() => element.className = 'toast', 3200);
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', {hour12: false}) : '-';
}

async function loadStatus() {
  try {
    const status = await api('/api/status');
    $('#statusDot').classList.toggle('ready', status.ready);
    $('#statusText').textContent = status.ready ? '系统运行正常' : '向量服务未连接';
    $('#statusText').title = `${status.vectorStore}\n${status.embeddingProvider}\n${status.chatProvider}`;
  } catch {
    $('#statusText').textContent = '后端服务异常';
  }
}

async function loadDashboard() {
  try {
    const data = await api('/api/dashboard');
    ['documentCount', 'readyCount', 'chunkCount', 'questionCount'].forEach(key => $(`#${key}`).textContent = data[key]);
    const container = $('#recentHistory');
    container.replaceChildren();
    if (!data.recentQuestions.length) {
      container.className = 'empty compact-empty'; container.textContent = '暂无提问记录'; return;
    }
    container.className = '';
    data.recentQuestions.forEach(item => {
      const row = document.createElement('div'); row.className = 'recent-item';
      const title = document.createElement('strong'); title.textContent = item.question;
      const meta = document.createElement('small'); meta.textContent = `${formatTime(item.createdAt)} · ${item.sourceCount} 个来源`;
      row.append(title, meta); container.append(row);
    });
  } catch (error) { toast(error.message, true); }
}

async function loadDocuments() {
  try {
    const documents = await api('/api/documents');
    $('#documentSummary').textContent = `共 ${documents.length} 份文献`;
    const body = $('#documentTable'); body.replaceChildren();
    if (!documents.length) {
      const row = body.insertRow(); const cell = row.insertCell(); cell.colSpan = 7; cell.className = 'empty'; cell.textContent = '暂无文献'; return;
    }
    documents.forEach(item => {
      const row = body.insertRow();
      const nameCell = row.insertCell();
      const tag = document.createElement('span'); tag.className = 'file-type'; tag.textContent = item.name.split('.').pop().toUpperCase();
      const name = document.createElement('strong'); name.textContent = item.name; name.title = item.name;
      nameCell.append(tag, name);
      row.insertCell().textContent = formatBytes(item.sizeBytes);
      row.insertCell().textContent = item.pageCount || '-';
      row.insertCell().textContent = item.chunkCount || '-';
      const statusCell = row.insertCell();
      const badge = document.createElement('span'); badge.className = `badge ${item.status.toLowerCase()}`;
      badge.textContent = {READY: '可用', PROCESSING: '处理中', FAILED: '失败'}[item.status] || item.status;
      if (item.errorMessage) badge.title = item.errorMessage; statusCell.append(badge);
      row.insertCell().textContent = formatTime(item.createdAt);
      const actionCell = row.insertCell(); const actions = document.createElement('div'); actions.className = 'table-actions';
      const download = document.createElement('a'); download.textContent = '下载'; download.href = `/api/documents/${item.id}/download`;
      const remove = document.createElement('button'); remove.className = 'delete'; remove.textContent = '删除'; remove.onclick = () => deleteDocument(item.id, item.name);
      actions.append(download, remove); actionCell.append(actions);
    });
  } catch (error) { toast(error.message, true); }
}

function setFiles(files) {
  selectedFiles = [...files].filter(file => /\.(pdf|txt|md|markdown)$/i.test(file.name)).slice(0, 20);
  const container = $('#selectedFiles'); container.replaceChildren();
  selectedFiles.forEach(file => { const chip = document.createElement('span'); chip.className = 'file-chip'; chip.textContent = `${file.name} · ${formatBytes(file.size)}`; container.append(chip); });
  $('#uploadButton').disabled = selectedFiles.length === 0;
}

async function uploadFiles() {
  if (!selectedFiles.length) return;
  const button = $('#uploadButton'); button.disabled = true; button.textContent = '正在解析并构建向量索引…';
  const form = new FormData(); selectedFiles.forEach(file => form.append('files', file));
  try {
    const result = await api('/api/documents', {method: 'POST', body: form});
    const failed = result.filter(item => item.status === 'FAILED');
    toast(failed.length ? `${result.length - failed.length} 份成功，${failed.length} 份失败` : `${result.length} 份文献已成功导入`, failed.length > 0);
    selectedFiles = []; $('#fileInput').value = ''; setFiles([]); await loadDocuments(); await loadDashboard(); await loadStatus();
  } catch (error) { toast(error.message, true); }
  finally { button.textContent = '上传并构建知识库'; button.disabled = selectedFiles.length === 0; }
}

async function deleteDocument(id, name) {
  if (!confirm(`确认删除“${name}”及其全部向量片段吗？`)) return;
  try { await api(`/api/documents/${id}`, {method: 'DELETE'}); toast('文献已删除'); await loadDocuments(); await loadDashboard(); }
  catch (error) { toast(error.message, true); }
}

function appendMessage(role, text, extraClass = '') {
  const row = document.createElement('div'); row.className = `message ${role} ${extraClass}`;
  if (role === 'assistant') { const avatar = document.createElement('span'); avatar.className = 'avatar'; avatar.textContent = '✦'; row.append(avatar); }
  const bubble = document.createElement('div'); bubble.className = 'bubble'; bubble.textContent = text; row.append(bubble);
  $('#messages').append(row); $('#messages').scrollTop = $('#messages').scrollHeight; return row;
}

async function askQuestion(event) {
  event.preventDefault(); const input = $('#questionInput'); const question = input.value.trim(); if (!question) return;
  appendMessage('user', question); input.value = '';
  const pending = appendMessage('assistant', '正在检索知识库并组织答案…', 'pending');
  try {
    const result = await api('/api/chat', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({question})});
    pending.remove(); appendMessage('assistant', `${result.answer}\n\n耗时 ${result.durationMs} ms`); renderSources(result.sources); loadDashboard();
  } catch (error) { pending.remove(); appendMessage('assistant', `提问失败：${error.message}`); toast(error.message, true); }
}

function renderSources(sources) {
  const container = $('#sources'); container.replaceChildren();
  if (!sources.length) { container.className = 'empty'; container.textContent = '本次回答没有检索到来源'; return; }
  container.className = '';
  sources.forEach((source, index) => {
    const card = document.createElement('article'); card.className = 'source-card'; card.onclick = () => card.classList.toggle('open');
    const header = document.createElement('header'); const title = document.createElement('strong'); title.textContent = `[${index + 1}] ${source.title}`;
    const score = document.createElement('span'); score.textContent = `相似度 ${(source.score * 100).toFixed(1)}%`;
    const content = document.createElement('p'); content.textContent = source.content; header.append(title, score); card.append(header, content); container.append(card);
  });
}

async function loadHistory() {
  try {
    const items = await api('/api/history?limit=100'); const container = $('#historyList'); container.replaceChildren();
    if (!items.length) { const empty = document.createElement('div'); empty.className = 'empty'; empty.textContent = '暂无问答记录'; container.append(empty); return; }
    items.forEach(item => {
      const card = document.createElement('article'); card.className = 'history-item';
      const header = document.createElement('header'); const title = document.createElement('h4'); title.textContent = item.question;
      const time = document.createElement('time'); time.textContent = formatTime(item.createdAt); const answer = document.createElement('p'); answer.textContent = item.answer;
      header.append(title, time); card.append(header, answer); container.append(card);
    });
  } catch (error) { toast(error.message, true); }
}

$$('.nav-item').forEach(item => item.addEventListener('click', () => showPage(item.dataset.page)));
$$('[data-go]').forEach(item => item.addEventListener('click', () => showPage(item.dataset.go)));
$('#menuButton').addEventListener('click', () => $('#sidebar').classList.toggle('open'));
$('#fileInput').addEventListener('change', event => setFiles(event.target.files));
$('#uploadButton').addEventListener('click', uploadFiles);
$('#refreshDocuments').addEventListener('click', loadDocuments);
const dropZone = $('#dropZone');
['dragenter', 'dragover'].forEach(type => dropZone.addEventListener(type, event => { event.preventDefault(); dropZone.classList.add('dragging'); }));
['dragleave', 'drop'].forEach(type => dropZone.addEventListener(type, event => { event.preventDefault(); dropZone.classList.remove('dragging'); }));
dropZone.addEventListener('drop', event => setFiles(event.dataTransfer.files));
$('#chatForm').addEventListener('submit', askQuestion);
$('#questionInput').addEventListener('keydown', event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); $('#chatForm').requestSubmit(); } });
$('#clearChat').addEventListener('click', () => { $('#messages').innerHTML = '<div class="message assistant"><span class="avatar">✦</span><div class="bubble">当前对话已清空，可以继续提问。</div></div>'; renderSources([]); });
$('#clearHistory').addEventListener('click', async () => { if (!confirm('确认清空全部问答记录吗？')) return; try { await api('/api/history', {method: 'DELETE'}); toast('问答记录已清空'); loadHistory(); loadDashboard(); } catch (error) { toast(error.message, true); } });

loadStatus(); loadDashboard(); setInterval(loadStatus, 30000);
