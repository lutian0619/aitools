#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${PORT:-8790}"

cd "$ROOT"

if ! command -v node >/dev/null 2>&1; then
  echo "缺少 Node.js。请先安装 Node.js 后重试。"
  exit 1
fi

echo "启动快印 APK 下载服务..."
echo "本机: http://127.0.0.1:${PORT}/"
echo "本机 APK: http://127.0.0.1:${PORT}/app.apk"

LAN_IPS="$(ifconfig | awk '/inet / && $2 != "127.0.0.1" && $2 !~ /^0\./ {print $2}')"
if [ -n "$LAN_IPS" ]; then
  echo "局域网地址:"
  for ip in $LAN_IPS; do
    echo "  页面: http://${ip}:${PORT}/"
    echo "  APK:  http://${ip}:${PORT}/app.apk"
  done
else
  echo "局域网手机端请使用电脑 IP + 端口，例如: http://电脑局域网IP:${PORT}/app.apk"
fi

PORT="$PORT" node server.js
