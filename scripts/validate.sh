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

# ─── Git 提交格式检查（C7 / 02-workflow §2） ─────────────────────
# C7 格式: <emoji> <type>(<scope>): <description>
# 校验: emoji 必填且与 type 匹配（02-workflow §2.1 映射表）、type 取自该表、
# scope 为小写标识（§2.2 关键字或业务域，可带 / 子路径）、description 非占位文案。
# 合并提交由托管平台生成，不适用 C7，跳过。

# 去掉变体选择符 U+FE0F，使 ♻ 与 ♻️、⚡ 与 ⚡️ 等价比较
strip_vs16() { printf '%s' "$1" | sed $'s/\357\270\217//g'; }

# type 对应的标准 emoji（02-workflow §2.1）
emoji_for() {
  case "$1" in
    feat) printf '✨' ;;
    fix) printf '🐛' ;;
    refactor) printf '♻' ;;
    style) printf '💄' ;;
    docs) printf '📝' ;;
    test) printf '✅' ;;
    chore) printf '🔧' ;;
    perf) printf '⚡' ;;
    deps) printf '⬆' ;;
    security) printf '🔒' ;;
    *) return 1 ;;
  esac
}

# 校验单条提交标题；通过无输出返回 0，失败输出原因返回 1
validate_commit_msg() {
  local msg="$1" emoji type scope desc noemoji re scope_re expect lower desc_re
  re='^([^[:space:]]+)[[:space:]]+([a-z]+)\(([^)]+)\):[[:space:]]*(.+)$'
  if [[ "$msg" =~ $re ]]; then
    emoji="${BASH_REMATCH[1]}"
    type="${BASH_REMATCH[2]}"
    scope="${BASH_REMATCH[3]}"
    desc="${BASH_REMATCH[4]}"
  else
    # 无 emoji 的提交单独归因，便于按 C7 修正
    noemoji='^([a-z]+)\(([^)]+)\):[[:space:]]*(.+)$'
    if [[ "$msg" =~ $noemoji ]]; then
      echo "缺少 emoji 前缀（C7: <emoji> <type>(<scope>): <description>）"
      return 1
    fi
    echo "不符合 C7 格式 <emoji> <type>(<scope>): <description>"
    return 1
  fi

  if ! expect="$(emoji_for "$type")"; then
    echo "未知 type: ${type}（允许: feat|fix|refactor|style|docs|test|chore|perf|deps|security）"
    return 1
  fi
  if [[ "$(strip_vs16 "$emoji")" != "$(strip_vs16 "$expect")" ]]; then
    echo "emoji 与 type 不匹配: ${type} 应为 ${expect}，实际为 ${emoji}"
    return 1
  fi
  scope_re='^[a-z][a-z0-9-]*(/[a-z][a-z0-9-]*)*$'
  if [[ ! "$scope" =~ $scope_re ]]; then
    echo "scope 非法: ${scope}（小写标识，可带 / 子路径）"
    return 1
  fi
  lower="$(printf '%s' "$desc" | tr '[:upper:]' '[:lower:]')"
  desc_re='^(wip|tmp|temp|fixup|squash|misc|xxx+)([[:space:][:punct:]].*)?$|^commit all pending.*$'
  if [[ "$lower" =~ $desc_re ]]; then
    echo "占位/临时描述不符合 C7: $desc"
    return 1
  fi
  return 0
}

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
  local msg err
  while IFS= read -r msg; do
    if ! err="$(validate_commit_msg "$msg")"; then
      fail "提交格式错误: ${msg}（${err}）"
      BAD=1
    fi
  done < <(git log --no-merges --format='%s' -n "$N" 2>/dev/null)

  if [ "$BAD" -eq 0 ]; then
    pass "最近 $N 条提交格式正确"
  fi
}

# ─── 文档检查（GOV-008：链接、元信息、规则编号） ────────────────
# 校验范围见 scripts/check-docs.mjs 顶部注释；缺 node 按缺少依赖返回非零（QA-004）。
check_docs() {
  echo ""
  echo "=== 文档检查 ==="
  if ! command -v node >/dev/null 2>&1; then
    fail "node 未安装（缺少依赖），无法执行文档检查"
    return
  fi
  if node scripts/check-docs.mjs; then
    pass "文档链接、元信息与规则编号检查通过"
  else
    fail "文档检查失败（断链、旧锚点、元信息缺失或未登记规则）"
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

# ─── 契约一致性检查（07 §4.1 / 08 §4.2：openapi-typescript 生成类型漂移校验） ─
# 基线 web/openapi/contract.json 由后端运行期导出（pnpm contract:gen）。
# 校验：用基线重新生成类型并与提交的 types/generated/contract.d.ts 比对，漂移即失败。
# 当前为“可发现”模式：基线缺失时跳过（先报告，不阻断），接入后端后转阻断（QA-008）。
# 校验依赖缺失、重新生成失败、类型漂移均按失败返回非零（QA-004）。
# 本脚本只读校验，不修改任何源代码（lint 为 check-only，lint:fix 独立且不被本脚本调用）。
check_contract() {
  echo ""
  echo "=== 前端契约一致性 ==="
  if [ ! -f "web/openapi/contract.json" ]; then
    warn "未找到基线 web/openapi/contract.json（后端未导出或未运行 pnpm contract:gen），跳过契约校验"
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

# ─── 主入口 ──────────────────────────────────────────────────────
MODE="${1:---all}"

echo "========================================"
echo "  RoboTest 质量验证"
echo "========================================"

check_commit_format
check_docs

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
