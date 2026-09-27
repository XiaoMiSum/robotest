#!/bin/bash
# scripts/validate-web.sh — 前端（web）质量验证（web/AGENT 提交前只运行本脚本）
# 用法: bash scripts/validate-web.sh
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

# ─── 工具链版本一致性（QA-001：按锁定版本复现安装） ───────────────
# Node 主版本须与 web/.nvmrc 一致，pnpm 须与 package.json packageManager 精确一致；
# 不一致即失败：新环境按锁定安装（nvm use / corepack enable），升级锁定须同步改文件。
check_toolchain() {
  echo ""
  echo "=== 工具链版本 ==="
  local nvmrc="web/.nvmrc" pkg="web/package.json"
  local want_node want_pnpm got_node got_pnpm

  if [ ! -f "$nvmrc" ] || [ ! -f "$pkg" ]; then
    fail "缺少工具链锁定文件（$nvmrc、$pkg 之一）"
    return
  fi
  want_node="$(tr -d '[:space:]' < "$nvmrc")"
  want_pnpm="$(sed -n 's/.*"packageManager": *"pnpm@\([^"]*\)".*/\1/p' "$pkg")"
  if [ -z "$want_node" ] || [ -z "$want_pnpm" ]; then
    fail "锁定文件内容不完整（.nvmrc 或 packageManager 字段）"
    return
  fi

  if ! command -v node >/dev/null 2>&1; then
    fail "node 未安装（缺少依赖），无法按锁定版本校验"
    return
  fi
  got_node="$(node --version | sed 's/^v//')"
  if [ "${want_node%%.*}" != "${got_node%%.*}" ]; then
    fail "Node 主版本不一致：锁定 ${want_node}（.nvmrc），当前 v${got_node}——执行 cd web && nvm use"
  else
    pass "Node 主版本一致（锁定 ${want_node}，当前 v${got_node}）"
  fi

  if ! command -v pnpm >/dev/null 2>&1; then
    fail "pnpm 未安装（缺少依赖），无法按锁定版本校验"
    return
  fi
  got_pnpm="$(pnpm --version)"
  if [ "$want_pnpm" != "$got_pnpm" ]; then
    fail "pnpm 版本不一致：锁定 ${want_pnpm}（packageManager），当前 ${got_pnpm}——corepack enable 或安装锁定版本"
  else
    pass "pnpm 版本与 packageManager 一致（${got_pnpm}）"
  fi
}

# ─── 前端验证 ──────────────────────────────────────────────────
check_frontend() {
  echo ""
  echo "=== 前端验证 ==="

  # 缺依赖必须返回非零（QA-004），跳过会让门禁形同虚设
  if [ ! -d "web/node_modules" ]; then
    fail "web/node_modules 不存在（缺少依赖），请先执行 pnpm install"
    return
  fi
  if ! command -v pnpm >/dev/null 2>&1; then
    fail "pnpm 未安装（缺少依赖）"
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

  echo "--- unit tests + coverage (C8) ---"
  # test:unit 已内联 --coverage，低于 70% 阈值即失败（QA-005）
  if (cd web && pnpm run test:unit 2>&1); then
    pass "单元测试与覆盖率通过（C8 ≥ 70%）"
  else
    fail "单元测试或覆盖率阈值失败（C8）"
  fi
}

# ─── 契约一致性检查（07 §4.1 / 08 §4.2：openapi-typescript 生成类型漂移校验） ─
# 基线 web/openapi/contract.json 由后端运行期导出（pnpm contract:gen）。
# 校验：用基线重新生成类型并与提交的 types/generated/contract.d.ts 比对，漂移即失败。
# 基线缺失、校验依赖缺失、重新生成失败、类型漂移均按失败返回非零（QA-008 转阻断）。
# 本脚本只读校验，不修改任何源代码（lint 为 check-only，lint:fix 独立且不被本脚本调用）。
check_contract() {
  echo ""
  echo "=== 前端契约一致性 ==="
  if [ ! -f "web/openapi/contract.json" ]; then
    fail "缺少 OpenAPI 基线 web/openapi/contract.json（QA-008 阻断），请运行后端后执行 pnpm contract:gen"
    return
  fi
  # 校验依赖缺失按失败处理（QA-004）：跳过会让契约门禁形同虚设
  if [ ! -d "web/node_modules" ] || [ ! -d "web/node_modules/openapi-typescript" ]; then
    fail "openapi-typescript 未安装（缺少依赖），请先执行 pnpm install"
    return
  fi
  local TMP_GEN="${TMPDIR:-/tmp}/contract.check.gen.d.ts"
  # 重新生成失败属契约失败，必须返回非零（QA-004），不得静默跳过
  if (cd web && npx openapi-typescript "openapi/contract.json" -o "$TMP_GEN" 2>/dev/null); then
    if diff -q "web/src/types/generated/contract.d.ts" "$TMP_GEN" >/dev/null 2>&1; then
      pass "契约类型与基线一致"
    else
      fail "契约漂移：src/types/generated/contract.d.ts 与 openapi/contract.json 不一致，请运行 pnpm contract:gen"
    fi
  else
    fail "契约类型重新生成失败（契约校验失败），请检查 openapi/contract.json 与 openapi-typescript"
  fi
  rm -f "$TMP_GEN"
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

echo "========================================"
echo "  RoboTest 质量验证 — 前端（web）"
echo "========================================"

if [ "${VALIDATE_SKIP_COMMIT_CHECK:-0}" != "1" ]; then
  check_commit_format
fi
check_toolchain
check_frontend
check_any_usage
check_contract

echo ""
echo "========================================"
if [ "$FAILURES" -gt 0 ]; then
  echo -e "${RED}验证完成: $FAILURES 项失败${NC}"
  exit 1
else
  echo -e "${GREEN}验证完成: 全部通过${NC}"
  exit 0
fi
