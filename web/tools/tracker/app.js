const API = "/api/tools/tracker";
const DEVICE_ID_KEY = "life-tracker-device-id";
const PROJECT_SCHEMA = "life-tracker.project.v1";
const RECORD_SCHEMA = "life-tracker.record.v2";
const LEGACY_RECORD_SCHEMA = "life-tracker.record.v1";
const KIND_LABELS = {
  number: "数值",
  check: "打卡",
  text: "打卡",
};

let entries = [];
let projects = [];
let records = [];
let currentTab = "record";
let returnTab = "record";
let currentProjectId = "";
let detailProjectId = "";
let detailSelectedDate = "";
let detailTrendDate = "";
let annualYear = new Date().getFullYear();
let recordVisibleMonth = new Date();
let detailVisibleMonth = new Date();
let editingRecordId = 0;
let inlineProjectId = "";
let editingProjectId = 0;
let projectFormOpen = false;
let toastTimer = 0;

const $ = (id) => document.getElementById(id);

init();

async function init() {
  $("dateInput").value = todayString();
  bindEvents();
  await refreshData();
  await switchTab("record");
}

function bindEvents() {
  document.querySelectorAll(".tab").forEach((button) => {
    button.addEventListener("click", () => switchTab(button.dataset.tab));
  });
  $("dateInput").addEventListener("change", () => {
    editingRecordId = 0;
    inlineProjectId = "";
    syncRecordMonth();
    renderRecordPage();
  });
  $("recordPrevMonth").addEventListener("click", () => {
    recordVisibleMonth.setDate(1);
    recordVisibleMonth.setMonth(recordVisibleMonth.getMonth() - 1);
    renderRecordPage();
  });
  $("recordNextMonth").addEventListener("click", () => {
    recordVisibleMonth.setDate(1);
    recordVisibleMonth.setMonth(recordVisibleMonth.getMonth() + 1);
    renderRecordPage();
  });
  $("recordToday").addEventListener("click", () => {
    setRecordDate(todayString());
    editingRecordId = 0;
    inlineProjectId = "";
    renderRecordPage();
  });
  $("projectForm").addEventListener("submit", saveProject);
  $("addProject").addEventListener("click", () => openProjectForm());
  $("cancelProject").addEventListener("click", closeProjectForm);
  $("projectKindInput").addEventListener("change", updateProjectFields);
  document.querySelectorAll("[data-project-kind-choice] [data-kind]").forEach((button) => {
    button.addEventListener("click", () => setProjectKind(button.dataset.kind));
  });
  $("backToProjects").addEventListener("click", () => switchTab("projects"));
  $("openAnnualStats").addEventListener("click", () => {
    annualYear = new Date().getFullYear();
    switchTab("annualStats");
  });
  $("backToDetail").addEventListener("click", () => switchTab("itemDetail"));
  $("annualPrevYear").addEventListener("click", () => {
    annualYear -= 1;
    renderAnnualStats();
  });
  $("annualNextYear").addEventListener("click", () => {
    annualYear += 1;
    renderAnnualStats();
  });
  document.querySelectorAll("[data-project-stat-choice] [data-stat]").forEach((button) => {
    button.addEventListener("click", () => setProjectStatMode(button.dataset.stat));
  });
  $("openSettings").addEventListener("click", async () => {
    returnTab = currentTab;
    await switchTab("settings");
  });
  $("backSettings").addEventListener("click", () => switchTab(returnTab));
  $("refreshData").addEventListener("click", refreshData);
  $("importZip").addEventListener("click", () => $("zipInput").click());
  $("zipInput").addEventListener("change", importZip);
}

async function switchTab(tab) {
  currentTab = tab;
  document.querySelector(".app").classList.toggle("settings-mode", tab === "settings");
  document.querySelector(".app").classList.toggle("byday-mode", tab === "byday");
  $("tabs").hidden = tab === "settings";
  document.querySelectorAll(".tab").forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === (["itemDetail", "annualStats"].includes(tab) ? "projects" : tab));
  });
  document.querySelectorAll(".page").forEach((page) => page.classList.remove("active"));
  const pageId = tab === "byday" ? "record" : tab;
  $(pageId).classList.add("active");
  if (tab === "record") setRecordDate(todayString());
  if (tab === "record" || tab === "byday") renderRecordPage();
  if (tab === "projects") renderProjects();
  if (tab === "itemDetail") renderProjectDetail();
  if (tab === "annualStats") renderAnnualStats();
}

function updateProjectFields() {
  const isCheck = $("projectKindInput").value === "check";
  $("projectUnitField").hidden = isCheck;
  $("projectStatField").hidden = isCheck;
  if (isCheck) $("projectUnitInput").value = "";
  document.querySelectorAll("[data-project-kind-choice] [data-kind]").forEach((button) => {
    button.classList.toggle("active", button.dataset.kind === $("projectKindInput").value);
  });
  document.querySelectorAll("[data-project-stat-choice] [data-stat]").forEach((button) => {
    button.classList.toggle("active", button.dataset.stat === $("projectStatInput").value);
  });
}

function setProjectKind(kind) {
  $("projectKindInput").value = kind;
  updateProjectFields();
}

function setProjectStatMode(mode) {
  $("projectStatInput").value = mode === "avg" ? "avg" : "sum";
  updateProjectFields();
}

async function refreshData() {
  try {
    const response = await fetch(`${API}/entries`, { cache: "no-store" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const body = await response.json();
    entries = (body.entries || []).map(normalizeEntry);
    projects = entries
      .filter((entry) => !entry.deletedAt)
      .map(entryToProject)
      .filter(Boolean)
      .sort((a, b) => a.name.localeCompare(b.name, "zh-CN"));
    records = entries
      .filter((entry) => !entry.deletedAt)
      .map(entryToRecord)
      .filter(Boolean)
      .sort((a, b) => b.sortTime - a.sortTime || b.id - a.id);
    ensureProjectSelection();
    $("syncDebug").textContent = `已读取 ${projects.length} 个事项、${records.length} 条记录`;
    renderRecordPage();
    if (currentTab === "projects") renderProjects();
    if (currentTab === "itemDetail") renderProjectDetail();
  } catch (error) {
    $("syncDebug").textContent = "无法连接电脑服务";
    showToast("无法读取日常格数据", "warn");
  }
}

function ensureProjectSelection() {
  if (!projects.length) {
    currentProjectId = "";
    return;
  }
  if (!projects.some((project) => String(project.id) === String(currentProjectId))) {
    currentProjectId = String(projects[0].id);
  }
}

function recordForDate(projectId, date) {
  return records.find((record) => String(record.projectId) === String(projectId) && record.date === date) || null;
}

function setRecordDate(date) {
  $("dateInput").value = date;
  syncRecordMonth();
}

function syncRecordMonth() {
  const selected = new Date(`${$("dateInput").value || todayString()}T12:00:00`);
  recordVisibleMonth = Number.isFinite(selected.getTime())
    ? new Date(selected.getFullYear(), selected.getMonth(), 1)
    : new Date();
}

function renderRecordPage() {
  if (currentTab === "record") setRecordDate(todayString());
  if (currentTab === "byday") renderRecordCalendar();
  const recordDate = $("dateInput").value || todayString();
  $("selectedDateLabel").textContent = currentTab === "record" ? `今天 · ${recordDate}` : formatHistoryDate(recordDate);
  $("recordEditor").innerHTML = "";
  const host = $("dailyList");
  host.innerHTML = "";
  if (!projects.length) {
    host.innerHTML = `
      <article class="empty-state">
        <h3>还没有事项</h3>
        <button class="primary" type="button" data-action="create">新增事项</button>
      </article>
    `;
    host.querySelector("[data-action='create']").onclick = async () => {
      await switchTab("projects");
      openProjectForm();
    };
    return;
  }
  projects.forEach((project) => {
    const record = recordForDate(project.id, recordDate);
    host.appendChild(dailyItem(project, record));
  });
}

function renderRecordCalendar() {
  const selectedDate = $("dateInput").value || todayString();
  const year = recordVisibleMonth.getFullYear();
  const month = recordVisibleMonth.getMonth();
  $("recordMonthLabel").textContent = `${year} 年 ${month + 1} 月`;
  const counts = new Map();
  records.forEach((record) => counts.set(record.date, (counts.get(record.date) || 0) + 1));
  const first = new Date(year, month, 1);
  const cursor = new Date(first);
  cursor.setDate(cursor.getDate() - cursor.getDay());
  const cells = [];
  ["日", "一", "二", "三", "四", "五", "六"].forEach((day) => {
    cells.push(`<div class="weekday">${day}</div>`);
  });
  for (let i = 0; i < 42; i++) {
    const date = formatDate(cursor);
    const count = counts.get(date) || 0;
    const classes = [
      "day",
      cursor.getMonth() !== month ? "dim" : "",
      count ? "has" : "",
      date === selectedDate ? "selected" : "",
      date === todayString() ? "today" : "",
      date > todayString() ? "future" : "",
    ].filter(Boolean).join(" ");
    cells.push(`
      <button type="button" class="${classes}" data-date="${date}" ${date > todayString() ? "disabled" : ""}>
        ${cursor.getDate()}<br>${count ? count : "&nbsp;"}
      </button>
    `);
    cursor.setDate(cursor.getDate() + 1);
  }
  $("recordCalendarGrid").innerHTML = cells.join("");
  $("recordCalendarGrid").querySelectorAll("[data-date]").forEach((button) => {
    button.onclick = () => {
      setRecordDate(button.dataset.date);
      editingRecordId = 0;
      inlineProjectId = "";
      renderRecordPage();
    };
  });
}

function dailyItem(project, record) {
  const item = document.createElement("article");
  const isOpen = String(inlineProjectId) === String(project.id);
  item.className = `daily-item${record ? " recorded" : ""}${isOpen ? " selected" : ""}`;
  item.innerHTML = `
    <div class="daily-row">
      <span class="daily-state" aria-hidden="true">${record ? "✓" : ""}</span>
      <button type="button" class="daily-main" data-action="open">
        <span class="daily-titleline">
          <strong>${escapeHtml(project.name)}</strong>
          <em>${record ? "已记录" : "未记录"}</em>
        </span>
        <span class="daily-value">${escapeHtml(dailySummary(project, record))}</span>
      </button>
      ${dailyAction(project, record)}
    </div>
  `;
  if (project.kind === "check") {
    item.querySelector("[data-action='open']").onclick = () => openInlineEditor(project, record);
    item.querySelector("[data-action='edit']").onclick = () => {
      if (isOpen) {
        closeRecordEditor();
        return;
      }
      openInlineEditor(project, record);
    };
  } else {
    item.querySelector("[data-action='open']").onclick = () => openInlineEditor(project, record);
    item.querySelector("[data-action='edit']").onclick = () => {
      if (isOpen) {
        closeRecordEditor();
        return;
      }
      openInlineEditor(project, record);
    };
  }
  if (isOpen) item.appendChild(recordEditor(project, record));
  return item;
}

function dailySummary(project, record) {
  if (!record) return "这天还没有这项记录";
  if (project.kind === "check") return record.content ? `已打卡 · ${record.content}` : "已打卡";
  if (project.kind === "number") {
    const value = record.amount === null ? "" : `${formatNumber(record.amount)}${record.unit || project.unit || ""}`;
    return record.content ? `${value} · ${record.content}` : value;
  }
  return record.content || "已记录";
}

function dailyAction(project, record) {
  if (project.kind === "check") {
    const selected = String(inlineProjectId) === String(project.id);
    if (selected) return `<button type="button" class="daily-action active" data-action="edit">收起</button>`;
    return `<button type="button" class="daily-action${record ? "" : " primary"}" data-action="edit">${record ? "修改" : "打卡"}</button>`;
  }
  const selected = String(inlineProjectId) === String(project.id);
  if (selected) return `<button type="button" class="daily-action active" data-action="edit">收起</button>`;
  return `<button type="button" class="daily-action${record ? "" : " primary"}" data-action="edit">${record ? "修改" : "打卡"}</button>`;
}

function recordEditor(project, record) {
  const form = document.createElement("form");
  form.className = "record-editor";
  const isNumber = project.kind === "number";
  form.innerHTML = `
    ${isNumber ? `
      <label>
        <span>${project.unit ? `数值（${escapeHtml(project.unit)}）` : "数值"}</span>
        <input name="amount" type="number" step="0.01" inputmode="decimal" value="${record && record.amount !== null ? escapeHtml(record.amount) : ""}" autofocus>
      </label>
    ` : ""}
    <label class="${isNumber ? "" : "wide"}">
      <span>说明（可选）</span>
      <textarea name="content" rows="3" placeholder="可选：补充这次记录的情况">${escapeHtml(record ? record.content || "" : "")}</textarea>
    </label>
    <div class="record-editor-actions">
      <button class="primary" type="submit">${record ? "保存修改" : (isNumber ? "保存记录" : "保存打卡")}</button>
      <button type="button" data-action="cancel">取消</button>
      ${record ? `<button type="button" class="danger ghost" data-action="delete">删除记录</button>` : ""}
    </div>
  `;
  form.onsubmit = (event) => saveInlineRecord(event, project, record);
  form.querySelector("[data-action='cancel']").onclick = closeRecordEditor;
  if (record) {
    form.querySelector("[data-action='delete']").onclick = () => deleteRecord(record.id);
  }
  window.setTimeout(() => {
    const firstInput = form.querySelector("input, textarea");
    if (firstInput) firstInput.focus();
  }, 0);
  return form;
}

function openInlineEditor(project, record) {
  currentProjectId = String(project.id);
  inlineProjectId = String(project.id);
  editingRecordId = record ? Number(record.id) : 0;
  renderRecordPage();
}

function closeRecordEditor() {
  editingRecordId = 0;
  inlineProjectId = "";
  renderRecordPage();
}

async function saveProject(event) {
  event.preventDefault();
  await saveProjectValues({
    id: 0,
    name: $("projectNameInput").value.trim(),
    kind: $("projectKindInput").value,
    unit: $("projectKindInput").value === "number" ? $("projectUnitInput").value.trim() : "",
    statMode: $("projectKindInput").value === "number" ? $("projectStatInput").value : "sum",
  });
}

async function saveProjectValues({ id, name, kind, unit, statMode = "sum" }) {
  if (!name) {
    showToast("请填写事项名称", "warn");
    return;
  }
  const now = Date.now();
  const projectId = Number(id || 0);
  const existing = projectId ? entries.find((entry) => Number(entry.id) === Number(projectId)) : null;
  const project = {
    schema: PROJECT_SCHEMA,
    name,
    kind,
    unit: kind === "number" ? unit : "",
    statMode: kind === "number" && statMode === "avg" ? "avg" : "sum",
    note: "",
  };
  const entry = {
    id: projectId || now,
    createdAt: existing ? existing.createdAt : now,
    updatedAt: now,
    deletedAt: 0,
    version: (existing ? Number(existing.version || 1) : 0) + 1,
    deviceId: ensureDeviceId(),
    tags: ["project", project.kind],
    attachments: [],
    body: JSON.stringify(project),
  };
  await postEntries([entry]);
  const wasEditing = Boolean(projectId);
  showToast(wasEditing ? "已更新事项" : "已添加事项");
  projectFormOpen = false;
  editingProjectId = 0;
  $("projectForm").reset();
  $("projectKindInput").value = "number";
  $("projectStatInput").value = "sum";
  updateProjectFields();
  await refreshData();
  currentProjectId = String(entry.id);
  if (wasEditing) {
    if (currentTab === "projects") renderProjects();
    return;
  }
  inlineProjectId = project.kind === "check" ? "" : String(entry.id);
  await switchTab("record");
}

async function saveInlineRecord(event, project, existingRecord) {
  event.preventDefault();
  const now = Date.now();
  const form = event.currentTarget;
  const amountInput = form.elements.amount;
  const contentInput = form.elements.content;
  const recordDate = $("dateInput").value || todayString();
  if (recordDate > todayString()) {
    showToast("不能记录未来日期", "warn");
    return;
  }
  const amount = project.kind === "number" ? parseNumber(amountInput.value) : null;
  const content = contentInput ? contentInput.value.trim() : "";
  if (project.kind === "number" && amount === null) {
    showToast("请填写数值", "warn");
    return;
  }
  const recordId = existingRecord ? Number(existingRecord.id) : 0;
  const existing = recordId ? entries.find((entry) => Number(entry.id) === recordId) : null;
  const record = {
    schema: RECORD_SCHEMA,
    projectId: String(project.id),
    projectName: project.name,
    kind: project.kind,
    date: recordDate,
    amount,
    unit: project.unit || "",
    content,
    note: "",
  };
  const entry = {
    id: recordId || now,
    createdAt: existing ? existing.createdAt : dateToTime(record.date, now),
    updatedAt: now,
    deletedAt: 0,
    version: (existing ? Number(existing.version || 1) : 0) + 1,
    deviceId: ensureDeviceId(),
    tags: ["record", String(project.id), record.date],
    attachments: [],
    body: JSON.stringify(record),
  };
  await postEntries([entry]);
  showToast(recordId ? "已更新记录" : "已保存记录");
  editingRecordId = 0;
  inlineProjectId = "";
  await refreshData();
}

async function toggleCheck(project, record) {
  if (record) {
    await removeRecord(record.id, false, "");
    showToast("已取消");
    return;
  }
  const now = Date.now();
  const recordDate = $("dateInput").value || todayString();
  const nextRecord = {
    schema: RECORD_SCHEMA,
    projectId: String(project.id),
    projectName: project.name,
    kind: project.kind,
    date: recordDate,
    amount: null,
    unit: "",
    content: "",
    note: "",
  };
  const entry = {
    id: now,
    createdAt: dateToTime(recordDate, now),
    updatedAt: now,
    deletedAt: 0,
    version: 1,
    deviceId: ensureDeviceId(),
    tags: ["record", String(project.id), recordDate],
    attachments: [],
    body: JSON.stringify(nextRecord),
  };
  await postEntries([entry]);
  showToast("已完成");
  await refreshData();
}

function renderProjects() {
  $("projectFormPanel").hidden = !projectFormOpen;
  $("addProject").hidden = projectFormOpen || Boolean(editingProjectId);
  updateProjectFields();
  const host = $("projectList");
  host.innerHTML = "";
  if (!projects.length) {
    host.innerHTML = `<article class="record-card empty-card"><h3>还没有事项</h3></article>`;
    return;
  }
  projects.forEach((project) => host.appendChild(projectCard(project)));
}

function openProjectForm() {
  projectFormOpen = true;
  editingProjectId = 0;
  $("projectFormTitle").textContent = "新增事项";
  $("saveProject").textContent = "保存事项";
  $("projectNameInput").value = "";
  $("projectKindInput").value = "number";
  $("projectStatInput").value = "sum";
  $("projectUnitInput").value = "";
  renderProjects();
}

function closeProjectForm() {
  projectFormOpen = false;
  editingProjectId = 0;
  $("projectForm").reset();
  $("projectFormTitle").textContent = "新增事项";
  $("saveProject").textContent = "保存事项";
  $("projectKindInput").value = "number";
  $("projectStatInput").value = "sum";
  updateProjectFields();
  if (currentTab === "projects") renderProjects();
}

function projectCard(project) {
  const card = document.createElement("article");
  const isEditing = Number(editingProjectId) === Number(project.id);
  card.className = `record-card item-card${isEditing ? " editing" : ""}`;
  const count = records.filter((record) => String(record.projectId) === String(project.id)).length;
  card.innerHTML = `
    <div class="record-card-head">
      <div>
        <h3>${escapeHtml(project.name)}</h3>
        <div class="record-meta">${escapeHtml(KIND_LABELS[project.kind] || project.kind)}${project.unit ? ` · ${escapeHtml(project.unit)}` : ""} · ${count} 条记录</div>
      </div>
    </div>
    <div class="record-actions">
      <button type="button" data-action="edit">${isEditing ? "收起" : "编辑"}</button>
    </div>
  `;
  card.querySelector(".record-card-head").onclick = () => showProjectDetail(project.id);
  card.querySelector('[data-action="edit"]').onclick = () => editProject(project.id);
  if (isEditing) card.appendChild(projectInlineEditor(project));
  return card;
}

function projectInlineEditor(project) {
  const form = document.createElement("form");
  form.className = "record-form project-inline-form";
  form.innerHTML = `
    <label>
      <span>事项名称</span>
      <input name="name" type="text" value="${escapeHtml(project.name)}" placeholder="事项名称">
    </label>
    <label>
      <span>记录类型</span>
      <input name="kind" type="hidden" value="${escapeHtml(project.kind)}">
      <div class="segmented-control project-kind-choice">
        <button type="button" class="${project.kind === "number" ? "active" : ""}" data-kind="number">数值</button>
        <button type="button" class="${project.kind === "check" ? "active" : ""}" data-kind="check">打卡</button>
      </div>
    </label>
    <label data-unit-field>
      <span>默认单位</span>
      <input name="unit" type="text" value="${escapeHtml(project.unit || "")}" placeholder="kg、页">
    </label>
    <label data-stat-field>
      <span>统计方式</span>
      <input name="statMode" type="hidden" value="${escapeHtml(project.statMode || "sum")}">
      <div class="segmented-control project-stat-choice">
        <button type="button" class="${project.statMode !== "avg" ? "active" : ""}" data-stat="sum">累计</button>
        <button type="button" class="${project.statMode === "avg" ? "active" : ""}" data-stat="avg">平均</button>
      </div>
    </label>
    <div class="form-actions wide">
      <button class="primary" type="submit">保存修改</button>
      <button type="button" data-action="cancel">取消</button>
      <button type="button" class="danger" data-action="delete">删除事项</button>
    </div>
  `;
  const kindInput = form.elements.kind;
  const statInput = form.elements.statMode;
  const unitField = form.querySelector("[data-unit-field]");
  const statField = form.querySelector("[data-stat-field]");
  const syncUnit = () => {
    unitField.hidden = kindInput.value !== "number";
    statField.hidden = kindInput.value !== "number";
    if (kindInput.value !== "number") form.elements.unit.value = "";
    form.querySelectorAll("[data-kind]").forEach((button) => {
      button.classList.toggle("active", button.dataset.kind === kindInput.value);
    });
    form.querySelectorAll("[data-stat]").forEach((button) => {
      button.classList.toggle("active", button.dataset.stat === statInput.value);
    });
  };
  form.querySelectorAll("[data-kind]").forEach((button) => {
    button.onclick = () => {
      kindInput.value = button.dataset.kind;
      syncUnit();
    };
  });
  form.querySelectorAll("[data-stat]").forEach((button) => {
    button.onclick = () => {
      statInput.value = button.dataset.stat === "avg" ? "avg" : "sum";
      syncUnit();
    };
  });
  syncUnit();
  form.onsubmit = async (event) => {
    event.preventDefault();
    await saveProjectValues({
      id: project.id,
      name: form.elements.name.value.trim(),
      kind: kindInput.value,
      unit: form.elements.unit.value.trim(),
      statMode: statInput.value,
    });
  };
  form.querySelector("[data-action='cancel']").onclick = closeProjectForm;
  form.querySelector("[data-action='delete']").onclick = () => deleteProject(project.id);
  return form;
}

function showProjectDetail(id) {
  detailProjectId = String(id);
  detailSelectedDate = todayString();
  detailTrendDate = todayString();
  detailVisibleMonth = new Date(`${detailSelectedDate}T12:00:00`);
  switchTab("itemDetail");
}

function editProject(id) {
  const project = projects.find((item) => String(item.id) === String(id));
  if (!project) return;
  if (Number(editingProjectId) === Number(id)) {
    closeProjectForm();
    return;
  }
  projectFormOpen = false;
  editingProjectId = Number(project.id);
  renderProjects();
}

async function deleteProject(id) {
  const hasRecords = records.some((record) => String(record.projectId) === String(id));
  const message = hasRecords ? "这个事项已有记录。删除事项后历史记录仍保留，但事项不会再出现在打卡入口。确定删除？" : "删除这个事项？";
  if (!window.confirm(message)) return;
  const entry = entries.find((item) => Number(item.id) === Number(id));
  if (!entry) return;
  await postEntries([{ ...entry, deletedAt: Date.now(), updatedAt: Date.now(), version: Number(entry.version || 1) + 1 }]);
  showToast("已删除事项");
  editingProjectId = 0;
  projectFormOpen = false;
  await refreshData();
}

function renderProjectDetail() {
  const project = projects.find((item) => String(item.id) === String(detailProjectId));
  if (!project) {
    switchTab("projects");
    return;
  }
  const list = records.filter((record) => String(record.projectId) === String(project.id));
  $("detailTitle").textContent = project.name;
  $("detailMeta").textContent = `${KIND_LABELS[project.kind] || "事项"}${project.unit ? ` · ${project.unit}` : ""}`;
  renderDetailStats(project, list);
  renderDetailVisual(project, list);
  renderDetailHistory(project, list);
}

function renderDetailStats(project, list) {
  if (project.kind === "number") {
    const values = list.map((record) => Number(record.amount)).filter((value) => Number.isFinite(value));
    const latest = values.length ? `${formatNumber(values[0])}${project.unit || ""}` : "暂无";
    const avg = values.length ? `${formatNumber(values.reduce((sum, value) => sum + value, 0) / values.length)}${project.unit || ""}` : "暂无";
    const range = values.length ? `${formatNumber(Math.min(...values))}-${formatNumber(Math.max(...values))}${project.unit || ""}` : "暂无";
    $("detailStats").innerHTML = statCards([
      ["最近", latest],
      ["记录数", `${list.length} 条`],
      ["平均", avg],
      ["范围", range],
    ]);
    return;
  }
  if (project.kind === "check") {
    const days = new Set(list.map((record) => record.date));
    $("detailStats").innerHTML = statCards([
      ["完成次数", `${list.length} 次`],
      ["完成天数", `${days.size} 天`],
      ["最近完成", list[0] ? list[0].date : "暂无"],
    ]);
    return;
  }
  const days = new Set(list.map((record) => record.date));
  $("detailStats").innerHTML = statCards([
    ["记录数", `${list.length} 条`],
    ["记录天数", `${days.size} 天`],
    ["最近日期", list[0] ? list[0].date : "暂无"],
  ]);
}

function renderDetailVisual(project, list) {
  if (project.kind === "number") {
    $("detailVisual").innerHTML = renderLineChart(list, project.unit);
    $("detailVisual").querySelectorAll("[data-trend-shift]").forEach((button) => {
      button.onclick = () => {
        const next = shiftTrendDate(detailTrendDate || todayString(), Number(button.dataset.trendShift));
        if (trendWindow(next).start > todayString()) return;
        detailTrendDate = clampTrendDate(next);
        renderProjectDetail();
      };
    });
    $("detailVisual").querySelectorAll("[data-bar-value]").forEach((bar) => {
      bar.onclick = () => {
        const caption = $("detailVisual").querySelector(".chart-caption");
        if (!caption) return;
        const selected = bar.classList.contains("selected");
        $("detailVisual").querySelectorAll("[data-bar-value]").forEach((item) => item.classList.remove("selected"));
        if (selected) {
          caption.querySelector("strong").textContent = caption.dataset.defaultStrong || "";
          caption.querySelector("span").textContent = caption.dataset.defaultSpan || "";
          return;
        }
        bar.classList.add("selected");
        caption.querySelector("strong").textContent = bar.dataset.barAmount || "";
        caption.querySelector("span").textContent = bar.dataset.barDate || "";
      };
    });
    return;
  }
  renderCalendar(project, list);
}

function renderLineChart(list, unit) {
  const allPoints = [...list]
    .filter((record) => Number.isFinite(Number(record.amount)))
    .sort((a, b) => a.sortTime - b.sortTime);
  const trend = trendPoints(allPoints, detailTrendDate || todayString());
  const points = trend.points;
  if (!points.length) {
    return `
      <div class="trend-head">
        ${trendWindowNav(trend)}
      </div>
      <div class="line-chart empty-chart">
        <strong>${escapeHtml(trend.title)}</strong>
        <span>这个时间段还没有数值</span>
      </div>
    `;
  }
  const values = points.map((record) => Number(record.amount));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const spread = max - min || 1;
  const baseMin = min >= 0 ? 0 : -niceAxisMax(Math.abs(min));
  const baseMax = max <= 0 ? 0 : niceAxisMax(max);
  const baseSpread = baseMax - baseMin || 1;
  const width = 640;
  const height = 220;
  const padX = 60;
  const padY = 40;
  const slot = (width - padX * 2) / Math.max(1, trend.days);
  const barWidth = Math.max(6, Math.min(24, slot * 0.55));
  const bars = points.map((record) => {
    const index = trend.days <= 1 ? 0.5 : record.dayIndex / Math.max(1, trend.days - 1);
    const x = padX + index * (width - padX * 2);
    const y = height - padY - ((Number(record.amount) - baseMin) / baseSpread) * (height - padY * 2);
    const barHeight = Math.max(3, height - padY - y);
    return { x, y, barHeight, record };
  });
  const rects = bars.map((bar) => `
    <rect x="${(bar.x - barWidth / 2).toFixed(1)}" y="${bar.y.toFixed(1)}" width="${barWidth.toFixed(1)}" height="${bar.barHeight.toFixed(1)}" rx="5"
      data-bar-value="1"
      data-bar-date="${escapeHtml(bar.record.date)}"
      data-bar-amount="${escapeHtml(`${formatNumber(bar.record.amount)}${unit || ""}`)}">
      <title>${escapeHtml(bar.record.date)} ${escapeHtml(formatNumber(bar.record.amount))}${escapeHtml(unit || "")}</title>
    </rect>
  `).join("");
  const latest = bars[bars.length - 1];
  const latestLabel = `${formatNumber(latest.record.amount)}${unit || ""}`;
  const axisLabels = [
    [padY, baseMax],
    [height / 2, (baseMax + baseMin) / 2],
    [height - padY, baseMin],
  ].map(([y, value]) => `<text x="${width - 14}" y="${(y + 4).toFixed(1)}" text-anchor="end">${escapeHtml(formatNumber(value))}${escapeHtml(unit || "")}</text>`).join("");
  const firstDate = shortDate(trend.start);
  const lastDate = shortDate(trend.end);
  return `
    <div class="trend-head">
      ${trendWindowNav(trend)}
    </div>
    <div class="line-chart">
      <div class="chart-caption" data-default-strong="${escapeHtml(latestLabel)}" data-default-span="${escapeHtml(trend.caption)}">
        <strong>${escapeHtml(latestLabel)}</strong>
        <span>${escapeHtml(trend.caption)}</span>
      </div>
      <svg viewBox="0 0 ${width} ${height}" role="img" aria-label="数值变化">
        <line x1="${padX}" y1="${padY}" x2="${width - padX}" y2="${padY}" stroke="#ead5dd" />
        <line x1="${padX}" y1="${height / 2}" x2="${width - padX}" y2="${height / 2}" stroke="#ead5dd" />
        <line x1="${padX}" y1="${height - padY}" x2="${width - padX}" y2="${height - padY}" stroke="#dad0c2" />
        <g fill="#c8a15a">${rects}</g>
        <g class="chart-labels">
          ${axisLabels}
          <text x="${padX}" y="${height - 10}" text-anchor="start">${escapeHtml(firstDate)}</text>
          <text x="${width - padX}" y="${height - 10}" text-anchor="end">${escapeHtml(lastDate)}</text>
        </g>
      </svg>
    </div>
  `;
}

function trendWindowNav(trend) {
  const disableNext = trendWindow(shiftTrendDate(detailTrendDate || todayString(), 1)).start > todayString();
  return `
    <div class="trend-window">
      <button type="button" data-trend-shift="-1" aria-label="上一个时间段">‹</button>
      <strong>${escapeHtml(trend.title)}</strong>
      <button type="button" data-trend-shift="1" aria-label="下一个时间段" ${disableNext ? "disabled" : ""}>›</button>
    </div>
  `;
}

function trendPoints(allPoints, anchorDate) {
  const window = trendWindow(anchorDate);
  const byDate = new Map();
  allPoints.forEach((record) => {
    if (record.date < window.start || record.date > window.end) return;
    byDate.set(record.date, record);
  });
  const points = [];
  for (let index = 0; index < window.days; index++) {
    const date = formatDate(addDays(window.startDate, index));
    const record = byDate.get(date);
    if (record) points.push({ ...record, dayIndex: index });
  }
  return {
    points,
    start: window.start,
    end: window.end,
    days: window.days,
    title: window.title,
    caption: points.length ? `${points.length} 天有记录` : "没有记录",
  };
}

function trendWindow(anchorDate) {
  const anchor = parseDate(anchorDate || todayString()) || parseDate(todayString());
  const startDate = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const endDate = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0);
  const start = formatDate(startDate);
  const end = formatDate(endDate);
  const title = `${startDate.getFullYear()} 年 ${startDate.getMonth() + 1} 月`;
  return { startDate, endDate, start, end, days: Math.max(1, Math.round((endDate - startDate) / 86400000) + 1), title };
}

function shiftTrendDate(date, delta) {
  const base = parseDate(date || todayString()) || parseDate(todayString());
  return formatDate(new Date(base.getFullYear(), base.getMonth() + delta, Math.min(base.getDate(), 28)));
}

function clampTrendDate(date) {
  return date > todayString() ? todayString() : date;
}

function niceAxisMax(value) {
  if (!Number.isFinite(value) || value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  const step = normalized <= 2 ? 2 : normalized <= 4 ? 4 : normalized <= 6 ? 6 : normalized <= 8 ? 8 : 10;
  const max = step * magnitude;
  return Math.max(2, Math.ceil(max));
}

function renderCalendar(project, list, append = false) {
  const recordsByDate = new Map();
  list.forEach((record) => {
    recordsByDate.set(record.date, (recordsByDate.get(record.date) || 0) + 1);
  });
  const year = detailVisibleMonth.getFullYear();
  const month = detailVisibleMonth.getMonth();
  const first = new Date(year, month, 1);
  const cursor = new Date(first);
  cursor.setDate(cursor.getDate() - cursor.getDay());
  const cells = [];
  ["日", "一", "二", "三", "四", "五", "六"].forEach((day) => {
    cells.push(`<div class="calendar-weekday">${day}</div>`);
  });
  for (let i = 0; i < 42; i++) {
    const date = formatDate(cursor);
    const count = recordsByDate.get(date) || 0;
    const classes = [
      "calendar-cell",
      cursor.getMonth() !== month ? "dim" : "",
      count ? "has" : "",
      date === detailSelectedDate ? "selected" : "",
      date === todayString() ? "today" : "",
      date > todayString() ? "future" : "",
    ].filter(Boolean).join(" ");
    cells.push(`
      <button type="button" class="${classes}" data-date="${date}" ${date > todayString() ? "disabled" : ""}>
        <span>${cursor.getDate()}</span>
        ${count ? `<small aria-label="有记录">✓</small>` : ""}
      </button>
    `);
    cursor.setDate(cursor.getDate() + 1);
  }
  const markup = `
    <div class="calendar-panel">
      <div class="calendarbar">
        <button type="button" data-calendar="prev">‹</button>
        <strong>${year} 年 ${month + 1} 月</strong>
        <button type="button" data-calendar="next">›</button>
      </div>
      <div class="calendar-grid">${cells.join("")}</div>
    </div>
  `;
  if (append) $("detailVisual").insertAdjacentHTML("beforeend", markup);
  else $("detailVisual").innerHTML = markup;
  $("detailVisual").querySelector('[data-calendar="prev"]').onclick = () => {
    detailVisibleMonth.setMonth(detailVisibleMonth.getMonth() - 1);
    renderProjectDetail();
  };
  $("detailVisual").querySelector('[data-calendar="next"]').onclick = () => {
    detailVisibleMonth.setMonth(detailVisibleMonth.getMonth() + 1);
    renderProjectDetail();
  };
  $("detailVisual").querySelectorAll("[data-date]").forEach((button) => {
    button.onclick = () => {
      detailSelectedDate = button.dataset.date;
      renderProjectDetail();
    };
  });
}

function renderDetailHistory(project, list) {
  const host = $("detailHistory");
  host.innerHTML = "";
  $("detailHistoryTitle").textContent = project.kind === "number" ? "记录" : formatHistoryDate(detailSelectedDate);
  if (!list.length) {
    host.innerHTML = `<article class="record-card empty-card"><h3>暂无记录</h3></article>`;
    return;
  }
  if (project.kind === "number") {
    list.forEach((record) => host.appendChild(measureRow(record)));
    return;
  }
  const selected = list.filter((record) => record.date === detailSelectedDate);
  if (!selected.length) {
    host.innerHTML = `<article class="record-card empty-card"><h3>这天没有记录</h3></article>`;
    return;
  }
  selected.forEach((record) => host.appendChild(project.kind === "check" ? checkRow(record) : textRow(record)));
}

function renderAnnualStats() {
  const project = projects.find((item) => String(item.id) === String(detailProjectId));
  if (!project) {
    switchTab("projects");
    return;
  }
  const list = records.filter((record) => String(record.projectId) === String(project.id) && record.date.startsWith(`${annualYear}-`));
  $("annualMeta").textContent = `${KIND_LABELS[project.kind] || "事项"} · ${annualYear}`;
  $("annualTitle").textContent = `${project.name} · 年度统计`;
  $("annualYearLabel").textContent = annualYear;
  const days = new Set(list.map((record) => record.date));
  const numericValues = list.map((record) => Number(record.amount)).filter((value) => Number.isFinite(value));
  const total = project.kind === "number"
    ? numericValues.reduce((sum, value) => sum + value, 0)
    : list.length;
  const average = numericValues.length ? total / numericValues.length : 0;
  $("annualSummary").innerHTML = statCards(project.kind === "number" ? [
    ["记录天数", `${days.size} 天`],
    [project.statMode === "avg" ? "年度平均" : "年度累计", `${formatNumber(project.statMode === "avg" ? average : total)}${project.unit || ""}`],
    ["记录数", `${list.length} 条`],
  ] : [
    ["打卡天数", `${days.size} 天`],
    ["打卡次数", `${list.length} 次`],
    ["年份", String(annualYear)],
  ]);
  $("annualWeekly").innerHTML = renderAnnualMonthly(project, list);
  $("annualWeekly").querySelectorAll("[data-annual-month]").forEach((bar) => {
    const showMonthValue = () => {
      const selected = bar.classList.contains("selected");
      $("annualWeekly").querySelectorAll("[data-annual-month]").forEach((item) => item.classList.remove("selected"));
      const caption = $("annualWeekly").querySelector("[data-annual-month-selected]");
      if (selected) {
        if (caption) caption.textContent = caption.dataset.defaultValue || "";
        return;
      }
      bar.classList.add("selected");
      if (caption) caption.textContent = bar.dataset.value || "";
    };
    bar.addEventListener("click", showMonthValue);
    bar.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      showMonthValue();
    });
  });
  $("annualGrid").innerHTML = renderAnnualGrid(project, list);
}

function renderAnnualMonthly(project, list) {
  const months = annualMonthStats(project, list);
  const values = months.map((month) => month.value);
  const max = niceAxisMax(Math.max(1, ...values));
  const width = 640;
  const height = 180;
  const padX = 38;
  const padY = 28;
  const slot = (width - padX * 2) / months.length;
  const barWidth = Math.max(18, Math.min(30, slot * 0.55));
  const activeMonths = months.filter((month) => month.entries > 0).length;
  const defaultCaption = activeMonths ? `${annualYear} 年共有 ${activeMonths} 个月有记录` : `${annualYear} 年暂无记录`;
  const bars = months.map((month, index) => {
    const x = padX + index * slot + slot / 2;
    const barHeight = Math.max(month.value ? 2 : 0, (height - padY * 2) * month.value / max);
    const y = height - padY - barHeight;
    const label = annualMonthText(project, index, month);
    return `<rect x="${(x - barWidth / 2).toFixed(1)}" y="${y.toFixed(1)}" width="${barWidth.toFixed(1)}" height="${barHeight.toFixed(1)}" rx="4" data-annual-month="${index + 1}" data-value="${escapeHtml(label)}" role="button" tabindex="0" aria-label="${escapeHtml(label)}"><title>${escapeHtml(label)}</title></rect>`;
  }).join("");
  return `
    <section class="annual-section">
      <h3>按月统计</h3>
      <p data-annual-month-selected data-default-value="${escapeHtml(defaultCaption)}">${defaultCaption}</p>
      <div class="annual-month-chart">
        <svg viewBox="0 0 ${width} ${height}" role="img" aria-label="按月统计">
          <line x1="${padX}" y1="${padY}" x2="${width - padX}" y2="${padY}" stroke="#ead5dd" />
          <line x1="${padX}" y1="${height - padY}" x2="${width - padX}" y2="${height - padY}" stroke="#dad0c2" />
          <g fill="#c8a15a">${bars}</g>
          <g class="chart-labels">
            <text x="${width - 8}" y="${padY + 4}" text-anchor="end">${formatNumber(max)}${project.kind === "number" ? escapeHtml(project.unit || "") : "次"}</text>
            <text x="${padX}" y="${height - 8}" text-anchor="start">1月</text>
            <text x="${width - padX}" y="${height - 8}" text-anchor="end">12月</text>
          </g>
        </svg>
      </div>
    </section>
  `;
}

function annualMonthStats(project, list) {
  const months = Array.from({ length: 12 }, () => ({ values: [], count: 0, entries: 0, value: 0 }));
  list.forEach((record) => {
    const parsed = parseDate(record.date);
    if (!parsed) return;
    const index = parsed.getMonth();
    months[index].entries += 1;
    if (project.kind === "number") {
      const value = Number(record.amount);
      if (Number.isFinite(value)) months[index].values.push(value);
    } else {
      months[index].count += 1;
    }
  });
  months.forEach((month) => {
    if (project.kind === "number") {
      const sum = month.values.reduce((total, value) => total + value, 0);
      month.value = project.statMode === "avg" && month.values.length ? sum / month.values.length : sum;
    } else {
      month.value = month.count;
    }
  });
  return months;
}

function annualMonthText(project, index, month) {
  const label = `${annualYear} 年 ${index + 1} 月`;
  if (!month.entries) return `${label} 无记录`;
  if (project.kind !== "number") return `${label} ${month.count} 次`;
  return `${label} ${formatNumber(month.value)}${project.unit || ""}`;
}

function renderAnnualGrid(project, list) {
  const byDate = new Map();
  list.forEach((record) => {
    const values = byDate.get(record.date) || [];
    values.push(record);
    byDate.set(record.date, values);
  });
  const rows = [];
  for (let month = 0; month < 12; month++) {
    const days = new Date(annualYear, month + 1, 0).getDate();
    const cells = [];
    for (let day = 1; day <= days; day++) {
      const date = `${annualYear}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
      const items = byDate.get(date) || [];
      const value = annualDayText(project, date, items);
      cells.push(`<span class="${items.length ? "lit" : ""}" title="${escapeHtml(value)}" aria-label="${escapeHtml(value)}"></span>`);
    }
    rows.push(`
      <div class="annual-month-row">
        <span>${month + 1}月</span>
        <div class="annual-day-grid">${cells.join("")}</div>
      </div>
    `);
  }
  return `
    <section class="annual-section">
      <h3>全年一览</h3>
      <p>${annualYear} 年共有 ${byDate.size} 天有记录</p>
      <div class="annual-grid">${rows.join("")}</div>
    </section>
  `;
}

function annualDayText(project, date, items) {
  if (!items.length) return `${date} 无记录`;
  if (project.kind !== "number") return `${date} 打卡 ${items.length} 次`;
  const values = items.map((item) => Number(item.amount)).filter((value) => Number.isFinite(value));
  const sum = values.reduce((total, value) => total + value, 0);
  const value = project.statMode === "avg" && values.length ? sum / values.length : sum;
  return `${date} ${formatNumber(value)}${project.unit || ""}`;
}

function statCards(items) {
  return items.map(([label, value]) => `
    <article class="stat-card">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
    </article>
  `).join("");
}

function measureRow(record) {
  const row = document.createElement("div");
  row.className = "measure-row";
  row.innerHTML = `
    <span>${escapeHtml(record.date)}</span>
    <strong>${escapeHtml(formatNumber(record.amount))}${escapeHtml(record.unit || "")}</strong>
    <p>${escapeHtml(record.content || "")}</p>
  `;
  return row;
}

function checkRow(record) {
  const row = document.createElement("div");
  row.className = "measure-row";
  row.innerHTML = `
    <span>${escapeHtml(record.date)}</span>
    <strong>已打卡</strong>
    <p>${escapeHtml(record.content || "")}</p>
  `;
  return row;
}

function textRow(record) {
  const row = document.createElement("div");
  row.className = "text-row";
  row.innerHTML = `
    <time>${escapeHtml(record.date)}</time>
    <p>${escapeHtml(record.content || "")}</p>
  `;
  return row;
}

function editRecord(id) {
  const record = records.find((item) => Number(item.id) === Number(id));
  if (!record) return;
  editingRecordId = record.id;
  currentProjectId = String(record.projectId);
  inlineProjectId = String(record.projectId);
  $("dateInput").value = record.date || todayString();
  switchTab(record.date === todayString() ? "record" : "byday");
}

async function deleteRecord(id) {
  await removeRecord(id, true, "已删除");
}

async function removeRecord(id, ask, message) {
  const entry = entries.find((item) => Number(item.id) === Number(id));
  if (!entry) return;
  if (ask && !window.confirm("删除这条记录？")) return;
  await postEntries([{ ...entry, deletedAt: Date.now(), updatedAt: Date.now(), version: Number(entry.version || 1) + 1 }]);
  if (message) showToast(message);
  editingRecordId = 0;
  inlineProjectId = "";
  await refreshData();
}

async function postEntries(nextEntries) {
  const response = await fetch(`${API}/entries`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ entries: nextEntries }),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
}

async function importZip() {
  const file = $("zipInput").files[0];
  $("zipInput").value = "";
  if (!file) return;
  const response = await fetch(`${API}/latest`, { method: "PUT", body: file });
  if (!response.ok) {
    showToast("导入失败", "warn");
    return;
  }
  showToast("已导入备份");
  await refreshData();
}

function currentProject() {
  return projects.find((project) => String(project.id) === String(currentProjectId)) || null;
}

function entryToProject(entry) {
  try {
    const data = JSON.parse(entry.body || "{}");
    if (data.schema !== PROJECT_SCHEMA) return null;
    return {
      id: Number(entry.id),
      name: data.name || "未命名事项",
      kind: ["number", "check"].includes(data.kind) ? data.kind : data.kind === "text" ? "check" : "number",
      unit: data.unit || "",
      statMode: data.statMode === "avg" ? "avg" : "sum",
      note: data.note || "",
      sortTime: Number(entry.createdAt || entry.id),
    };
  } catch (error) {
    return null;
  }
}

function entryToRecord(entry) {
  try {
    const data = JSON.parse(entry.body || "{}");
    if (data.schema === LEGACY_RECORD_SCHEMA) return legacyRecord(entry, data);
    if (data.schema !== RECORD_SCHEMA) return null;
    const project = projects.find((item) => String(item.id) === String(data.projectId));
    const date = data.date || data.periodStart || formatDate(new Date(Number(entry.createdAt || entry.id)));
    return {
      id: Number(entry.id),
      projectId: String(data.projectId || ""),
      projectName: data.projectName || (project ? project.name : "已删除事项"),
      kind: ["number", "check", "text"].includes(data.kind) ? data.kind : "number",
      date,
      amount: data.amount === null || data.amount === undefined || data.amount === "" ? null : Number(data.amount),
      unit: data.unit || (project ? project.unit : ""),
      content: data.content || "",
      note: data.note || "",
      sortTime: dateToTime(date, Number(entry.createdAt || entry.id)),
    };
  } catch (error) {
    return null;
  }
}

function legacyRecord(entry, data) {
  const date = data.date || data.periodStart || formatDate(new Date(Number(entry.createdAt || entry.id)));
  return {
    id: Number(entry.id),
    projectId: `legacy:${data.type || "record"}`,
    projectName: data.typeName || data.type || "旧记录",
    kind: data.amount === null || data.amount === undefined || data.amount === "" ? "text" : "number",
    date,
    amount: data.amount === null || data.amount === undefined || data.amount === "" ? null : Number(data.amount),
    unit: data.unit || "",
    content: data.content || "",
    note: data.note || "",
    sortTime: dateToTime(date, Number(entry.createdAt || entry.id)),
  };
}

function normalizeEntry(entry) {
  return {
    ...entry,
    id: Number(entry.id || Date.now()),
    createdAt: Number(entry.createdAt || entry.id || Date.now()),
    updatedAt: Number(entry.updatedAt || entry.createdAt || entry.id || Date.now()),
    deletedAt: entry.deletedAt ? Number(entry.deletedAt) : 0,
    version: Number(entry.version || 1),
    tags: Array.isArray(entry.tags) ? entry.tags : [],
    attachments: Array.isArray(entry.attachments) ? entry.attachments : [],
    body: typeof entry.body === "string" ? entry.body : "",
  };
}

function parseNumber(value) {
  if (value === "") return null;
  const next = Number(value);
  return Number.isFinite(next) ? next : null;
}

function dateToTime(date, fallback) {
  const parsed = Date.parse(`${date}T12:00:00`);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function todayString() {
  return formatDate(new Date());
}

function formatDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatHistoryDate(date) {
  const parsed = Date.parse(`${date}T00:00:00`);
  if (!Number.isFinite(parsed)) return date;
  const target = formatDate(new Date(parsed));
  if (target === todayString()) return `${target} 今天`;
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  if (target === formatDate(yesterday)) return `${target} 昨天`;
  return target;
}

function parseDate(date) {
  const parsed = new Date(`${date}T12:00:00`);
  return Number.isFinite(parsed.getTime()) ? parsed : null;
}

function addDays(date, days) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function startOfWeek(date) {
  const next = new Date(date);
  const day = next.getDay() || 7;
  next.setDate(next.getDate() - day + 1);
  return next;
}

function shortDate(date) {
  return String(date || "").replace(/^\d{4}-/, "");
}

function weekKey(date) {
  const parsed = new Date(`${date}T12:00:00`);
  if (!Number.isFinite(parsed.getTime())) return date;
  const day = parsed.getDay() || 7;
  parsed.setDate(parsed.getDate() - day + 1);
  return formatDate(parsed);
}

function formatNumber(value) {
  if (!Number.isFinite(value)) return "";
  if (Math.abs(value - Math.round(value)) < 0.0001) return String(Math.round(value));
  const rounded = Math.round((value + Number.EPSILON) * 100) / 100;
  return rounded.toFixed(2).replace(/\.?0+$/, "");
}

function ensureDeviceId() {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = `web-tracker-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
    localStorage.setItem(DEVICE_ID_KEY, id);
  }
  return id;
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

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
