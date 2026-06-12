# aitools

`aitools` 是个人局域网小工具平台：电脑端运行一个共享服务，Web 端提供管理台和部分工具客户端，Android 端按工具拆成独立 APK。

当前约定是：APK 安装、更新、打开工具统一由“工具市场”管理；需要跨设备数据的工具才保留同步能力；纯手机工具不再各自实现电脑服务探测或 APK 更新。

## 当前工具

| 工具 | 形态 | 数据同步 | 说明 |
| --- | --- | --- | --- |
| 日常格 | Android + Web | 是 | 自定义事项记录，支持补打、按事项查看趋势、Web 客户端和手机同步。 |
| 一页日记 | Android + Web | 是 | 日记、标签、图片附件、ZIP 备份和手机同步。 |
| 快印 | Android | 否 | 接收分享文字，预览 A4 页面并调用 Android 系统打印。 |
| 节拍器 | Android | 否 | 简洁节拍器，支持 BPM 调速、单拍/常用拍号和意大利语速度名称。 |
| 相似词决斗 | Android | 否 | 高一英语易混词选择小游戏，支持限时闯关、错题复习和词对图鉴。 |
| 街区对决 | Android | 否 | 轻量卡牌对战游戏，三据点五格拉锯，支持单机 AI 对战、场地、事件和卡牌技能。 |
| 工具市场 | Android | 否 | 统一发现电脑服务、安装/更新 APK、打开已安装工具。 |

## 职责边界

- 电脑服务负责 Web 入口、工具注册、APK 下载、UDP 局域网发现和同步 API。
- 工具市场负责 Android 工具的发现、安装、更新和打开，不读取各业务工具的私有数据。
- 日常格、一页日记这类数据工具可以保留电脑服务探测，但只用于数据同步。
- 快印、节拍器、相似词决斗、街区对决这类纯手机工具不连接电脑服务，不参与同步。
- Web 端不负责手机数据同步；Web 日记设置页只保留 ZIP 备份导入导出和关于说明。

## 目录结构

```text
android/
  tracker-app/      日常格 Android 工程
  diary-app/        一页日记 Android 工程
  print-app/        快印 Android 工程
  metronome-app/    节拍器 Android 工程
  word-duel-app/    相似词决斗 Android 工程
  street-duel-app/  街区对决 Android 工程
  market-app/       工具市场 Android 工程
artifacts/apk/      构建后给局域网下载的 APK
server/             统一 LAN 服务
server/config/      工具注册配置
sync/               各工具运行数据和同步状态
web/                电脑端管理台和共享 Web 资源
web/tools/tracker/  日常格 Web 客户端
web/tools/diary/    一页日记 Web 客户端
```

`sync/` 和 `artifacts/apk/` 是运行或构建产物，不作为源代码提交。

## 启动服务

推荐用统一控制脚本管理 Web 服务：

```bash
scripts/webctl.sh start    # 启动
scripts/webctl.sh stop     # 停止
scripts/webctl.sh restart  # 重启
scripts/webctl.sh status   # 查看状态
scripts/webctl.sh open     # 启动并打开 Web
```

macOS 菜单栏 App：

```bash
scripts/build-macos-web-app.sh
open -g "build/macos/AITools Web.app"
```

运行后屏幕顶部菜单栏会出现 `AITools On` / `AITools Off`，可直接启动、停止、重启和后台打开 Web。生成的 `.app` 在 `build/macos/` 下，是本机构建产物，不提交到 Git；需要时可以拖到 Dock。

原始启动脚本仍可直接使用：

```bash
scripts/start-server.sh
```

默认地址：

```text
http://127.0.0.1:8788/web/
```

Web 入口：

```text
http://127.0.0.1:8788/web/
http://127.0.0.1:8788/tools/tracker/
http://127.0.0.1:8788/tools/diary/
```

手机应使用电脑的局域网 IP，例如：

```text
http://192.168.x.x:8788/web/
```

## APK 构建

所有 Android APK 必须使用同一把调试签名 key，避免手机端覆盖安装时
出现签名不一致。

- 默认签名文件：`~/.android/debug.keystore`
- 统一校验脚本：`scripts/android-signing.sh`
- 固定 SHA-256 指纹：

```text
9E:DB:23:63:FC:93:67:27:3E:61:35:59:F3:EC:76:35:A8:61:EB:3D:6F:D8:EC:B7:05:BA:C8:5D:A7:08:B6:A5
```

- 各 `android/*-app/scripts/build-apk-manual.sh` 必须在签名前调用
  `verify_android_keystore "$KEYSTORE"`。
- 不允许默认使用 `/private/tmp/*.keystore`，也不允许在构建时自动生成新的 keystore。
- 如果 keystore 缺失或指纹不匹配，构建必须失败；应恢复原 key，不要换 key 后继续出包。
- 如确需临时指定 key，可使用对应环境变量，例如 `TRACKER_KEYSTORE`、
  `DIARY_KEYSTORE`、`MARKET_KEYSTORE`，但指纹仍必须匹配。
- 如果要同步更新 `ANDROID_DEBUG_KEYSTORE_SHA256`，必须确认这是一次有意
  签名迁移，并接受覆盖安装影响。

单独构建：

```bash
scripts/build-tracker-apk.sh
scripts/build-diary-apk.sh
scripts/build-print-apk.sh
scripts/build-metronome-apk.sh
scripts/build-word-duel-apk.sh
scripts/build-street-duel-apk.sh
scripts/build-market-apk.sh
```

全部构建：

```bash
scripts/build-all-apks.sh
```

构建后的统一下载地址：

```text
http://127.0.0.1:8788/api/tools/tracker/apk
http://127.0.0.1:8788/api/tools/diary/apk
http://127.0.0.1:8788/api/tools/print/apk
http://127.0.0.1:8788/api/tools/metronome/apk
http://127.0.0.1:8788/api/tools/word-duel/apk
http://127.0.0.1:8788/api/tools/street-duel/apk
http://127.0.0.1:8788/api/tools/market/apk
```

日记旧下载地址仍兼容：

```text
http://127.0.0.1:8788/app.apk
```

## 服务接口

数据存储、同步协议和备份机制的详细说明见：

```text
docs/data-storage-sync-backup.md
```

平台状态：

```text
GET /api/status
GET /api/tools
GET /api/tools/:id/status
```

APK 下载：

```text
GET /api/tools/:id/apk
```

同步工具接口，仅对 `sync.enabled = true` 的工具可用：

```text
GET  /api/tools/:id/latest
PUT  /api/tools/:id/latest
GET  /api/tools/:id/manifest
GET  /api/tools/:id/entries
POST /api/tools/:id/entries
POST /api/tools/:id/entries/query
```

一页日记保留旧同步接口，兼容现有手机端：

```text
GET  /api/latest
PUT  /api/latest
GET  /api/manifest
GET  /api/entries
POST /api/entries
POST /api/entries/query
GET  /api/attachments/:filename
```

## 局域网发现

服务在 HTTP 端口同端口监听 UDP 发现请求，默认是 `8788`。

支持的发现消息：

```text
aitools-discovery
one-page-diary-discovery
```

返回内容包含当前服务地址、候选 URL 列表和工具包名。工具市场会结合已保存地址、UDP 返回地址、UDP 响应来源地址和本机私有网段候选进行探测。

需要数据同步的业务 App 也可以保留电脑服务探测，但只用于同步，不承担 APK 更新。探测时不能只识别旧的单工具状态；统一服务的 `GET /api/status` 返回 `app = "aitools"`，业务 App 应从 `tools` 列表中确认自己的工具存在且 `sync.enabled = true`。一页日记仍兼容旧的 `one-page-diary-discovery` 消息和旧 `/api/*` 同步接口。

## 工具注册

工具配置在：

```text
server/config/tools.json
```

新增工具时至少维护：

- `id`：工具唯一标识。
- `name` / `app` / `description`：管理台和接口展示信息。
- `packageName`：Android 包名，供工具市场判断安装状态。
- `webPath`：有 Web 客户端时填写。
- `apkFileName` / `apkCandidates`：APK 下载文件名和候选构建产物。
- `sync.enabled` / `sync.stateName`：是否启用同步和同步数据目录名。

## UI 与契约

修改 Web 或 Android UI 前先读：

```text
web/DESIGN_SYSTEM.md
```

核心约定：

- Web 页面必须使用共享顶栏、产品切换器和视觉 token。
- Android App 标题栏、返回按钮、设置入口和设置页结构必须统一。
- 所有 Android App 设置页必须有“关于”；有本地数据、同步、备份、导入导出能力时必须有“数据管理”。
- APK 安装、更新、打开工具统一由工具市场承担。

UI 改动后运行：

```bash
node scripts/validate-design-contract.js
```

## 安全

这个服务只面向可信局域网，不要直接暴露到公网。不要提交 APK、私有同步 ZIP、keystore、本机 SDK 路径或 Android 构建输出。
