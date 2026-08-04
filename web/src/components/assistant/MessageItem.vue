<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { AiMessageRole, AiMinderCommandsEvent } from '@/types'
import MarkdownView from '@/components/common/MarkdownView.vue'
import { filterAssistantLinks, collectRegisteredPrefixes } from '@/utils/assistantLinkWhitelist'
import { useAssistantContextStore } from '@/stores/assistantContext'
import router from '@/router'
import type { DslPlan, DslPlanResult } from '@/components/project/minder/ai/dslRunner'
import DslPreviewDialog from './DslPreviewDialog.vue'
import {
  formatCountdown,
  parseConfirmPreview,
  remainingMs,
  resolveConfirmStatus,
  type ConfirmCardState,
} from './assistantConfirm'

/**
 * 消息渲染（全局智能助手详细设计 5.1 / 交互设计 4.2-6.2）：
 * user/assistant/tool 三种角色 + Markdown 链接白名单过滤 + 工具过程卡片 +
 * 写操作确认卡片（明细表格 + 确认/取消 + 倒计时）+ DSL 预览执行入口。
 * 纯展示 + emit：确认/取消/DSL 确认均上抛由面板协调（approve 走 SSE、DSL 执行需 minder 宿主）。
 */

/** 本地消息视图模型：历史 AiMessage 字段 + 流式过程中的临时状态 */
export interface AssistantMessageItem {
  id: string
  role: AiMessageRole
  content: string
  createdAt: string
  /** 流式进行中（渲染光标） */
  streaming?: boolean
  /** 首帧超时提示（4.1：10 秒未收到首帧） */
  slowHint?: boolean
  /** 工具过程卡片（tool_call 帧，执行中/完成） */
  toolProcesses?: { toolName: string; summary: string; status: 'running' | 'done' }[]
  /** 写操作确认卡片（confirm_required 帧） */
  confirmCard?: ConfirmCardState | null
  /** 对话式编辑 DSL（minder_commands 帧） */
  dslCommands?: AiMinderCommandsEvent | null
}

const props = defineProps<{ message: AssistantMessageItem }>()

const emit = defineEmits<{
  /** 确认执行写操作（令牌经请求体传递，不入 URL） */
  confirm: [confirmToken: string]
  cancel: [confirmToken: string]
  /** 用户确认 DSL 预览：由面板经 assistantContext.dslHost.apply 执行并追加本地提示（4.3） */
  confirmDsl: [plan: DslPlan]
  /** 用户取消 DSL 预览：面板追加「已取消」本地提示，无任何变更（交互设计 6.2） */
  cancelDsl: []
}>()

// 链接白名单前缀：从路由表收集站内顶层路径（4.6 输出防护，外部 URL 降级纯文本）
const linkPrefixes = collectRegisteredPrefixes(router.getRoutes())

const isUser = computed(() => props.message.role === 'user')
const isTool = computed(() => props.message.role === 'tool')
const safeContent = computed(() => filterAssistantLinks(props.message.content ?? '', linkPrefixes))

// ==================== 确认卡片（5.2：倒计时归零置已超时不可操作） ====================

const now = ref(Date.now())
let countdownTimer: ReturnType<typeof setInterval> | null = null

const confirmStatus = computed(() =>
  props.message.confirmCard ? resolveConfirmStatus(props.message.confirmCard, now.value) : null,
)
const countdownText = computed(() => {
  const card = props.message.confirmCard
  return card ? formatCountdown(remainingMs(card, now.value)) : ''
})
/** 待确认状态（可操作）才显示倒计时与按钮 */
const confirmWaiting = computed(() => confirmStatus.value === 'waiting')
/** 终态中文标签（交互设计 5.2：已超时 / 执行成功 / 已取消 / 失败原因） */
const confirmStatusLabel = computed(() => {
  switch (confirmStatus.value) {
    case 'expired':
      return '已超时'
    case 'approved':
      return '执行成功'
    case 'cancelled':
      return '已取消'
    case 'failed':
      return '执行失败'
    default:
      return ''
  }
})
const previewFields = computed(() =>
  props.message.confirmCard ? Object.entries(parseConfirmPreview(props.message.confirmCard.preview)) : [],
)

watch(
  () => props.message.confirmCard,
  (card) => {
    if (countdownTimer) clearInterval(countdownTimer)
    countdownTimer = null
    if (card?.status === 'waiting') {
      countdownTimer = setInterval(() => {
        now.value = Date.now()
      }, 1000)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

function handleConfirm(): void {
  const card = props.message.confirmCard
  if (card) emit('confirm', card.confirmToken)
}

function handleCancel(): void {
  const card = props.message.confirmCard
  if (card) emit('cancel', card.confirmToken)
}

// ==================== DSL 预览执行入口（4.3 / 交互设计 6.2） ====================

const assistantContext = useAssistantContextStore()
const dslPreviewVisible = ref(false)
const dslPlan = ref<DslPlan | null>(null)

/** 打开预览前校验仍在对应文档页且宿主存在（5.2：已离开则提示重试，指令丢弃不缓存） */
function openDslPreview(): void {
  const commands = props.message.dslCommands
  const host = assistantContext.dslHost
  if (!commands || !host || host.documentId !== commands.documentId) {
    ElMessage.warning('请回到文档后重试')
    return
  }
  const result: DslPlanResult = host.buildPlan(commands.commands, assistantContext.selectedNodeId)
  if (!result.ok) {
    // 解析中止（4.4.1）：零命中/多义/@selected 无选中/超上限，按原因提示，不进预览
    ElMessage.warning(dslAbortMessage(result))
    return
  }
  dslPlan.value = result.plan
  dslPreviewVisible.value = true
}

/** 解析中止原因转提示文案（4.4.1：任一引用失败即整批中止） */
function dslAbortMessage(result: { reason: { kind: string } }): string {
  switch (result.reason.kind) {
    case 'too-many':
      return '指令数量超过上限（10 条），请分批执行'
    case 'no-selected':
      return '未选中对应节点，请先选中目标节点后重试'
    case 'ambiguous':
      return '存在多个同名节点，请补充说明以精确定位'
    default:
      return '未找到匹配节点，请检查节点标题'
  }
}

function handleConfirmDsl(): void {
  if (dslPlan.value) emit('confirmDsl', dslPlan.value)
  dslPreviewVisible.value = false
}

function handleCancelDsl(): void {
  dslPreviewVisible.value = false
  emit('cancelDsl')
}
</script>

<template>
  <div class="msg" :class="[`msg--${message.role}`, { 'msg--streaming': message.streaming }]">
    <!-- 用户消息：右侧气泡 -->
    <div v-if="isUser" class="msg__bubble msg__bubble--user">{{ message.content }}</div>

    <!-- 工具消息（历史 role=tool）：灰色小卡片（设计 3.1） -->
    <div v-else-if="isTool" class="msg__tool-card">
      <span class="msg__tool-icon">🔧</span>
      <span>{{ message.content || '工具执行' }}</span>
    </div>

    <!-- 助手消息：Markdown + 过程卡片 + 确认卡片 + DSL 入口 -->
    <div v-else class="msg__assistant">
      <MarkdownView v-if="message.content" :content="safeContent" />
      <span v-if="message.streaming" class="msg__cursor">▌</span>
      <div v-if="message.streaming && message.slowHint" class="msg__slow-hint">响应较慢，可停止重试</div>

      <!-- 工具过程卡片（4.3：执行中 loading / 完成摘要） -->
      <div v-if="message.toolProcesses?.length" class="msg__process">
        <div v-for="(proc, index) in message.toolProcesses" :key="index" class="msg__process-item">
          <el-icon v-if="proc.status === 'running'" class="is-loading"><Loading /></el-icon>
          <span v-else class="msg__process-ok">✓</span>
          <span>{{ proc.status === 'running' ? `正在查询：${proc.summary}` : `查询完成：${proc.summary}` }}</span>
        </div>
      </div>

      <!-- 写操作确认卡片（5.1：明细表格 + 倒计时 + 取消/确认执行） -->
      <div v-if="message.confirmCard" class="msg__confirm">
        <div class="msg__confirm-title">📋 待确认操作：{{ message.confirmCard.toolName }}</div>
        <table v-if="previewFields.length" class="msg__confirm-table">
          <tbody>
            <tr v-for="(field, index) in previewFields" :key="index">
              <td class="msg__confirm-key">{{ field[0] }}</td>
              <td class="msg__confirm-value">{{ field[1] }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="msg__confirm-empty">无参数明细</div>
        <div v-if="confirmWaiting" class="msg__confirm-countdown">剩余确认时间 {{ countdownText }}</div>
        <div v-else class="msg__confirm-finished">
          <span :class="{ 'msg__confirm-failed': confirmStatus === 'failed' }">{{ confirmStatusLabel }}</span>
          <span v-if="confirmStatus === 'failed' && message.confirmCard?.error" class="msg__confirm-error">
            {{ message.confirmCard.error }}
          </span>
        </div>
        <div v-if="confirmWaiting" class="msg__confirm-actions">
          <el-button size="small" @click="handleCancel">取消</el-button>
          <el-button size="small" type="primary" @click="handleConfirm">确认执行</el-button>
        </div>
      </div>

      <!-- DSL 预览执行入口（交互设计 6.2） -->
      <div v-if="message.dslCommands" class="msg__dsl">
        <el-button size="small" type="primary" plain @click="openDslPreview">
          查看编辑预览
        </el-button>
      </div>
    </div>

    <DslPreviewDialog
      v-model="dslPreviewVisible"
      :plan="dslPlan"
      @confirm="handleConfirmDsl"
      @cancel="handleCancelDsl"
    />
  </div>
</template>

<style scoped lang="scss">
.msg {
  display: flex;
  flex-direction: column;
}

.msg--user {
  align-items: flex-end;
}

.msg__bubble {
  max-width: 85%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.msg__bubble--user {
  background: var(--el-color-primary-light-8);
  color: var(--el-text-color-primary);
}

.msg--assistant,
.msg--tool {
  align-items: flex-start;
}

.msg__assistant {
  width: 100%;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.msg__cursor {
  color: var(--el-color-primary);
  animation: msg-cursor-blink 1s step-end infinite;
}

.msg__slow-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-color-warning);
}

@keyframes msg-cursor-blink {
  50% {
    opacity: 0;
  }
}

.msg__tool-card {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 85%;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.msg__process {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
}

.msg__process-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.msg__process-ok {
  color: var(--el-color-success);
}

.msg__confirm {
  margin-top: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
}

.msg__confirm-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 6px;
}

.msg__confirm-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.msg__confirm-table td {
  padding: 4px 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.msg__confirm-key {
  width: 90px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
}

.msg__confirm-value {
  color: var(--el-text-color-primary);
}

.msg__confirm-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.msg__confirm-countdown {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
}

.msg__confirm-finished {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.msg__confirm-failed {
  color: var(--el-color-danger);
}

.msg__confirm-error {
  margin-left: 8px;
  color: var(--el-color-danger);
}

.msg__confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.msg__dsl {
  margin-top: 8px;
}
</style>
