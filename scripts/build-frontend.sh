#!/bin/bash
# scripts/build-frontend.sh — 前端构建脚本
# 用法: bash scripts/build-frontend.sh [--skip-checks]
# 产物: web/dist/
# --skip-checks: 跳过 lint / typecheck / test，仅构建（适用于本地快速验证）

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SKIP_CHECKS=0

case "${1:-}" in
  --skip-checks) SKIP_CHECKS=1 ;;
  "") ;;
  *)
    echo "用法: bash scripts/build-frontend.sh [--skip-checks]"
    exit 1
    ;;
esac

echo "========================================"
echo "  RoboTest 前端构建"
echo "========================================"

cd "$ROOT_DIR/web"

# ─── 依赖安装 ────────────────────────────────────────────────────
echo ""
echo "=== 安装依赖 (frozen-lockfile) ==="
pnpm install --frozen-lockfile

# ─── 质量门禁 ────────────────────────────────────────────────────
if [ "$SKIP_CHECKS" -eq 0 ]; then
  echo ""
  echo "=== lint ==="
  pnpm run lint
  echo ""
  echo "=== typecheck ==="
  pnpm run typecheck
  echo ""
  echo "=== unit tests ==="
  pnpm run test:unit
else
  echo ""
  echo "--skip-checks: 跳过 lint / typecheck / test"
fi

# ─── 构建 ────────────────────────────────────────────────────────
echo ""
echo "=== 构建 ==="
pnpm run build

echo ""
echo -e "${GREEN}前端构建完成: web/dist/${NC}"
