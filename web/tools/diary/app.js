const DB_NAME = "one-page-diary-web";
const DB_VERSION = 1;
const DEVICE_ID_KEY = "one-page-diary-device-id";
const AUTHOR_KEY = "one-page-diary-current-author";
const DEFAULT_AUTHOR = "小妈妈";
const PAGE_SIZE = 30;
const tagPattern = /#([\p{L}\p{N}_-]+)/gu;
const attachmentPattern = /!\[图片\]\(attachment:([^)]+)\)/g;

let db;
let deviceId;
let currentTab = "write";
let returnTab = "write";
let editingId = 0;
let originalBody = "";
let selectedDay = startOfDay(Date.now());
let visibleMonth = new Date(selectedDay);
let selectedTag = null;
let selectedAuthor = null;
let calendarLimit = PAGE_SIZE;
let tagLimit = PAGE_SIZE;
let detailEntry = null;
let settingsReturnTab = "write";
let lastServiceUpdatedAt = null;
let toastTimer = 0;
let serverEntriesCache = null;

const $ = (id) => document.getElementById(id);

init();

async function init() {
  deviceId = ensureDeviceId();
  db = await openDb();
  bindEvents();
  startAutoServiceSync();
  await autoMergeLatest();
  await renderWrite();
  await switchTab("write");
}

function bindEvents() {
  document.querySelectorAll(".tab").forEach((button) => {
    button.addEventListener("click", () => requestSwitch(button.dataset.tab));
  });
  $("saveEntry").addEventListener("click", saveEntry);
  $("newEntry").addEventListener("click", newEntry);
  $("pickImage").addEventListener("click", () => $("imageInput").click());
  $("imageInput").addEventListener("change", insertImage);
  $("editor").addEventListener("input", () => {
    renderTagHints();
    updateWriteActions();
  });
  $("editor").addEventListener("paste", (event) => {
    event.preventDefault();
    insertAtCursor(event.clipboardData.getData("text/plain"));
  });
  $("prevMonth").addEventListener("click", async () => {
    visibleMonth.setMonth(visibleMonth.getMonth() - 1);
    await renderCalendar();
  });
  $("nextMonth").addEventListener("click", async () => {
    visibleMonth.setMonth(visibleMonth.getMonth() + 1);
    await renderCalendar();
  });
  $("todayBtn").addEventListener("click", async () => {
    selectedDay = startOfDay(Date.now());
    visibleMonth = new Date(selectedDay);
    calendarLimit = PAGE_SIZE;
    await renderCalendar();
  });
  $("allDaysBtn").addEventListener("click", async () => {
    selectedDay = -1;
    calendarLimit = PAGE_SIZE;
    await renderCalendar();
  });
  $("backDetail").addEventListener("click", () => switchTab(returnTab));
  $("editDetail").addEventListener("click", () => loadEntry(detailEntry));
  $("deleteDetail").addEventListener("click", deleteDetail);
  $("openSettings").addEventListener("click", requestSettings);
  $("backSettings").addEventListener("click", () => switchTab(settingsReturnTab));
  $("exportZip").addEventListener("click", exportZip);
  $("importZip").addEventListener("click", () => $("zipInput").click());
  $("zipInput").addEventListener("change", importZip);
  $("currentAuthor").addEventListener("input", saveCurrentAuthor);
  $("lightbox").addEventListener("click", () => {
    $("lightbox").hidden = true;
    $("lightbox").innerHTML = "";
  });
  window.addEventListener("beforeunload", (event) => {
    if (hasUnsavedDraft()) {
      event.preventDefault();
      event.returnValue = "";
    }
  });
}

function startAutoServiceSync() {
  if (!location.protocol.startsWith("http")) return;
  window.addEventListener("focus", () => autoRefreshFromService());
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) autoRefreshFromService();
  });
  setInterval(() => autoRefreshFromService(), 10000);
}

function showToast(message, type = "ok") {
  const box = $("toast");
  window.clearTimeout(toastTimer);
  box.className = `toast ${type}`;
  box.textContent = message;
  box.hidden = false;
  toastTimer = window.setTimeout(() => {
    box.hidden = true;
  }, 2200);
}

function confirmDialog(message, okText = "确定") {
  return new Promise((resolve) => {
    const layer = $("confirmLayer");
    const text = $("confirmText");
    const ok = $("confirmOk");
    const cancel = $("confirmCancel");
    const close = (value) => {
      layer.hidden = true;
      ok.onclick = null;
      cancel.onclick = null;
      layer.onclick = null;
      resolve(value);
    };
    text.textContent = message;
    ok.textContent = okText;
    cancel.textContent = "取消";
    ok.onclick = () => close(true);
    cancel.onclick = () => close(false);
    layer.onclick = (event) => {
      if (event.target === layer) close(false);
    };
    layer.hidden = false;
  });
}

function ensureDeviceId() {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = `web-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
    localStorage.setItem(DEVICE_ID_KEY, id);
  }
  return id;
}

function openDb() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const next = request.result;
      if (!next.objectStoreNames.contains("entries")) {
        next.createObjectStore("entries", { keyPath: "id" });
      }
      if (!next.objectStoreNames.contains("attachments")) {
        next.createObjectStore("attachments", { keyPath: "filename" });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function tx(store, mode = "readonly") {
  return db.transaction(store, mode).objectStore(store);
}

function getAll(store) {
  return new Promise((resolve, reject) => {
    const request = tx(store).getAll();
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function getByKey(store, key) {
  return new Promise((resolve, reject) => {
    const request = tx(store).get(key);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function put(store, value) {
  return new Promise((resolve, reject) => {
    const request = tx(store, "readwrite").put(value);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

function del(store, key) {
  return new Promise((resolve, reject) => {
    const request = tx(store, "readwrite").delete(key);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

async function allEntries() {
  const entries = (await fetchServerEntries()).filter((entry) => !entry.deletedAt);
  return entries.sort((a, b) => b.createdAt - a.createdAt || b.id - a.id);
}

async function allStoredEntries() {
  const entries = await fetchServerEntries();
  return entries.sort((a, b) => b.createdAt - a.createdAt || b.id - a.id);
}

async function fetchServerEntries() {
  if (serverEntriesCache) return cloneEntries(serverEntriesCache);
  const response = await fetch("/api/entries", { cache: "no-store" });
  if (!response.ok) throw new Error(`服务端日记失败 ${response.status}`);
  const body = await response.json();
  if (body.app !== "one-page-diary") throw new Error("不是一页日记服务");
  serverEntriesCache = (body.entries || []).map(normalizeEntry);
  return cloneEntries(serverEntriesCache);
}

function cloneEntries(entries) {
  return entries.map((entry) => ({
    ...entry,
    tags: [...entry.tags],
    attachments: [...entry.attachments],
  }));
}

function normalizeEntry(entry) {
  const body = entry && typeof entry.body === "string" ? entry.body : "";
  const id = Number(entry && entry.id ? entry.id : Date.now());
  const createdAt = normalizeTime(entry && entry.createdAt, id);
  const updatedAt = normalizeTime(entry && entry.updatedAt, createdAt);
  return {
    id,
    createdAt,
    updatedAt,
    deletedAt: normalizeTime(entry && entry.deletedAt, 0),
    version: Math.max(1, Number(entry && entry.version ? entry.version : 1)),
    deviceId: entry && typeof entry.deviceId === "string" ? entry.deviceId : "",
    author: entryAuthor(entry),
    body,
    tags: Array.isArray(entry && entry.tags) ? entry.tags : extractTags(body),
    attachments: normalizeAttachments(entry && entry.attachments, body)
  };
}

function normalizeTime(value, fallback) {
  if (value === null || value === undefined || value === "" || value === false) return fallback;
  if (typeof value === "number") return Number.isFinite(value) ? value : fallback;
  if (typeof value === "string") {
    const numeric = Number(value);
    if (Number.isFinite(numeric)) return numeric;
    const normalized = value.replace(/([+-]\d{2})(\d{2})$/, "$1:$2");
    const parsed = Date.parse(normalized);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

async function switchTab(tab) {
  currentTab = tab;
  document.querySelector(".app").classList.toggle("settings-mode", tab === "settings");
  $("tabs").hidden = tab === "settings";
  document.querySelectorAll(".tab").forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === tab);
  });
  document.querySelectorAll(".page").forEach((page) => page.classList.remove("active"));
  $(tab).classList.add("active");
  if (tab === "settings") $("currentAuthor").value = currentAuthor();
  if (tab === "write") await renderWrite();
  if (tab === "calendar") await renderCalendar();
  if (tab === "tags") await renderTags();
}

async function requestSwitch(tab) {
  if (tab === currentTab) return;
  if (currentTab === "write" && hasUnsavedDraft() && !(await confirmDialog("当前内容还没有保存，放弃这次修改？", "放弃"))) {
    return;
  }
  if (currentTab === "write" && hasUnsavedDraft()) {
    await clearDraft();
  }
  await switchTab(tab);
}

async function requestSettings() {
  if (currentTab === "settings") return;
  if (currentTab === "write" && hasUnsavedDraft() && !(await confirmDialog("当前内容还没有保存，放弃这次修改？", "放弃"))) {
    return;
  }
  if (currentTab === "write" && hasUnsavedDraft()) {
    await clearDraft();
  }
  settingsReturnTab = currentTab === "detail" ? returnTab : currentTab;
  await switchTab("settings");
}

async function renderWrite() {
  $("currentAuthor").value = currentAuthor();
  await renderTagHints();
  updateWriteActions();
}

async function saveEntry() {
  const body = editorValue().trim();
  if (!body) {
    showToast("还没有内容", "warn");
    return;
  }
  const now = Date.now();
  const id = editingId || now;
  const existing = editingId ? await getEntry(editingId) : null;
  const entry = {
    id,
    createdAt: existing ? existing.createdAt : now,
    updatedAt: now,
    deletedAt: 0,
    version: (existing ? existing.version : 0) + 1,
    deviceId,
    author: existing ? entryAuthor(existing) : currentAuthor(),
    body,
    tags: extractTags(body),
    attachments: extractAttachments(body)
  };
  await saveServerEntry(entry);
  editingId = 0;
  originalBody = "";
  serverEntriesCache = null;
  await setEditorValue("");
  await showDetail(entry, "write");
}

async function newEntry() {
  if (!editingId && !editorValue().trim()) {
    $("editor").focus();
    return;
  }
  const message = editingId ? "取消编辑，回到空白日记？" : "清空当前内容？";
  const okText = editingId ? "取消编辑" : "清空";
  if (hasUnsavedDraft() && !(await confirmDialog(message, okText))) return;
  await clearDraft();
  await switchTab("write");
}

async function clearDraft() {
  for (const filename of extractAttachments(editorValue())) {
    if (!(await attachmentReferenced(filename))) {
      await del("attachments", filename);
    }
  }
  editingId = 0;
  originalBody = "";
  await setEditorValue("");
  updateWriteActions();
}

function updateWriteActions() {
  const clearLabel = editingId ? "取消编辑" : "清空";
  $("newEntry").title = clearLabel;
  $("newEntry").setAttribute("aria-label", clearLabel);
  $("saveEntry").textContent = editingId ? "更新" : "保存";
}

function hasUnsavedDraft() {
  const body = editorValue();
  if (!editingId) return body.trim().length > 0;
  return body !== originalBody;
}

async function insertImage() {
  const file = $("imageInput").files[0];
  $("imageInput").value = "";
  if (!file) return;
  const ext = (file.type.split("/")[1] || "jpg").replace("jpeg", "jpg");
  const filename = `img-${formatFileDate(new Date())}-${Date.now()}.${ext}`;
  await put("attachments", { filename, blob: file, type: file.type });
  await insertImageAtCursor(filename);
  updateWriteActions();
}

function insertAtCursor(text) {
  const editor = $("editor");
  editor.focus();
  const range = currentEditorRange();
  range.deleteContents();
  range.insertNode(document.createTextNode(text));
  range.collapse(false);
  setEditorRange(range);
  renderTagHints();
  updateWriteActions();
}

async function insertImageAtCursor(filename) {
  const editor = $("editor");
  editor.focus();
  const range = currentEditorRange();
  range.deleteContents();
  const imageNode = await makeEditorImage(filename);
  const fragment = document.createDocumentFragment();
  fragment.appendChild(document.createElement("br"));
  fragment.appendChild(imageNode);
  const after = document.createElement("br");
  const next = document.createTextNode("");
  fragment.appendChild(after);
  fragment.appendChild(next);
  range.insertNode(fragment);
  const nextRange = document.createRange();
  nextRange.setStart(next, 0);
  nextRange.collapse(true);
  setEditorRange(nextRange);
}

async function makeEditorImage(filename) {
  const figure = document.createElement("figure");
  figure.className = "editor-image";
  figure.contentEditable = "false";
  figure.dataset.attachment = filename;
  const record = await getAttachment(filename);
  const img = document.createElement("img");
  img.src = record ? URL.createObjectURL(record.blob) : `/api/attachments/${encodeURIComponent(filename)}`;
  img.alt = "图片";
  img.onclick = () => {
    if (img.src) showLightbox(img.src);
  };
  const remove = document.createElement("button");
  remove.type = "button";
  remove.textContent = "移除";
  remove.onclick = async () => {
    if (!(await confirmDialog("从正文移除这张图片？", "移除"))) return;
    figure.remove();
    updateWriteActions();
  };
  figure.append(img, remove);
  return figure;
}

function editorValue(root = $("editor")) {
  return serializeEditorNode(root).replace(/\n{3,}/g, "\n\n").trim();
}

function serializeEditorNode(node) {
  if (node.nodeType === Node.TEXT_NODE) return node.nodeValue || "";
  if (node.nodeType !== Node.ELEMENT_NODE && node.nodeType !== Node.DOCUMENT_FRAGMENT_NODE) return "";
  if (node.nodeType === Node.ELEMENT_NODE) {
    if (node.classList.contains("editor-image")) {
      const filename = node.dataset.attachment;
      return filename ? `\n![图片](attachment:${filename})\n` : "";
    }
    if (node.tagName === "BR") return "\n";
  }
  let text = "";
  node.childNodes.forEach((child) => {
    text += serializeEditorNode(child);
  });
  if (node.nodeType === Node.ELEMENT_NODE && ["DIV", "P"].includes(node.tagName) && text && !text.endsWith("\n")) {
    text += "\n";
  }
  return text;
}

async function setEditorValue(body) {
  const editor = $("editor");
  editor.innerHTML = "";
  let cursor = 0;
  attachmentPattern.lastIndex = 0;
  let match;
  while ((match = attachmentPattern.exec(body || ""))) {
    appendEditorText((body || "").slice(cursor, match.index));
    editor.appendChild(await makeEditorImage(match[1]));
    editor.appendChild(document.createElement("br"));
    cursor = match.index + match[0].length;
  }
  appendEditorText((body || "").slice(cursor));
}

function appendEditorText(text) {
  if (!text) return;
  const editor = $("editor");
  const parts = text.split("\n");
  parts.forEach((part, index) => {
    if (index > 0) editor.appendChild(document.createElement("br"));
    if (part) editor.appendChild(document.createTextNode(part));
  });
}

function currentEditorRange() {
  const editor = $("editor");
  const selection = window.getSelection();
  if (selection && selection.rangeCount) {
    const range = selection.getRangeAt(0);
    if (editor.contains(range.commonAncestorContainer)) return range;
  }
  const range = document.createRange();
  range.selectNodeContents(editor);
  range.collapse(false);
  return range;
}

function setEditorRange(range) {
  const selection = window.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);
}

function editorTextBeforeCursor() {
  const editor = $("editor");
  const selection = window.getSelection();
  if (!selection || !selection.rangeCount) return "";
  const range = selection.getRangeAt(0).cloneRange();
  if (!editor.contains(range.commonAncestorContainer)) return "";
  range.selectNodeContents(editor);
  range.setEnd(selection.anchorNode, selection.anchorOffset);
  return range.cloneContents().textContent || "";
}

function deleteBackwardCharacters(count) {
  const selection = window.getSelection();
  if (!selection || !selection.rangeCount || count <= 0) return false;
  if (typeof selection.modify !== "function") return false;
  for (let i = 0; i < count; i++) selection.modify("extend", "backward", "character");
  selection.deleteFromDocument();
  return true;
}

async function renderTagHints() {
  const box = $("tagHints");
  box.innerHTML = "";
  const counts = await tagCounts();
  const prefix = currentTagPrefix();
  if (prefix === null) return;
  const names = Object.keys(counts).filter((tag) => !prefix || tag.toLowerCase().startsWith(prefix.toLowerCase())).slice(0, 6);
  if (!names.length) return;
  let row = document.createElement("div");
  row.className = "chiprow";
  box.appendChild(row);
  names.forEach((tag, index) => {
    if (index && index % 3 === 0) {
      row = document.createElement("div");
      row.className = "chiprow";
      box.appendChild(row);
    }
    const button = document.createElement("button");
    button.className = "chip";
    button.textContent = `#${tag}`;
    button.onclick = () => insertTag(tag);
    row.appendChild(button);
  });
}

function currentTagPrefix() {
  const before = editorTextBeforeCursor();
  const hash = before.lastIndexOf("#");
  if (hash < 0) return null;
  const text = before.slice(hash + 1);
  return /\s/.test(text) ? null : text;
}

function insertTag(tag) {
  const before = editorTextBeforeCursor();
  const hash = before.lastIndexOf("#");
  const prefix = hash >= 0 ? before.slice(hash + 1) : "";
  const replacingPrefix = hash >= 0 && !/\s/.test(prefix);
  if (replacingPrefix) deleteBackwardCharacters(prefix.length + 1);
  const lead = !replacingPrefix && before && !/\s$/.test(before) ? " " : "";
  insertAtCursor(`${lead}#${tag} `);
}

async function renderCalendar() {
  const entries = filterEntriesByAuthor(await allEntries());
  const counts = new Map();
  entries.forEach((entry) => counts.set(startOfDay(entry.createdAt), (counts.get(startOfDay(entry.createdAt)) || 0) + 1));
  await renderAuthorFilters($("calendarAuthorFilters"), async () => {
    calendarLimit = PAGE_SIZE;
    await renderCalendar();
  });
  $("monthTitle").textContent = `${visibleMonth.getFullYear()} 年 ${visibleMonth.getMonth() + 1} 月`;
  const grid = $("calendarGrid");
  grid.innerHTML = "";
  ["日", "一", "二", "三", "四", "五", "六"].forEach((name) => {
    const cell = document.createElement("div");
    cell.className = "weekday";
    cell.textContent = name;
    grid.appendChild(cell);
  });
  const first = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth(), 1);
  const cursor = new Date(first);
  cursor.setDate(cursor.getDate() - cursor.getDay());
  for (let i = 0; i < 42; i++) {
    const day = startOfDay(cursor.getTime());
    const count = counts.get(day) || 0;
    const cell = document.createElement("button");
    cell.className = "day";
    if (cursor.getMonth() !== visibleMonth.getMonth()) cell.classList.add("dim");
    if (day === selectedDay) cell.classList.add("selected");
    cell.innerHTML = `${cursor.getDate()}<br>${count ? count : "&nbsp;"}`;
    cell.onclick = async () => {
      selectedDay = day;
      calendarLimit = PAGE_SIZE;
      await renderCalendar();
    };
    grid.appendChild(cell);
    cursor.setDate(cursor.getDate() + 1);
  }
  let list = selectedDay === -1 ? entries : entries.filter((entry) => startOfDay(entry.createdAt) === selectedDay);
  $("calendarListTitle").textContent = selectedDay === -1 ? `全部日记 · ${list.length}` : `${formatDay(selectedDay)} · ${list.length}`;
  await renderList($("calendarEntries"), list, calendarLimit, async () => {
    calendarLimit += PAGE_SIZE;
    await renderCalendar();
  });
}

async function renderTags() {
  await renderAuthorFilters($("tagAuthorFilters"), async () => {
    tagLimit = PAGE_SIZE;
    await renderTags();
  });
  const filters = $("tagFilters");
  filters.innerHTML = "";
  addFilterLabel(filters, "标签");
  const counts = await tagCounts();
  const entries = filterEntriesByAuthor(await allEntries());
  addChip(filters, `全部标签 ${entries.length}`, selectedTag === null, async () => {
    selectedTag = null;
    tagLimit = PAGE_SIZE;
    await renderTags();
  });
  Object.keys(counts).sort().forEach((tag) => {
    addChip(filters, `#${tag} ${counts[tag]}`, selectedTag === tag, async () => {
      selectedTag = tag;
      tagLimit = PAGE_SIZE;
      await renderTags();
    });
  });
  const list = selectedTag ? entries.filter((entry) => entry.tags.includes(selectedTag)) : entries;
  $("tagListTitle").textContent = selectedTag ? `#${selectedTag} · ${list.length}` : `全部日记 · ${list.length}`;
  await renderList($("tagEntries"), list, tagLimit, async () => {
    tagLimit += PAGE_SIZE;
    await renderTags();
  });
}

function addChip(row, text, active, onClick) {
  const button = document.createElement("button");
  button.className = "chip";
  if (active) button.classList.add("primary");
  button.textContent = text;
  button.onclick = onClick;
  row.appendChild(button);
}

function addFilterLabel(row, text) {
  const label = document.createElement("span");
  label.className = "filter-label";
  label.textContent = text;
  row.appendChild(label);
}

async function renderAuthorFilters(container, refresh) {
  container.innerHTML = "";
  const counts = authorCounts(await allEntries());
  addFilterLabel(container, "写作者");
  addChip(container, `全部作者 ${Object.values(counts).reduce((sum, count) => sum + count, 0)}`, selectedAuthor === null, async () => {
    selectedAuthor = null;
    await refresh();
  });
  Object.keys(counts).sort((a, b) => a.localeCompare(b, "zh-Hans-CN")).forEach((author) => {
    addChip(container, `${author} ${counts[author]}`, selectedAuthor === author, async () => {
      selectedAuthor = author;
      await refresh();
    });
  });
}

function authorCounts(entries) {
  const counts = {};
  entries.forEach((entry) => {
    const author = entryAuthor(entry);
    counts[author] = (counts[author] || 0) + 1;
  });
  return counts;
}

function filterEntriesByAuthor(entries) {
  return selectedAuthor ? entries.filter((entry) => entryAuthor(entry) === selectedAuthor) : entries;
}

function entryAuthor(entry) {
  const author = entry && typeof entry.author === "string" ? entry.author.trim() : "";
  return author || DEFAULT_AUTHOR;
}

function currentAuthor() {
  return (localStorage.getItem(AUTHOR_KEY) || "").trim() || DEFAULT_AUTHOR;
}

function saveCurrentAuthor() {
  const input = $("currentAuthor");
  const author = input.value.trim() || DEFAULT_AUTHOR;
  localStorage.setItem(AUTHOR_KEY, author);
}

async function renderList(container, entries, limit, loadMore) {
  container.innerHTML = "";
  if (!entries.length) {
    container.innerHTML = `<div class="meta">还没有日记</div>`;
    return;
  }
  for (const entry of entries.slice(0, limit)) {
    const card = document.createElement("div");
    card.className = "entry";
    card.innerHTML = `<div class="date">${formatDate(entry.createdAt)} · ${escapeHtml(entryAuthor(entry))} ${formatTags(entry.tags)} ${entry.attachments.length ? `${entry.attachments.length} 图` : ""}</div><div class="preview">${escapeHtml(preview(entry.body))}</div>`;
    card.onclick = () => showDetail(entry, currentTab);
    container.appendChild(card);
  }
  if (entries.length > limit) {
    const more = document.createElement("button");
    more.textContent = `加载更多 · 还剩 ${entries.length - limit}`;
    more.onclick = loadMore;
    container.appendChild(more);
  }
}

async function showDetail(entry, from) {
  detailEntry = entry;
  returnTab = from;
  document.querySelectorAll(".page").forEach((page) => page.classList.remove("active"));
  $("detail").classList.add("active");
  document.querySelectorAll(".tab").forEach((button) => button.classList.remove("active"));
  $("detailDate").textContent = formatDate(entry.createdAt);
  $("detailTags").textContent = `${entryAuthor(entry)} ${formatTags(entry.tags)}`.trim();
  await renderBody($("detailBody"), entry.body);
}

async function renderBody(container, body) {
  container.innerHTML = "";
  let cursor = 0;
  attachmentPattern.lastIndex = 0;
  let match;
  while ((match = attachmentPattern.exec(body || ""))) {
    addTextBlock(container, body.slice(cursor, match.index));
    await addImageBlock(container, match[1]);
    cursor = match.index + match[0].length;
  }
  addTextBlock(container, (body || "").slice(cursor));
}

function addTextBlock(container, text) {
  const clean = text.trim();
  if (!clean) return;
  const p = document.createElement("p");
  p.innerHTML = escapeHtml(clean).replace(tagPattern, '<span class="tag">#$1</span>').replace(/\n/g, "<br>");
  container.appendChild(p);
}

async function addImageBlock(container, filename) {
  const img = document.createElement("img");
  const record = await getAttachment(filename);
  img.src = record ? URL.createObjectURL(record.blob) : `/api/attachments/${encodeURIComponent(filename)}`;
  img.onerror = () => {
    const missing = document.createElement("p");
    missing.className = "meta";
    missing.textContent = "图片不存在";
    img.replaceWith(missing);
  };
  img.onclick = () => showLightbox(img.src);
  container.appendChild(img);
}

function showLightbox(src) {
  const box = $("lightbox");
  box.innerHTML = "";
  const img = document.createElement("img");
  img.src = src;
  box.appendChild(img);
  box.hidden = false;
}

async function loadEntry(entry) {
  if (hasUnsavedDraft() && !(await confirmDialog("当前内容还没有保存，放弃这次修改？", "放弃"))) return;
  editingId = entry.id;
  originalBody = entry.body;
  await setEditorValue(entry.body);
  await switchTab("write");
}

async function deleteDetail() {
  if (!detailEntry || !(await confirmDialog("删除这篇日记？", "删除"))) return;
  const existing = await getEntry(detailEntry.id);
  if (existing) {
    const now = Date.now();
    await saveServerEntry({
      ...existing,
      updatedAt: now,
      deletedAt: now,
      version: existing.version + 1,
      deviceId,
      author: entryAuthor(existing)
    });
    serverEntriesCache = null;
  }
  await switchTab(returnTab);
}

async function tagCounts() {
  const counts = {};
  const entries = filterEntriesByAuthor(await allEntries());
  entries.forEach((entry) => entry.tags.forEach((tag) => counts[tag] = (counts[tag] || 0) + 1));
  return counts;
}

async function attachmentReferenced(filename) {
  const entries = await allEntries();
  return entries.some((entry) => normalizeAttachments(entry.attachments).includes(filename));
}

function getAttachment(filename) {
  return getByKey("attachments", filename);
}

async function getEntry(id) {
  const entries = await fetchServerEntries();
  return entries.find((entry) => String(entry.id) === String(id)) || null;
}

function extractTags(body) {
  const tags = new Set();
  for (const match of (body || "").matchAll(tagPattern)) tags.add(match[1].trim());
  return [...tags];
}

function extractAttachments(body) {
  const names = new Set();
  for (const match of (body || "").matchAll(attachmentPattern)) {
    if (safeName(match[1])) names.add(match[1]);
  }
  return [...names];
}

function safeName(name) {
  return name && !name.includes("/") && !name.includes("\\") && !name.includes("..");
}

function preview(body) {
  const text = (body || "").replace(attachmentPattern, "[图片]").replace(/\s+/g, " ").trim();
  return text.length > 82 ? `${text.slice(0, 82)}...` : text;
}

function formatTags(tags) {
  return tags && tags.length ? tags.map((tag) => `#${tag}`).join(" ") : "";
}

function startOfDay(ms) {
  const date = new Date(ms);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

function formatDate(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function formatDay(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`;
}

function formatFileDate(d) {
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}`;
}

function pad(n) {
  return String(n).padStart(2, "0");
}

function escapeHtml(text) {
  return text.replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch]));
}

async function exportZip() {
  const blob = await buildZipBlob();
  downloadBlob(blob, `one-page-diary-backup-${formatFileDate(new Date())}.zip`, "application/zip");
}

async function buildZipBlob() {
  const files = [];
  const storedEntries = await allStoredEntries();
  files.push({
    name: "diary.json",
    data: new TextEncoder().encode(JSON.stringify({
      app: "one-page-diary",
      version: 2,
      deviceId,
      exportedAt: Date.now(),
      entries: storedEntries
    }))
  });
  files.push({ name: "diary.md", data: new TextEncoder().encode(await buildMarkdown()) });
  for (const name of new Set(storedEntries.filter((entry) => !entry.deletedAt).flatMap((entry) => normalizeAttachments(entry.attachments, entry.body)))) {
    const record = await getAttachment(name);
    if (record) {
      files.push({ name: `attachments/${name}`, data: new Uint8Array(await record.blob.arrayBuffer()) });
      continue;
    }
    const response = await fetch(`/api/attachments/${encodeURIComponent(name)}`, { cache: "no-store" });
    if (response.ok) files.push({ name: `attachments/${name}`, data: new Uint8Array(await response.arrayBuffer()) });
  }
  return writeZip(files);
}

async function buildMarkdown() {
  const entries = await allEntries();
  return "# 一页日记\n\n" + entries.map((entry) => `## ${formatDate(entry.createdAt)} · ${entryAuthor(entry)}\n\n${entry.body.replace(attachmentPattern, (_, name) => `![图片](attachments/${name})`).trim()}\n`).join("\n");
}

function downloadBlob(blob, name, type) {
  const url = URL.createObjectURL(new Blob([blob], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

async function importZip() {
  const file = $("zipInput").files[0];
  $("zipInput").value = "";
  if (!file) return;
  try {
    await mergeZipBuffer(await file.arrayBuffer());
    await refreshCurrentPage();
    showToast("导入完成");
  } catch (error) {
    showToast(error.message || "导入失败", "warn");
  }
}

async function mergeZipBuffer(buffer) {
  const entries = await readZip(buffer);
  const diary = entries.get("diary.json");
  if (!diary) throw new Error("ZIP 里没有 diary.json");
  const raw = JSON.parse(new TextDecoder().decode(diary));
  const diaryEntries = Array.isArray(raw) ? raw : raw.entries || [];
  for (const [name, data] of entries) {
    if (name.startsWith("attachments/")) {
      const filename = name.slice("attachments/".length);
      if (safeName(filename)) await put("attachments", { filename, blob: new Blob([data]) });
    }
  }
  const importedEntries = diaryEntries.map(normalizeEntry);
  if (importedEntries.length) await pushLocalEntries(importedEntries);
  serverEntriesCache = null;
}

async function saveServerEntry(entry) {
  await pushLocalEntries([entry]);
  serverEntriesCache = null;
}

async function refreshServerEntries(silent = false) {
  try {
    serverEntriesCache = null;
    const entries = await allEntries();
    if (!silent && currentTab === "write" && !hasUnsavedDraft()) {
      selectedDay = -1;
      calendarLimit = PAGE_SIZE;
      await switchTab("calendar");
    } else {
      await refreshCurrentPage();
    }
    if (!silent) showToast("已刷新");
  } catch (error) {
    if (silent) throw error;
    showToast(error.message || "刷新失败", "warn");
  }
}

function normalizeAttachments(attachments, body = "") {
  const names = new Set();
  const add = (value) => {
    if (typeof value === "string") {
      if (safeName(value)) names.add(value);
      return;
    }
    if (value && typeof value === "object") {
      const name = value.filename || value.name || value.path || "";
      if (safeName(name)) names.add(name);
    }
  };
  if (Array.isArray(attachments)) attachments.forEach(add);
  if (!names.size && body) extractAttachments(body).forEach(add);
  return [...names];
}

async function pushLocalEntries(entries) {
  const response = await fetch("/api/entries", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      entries,
      attachments: await attachmentPayloadForEntries(entries)
    })
  });
  if (!response.ok) throw new Error(`提交失败 ${response.status}`);
  await response.json().catch(() => null);
}

async function attachmentPayloadForEntries(entries) {
  const payload = {};
  const names = new Set(entries.filter((entry) => !entry.deletedAt).flatMap((entry) => normalizeAttachments(entry.attachments, entry.body)));
  for (const name of names) {
    if (!safeName(name)) continue;
    const record = await getAttachment(name);
    if (record) payload[name] = await blobToBase64(record.blob);
  }
  return payload;
}

async function blobToBase64(blob) {
  const buffer = await blob.arrayBuffer();
  let binary = "";
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

async function autoMergeLatest() {
  if (!location.protocol.startsWith("http")) return;
  try {
    await autoRefreshFromService(true);
  } catch (error) {
  }
}

async function autoRefreshFromService(force = false) {
  const response = await fetch("/api/status", { cache: "no-store" });
  if (!response.ok) return;
  const status = await response.json();
  const diary = diaryServiceStatus(status);
  if (!diary || !diary.hasLatest || !diary.updatedAt) return;
  if (!force && diary.updatedAt === lastServiceUpdatedAt) return;
  lastServiceUpdatedAt = diary.updatedAt;
  await refreshServerEntries(true);
  await refreshCurrentPage();
}

function diaryServiceStatus(status) {
  if (!status || typeof status !== "object") return null;
  if (status.app === "one-page-diary") {
    return {
      hasLatest: Boolean(status.hasLatest),
      updatedAt: status.updatedAt || null
    };
  }
  if (status.app === "aitools" && Array.isArray(status.tools)) {
    const tool = status.tools.find((item) => item.id === "diary" || item.app === "one-page-diary");
    if (!tool || !tool.sync || !tool.sync.enabled) return null;
    return {
      hasLatest: Boolean(tool.sync.hasLatest),
      updatedAt: tool.sync.updatedAt || null
    };
  }
  return null;
}

async function refreshCurrentPage() {
  if (currentTab === "write") await renderWrite();
  if (currentTab === "calendar") await renderCalendar();
  if (currentTab === "tags") await renderTags();
}

function writeZip(files) {
  const local = [];
  const central = [];
  let offset = 0;
  files.forEach((file) => {
    const name = new TextEncoder().encode(file.name);
    const data = file.data;
    const crc = crc32(data);
    const localHeader = makeLocalHeader(name, data, crc);
    local.push(localHeader, name, data);
    const centralHeader = makeCentralHeader(name, data, crc, offset);
    central.push(centralHeader, name);
    offset += localHeader.length + name.length + data.length;
  });
  const centralSize = central.reduce((sum, part) => sum + part.length, 0);
  const end = makeEndHeader(files.length, centralSize, offset);
  return new Blob([...local, ...central, end]);
}

function makeLocalHeader(name, data, crc) {
  const out = new Uint8Array(30);
  const view = new DataView(out.buffer);
  view.setUint32(0, 0x04034b50, true);
  view.setUint16(4, 20, true);
  view.setUint16(6, 0, true);
  view.setUint16(8, 0, true);
  view.setUint16(10, 0, true);
  view.setUint16(12, 0, true);
  view.setUint32(14, crc, true);
  view.setUint32(18, data.length, true);
  view.setUint32(22, data.length, true);
  view.setUint16(26, name.length, true);
  view.setUint16(28, 0, true);
  return out;
}

function makeCentralHeader(name, data, crc, offset) {
  const out = new Uint8Array(46);
  const view = new DataView(out.buffer);
  view.setUint32(0, 0x02014b50, true);
  view.setUint16(4, 20, true);
  view.setUint16(6, 20, true);
  view.setUint16(8, 0, true);
  view.setUint16(10, 0, true);
  view.setUint16(12, 0, true);
  view.setUint16(14, 0, true);
  view.setUint32(16, crc, true);
  view.setUint32(20, data.length, true);
  view.setUint32(24, data.length, true);
  view.setUint16(28, name.length, true);
  view.setUint16(30, 0, true);
  view.setUint16(32, 0, true);
  view.setUint16(34, 0, true);
  view.setUint16(36, 0, true);
  view.setUint32(38, 0, true);
  view.setUint32(42, offset, true);
  return out;
}

function makeEndHeader(count, centralSize, offset) {
  const out = new Uint8Array(22);
  const view = new DataView(out.buffer);
  view.setUint32(0, 0x06054b50, true);
  view.setUint16(4, 0, true);
  view.setUint16(6, 0, true);
  view.setUint16(8, count, true);
  view.setUint16(10, count, true);
  view.setUint32(12, centralSize, true);
  view.setUint32(16, offset, true);
  view.setUint16(20, 0, true);
  return out;
}

async function readZip(buffer) {
  const bytes = new Uint8Array(buffer);
  const view = new DataView(buffer);
  let eocd = -1;
  for (let i = bytes.length - 22; i >= 0; i--) {
    if (view.getUint32(i, true) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new Error("bad zip");
  const count = view.getUint16(eocd + 10, true);
  let pos = view.getUint32(eocd + 16, true);
  const result = new Map();
  for (let i = 0; i < count; i++) {
    const method = view.getUint16(pos + 10, true);
    const compressedSize = view.getUint32(pos + 20, true);
    const fileNameLength = view.getUint16(pos + 28, true);
    const extraLength = view.getUint16(pos + 30, true);
    const commentLength = view.getUint16(pos + 32, true);
    const localOffset = view.getUint32(pos + 42, true);
    const name = new TextDecoder().decode(bytes.slice(pos + 46, pos + 46 + fileNameLength));
    const localNameLength = view.getUint16(localOffset + 26, true);
    const localExtraLength = view.getUint16(localOffset + 28, true);
    const dataStart = localOffset + 30 + localNameLength + localExtraLength;
    const data = bytes.slice(dataStart, dataStart + compressedSize);
    if (!name.endsWith("/")) {
      result.set(name, method === 0 ? data : await inflateRaw(data));
    }
    pos += 46 + fileNameLength + extraLength + commentLength;
  }
  return result;
}

async function inflateRaw(data) {
  if (!("DecompressionStream" in window)) throw new Error("browser cannot decompress zip");
  const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

function crc32(data) {
  let crc = -1;
  for (let i = 0; i < data.length; i++) {
    crc ^= data[i];
    for (let j = 0; j < 8; j++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return (crc ^ -1) >>> 0;
}
