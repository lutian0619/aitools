#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${PORT:-8788}"
PID_FILE="${TMPDIR:-/tmp}/aitools-web-${PORT}.pid"
LOG_FILE="${TMPDIR:-/tmp}/aitools-web-${PORT}.log"
URL="http://127.0.0.1:${PORT}/web/"

cd "$ROOT"

service_pids() {
  {
    if [ -f "$PID_FILE" ]; then
      pid="$(cat "$PID_FILE" 2>/dev/null || true)"
      if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        echo "$pid"
      fi
    fi
    if command -v lsof >/dev/null 2>&1; then
      lsof -tiTCP:"$PORT" -sTCP:LISTEN -n -P 2>/dev/null || true
    fi
    pgrep -f "node .*server/server.js|node server/server.js" 2>/dev/null || true
  } | sed '/^$/d' | sort -u
}

is_running() {
  [ -n "$(service_pids)" ]
}

lan_urls() {
  if command -v ifconfig >/dev/null 2>&1; then
    ifconfig | awk -v port="$PORT" '/inet / && $2 != "127.0.0.1" && $2 !~ /^0\./ {print "  http://" $2 ":" port "/web/"}'
  fi
}

status() {
  pids="$(service_pids)"
  if [ -n "$pids" ]; then
    echo "aitools Web: running"
    echo "PID: $(echo "$pids" | paste -sd, -)"
    echo "Local: $URL"
    lan="$(lan_urls)"
    if [ -n "$lan" ]; then
      echo "LAN:"
      echo "$lan"
    fi
    echo "Log: $LOG_FILE"
  else
    echo "aitools Web: stopped"
    echo "Local: $URL"
  fi
}

start() {
  if is_running; then
    echo "aitools Web is already running."
    status
    return
  fi

  if ! command -v node >/dev/null 2>&1; then
    echo "Node.js is missing. Install Node.js first."
    exit 1
  fi

  : > "$LOG_FILE"
  nohup env PORT="$PORT" node server/server.js >> "$LOG_FILE" 2>&1 &
  echo "$!" > "$PID_FILE"

  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if is_running; then
      echo "aitools Web started."
      status
      return
    fi
    sleep 0.3
  done

  echo "aitools Web failed to start. Last log lines:"
  tail -40 "$LOG_FILE" || true
  exit 1
}

stop() {
  pids="$(service_pids)"
  if [ -z "$pids" ]; then
    echo "aitools Web is already stopped."
    rm -f "$PID_FILE"
    return
  fi

  echo "$pids" | while IFS= read -r pid; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
  done

  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if ! is_running; then
      rm -f "$PID_FILE"
      echo "aitools Web stopped."
      return
    fi
    sleep 0.3
  done

  echo "aitools Web did not stop after SIGTERM."
  status
  exit 1
}

open_web() {
  start
  if command -v open >/dev/null 2>&1; then
    open "$URL"
  else
    echo "$URL"
  fi
}

menu() {
  while true; do
    clear || true
    status
    echo
    echo "1) Start and open Web"
    echo "2) Start"
    echo "3) Stop"
    echo "4) Restart"
    echo "5) Show log"
    echo "0) Quit"
    echo
    printf "Choose: "
    read -r choice
    case "$choice" in
      1) open_web ;;
      2) start ;;
      3) stop ;;
      4) stop; start ;;
      5) tail -80 "$LOG_FILE" 2>/dev/null || echo "No log yet." ;;
      0) exit 0 ;;
      *) echo "Unknown choice: $choice" ;;
    esac
    echo
    printf "Press Enter to continue..."
    read -r _
  done
}

case "${1:-menu}" in
  start) start ;;
  stop) stop ;;
  restart) stop; start ;;
  status) status ;;
  open) open_web ;;
  menu) menu ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|open|menu}"
    exit 2
    ;;
esac
