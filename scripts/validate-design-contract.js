#!/usr/bin/env node
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const toolsConfig = JSON.parse(fs.readFileSync(path.join(root, "server/config/tools.json"), "utf8"));
const pages = [
  "web/index.html",
  "web/tools/diary/index.html",
  "web/tools/tracker/index.html",
];
const cssFiles = [
  "web/styles.css",
  "web/tools/diary/styles.css",
  "web/tools/tracker/styles.css",
];
const androidPaletteFiles = findAndroidMainActivities();
const androidMarketFiles = [
  "android/market-app/app/src/main/java/com/aitools/market/MainActivity.java",
];
const androidMarketManifestFiles = [
  "android/market-app/app/src/main/AndroidManifest.xml",
];

const failures = [];

for (const rel of existingFiles(pages)) {
  const html = read(rel);
  requireContains(rel, html, "/web/app-shell.css", "must include shared app shell CSS");
  requireContains(rel, html, "/web/product-switcher.css", "must include shared product switcher CSS");
  requireContains(rel, html, "class=\"shellbar\"", "must use shared shellbar");
  requireContains(rel, html, "class=\"shellbrand\"", "must use shared shellbrand");
  requireContains(rel, html, "class=\"shelltitle\"", "must use shared shelltitle");
  requireContains(rel, html, "class=\"shellactions\"", "must use shared shellactions");
  requireContains(rel, html, "data-product-switcher-host", "must mount product switcher in shellactions");
  requireContains(rel, html, "/web/product-switcher.js", "must include product switcher script");
}

for (const rel of existingFiles(cssFiles)) {
  const css = read(rel);
  if (/^header\s*\{/m.test(css)) {
    failures.push(`${rel}: must not define a bare header topbar; use .shellbar from app-shell.css`);
  }
}

for (const rel of existingFiles(["web/tools/diary/styles.css", "web/tools/tracker/styles.css"])) {
  const css = read(rel);
  for (const token of ["--paper", "--panel", "--line", "--accent"]) {
    if (new RegExp(`${escapeRegExp(token)}\\s*:`).test(css)) {
      failures.push(`${rel}: must not redefine core token ${token}; update app-shell.css and all products together`);
    }
  }
}

for (const rel of existingFiles(androidPaletteFiles)) {
  const source = read(rel);
  requireContains(rel, source, "private static final int INK = Color.rgb(47, 41, 37);", "must use shared Android INK color");
  requireContains(rel, source, "private static final int MUTED = Color.rgb(117, 104, 95);", "must use shared Android MUTED color");
  requireContains(rel, source, "private static final int PAPER = Color.rgb(253, 239, 239);", "must use shared Android PAPER color");
  requireContains(rel, source, "private static final int CARD = Color.rgb(255, 249, 246);", "must use shared Android CARD color");
  requireContains(rel, source, "private static final int LINE = Color.rgb(218, 208, 194);", "must use shared Android LINE color");
  requireContains(rel, source, "private static final int ACCENT = Color.rgb(138, 113, 94);", "must use shared Android ACCENT color");
  requireContains(rel, source, "showSettingsPage()", "must provide an Android settings page");
  requireContains(rel, source, "设置", "must expose a visible Android settings entry");
  requireContains(rel, source, "关于", "Android settings page must include an about section");
  if (source.includes("smallButton(\"设置\")") || source.includes("headerSettings.setText(\"设置\")")) {
    failures.push(`${rel}: settings entry must be a simple icon, not a boxed text button`);
  }
  if (/SettingsIconButton\s+extends\s+View/.test(source)) {
    requireContains(rel, source, "setClickable(true);", "custom Android settings icon must be explicitly clickable");
    requireContains(rel, source, "canvas.drawCircle", "custom Android settings icon must draw the shared gear form");
    if (/canvas\.drawLine\(left,\s*y1,\s*right,\s*y1/.test(source) || /canvas\.drawLine\(left,\s*y2,\s*right,\s*y2/.test(source)) {
      failures.push(`${rel}: custom Android settings icon must not use a hamburger/menu glyph`);
    }
  }
  for (const action of ["重置记录", "清空记录", "删除全部", "重置所有"]) {
    for (const mainFunction of ["buildHome", "buildMainPage", "buildUi"]) {
      const mainSurface = extractJavaMethod(source, mainFunction);
      if (mainSurface.includes(action)) {
        failures.push(`${rel}: destructive local-data action "${action}" must not appear on the primary app surface; put it under settings/data-management`);
      }
    }
  }
}

for (const rel of existingFiles(androidMarketFiles)) {
  const source = read(rel);
  requireContains(rel, source, "自动探测", "market app must expose service discovery");
  requireContains(rel, source, "openApk", "market app must expose APK install/update download");
}

for (const rel of existingFiles(androidMarketManifestFiles)) {
  const source = read(rel);
  for (const tool of toolsConfig) {
    if (!tool.packageName) continue;
    requireContains(rel, source, `<package android:name="${tool.packageName}" />`, `market app must query installed package ${tool.packageName}`);
  }
}

if (failures.length) {
  console.error("Design contract failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log("Design contract passed.");

function read(rel) {
  return fs.readFileSync(path.join(root, rel), "utf8");
}

function existingFiles(files) {
  return files.filter((rel) => fs.existsSync(path.join(root, rel)));
}

function findAndroidMainActivities() {
  const androidDir = path.join(root, "android");
  if (!fs.existsSync(androidDir)) return [];
  const files = [];
  for (const appDir of fs.readdirSync(androidDir)) {
    if (!appDir.endsWith("-app")) continue;
    const javaRoot = path.join(androidDir, appDir, "app", "src", "main", "java");
    collectMainActivities(javaRoot, files);
  }
  return files.map((file) => path.relative(root, file));
}

function collectMainActivities(dir, files) {
  if (!fs.existsSync(dir)) return;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      collectMainActivities(fullPath, files);
    } else if (entry.isFile() && entry.name === "MainActivity.java") {
      files.push(fullPath);
    }
  }
}

function extractJavaMethod(source, name) {
  const start = source.indexOf(` ${name}(`);
  if (start === -1) return "";
  const braceStart = source.indexOf("{", start);
  if (braceStart === -1) return "";
  let depth = 0;
  for (let i = braceStart; i < source.length; i++) {
    if (source[i] === "{") depth++;
    if (source[i] === "}") depth--;
    if (depth === 0) return source.slice(braceStart, i + 1);
  }
  return source.slice(braceStart);
}

function requireContains(rel, text, needle, message) {
  if (!text.includes(needle)) failures.push(`${rel}: ${message}`);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
