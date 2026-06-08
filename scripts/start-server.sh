#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${PORT:-8788}"

cd "$ROOT"

if ! command -v node >/dev/null 2>&1; then
  echo "缺少 Node.js。请先安装 Node.js 后重试。"
  exit 1
fi

echo "启动 aitools 局域网服务..."
echo "电脑端: http://127.0.0.1:${PORT}/web/"
echo "日记:   http://127.0.0.1:${PORT}/tools/diary/"
echo "日常格: http://127.0.0.1:${PORT}/tools/tracker/"
echo "快印 APK: http://127.0.0.1:${PORT}/api/tools/print/apk"
echo "节拍器 APK: http://127.0.0.1:${PORT}/api/tools/metronome/apk"
echo "相似词决斗 APK: http://127.0.0.1:${PORT}/api/tools/word-duel/apk"
echo "街区对决 APK: http://127.0.0.1:${PORT}/api/tools/street-duel/apk"
echo "Egg和他的朋友们 APK: http://127.0.0.1:${PORT}/api/tools/egg-friends/apk"
echo "工具市场 APK: http://127.0.0.1:${PORT}/api/tools/market/apk"

LAN_IPS="$(ifconfig | awk '/inet / && $2 != "127.0.0.1" && $2 !~ /^0\./ {print $2}')"
if [ -n "$LAN_IPS" ]; then
  echo "局域网地址:"
  for ip in $LAN_IPS; do
    echo "  Web:      http://${ip}:${PORT}/web/"
    echo "  日常格 APK: http://${ip}:${PORT}/api/tools/tracker/apk"
    echo "  日记 APK: http://${ip}:${PORT}/api/tools/diary/apk"
    echo "  快印 APK: http://${ip}:${PORT}/api/tools/print/apk"
    echo "  节拍器 APK: http://${ip}:${PORT}/api/tools/metronome/apk"
    echo "  相似词决斗 APK: http://${ip}:${PORT}/api/tools/word-duel/apk"
    echo "  街区对决 APK: http://${ip}:${PORT}/api/tools/street-duel/apk"
    echo "  Egg和他的朋友们 APK: http://${ip}:${PORT}/api/tools/egg-friends/apk"
    echo "  工具市场 APK: http://${ip}:${PORT}/api/tools/market/apk"
  done
else
  echo "局域网手机端请使用电脑 IP + 端口，例如: http://电脑局域网IP:${PORT}"
fi

PORT="$PORT" node server/server.js
