#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/hify.pid"
TERM_TIMEOUT=30

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

if [[ ! -f "$PID_FILE" ]]; then
  warn "PID 文件不存在（$PID_FILE），Hify 可能未在运行"
  exit 0
fi

PID=$(cat "$PID_FILE")

if ! kill -0 "$PID" 2>/dev/null; then
  warn "进程 PID=${PID} 已不在运行"
  rm -f "$PID_FILE"
  exit 0
fi

info "正在停止 Hify（PID=${PID}）..."
kill -TERM "$PID" 2>/dev/null

elapsed=0
while kill -0 "$PID" 2>/dev/null; do
  sleep 1
  elapsed=$((elapsed + 1))
  if [[ $elapsed -ge $TERM_TIMEOUT ]]; then
    warn "进程未在 ${TERM_TIMEOUT}s 内退出，强制终止"
    kill -KILL "$PID" 2>/dev/null || true
    break
  fi
  printf "."
done
echo

rm -f "$PID_FILE"
info "✅ Hify 已停止"
