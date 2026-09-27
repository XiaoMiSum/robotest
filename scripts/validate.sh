#!/bin/bash
# scripts/validate.sh — 质量验证全量编排器（供人工 / CI 全量门禁）
# 各端 AGENT 提交前只运行对应端脚本，不运行本文件：
#   文档端: bash scripts/validate-docs.sh    （C7 提交格式 + 文档检查）
#   前端  : bash scripts/validate-web.sh     （C7 + 工具链 + lint + typecheck + 测试/覆盖率 + any + 契约）
#   后端  : bash scripts/validate-backend.sh （C7 + mvn test）
# 用法: bash scripts/validate.sh [--docs|--frontend|--backend|--all]
# 默认: --all（C7 由本编排器统一检查一次，子脚本经 VALIDATE_SKIP_COMMIT_CHECK 跳过）

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODE="${1:---all}"

case "$MODE" in
  --docs)
    exec bash "$SCRIPT_DIR/validate-docs.sh"
    ;;
  --frontend|-f)
    exec bash "$SCRIPT_DIR/validate-web.sh"
    ;;
  --backend|-b)
    exec bash "$SCRIPT_DIR/validate-backend.sh"
    ;;
  --all|-a|"")
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    NC='\033[0m'

    pass() { echo -e "${GREEN}✓ $1${NC}"; }
    fail() { echo -e "${RED}✗ $1${NC}"; FAILURES=$((FAILURES + 1)); }
    warn() { echo -e "${YELLOW}⚠ $1${NC}"; }
    FAILURES=0

    . "$SCRIPT_DIR/lib/commit-format.sh"

    echo "========================================"
    echo "  RoboTest 质量验证 — 全量编排"
    echo "========================================"

    check_commit_format

    SUB_FAIL=0
    for s in validate-docs.sh validate-web.sh validate-backend.sh; do
      if ! VALIDATE_SKIP_COMMIT_CHECK=1 bash "$SCRIPT_DIR/$s"; then
        SUB_FAIL=$((SUB_FAIL + 1))
      fi
    done

    echo ""
    echo "========================================"
    TOTAL=$((FAILURES + SUB_FAIL))
    if [ "$TOTAL" -gt 0 ]; then
      echo -e "${RED}验证完成: $TOTAL 项失败（提交格式/检查 ${FAILURES} 项，未通过端脚本 ${SUB_FAIL} 个）${NC}"
      exit 1
    else
      echo -e "${GREEN}验证完成: 全部通过${NC}"
      exit 0
    fi
    ;;
  *)
    echo "用法: bash scripts/validate.sh [--docs|--frontend|--backend|--all]"
    exit 1
    ;;
esac
