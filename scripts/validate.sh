#!/bin/bash
# scripts/validate.sh — 提交前质量验证脚本
# 用法: bash scripts/validate.sh [--frontend] [--backend] [--all]
# 默认: --all

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; FAILURES=$((FAILURES + 1)); }
warn() { echo -e "${YELLOW}⚠ $1${NC}"; }
FAILURES=0

# ─── Git 提交格式检查 ────────────────────────────────────────────
check_commit_format() {
  echo ""
  echo "=== Git 提交格式检查 ==="

  # 检查未提交的变更
  if ! git diff --quiet HEAD 2>/dev/null; then
    warn "存在未提交的变更，请先 commit 再运行验证"
  fi

  # 检查最近 N 条 commit 的格式
  local N=5
  local BAD=0
  while IFS= read -r msg; do
    # emoji 前缀可选且可能占多个字节（C locale 下按字节匹配），用 + 量化；无 emoji 提交由可选组兜底
    if ! echo "$msg" | grep -qE '^([^[:alnum:][:space:]]+ )?(feat|fix|refactor|style|docs|test|chore|perf|deps|security)\(.+\): .+'; then
      fail "提交格式错误: $msg"
      BAD=1
    fi
  done < <(git log --format="%s" -n "$N" 2>/dev/null)

  if [ "$BAD" -eq 0 ]; then
    pass "最近 $N 条提交格式正确"
  fi
}

# ─── 前端验证 ──────────────────────────────────────────────────
check_frontend() {
  echo ""
  echo "=== 前端验证 ==="

  if [ ! -d "web/node_modules" ]; then
    warn "web/node_modules 不存在，跳过前端验证"
    return
  fi

  echo "--- lint ---"
  if (cd web && pnpm run lint 2>&1); then
    pass "lint 通过"
  else
    fail "lint 失败"
  fi

  echo "--- typecheck ---"
  if (cd web && pnpm run typecheck 2>&1); then
    pass "typecheck 通过"
  else
    fail "typecheck 失败"
  fi

  echo "--- unit tests ---"
  if (cd web && pnpm run test:unit 2>&1); then
    pass "单元测试通过"
  else
    fail "单元测试失败"
  fi
}

# ─── 后端验证 ──────────────────────────────────────────────────
check_backend() {
  echo ""
  echo "=== 后端验证 ==="

  if [ ! -d "server" ]; then
    warn "server/ 目录不存在，跳过后端验证"
    return
  fi

  echo "--- tests ---"
  if (cd server && mvn test -q 2>&1); then
    pass "单元测试通过"
  else
    fail "单元测试失败"
  fi
}

# ─── 契约一致性检查（07 §4.1 / 08 §4.2：openapi-typescript 生成类型漂移校验） ─
# 基线 web/openapi/contract.json 由后端运行期导出（pnpm contract:gen）。
# 校验：用基线重新生成类型并与提交的 types/generated/contract.d.ts 比对，漂移即失败。
# 当前为“可发现”模式：基线缺失时跳过（先报告，不阻断），接入后端后转阻断。
check_contract() {
  echo ""
  echo "=== 前端契约一致性 ==="
  if [ ! -f "web/openapi/contract.json" ]; then
    warn "未找到基线 web/openapi/contract.json（后端未导出或未运行 pnpm contract:gen），跳过契约校验"
    return
  fi
  if [ ! -d "web/node_modules" ] || [ ! -d "web/node_modules/openapi-typescript" ]; then
    warn "openapi-typescript 未安装，跳过契约校验"
    return
  fi
  local TMP_GEN="$TMPDIR/contract.check.gen.d.ts"
  if (cd web && npx openapi-typescript "openapi/contract.json" -o "$TMP_GEN" 2>/dev/null); then
    if diff -q "web/src/types/generated/contract.d.ts" "$TMP_GEN" >/dev/null 2>&1; then
      pass "契约类型与基线一致"
      rm -f "$TMP_GEN"
    else
      fail "契约漂移：src/types/generated/contract.d.ts 与 openapi/contract.json 不一致，请运行 pnpm contract:gen"
      rm -f "$TMP_GEN"
    fi
  else
    warn "契约类型重新生成失败，跳过校验"
  fi
}

# ─── Any 类型检查（前端） ──────────────────────────────────────────
check_any_usage() {
  echo ""
  echo "=== TypeScript any 使用检查 ==="

  if [ ! -d "web/src" ]; then
    return
  fi

  # 排除 .d.ts 文件和 node_modules
  local ANY_COUNT
  ANY_COUNT=$(grep -r ': any\b\|as any\b\|<any>' web/src --include='*.ts' --include='*.vue' --exclude='*.d.ts' 2>/dev/null | wc -l | tr -d ' ')

  if [ "$ANY_COUNT" -gt 0 ]; then
    fail "发现 $ANY_COUNT 处 any 类型使用（C1 违规）"
  else
    pass "无 any 类型使用"
  fi
}

# ─── 主入口 ──────────────────────────────────────────────────────
MODE="${1:---all}"

echo "========================================"
echo "  RoboTest 质量验证"
echo "========================================"

check_commit_format

case "$MODE" in
  --frontend|-f)
    check_frontend
    check_any_usage
    check_contract
    ;;
  --backend|-b)
    check_backend
    ;;
  --all|-a|"")
    check_frontend
    check_any_usage
    check_contract
    check_backend
    ;;
  *)
    echo "用法: bash scripts/validate.sh [--frontend|--backend|--all]"
    exit 1
    ;;
esac

echo ""
echo "========================================"
if [ "$FAILURES" -gt 0 ]; then
  echo -e "${RED}验证完成: $FAILURES 项失败${NC}"
  exit 1
else
  echo -e "${GREEN}验证完成: 全部通过${NC}"
  exit 0
fi
