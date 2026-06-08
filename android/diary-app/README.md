# 一页日记

一个极简 Android 日记 APK 工程：三页签结构，打开就写，正文标签，本地保存，日历查看，标签筛选，局域网同步，批量导出 Markdown/JSON/ZIP。

## 功能

- 打开应用后自动聚焦到正文输入框。
- 三个页签：今天、日历、标签。
- 标签直接写在正文里，例如 `今天散步 #心情 #运动`，支持多个标签。
- 支持插入图片：图片保存到 App 私有附件目录，正文保留轻量附件标记。
- 支持日历查看：点日期后只看当天日记。
- 日记存储在本地 SQLite，标签单独建表并建索引，按标签快速筛选日记。
- 保存后进入只读详情页，历史日记先阅读，点编辑后再修改。
- 未保存内容在退出、新建或编辑另一篇前会提示确认。
- 详情页图片可以点开全屏预览。
- 日历和标签列表分批展示，支持加载更多。
- 编辑页图片缩略图支持长按移除。
- 历史日记点一下进入编辑，长按删除。
- 日记保存在手机本地应用数据里的 `one_page_diary.db`。
- 手机端通过局域网服务一键同步数据；备份和文件导入导出集中放在网页版。
- 手机端可点右上角设置图标，自动探测电脑服务并同步数据。APK 安装和更新统一交给工具市场。
- 同步时先拉取电脑端元数据，只传输有差异的日记和相关附件；服务端仍会维护 `latest.zip` 作为兼容备份。

## 网页版

`web/tools/diary/` 是电脑端 Web 客户端。Web 端通过电脑服务读写日记 entries，正文主存储在服务端 SQLite；浏览器本地只保存少量界面状态。设置页支持导入/导出与 Android 端一致的 ZIP：

```text
diary.json
diary.md
attachments/
```

启动服务后打开：

```text
http://127.0.0.1:8788/tools/diary/
```

## 局域网同步

项目根目录提供统一局域网服务，同时提供 Web 页面、APK 下载和 HTTP 同步接口：

```bash
scripts/start-server.sh
```

默认地址：

```text
http://127.0.0.1:8788/web/
http://电脑局域网IP:8788/web/
```

调试 APK 下载地址：

```text
http://127.0.0.1:8788/api/tools/diary/apk
http://电脑局域网IP:8788/api/tools/diary/apk
```

旧地址 `GET /app.apk` 仍兼容，但 APK 安装和更新统一由工具市场承担。

同步接口：

```text
PUT /api/latest   上传当前 ZIP
GET /api/latest   下载 latest.zip
GET /api/status   查看服务状态
GET /api/manifest 查看服务端日记元数据
POST /api/entries/query 拉取指定日记和附件
POST /api/entries 提交变更日记和附件
```

Android 端在设置页里可以自动探测电脑服务，也可以手动填电脑地址，例如：

```text
http://192.168.113.7:8788
```

Android 端点右上角设置图标进入设置页，然后可以点“自动探测电脑服务”或“同步”。服务端会把最新版兼容备份保存到：

也可以点“自动探测电脑服务”，手机会扫描当前局域网里的 `8788` 端口，找到服务后自动填入地址。

```text
sync/diary/latest.zip
```

一页日记的探测逻辑兼容两种服务状态：

- 旧的一页日记服务：`GET /api/status` 返回 `app = "one-page-diary"`。
- 当前统一 aitools 服务：`GET /api/status` 返回 `app = "aitools"`，并在 `tools` 列表里包含 `diary` 且 `sync.enabled = true`。

APK 安装、更新和打开工具由工具市场负责；一页日记自身的电脑服务探测只用于数据同步。

网页版右上角设置页也提供“同步”按钮。建议电脑和手机都用同一个 `scripts/start-server.sh` 服务地址同步；网页本地文件导入导出只作为手动备份入口。

通过 `scripts/start-server.sh` 打开网页版时，网页会在启动时自动尝试与电脑服务增量同步。网页保持打开时也会定期检查电脑服务，有新变更会自动合并并刷新当前页；也可以点右上角“同步”立即同步。

手机端增量同步规则：

- 先请求 `/api/manifest`，比较 `id`、`version`、删除状态和正文哈希。
- 只通过 `/api/entries/query` 拉取手机缺失或服务端更新的日记。
- 只通过 `/api/entries` 提交手机新增或版本更新的日记，并附带这些日记引用的图片。
- 服务端收到增量后会更新元数据状态，并重建 `sync/diary/latest.zip`，保证 ZIP 备份和 Web 端仍兼容。

冲突处理规则：

- 两端各自新增的日记会自动合并。
- 每篇日记带 `version` 和 `deviceId`，编辑会递增版本。
- 删除是软删除，会随同步传播，不会直接从备份包里消失。
- 如果两端同时编辑同一篇日记，同版本但内容不同，系统保留当前版本，并额外生成一篇带 `#冲突` 的副本，后续手工整理。
- 服务端每次收到新上传前，会把旧的 `latest.zip` 复制到 `sync/diary/history/`，作为额外保险。

## 图片功能方案

图片采用“正文引用 + 本地附件表”的方式：图片复制到应用私有目录 `attachments/`，SQLite 只保存文件名和对应日记 id，正文里插入类似 `![图片](attachment:xxx.jpg)` 的轻量标记。这样数据库不会被大图片撑大，JSON 备份可以带附件清单，网盘备份时再把 JSON 和图片目录一起导出。

## 打包 APK

当前目录已支持不用 Android Studio 的手工构建方式。依赖默认放在：

```text
/Users/gogogo/Library/Android/sdk
```

运行：

```bash
scripts/build-apk-manual.sh
```

APK 输出位置：

```text
build/outputs/apk/debug/app-debug.apk
```

推荐方式：安装 Android Studio，打开 `/Users/gogogo/PycharmProjects/diary` 这个目录，等同步完成后点 `Build > Build APK(s)`。

命令行方式：本机需要先有 Java、Gradle 和 Android SDK。装好后在项目根目录运行：

```bash
scripts/build-apk.sh
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如果使用 Android Studio，直接打开这个目录，等同步完成后点 `Build > Build APK(s)`。

如果要改用 Gradle 标准构建，网络正常后可安装：

```bash
brew install gradle android-commandlinetools
```

然后用 `sdkmanager` 安装 Android 35 平台和 build-tools，再执行 `scripts/build-apk.sh`。当前这台机器已经可以用 `scripts/build-apk-manual.sh` 构建，不需要 Android Studio。

## 安装到手机

方式一：把 `app-debug.apk` 传到手机，点开安装。手机需要允许“安装未知应用”。

方式二：如果电脑装了 adb 并连接了手机：

```bash
adb install -r build/outputs/apk/debug/app-debug.apk
```
