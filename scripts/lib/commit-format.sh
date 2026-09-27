# scripts/lib/commit-format.sh — C7 提交格式检查（供 validate-* 脚本 source）
# 依赖调用方先定义：颜色变量（RED/GREEN/YELLOW/NC）、pass/fail/warn 与 FAILURES 计数。

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
