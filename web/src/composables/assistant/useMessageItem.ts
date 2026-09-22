import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { AiMessageRole, AiMinderCommandsEvent } from '@/types'
import { filterAssistantLinks, collectRegisteredPrefixes } from '@/utils/assistantLinkWhitelist'
import { useAssistantContextStore } from '@/stores/assistantContext'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import type { DslPlan, DslPlanResult } from '@/components/project/functional-testing/minder/ai/dslRunner'
import {
  formatCountdown,
  parseConfirmPreview,
  remainingMs,
  resolveConfirmStatus,
  type ConfirmCardState,
} from '@/components/assistant/assistantConfirm'

export interface AssistantMessageItem {
  id: string
  role: AiMessageRole
  content: string
  createdAt: string
  streaming?: boolean
  slowHint?: boolean
  toolProcesses?: { toolName: string; summary: string; status: 'running' | 'done' }[]
  confirmCard?: ConfirmCardState | null
  dslCommands?: AiMinderCommandsEvent | null
}

const linkPrefixes = collectRegisteredPrefixes(router.getRoutes())

export interface UseMessageItemOptions {
  message: AssistantMessageItem
  onConfirm: (confirmToken: string) => void
  onCancel: (confirmToken: string) => void
  onConfirmDsl: (plan: DslPlan) => void
  onCancelDsl: () => void
}

export function useMessageItem(options: UseMessageItemOptions) {
  const { message, onConfirm, onCancel, onConfirmDsl, onCancelDsl } = options

  const assistantContext = useAssistantContextStore()
  const authStore = useAuthStore()

  const isUser = computed(() => message.role === 'user')
  const isTool = computed(() => message.role === 'tool')
  const safeContent = computed(() => filterAssistantLinks(message.content ?? '', linkPrefixes))

  const userAvatarUrl = computed(() => authStore.avatarUrl?.trim() || '')
  const userAvatarChar = computed(() => {
    const name = authStore.username.trim()
    return name ? name.charAt(0).toUpperCase() : '?'
  })

  const now = ref(Date.now())
  let countdownTimer: ReturnType<typeof setInterval> | null = null

  const confirmStatus = computed(() =>
    message.confirmCard ? resolveConfirmStatus(message.confirmCard, now.value) : null,
  )
  const countdownText = computed(() => {
    const card = message.confirmCard
    return card ? formatCountdown(remainingMs(card, now.value)) : ''
  })
  const confirmWaiting = computed(() => confirmStatus.value === 'waiting')
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
    message.confirmCard ? Object.entries(parseConfirmPreview(message.confirmCard.preview)) : [],
  )

  watch(
    () => message.confirmCard,
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
    const card = message.confirmCard
    if (card) onConfirm(card.confirmToken)
  }

  function handleCancel(): void {
    const card = message.confirmCard
    if (card) onCancel(card.confirmToken)
  }

  const dslPreviewVisible = ref(false)
  const dslPlan = ref<DslPlan | null>(null)

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

  function openDslPreview(): void {
    if (!authStore.hasPermission('case:edit')) {
      ElMessage.warning('无文档编辑权限，无法预览编辑指令')
      return
    }
    const commands = message.dslCommands
    const host = assistantContext.dslHost
    if (!commands || !host || host.documentId !== commands.documentId) {
      ElMessage.warning('请回到文档后重试')
      return
    }
    const result: DslPlanResult = host.buildPlan(commands.commands, assistantContext.selectedNodeId)
    if (!result.ok) {
      ElMessage.warning(dslAbortMessage(result))
      return
    }
    dslPlan.value = result.plan
    dslPreviewVisible.value = true
  }

  function handleConfirmDsl(): void {
    if (dslPlan.value) onConfirmDsl(dslPlan.value)
    dslPreviewVisible.value = false
  }

  function handleCancelDsl(): void {
    dslPreviewVisible.value = false
    onCancelDsl()
  }

  return {
    isUser,
    isTool,
    safeContent,
    userAvatarUrl,
    userAvatarChar,
    confirmStatus,
    countdownText,
    confirmWaiting,
    confirmStatusLabel,
    previewFields,
    handleConfirm,
    handleCancel,
    dslPreviewVisible,
    dslPlan,
    openDslPreview,
    handleConfirmDsl,
    handleCancelDsl,
  }
}
