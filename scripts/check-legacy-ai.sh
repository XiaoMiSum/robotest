#!/usr/bin/env bash
# 清理门禁（AI Task 迁移 2.7-Grep，S6 收口全绿；S0 交付脚本草稿）
#
# 判据：旧概念 grep 零命中。命令源自 docs/06-implementation-plan/06-ai-task-migration-plan.md
# 2.7 清理门禁（其文注明「命令为示意，排除集以实际目录为准」），本脚本落地为可执行判据：
#   - 排除「同名不同族」的保留类（接口测试域 DispatchableTask 族 TaskHandler、助手会话 cancel 端点）
#   - 排除「新架构合法字面量」（用量 success/failed、执行态 succeeded、提案确认 pending）
#
# 使用：bash scripts/check-legacy-ai.sh   （退出码 0 = 全绿；1 = 有残留，输出明细）
# 状态基线：B/C/D/E1/E2 绿；A 仅剩 `artifactKey` 命名残留（S6 实清）。

set -u
cd "$(dirname "$0")/.."

FAIL=0

report() { # $1=结论 0|1  $2=标题  $3=明细文件
  if [ "$1" -eq 0 ]; then
    echo "✓ $2"
  else
    echo "✗ $2"
    sed 's/^/    /' "$3"
    FAIL=1
  fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# --- A. 旧任务引擎符号（server main，排除 apitest 同名不同族与纯注释提及） ---
grep -rn --include='*.java' -E \
  "TaskHandler|AiTaskExecutor|AiArtifactConfirm|artifactKey|retryOfTaskId|casStart|casSucceed|casFail|casCancel|reportProgress|failStale" \
  server/src/main \
  | grep -v "/apitest/" \
  | grep -v "^\S*: *\*" \
  > "$TMP/a.txt" || true
# 注：仅行首注释被静态豁免仍需 S4/S6 实清——javadoc 提及旧名（如 TaskTypeAdapter 类注释）一并要求改写。
report "$( [ -s "$TMP/a.txt" ] && echo 1 || echo 0 )" \
  "A. 旧引擎符号（TaskHandler/AiTaskExecutor/AiArtifactConfirm/artifactKey/cas*/…）= 0" "$TMP/a.txt"

# --- B1. 旧五态常量与 cancelled 字面量（AI 语境） ---
grep -rn --include='*.java' -E "AiTaskStatus|\"cancelled\"" \
  server/src/main/java/io/github/xiaomisum/robotest/{service/ai,repository/ai,framework/common,controller/ai} \
  > "$TMP/b1.txt" || true
report "$( [ -s "$TMP/b1.txt" ] && echo 1 || echo 0 )" \
  "B1. 旧五态常量 AiTaskStatus 与 \"cancelled\" 字面量 = 0" "$TMP/b1.txt"

# --- B2. 旧任务态字面量（排除新架构合法命中：用量 success/failed、执行态、提案确认 pending） ---
grep -rn --include='*.java' -E "\"pending\"|\"succeeded\"|\"failed\"" \
  server/src/main/java/io/github/xiaomisum/robotest/{service/ai,repository/ai,framework/common} \
  | grep -vE "AiModelClient|AiEmbeddingClient|AgentExecutionRunner|AiTaskExecutionMapper|Constants.java|AiProposalConfirmStatus|ProposalDraft" \
  | grep -v "confirmStatus" \
  > "$TMP/b2.txt" || true
report "$( [ -s "$TMP/b2.txt" ] && echo 1 || echo 0 )" \
  "B2. 旧任务态字面量（排除用量/执行态/提案确认合法命中）= 0" "$TMP/b2.txt"

# --- C. 旧任务端点（controller/ai；助手会话 cancel 为另一资源，豁免） ---
grep -rn -E "artifacts/confirm|/cancel|/retry" \
  server/src/main/java/io/github/xiaomisum/robotest/controller/ai \
  | grep -v "AiAssistantController" \
  > "$TMP/c.txt" || true
report "$( [ -s "$TMP/c.txt" ] && echo 1 || echo 0 )" \
  "C. 旧端点 artifacts/confirm、/cancel、/retry = 0" "$TMP/c.txt"

# --- D. DDL 残留 ---
grep -rn "ai_artifact_confirm\|retry_of_task_id" server/src/main/resources > "$TMP/d.txt" || true
report "$( [ -s "$TMP/d.txt" ] && echo 1 || echo 0 )" \
  "D. DDL 残留 ai_artifact_confirm / retry_of_task_id = 0" "$TMP/d.txt"

# --- E1. 前端旧类型与旧端点函数（web，S5 后全绿） ---
grep -rn -E "AiArtifact|cancelAiTask|retryAiTask|fetchAiArtifact|isTerminalTaskStatus\(.*succeeded" \
  web/src --include='*.ts' --include='*.vue' > "$TMP/e1.txt" || true
report "$( [ -s "$TMP/e1.txt" ] && echo 1 || echo 0 )" \
  "E1. 前端 AiArtifact 族与 cancel/retry 端点函数 = 0" "$TMP/e1.txt"

# --- E2. 前端任务域 progress / phase 残留（S5 后全绿；el-progress 为组件标签非任务字段，豁免） ---
grep -rn -E "\bphase\b|\bprogress\b" \
  web/src/stores/aiTask.ts web/src/pages/project/ai web/src/composables/project/ai \
  --include='*.ts' --include='*.vue' | grep -v "el-progress" > "$TMP/e2.txt" 2>/dev/null || true
report "$( [ -s "$TMP/e2.txt" ] && echo 1 || echo 0 )" \
  "E2. 前端任务域 progress / phase = 0" "$TMP/e2.txt"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "清理门禁：全部通过"
  exit 0
fi
echo "清理门禁：存在残留（S6 收口前 A 预期未绿）"
exit 1
