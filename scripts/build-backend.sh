#!/bin/bash
# scripts/build-backend.sh — 后端构建脚本
# 用法: bash scripts/build-backend.sh [--dev|--prod|--merged] [--skip-tests]
# 默认: --prod
# 产物: server/target/robotest-server.jar

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROFILE="prod"
SKIP_TESTS=0

for arg in "$@"; do
  case "$arg" in
    --dev) PROFILE="dev" ;;
    --prod) PROFILE="prod" ;;
    --merged) PROFILE="merged" ;;
    --skip-tests) SKIP_TESTS=1 ;;
    *)
      echo "用法: bash scripts/build-backend.sh [--dev|--prod|--merged] [--skip-tests]"
      exit 1
      ;;
  esac
done

echo "========================================"
echo "  RoboTest 后端构建 (profile: $PROFILE)"
echo "========================================"

cd "$ROOT_DIR/server"

MVN_ARGS=(clean package "-P$PROFILE")
if [ "$SKIP_TESTS" -eq 1 ]; then
  MVN_ARGS+=("-DskipTests")
fi

echo ""
echo "=== mvn ${MVN_ARGS[*]} ==="
mvn "${MVN_ARGS[@]}"

echo ""
echo -e "${GREEN}后端构建完成: server/target/robotest-server.jar${NC}"
