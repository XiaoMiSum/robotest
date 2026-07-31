#!/bin/bash
# scripts/deploy-separate.sh — 分离部署构建（方案A）
# 用法: bash scripts/deploy-separate.sh [--skip-checks] [--skip-tests]
# 产物: dist-deploy/
#   ├── web/                  前端静态资源 → 部署至 Nginx / CDN
#   ├── robotest-server.jar   后端可执行 jar → 独立部署
#   └── nginx.conf.example    Nginx 反向代理配置示例

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/dist-deploy"
FRONTEND_ARGS=()
BACKEND_ARGS=(--prod)

for arg in "$@"; do
  case "$arg" in
    --skip-checks) FRONTEND_ARGS+=(--skip-checks) ;;
    --skip-tests) BACKEND_ARGS+=(--skip-tests) ;;
    *)
      echo "用法: bash scripts/deploy-separate.sh [--skip-checks] [--skip-tests]"
      exit 1
      ;;
  esac
done

echo "========================================"
echo "  RoboTest 分离部署构建（方案A）"
echo "========================================"

# ─── 构建 ────────────────────────────────────────────────────────
bash "$ROOT_DIR/scripts/build-frontend.sh" "${FRONTEND_ARGS[@]}"
bash "$ROOT_DIR/scripts/build-backend.sh" "${BACKEND_ARGS[@]}"

# ─── 汇总产物 ────────────────────────────────────────────────────
echo ""
echo "=== 汇总产物到 dist-deploy/ ==="
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/web"
cp -r "$ROOT_DIR/web/dist/." "$OUTPUT_DIR/web/"
cp "$ROOT_DIR/server/target/robotest-server.jar" "$OUTPUT_DIR/"
cp "$ROOT_DIR/scripts/nginx.conf.example" "$OUTPUT_DIR/"

echo ""
echo -e "${GREEN}分离部署构建完成:${NC}"
echo "  前端静态资源: dist-deploy/web/          → 部署至 Nginx / CDN"
echo "  后端服务:     dist-deploy/robotest-server.jar → java -jar 启动"
echo "  Nginx 示例:   dist-deploy/nginx.conf.example"
