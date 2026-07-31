#!/bin/bash
# scripts/dev.sh — 一键启动前后端开发服务器
# 用法: bash scripts/dev.sh
# 前端: Vite dev server (5173)，/api 与 /ws 代理至后端
# 后端: Spring Boot (8080)，dev profile

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_PID=""

# 退出时回收后台的后端进程，避免残留 Java 进程占用 8080
cleanup() {
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo ""
    echo "正在停止后端进程 (PID $BACKEND_PID)..."
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "========================================"
echo "  RoboTest 开发环境启动"
echo "========================================"

# ─── 后端（后台） ────────────────────────────────────────────────
echo ""
echo "=== 启动后端 (8080, dev profile) ==="
(cd "$ROOT_DIR/server" && mvn spring-boot:run -Pdev) &
BACKEND_PID=$!
echo -e "${GREEN}后端已后台启动 (PID $BACKEND_PID)${NC}"

# ─── 前端（前台） ────────────────────────────────────────────────
echo ""
echo "=== 启动前端 (5173) ==="
if [ ! -d "$ROOT_DIR/web/node_modules" ]; then
  echo "web/node_modules 不存在，先安装依赖..."
  (cd "$ROOT_DIR/web" && pnpm install)
fi
cd "$ROOT_DIR/web" && pnpm run dev
