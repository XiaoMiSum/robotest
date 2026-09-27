#!/bin/bash
# scripts/validate-backend.sh — 后端（server）质量验证（server/AGENT 提交前只运行本脚本）
# 用法: bash scripts/validate-backend.sh
# C7 提交格式检查默认执行；validate.sh 编排全量时经 VALIDATE_SKIP_COMMIT_CHECK=1 统一只查一次。

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; FAILURES=$((FAILURES + 1)); }
warn() { echo -e "${YELLOW}⚠ $1${NC}"; }
FAILURES=0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/commit-format.sh"

# ─── 后端验证 ──────────────────────────────────────────────────
check_backend() {
  echo ""
  echo "=== 后端验证 ==="

  if [ ! -d "server" ]; then
    warn "server/ 目录不存在，跳过后端验证"
    return
  fi
  # 缺依赖必须返回非零（QA-004）
  if ! command -v mvn >/dev/null 2>&1; then
    fail "mvn 未安装（缺少依赖）"
    return
  fi

  echo "--- tests ---"
  if (cd server && mvn test -q 2>&1); then
    pass "单元测试通过"
  else
    fail "单元测试失败"
  fi
}

echo "========================================"
echo "  RoboTest 质量验证 — 后端（server）"
echo "========================================"

if [ "${VALIDATE_SKIP_COMMIT_CHECK:-0}" != "1" ]; then
  check_commit_format
fi
check_backend

echo ""
echo "========================================"
if [ "$FAILURES" -gt 0 ]; then
  echo -e "${RED}验证完成: $FAILURES 项失败${NC}"
  exit 1
else
  echo -e "${GREEN}验证完成: 全部通过${NC}"
  exit 0
fi
