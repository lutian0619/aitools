# 快印

一个很小的 Android 快速打印工具：手机端从 AI、聊天、浏览器等 App 分享文字到快印，预览 A4 页面后调用 Android 系统打印服务。

## 功能

- 打开就是大文本框，适合直接粘贴 AI 生成的内容。
- 支持从其它 App 分享 `text/plain` 文本到快印。
- 支持 Android 选中文字后的“处理文字”入口。
- 自动保存当前草稿。
- 保留最近 20 条打印/分享历史，历史页显示保存时间。
- 固定 A4 打印，默认 11pt 字号、12mm 页边距。
- 支持调整打印字号。
- 粘贴明显 CSV 时会自动建议表格预览，也可手动选择按文字或按 CSV 表格打印。
- “预览”按钮会打开 A4 打印预览，确认后再调起系统打印。
- “分享”按钮可把当前文字发给其它 App。

## 打包

如果本机已经有 Android Studio，可以直接打开当前目录构建 APK。

也可以使用脚本，默认使用稳定 SDK 目录 `/Users/gogogo/Library/Android/sdk`。如果要临时切换，设置 `ANDROID_SDK_ROOT` 指向一个包含 Android 35 平台和 35.0.0 build-tools 的 SDK：

```bash
scripts/build-apk.sh
```

或者使用不依赖 Gradle 的手工脚本：

```bash
scripts/build-apk-manual.sh
```

APK 输出位置：

```text
build/outputs/apk/debug/app-debug.apk
```

## 局域网下载 APK

电脑和手机连同一个局域网后，在电脑上启动下载服务：

```bash
scripts/start-server.sh
```

默认端口是 `8790`，启动后脚本会打印手机可访问的局域网地址，例如：

```text
http://电脑局域网IP:8790/app.apk
```

如果要换端口：

```bash
PORT=8791 scripts/start-server.sh
```

## 使用方式

1. 在手机 AI App 里生成文字。
2. 点分享，选择“快印”。
3. 调整字号。
4. 点“预览”，确认 A4 页面效果后再打印或保存 PDF。
