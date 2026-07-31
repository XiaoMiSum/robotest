#!/bin/bash
# scripts/deploy-merged.sh — 合并打包部署构建（方案B）
# 用法: bash scripts/deploy-merged.sh [--skip-checks] [--skip-tests]
# 步骤: 前端构建 → 复制 dist 到 server/src/main/resources/static/ → mvn package -Pmerged
# 产物: server/target/robotest-server.jar（内嵌前端资源，单进程部署）

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
STATIC_DIR="$ROOT_DIR/server/src/main/resources/static"
FRONTEND_ARGS=()
BACKEND_ARGS=(--merged)

for arg in "$@"; do
  case "$arg" in
    --skip-checks) FRONTEND_ARGS+=(--skip-checks) ;;
    --skip-tests) BACKEND_ARGS+=(--skip-tests) ;;
    *)
      echo "用法: bash scripts/deploy-merged.sh [--skip-checks] [--skip-tests]"
      exit 1
      ;;
  esac
done

echo "========================================"
echo "  RoboTest 合并打包部署构建（方案B）"
echo "========================================"

# ─── 前端构建 ────────────────────────────────────────────────────
bash "$ROOT_DIR/scripts/build-frontend.sh" "${FRONTEND_ARGS[@]}"

# ─── 复制前端产物 ────────────────────────────────────────────────
echo ""
echo "=== 复制 web/dist/ → server/src/main/resources/static/ ==="
# 先清空再复制，避免残留上一次构建的带 hash 文件名的旧资源
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -r "$ROOT_DIR/web/dist/." "$STATIC_DIR/"

if [ ! -f "$STATIC_DIR/index.html" ]; then
  echo -e "${RED}✗ 复制失败: static/index.html 不存在${NC}"
  exit 1
fi

# ─── 后端构建（merged profile 会校验 static/index.html 存在） ────
bash "$ROOT_DIR/scripts/build-backend.sh" "${BACKEND_ARGS[@]}"

echo ""
echo -e "${GREEN}合并打包完成: server/target/robotest-server.jar（含前端资源）${NC}"
echo "  启动: java -jar server/target/robotest-server.jar"
