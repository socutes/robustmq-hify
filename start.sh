#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/hify.pid"
LOG_FILE="$ROOT_DIR/logs/hify.log"
JAR="$ROOT_DIR/hify-app.jar"
CONFIG="$ROOT_DIR/application.yml"

BACKEND_PORT="${SERVER_PORT:-8080}"
HEALTH_URL="http://localhost:${BACKEND_PORT}/api/v1/health"
HEALTH_TIMEOUT=90

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
die()   { error "$*"; exit 1; }

# ── 前置检查 ──────────────────────────────────────────────────
[[ -f "$JAR" ]]    || die "找不到 $JAR，请先构建或解压发布包"
command -v java &>/dev/null || die "未找到 java 命令，请安装 JRE 17+"

java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
[[ "$java_version" -ge 17 ]] 2>/dev/null || die "需要 Java 17+，当前版本：$java_version"

if [[ -f "$PID_FILE" ]]; then
  pid=$(cat "$PID_FILE")
  if kill -0 "$pid" 2>/dev/null; then
    warn "Hify 已在运行（PID=${pid}），如需重启请先执行 stop.sh"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

mkdir -p "$(dirname "$LOG_FILE")"

# ── 加载 .env（可选）─────────────────────────────────────────
if [[ -f "$ROOT_DIR/.env" ]]; then
  info "加载 .env 配置文件"
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

# ── 构造 JVM 参数 ─────────────────────────────────────────────
JVM_OPTS="${JVM_OPTS:--Xms256m -Xmx512m}"

SPRING_ARGS=(
  "--server.port=${SERVER_PORT:-8080}"
)

# 外部 application.yml 优先级高于 jar 内置配置
if [[ -f "$CONFIG" ]]; then
  SPRING_ARGS+=("--spring.config.location=file:${CONFIG}")
fi

# ── 启动 ──────────────────────────────────────────────────────
info "启动 Hify ..."
info "  JAR     : $JAR"
info "  日志    : $LOG_FILE"
info "  JVM     : $JVM_OPTS"

# shellcheck disable=SC2086
nohup java $JVM_OPTS -jar "$JAR" "${SPRING_ARGS[@]}" \
  >> "$LOG_FILE" 2>&1 &

PID=$!
echo "$PID" > "$PID_FILE"
info "进程 PID=$PID，日志：$LOG_FILE"

# ── 健康检查 ──────────────────────────────────────────────────
info "等待服务就绪（最多 ${HEALTH_TIMEOUT}s）..."
elapsed=0
until curl -sf "$HEALTH_URL" &>/dev/null; do
  if ! kill -0 "$PID" 2>/dev/null; then
    error "进程已退出，最后几行日志："
    tail -30 "$LOG_FILE" >&2
    rm -f "$PID_FILE"
    die "Hify 启动失败"
  fi
  sleep 2
  elapsed=$((elapsed + 2))
  if [[ $elapsed -ge $HEALTH_TIMEOUT ]]; then
    error "启动超时，最后几行日志："
    tail -30 "$LOG_FILE" >&2
    die "Hify 未能在 ${HEALTH_TIMEOUT}s 内就绪"
  fi
  printf "."
done
echo

info "✅ Hify 启动成功：http://localhost:${SERVER_PORT:-8080}"
