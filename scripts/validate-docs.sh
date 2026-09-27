#!/bin/bash
# scripts/validate-docs.sh — 文档端质量验证（docs/AGENT 提交前只运行本脚本）
# 用法: bash scripts/validate-docs.sh
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

# ─── 文档检查（GOV-008：链接、元信息、规则编号） ────────────────
# 校验范围见 scripts/check-docs.mjs 顶部注释；缺 node 按缺少依赖返回非零（QA-004）。
check_docs() {
  echo ""
  echo "=== 文档检查 ==="
  if ! command -v node >/dev/null 2>&1; then
    fail "node 未安装（缺少依赖），无法执行文档检查"
    return
  fi
  if node "$SCRIPT_DIR/check-docs.mjs"; then
    pass "文档链接、元信息与规则编号检查通过"
  else
    fail "文档检查失败（断链、旧锚点、元信息缺失或未登记规则）"
  fi
}

echo "========================================"
echo "  RoboTest 质量验证 — 文档端"
echo "========================================"

if [ "${VALIDATE_SKIP_COMMIT_CHECK:-0}" != "1" ]; then
  check_commit_format
fi
check_docs

echo ""
echo "========================================"
if [ "$FAILURES" -gt 0 ]; then
  echo -e "${RED}验证完成: $FAILURES 项失败${NC}"
  exit 1
else
  echo -e "${GREEN}验证完成: 全部通过${NC}"
  exit 0
fi
