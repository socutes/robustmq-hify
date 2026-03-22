#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$ROOT_DIR/.pids"
BACKEND_PID_FILE="$PID_DIR/backend.pid"
FRONTEND_PID_FILE="$PID_DIR/frontend.pid"
TERM_TIMEOUT=15

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

stop_process() {
  local name="$1"
  local pid_file="$2"

  if [[ ! -f "$pid_file" ]]; then
    warn "${name}：PID 文件不存在，跳过"
    return
  fi

  local pid
  pid=$(cat "$pid_file")

  if ! kill -0 "$pid" 2>/dev/null; then
    warn "${name}（PID=${pid}）已不在运行"
    rm -f "$pid_file"
    return
  fi

  info "停止 ${name}（PID=${pid}）..."
  kill -TERM "$pid" 2>/dev/null

  local elapsed=0
  while kill -0 "$pid" 2>/dev/null; do
    sleep 1
    elapsed=$((elapsed + 1))
    if [[ $elapsed -ge $TERM_TIMEOUT ]]; then
      warn "${name} 未在 ${TERM_TIMEOUT}s 内退出，强制 SIGKILL"
      kill -KILL "$pid" 2>/dev/null || true
      break
    fi
  done

  rm -f "$pid_file"
  info "${name} 已停止"
}

stop_process "前端" "$FRONTEND_PID_FILE"
stop_process "后端" "$BACKEND_PID_FILE"

echo
info "✅ Hify 已停止"
