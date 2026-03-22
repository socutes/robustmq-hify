#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$ROOT_DIR/.pids"
BACKEND_PID_FILE="$PID_DIR/backend.pid"
FRONTEND_PID_FILE="$PID_DIR/frontend.pid"
BACKEND_LOG="$ROOT_DIR/logs/backend.log"
FRONTEND_LOG="$ROOT_DIR/logs/frontend.log"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
HEALTH_URL="http://localhost:${BACKEND_PORT}/api/v1/health"
HEALTH_TIMEOUT=60

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
die()   { error "$*"; exit 1; }

mkdir -p "$PID_DIR" "$(dirname "$BACKEND_LOG")"

# ── 检查依赖命令 ──────────────────────────────────────────────
for cmd in mvn node npm curl nc; do
  command -v "$cmd" &>/dev/null || die "缺少命令：$cmd"
done

# ── 检查 MySQL ────────────────────────────────────────────────
info "检查 MySQL ${DB_HOST}:${DB_PORT} ..."
nc -z -w3 "$DB_HOST" "$DB_PORT" 2>/dev/null \
  || die "MySQL 不可达（${DB_HOST}:${DB_PORT}），请先启动 MySQL"
info "MySQL 可用"

# ── 检查 Redis ────────────────────────────────────────────────
info "检查 Redis ${REDIS_HOST}:${REDIS_PORT} ..."
nc -z -w3 "$REDIS_HOST" "$REDIS_PORT" 2>/dev/null \
  || die "Redis 不可达（${REDIS_HOST}:${REDIS_PORT}），请先启动 Redis"
info "Redis 可用"

# ── 构建后端 ──────────────────────────────────────────────────
info "构建后端 ..."
mvn -f "$ROOT_DIR/pom.xml" clean package -DskipTests -q \
  || die "后端构建失败"
info "后端构建完成"

JAR=$(find "$ROOT_DIR/hify-app/target" -maxdepth 1 -name "*.jar" ! -name "*sources*" | head -1)
[[ -f "$JAR" ]] || die "找不到后端 jar，构建可能未成功"

# ── 启动后端 ──────────────────────────────────────────────────
info "启动后端 ..."
nohup java -jar "$JAR" \
  --server.port="$BACKEND_PORT" \
  > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$BACKEND_PID_FILE"
info "后端进程 PID=${BACKEND_PID}，日志：$BACKEND_LOG"

# ── 等待健康检查 ──────────────────────────────────────────────
info "等待后端就绪（最多 ${HEALTH_TIMEOUT}s）..."
elapsed=0
until curl -sf "$HEALTH_URL" &>/dev/null; do
  sleep 2
  elapsed=$((elapsed + 2))
  if [[ $elapsed -ge $HEALTH_TIMEOUT ]]; then
    error "后端启动超时，最后几行日志："
    tail -20 "$BACKEND_LOG" >&2
    die "后端未能在 ${HEALTH_TIMEOUT}s 内就绪"
  fi
  echo -n "."
done
echo
info "后端已就绪"

# ── 安装前端依赖（仅首次或 package.json 变更后）──────────────
if [[ ! -d "$ROOT_DIR/hify-web/node_modules" ]]; then
  info "安装前端依赖 ..."
  npm --prefix "$ROOT_DIR/hify-web" install --silent \
    || die "前端依赖安装失败"
fi

# ── 启动前端 ──────────────────────────────────────────────────
info "启动前端开发服务器 ..."
nohup npm --prefix "$ROOT_DIR/hify-web" run dev \
  > "$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!
echo "$FRONTEND_PID" > "$FRONTEND_PID_FILE"
info "前端进程 PID=${FRONTEND_PID}，日志：$FRONTEND_LOG"

echo
info "✅ Hify 已启动"
info "   后端：http://localhost:${BACKEND_PORT}"
info "   前端：http://localhost:5173"
