const $ = (id) => document.getElementById(id);

load();

async function load() {
  $("serverState").textContent = "检查中";
  try {
    const status = await fetchJson("/api/status");
    $("serverState").textContent = status.ok ? "运行中" : "异常";
    $("serverState").className = status.ok ? "ok" : "warn";
    $("serverPort").textContent = status.port || "-";
    $("toolCount").textContent = Array.isArray(status.tools) ? `${status.tools.length} 个` : "-";
    renderTools(status.tools || []);
  } catch (error) {
    $("serverState").textContent = "无法连接";
    $("serverState").className = "warn";
    $("tools").innerHTML = "";
  }
}

async function fetchJson(url) {
  const response = await fetch(url, { cache: "no-store" });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

function renderTools(tools) {
  $("tools").innerHTML = tools.map((tool) => {
    const hasApk = tool.apk && tool.apk.hasApk;
    const sync = tool.sync && tool.sync.enabled
      ? (tool.sync.hasLatest ? `同步 ${formatSize(tool.sync.size)}` : "暂无同步包")
      : "无同步";
    const apkText = hasApk ? `APK ${formatSize(tool.apk.size)}` : "APK 未构建";
    const openAction = tool.webPath ? `<a class="button primary" href="${escapeAttribute(tool.webPath)}">打开产品</a>` : "";
    const apkAction = hasApk
      ? `<a class="button${tool.webPath ? "" : " primary"}" href="${escapeAttribute(tool.apk.download)}">下载 APK</a>`
      : `<span class="button disabled" aria-disabled="true">APK 未构建</span>`;
    return `
      <article class="toolCard">
        <h3>${escapeHtml(tool.name)}</h3>
        <p>${escapeHtml(tool.description || "")}</p>
        <div class="meta">
          <span class="${hasApk ? "ok" : "warn"}">${escapeHtml(apkText)}</span>
          <span>${escapeHtml(sync)}</span>
        </div>
        <div class="actions">
          ${openAction}
          ${apkAction}
          <a class="button" href="/api/tools/${encodeURIComponent(tool.id)}/status">接口状态</a>
        </div>
      </article>
    `;
  }).join("");
}

function formatSize(size) {
  if (!size) return "0 KB";
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function escapeAttribute(value) {
  return escapeHtml(value).replace(/'/g, "&#39;");
}
