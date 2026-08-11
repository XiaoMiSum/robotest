<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { AiMessageRole, AiMinderCommandsEvent } from '@/types'
import MarkdownView from '@/components/common/MarkdownView.vue'
import { filterAssistantLinks, collectRegisteredPrefixes } from '@/utils/assistantLinkWhitelist'
import { useAssistantContextStore } from '@/stores/assistantContext'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import type { DslPlan, DslPlanResult } from '@/components/project/minder/ai/dslRunner'
import AssistantIcon from './AssistantIcons.vue'
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
const authStore = useAuthStore()
const dslPreviewVisible = ref(false)
const dslPlan = ref<DslPlan | null>(null)

/** 打开预览前校验仍在对应文档页且宿主存在（5.2：已离开则提示重试，指令丢弃不缓存） */
function openDslPreview(): void {
  // 需求 3.5.3：仅编辑权限用户可编辑，无权限连预览都不展示（拦截在入口，避免用户误以为可执行）
  if (!authStore.hasPermission('case:edit')) {
    ElMessage.warning('无文档编辑权限，无法预览编辑指令')
    return
  }
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
    <!-- 用户消息：右侧主色气泡 + 右侧首字头像 -->
    <div v-if="isUser" class="msg__row msg__row--user">
      <div class="msg__bubble msg__bubble--user">{{ message.content }}</div>
      <span class="msg__avatar msg__avatar--user">{{ message.content.charAt(0) }}</span>
    </div>

    <!-- 工具消息（历史 role=tool）：灰色小卡片（设计 3.1） -->
    <div v-else-if="isTool" class="msg__tool-card">
      <AssistantIcon name="wrench" :size="14" class="msg__tool-icon" />
      <span>{{ message.content || '工具执行' }}</span>
    </div>

    <!-- 助手消息：AI 渐变头像 + 内容列（Markdown + 过程卡片 + 确认卡片 + DSL 入口） -->
    <div v-else class="msg__row msg__row--assistant">
      <span class="msg__avatar msg__avatar--ai">
        <AssistantIcon name="sparkles" :size="14" />
      </span>
      <div class="msg__assistant">
        <MarkdownView v-if="message.content" :content="safeContent" />
        <span v-if="message.streaming" class="msg__cursor">▍</span>
        <div v-if="message.streaming && message.slowHint" class="msg__slow-hint">响应较慢，可停止重试</div>

        <!-- 工具过程卡片（4.3：执行中 loading / 完成摘要） -->
        <div v-if="message.toolProcesses?.length" class="msg__process">
          <div v-for="(proc, index) in message.toolProcesses" :key="index" class="msg__process-item">
            <span v-if="proc.status === 'running'" class="msg__process-spinner" aria-hidden="true"></span>
            <AssistantIcon v-else-if="proc.status === 'done'" name="check" :size="12" class="msg__process-ok" />
            <AssistantIcon v-else name="close" :size="12" class="msg__process-fail" />
            <span class="msg__process-badge" :class="`msg__process-badge--${proc.status}`">
              {{ proc.status === 'running' ? '运行中' : proc.status === 'done' ? '完成' : '失败' }}
            </span>
            <span class="msg__process-summary">{{ proc.summary }}</span>
          </div>
        </div>

        <!-- 写操作确认卡片（5.1：明细表格 + 倒计时 + 取消/确认执行） -->
        <div v-if="message.confirmCard" class="msg__confirm">
          <div class="msg__confirm-title">
            <AssistantIcon name="clipboard" :size="14" class="msg__confirm-title-icon" />
            待确认操作：{{ message.confirmCard.toolName }}
          </div>
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
  // 消息进入：仅 transform/opacity（GPU 合成），初始态由 fill-mode: both 保持
  animation: msg-enter var(--transition-slow) both;
}

.msg--user {
  align-items: flex-end;
}

.msg--assistant,
.msg--tool {
  align-items: flex-start;
}

.msg__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-md);
  width: 100%;
  min-width: 0;
}

.msg__row--user {
  justify-content: flex-end;
}

.msg__avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-lg);
}

.msg__avatar--ai {
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-700));
  color: var(--color-neutral-0);
}

.msg__avatar--user {
  background: var(--color-neutral-200);
  color: var(--color-neutral-600);
  font-size: 13px;
  font-weight: 700;
}

.msg__bubble {
  max-width: 82%;
  padding: 10px 14px;
  border-radius: var(--radius-xl);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.msg__bubble--user {
  background: var(--color-primary-500);
  color: var(--color-neutral-0);
  border-top-right-radius: var(--radius-md);
}

.msg__assistant {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--color-neutral-700);
}

.msg__cursor {
  color: var(--color-primary-500);
  animation: msg-cursor-blink 1s step-end infinite;
}

.msg__slow-hint {
  margin-top: var(--space-sm);
  font-size: 12px;
  color: var(--color-warning);
}

@keyframes msg-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes msg-cursor-blink {
  0%,
  100% {
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
}

.msg__tool-card {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  max-width: 82%;
  padding: 6px 10px;
  border-radius: var(--radius-lg);
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  font-size: 12px;
  color: var(--color-neutral-500);
}

.msg__tool-icon {
  color: var(--color-neutral-400);
}

.msg__process {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-50);
}

.msg__process-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 12px;
  color: var(--color-neutral-600);
}

.msg__process-spinner {
  flex-shrink: 0;
  width: 12px;
  height: 12px;
  border: 2px solid var(--color-primary-100);
  border-top-color: var(--color-primary-500);
  border-radius: 50%;
  animation: msg-spin 0.8s linear infinite;
}

@keyframes msg-spin {
  to {
    transform: rotate(360deg);
  }
}

.msg__process-ok {
  flex-shrink: 0;
  color: var(--color-success);
}

.msg__process-fail {
  flex-shrink: 0;
  color: var(--color-danger);
}

.msg__process-badge {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  line-height: 18px;
  font-weight: 500;
}

.msg__process-badge--running {
  background: var(--color-info-light);
  color: var(--color-info);
}

.msg__process-badge--done {
  color: var(--color-success);
}

.msg__process-badge--failed {
  color: var(--color-danger);
}

.msg__process-summary {
  min-width: 0;
  word-break: break-word;
}

.msg__confirm {
  margin-top: var(--space-sm);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: 10px 12px;
  background: var(--color-neutral-50);
}

.msg__confirm-title {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-neutral-800);
  margin-bottom: var(--space-sm);
}

.msg__confirm-title-icon {
  color: var(--color-primary-500);
}

.msg__confirm-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.msg__confirm-table td {
  padding: 4px 8px;
  border: 1px solid var(--color-neutral-100);
}

.msg__confirm-key {
  width: 90px;
  background: var(--color-neutral-100);
  color: var(--color-neutral-500);
}

.msg__confirm-value {
  color: var(--color-neutral-700);
}

.msg__confirm-empty {
  font-size: 12px;
  color: var(--color-neutral-400);
}

.msg__confirm-countdown {
  margin-top: var(--space-sm);
  font-size: 12px;
  color: var(--color-warning);
}

.msg__confirm-finished {
  margin-top: var(--space-sm);
  font-size: 12px;
  color: var(--color-neutral-500);
}

.msg__confirm-failed {
  color: var(--color-danger);
}

.msg__confirm-error {
  margin-left: var(--space-sm);
  color: var(--color-danger);
}

.msg__confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.msg__dsl {
  margin-top: var(--space-sm);
}
</style>
