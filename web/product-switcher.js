(function () {
  const current = document.currentScript && document.currentScript.dataset.current || "";
  const root = document.createElement("div");
  root.className = "product-switcher";
  root.innerHTML = `
    <button type="button" aria-expanded="false">产品切换 ▾</button>
    <section class="product-switcher-panel" hidden>
      <div class="product-switcher-list">
        <a class="product-switcher-item" data-id="manage" href="/web/">
          <b>管理台</b>
          <small>服务状态、安装包和产品入口</small>
        </a>
      </div>
    </section>
  `;
  const host = document.querySelector("[data-product-switcher-host]");
  if (host) {
    root.classList.add("inline");
    host.appendChild(root);
  } else {
    document.body.appendChild(root);
  }

  const button = root.querySelector("button");
  const panel = root.querySelector(".product-switcher-panel");
  const list = root.querySelector(".product-switcher-list");

  button.addEventListener("click", () => {
    const open = panel.hidden;
    panel.hidden = !open;
    button.setAttribute("aria-expanded", String(open));
  });

  document.addEventListener("click", (event) => {
    if (!root.contains(event.target)) {
      panel.hidden = true;
      button.setAttribute("aria-expanded", "false");
    }
  });

  loadProducts();

  async function loadProducts() {
    markActive();
    try {
      const response = await fetch("/api/status", { cache: "no-store" });
      if (!response.ok) return;
      const status = await response.json();
      for (const tool of status.tools || []) {
        if (!tool.webPath) continue;
        const item = document.createElement("a");
        item.className = "product-switcher-item";
        item.dataset.id = tool.id;
        item.href = tool.webPath;
        item.innerHTML = `
          <b>${escapeHtml(tool.name || tool.id)}</b>
          <small>${escapeHtml(tool.description || tool.app || "")}</small>
        `;
        list.appendChild(item);
      }
      markActive();
    } catch (error) {
    }
  }

  function markActive() {
    root.querySelectorAll(".product-switcher-item").forEach((item) => {
      item.classList.toggle("active", item.dataset.id === current);
    });
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }
})();
