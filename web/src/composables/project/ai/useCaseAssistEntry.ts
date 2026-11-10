import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchAiStatus } from '@/services/ai'
import { useAuthStore } from '@/stores/auth'
import { useAssistTask } from '@/composables/project/ai/useAssistTask'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type {
  AiAssistSourceRef,
  AiAssistTaskType,
  AiProposalConfirmResult,
} from '@/types'

export type CaseAssistKind = Extract<AiAssistTaskType, 'case_complete' | 'case_priority'>

export interface CaseAssistEntryOptions {
  /** 当前脑图文档（补全 / 级别推荐输入的 documentId） */
  documentId: () => string
  selectedNodeId: () => string
  selectedType: () => string
  /** 文档 / 节点只读时入口置灰（详设 5.3） */
  isReadonly?: () => boolean
  /** 采纳成功后刷新脑图（交互 2.1.2 脑图联动） */
  onConfirmed?: () => void
}

/**
 * 脑图工具栏 AI 入口（详设 5.1 / 交互 2.3）：
 * AI 未启用或无 ai:task 时入口隐藏；无选中 / 非用例节点 / 只读时置灰并给出原因。
 * 面板私有状态由两个 useAssistTask 实例承载，按当前建议类型切换。
 */
export function useCaseAssistEntry(options: CaseAssistEntryOptions) {
  const authStore = useAuthStore()
  const router = useRouter()

  const aiAvailable = ref(false)

  async function loadAvailability(): Promise<void> {
    try {
      aiAvailable.value = (await fetchAiStatus()).available
    } catch {
      // 状态接口失败按不可用处理：入口隐藏，不阻塞脑图编辑（总册 4.5）
      aiAvailable.value = false
    }
  }

  onMounted(loadAvailability)

  const entryVisible = computed(
    () => aiAvailable.value && authStore.hasPermission('ai:task'),
  )

  /** 入口置灰原因（交互 2.3 常驻展示，不靠悬浮猜测） */
  const gateReason = computed(() => {
    if (!options.documentId()) return '请先打开用例文档'
    if (!options.selectedNodeId()) return '请先在脑图中选中一个用例节点'
    if (options.selectedType() !== 'case') return '仅用例节点可使用 AI 补全或级别推荐'
    if (options.isReadonly?.() === true) return '当前文档只读，不可发起建议'
    return ''
  })

  const canLaunch = computed(() => entryVisible.value && gateReason.value === '')
  /** 动作区放行条件：文档可写 + 具采纳权限（后端不校验，仅入口显隐，详设 3.1） */
  const canAdopt = computed(
    () => options.isReadonly?.() !== true && authStore.hasPermission('ai:confirm'),
  )

  const complete = useAssistTask('case_complete', {
    target: () => ({ documentId: options.documentId() }),
    onConfirmed: options.onConfirmed,
  })
  const priority = useAssistTask('case_priority', {
    target: () => ({ documentId: options.documentId() }),
    onConfirmed: options.onConfirmed,
  })

  const activeKind = ref<CaseAssistKind>('case_complete')
  const panelVisible = ref(false)
  const active = computed(() => (activeKind.value === 'case_complete' ? complete : priority))

  // 展平为顶层 computed，便于宿主模板直接取用（嵌套 ref 不会自动解包）
  const rows = computed<AssistRow[]>(() => active.value.rows.value)
  const loading = computed(() => active.value.loading.value)
  const confirming = computed(() => active.value.confirming.value)
  const receipt = computed<AiProposalConfirmResult[]>(() => active.value.receipt.value)
  const processed = computed(() => active.value.processed.value)
  const submitting = computed(() => active.value.submitting.value)
  const running = computed(() => submitting.value || active.value.isRunning.value)
  const failed = computed(() => active.value.isFailed.value)
  const failedReason = computed(() => active.value.failedReason.value)
  const progressText = computed(() => active.value.executionSummary.value?.text ?? '')
  const loadError = computed(() => active.value.loadError.value)

  async function launch(kind: CaseAssistKind): Promise<void> {
    if (!entryVisible.value) return
    if (gateReason.value) {
      ElMessage.warning(gateReason.value)
      return
    }
    activeKind.value = kind
    panelVisible.value = true
    const other = kind === 'case_complete' ? priority : complete
    other.reset()
    await active.value.launch({
      documentId: options.documentId(),
      nodeIds: [options.selectedNodeId()],
    })
  }

  function launchComplete(): Promise<void> {
    return launch('case_complete')
  }

  function launchPriority(): Promise<void> {
    return launch('case_priority')
  }

  function retry(): Promise<void> {
    return active.value.retry()
  }

  function refresh(): Promise<void> {
    return active.value.refresh()
  }

  function adopt(key: string): Promise<void> {
    return active.value.adopt(key)
  }

  function adoptEdited(key: string, content: Record<string, unknown>): Promise<void> {
    return active.value.adoptEdited(key, content)
  }

  function batchAdopt(keys: string[]): Promise<void> {
    return active.value.batchAdopt(keys)
  }

  function reject(key: string, note?: string): Promise<void> {
    return active.value.reject(key, note)
  }

  function clearReceipt(): void {
    active.value.clearReceipt()
  }

  function openSource(source: AiAssistSourceRef): void {
    // 来源引用仅来自需求上下文（详设 3.2 / 3.3），按需求详情定位
    if (!source.id) return
    void router.push({
      name: 'RequirementDetail',
      params: { requirementId: source.id },
    })
  }

  function closePanel(): void {
    complete.reset()
    priority.reset()
    panelVisible.value = false
  }

  return {
    entryVisible,
    gateReason,
    canLaunch,
    canAdopt,
    panelVisible,
    activeKind,
    rows,
    loading,
    confirming,
    receipt,
    processed,
    submitting,
    running,
    failed,
    failedReason,
    progressText,
    loadError,
    launchComplete,
    launchPriority,
    retry,
    refresh,
    adopt,
    adoptEdited,
    batchAdopt,
    reject,
    clearReceipt,
    openSource,
    closePanel,
  }
}
