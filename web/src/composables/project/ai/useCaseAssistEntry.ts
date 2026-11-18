import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchAiStatus } from '@/services/ai'
import { useAssistTask } from '@/composables/project/ai/useAssistTask'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type {
  AiAssistSourceRef,
  AiProposalConfirmResult,
} from '@/types'

export interface CaseAssistEntryOptions {
  /** 当前脑图文档（用例优化输入的 documentId） */
  documentId: () => string
  selectedNodeId: () => string
  selectedType: () => string
  /** 文档 / 节点只读时入口置灰（详设 5.3） */
  isReadonly?: () => boolean
  /** 采纳成功后刷新脑图（交互 2.1.2 脑图联动） */
  onConfirmed?: () => void
}

/**
 * 脑图用例 AI 入口（详设 5.1 / 交互 2.3）：
 * AI 未启用时入口隐藏；无选中 / 非用例节点 / 只读时置灰并给出原因。
 * 单次优化同时补全字段并推荐级别，面板私有状态由同一个 useAssistTask 承载。
 */
export function useCaseAssistEntry(options: CaseAssistEntryOptions) {
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

  const entryVisible = computed(() => aiAvailable.value)

  /** 入口置灰原因（交互 2.3 常驻展示，不靠悬浮猜测） */
  const gateReason = computed(() => {
    if (!options.documentId()) return '请先打开用例文档'
    if (!options.selectedNodeId()) return '请先在脑图中选中一个用例节点'
    if (options.selectedType() !== 'case') return '仅用例节点可使用 AI 优化'
    if (options.isReadonly?.() === true) return '当前文档只读，不可发起建议'
    return ''
  })

  const canLaunch = computed(() => entryVisible.value && gateReason.value === '')
  /** 动作区放行条件：文档可写（只读时置灰，详设 3.1） */
  const canAdopt = computed(() => options.isReadonly?.() !== true)

  const task = useAssistTask('case_optimize', {
    target: () => ({ documentId: options.documentId() }),
    onConfirmed: options.onConfirmed,
  })

  const panelVisible = ref(false)

  // 展平为顶层 computed，便于宿主模板直接取用（嵌套 ref 不会自动解包）
  const rows = computed<AssistRow[]>(() => task.rows.value)
  const loading = computed(() => task.loading.value)
  const confirming = computed(() => task.confirming.value)
  const receipt = computed<AiProposalConfirmResult[]>(() => task.receipt.value)
  const processed = computed(() => task.processed.value)
  const submitting = computed(() => task.submitting.value)
  const running = computed(() => submitting.value || task.isRunning.value)
  const failed = computed(() => task.isFailed.value)
  const failedReason = computed(() => task.failedReason.value)
  const progressText = computed(() => task.executionSummary.value?.text ?? '')
  const loadError = computed(() => task.loadError.value)

  async function launchOptimize(): Promise<void> {
    if (!entryVisible.value) return
    if (gateReason.value) {
      ElMessage.warning(gateReason.value)
      return
    }
    panelVisible.value = true
    await task.launch({
      documentId: options.documentId(),
      nodeIds: [options.selectedNodeId()],
    })
  }

  function retry(): Promise<void> {
    return task.retry()
  }

  function refresh(): Promise<void> {
    return task.refresh()
  }

  function adopt(key: string): Promise<void> {
    return task.adopt(key)
  }

  function adoptEdited(key: string, content: Record<string, unknown>): Promise<void> {
    return task.adoptEdited(key, content)
  }

  function batchAdopt(keys: string[]): Promise<void> {
    return task.batchAdopt(keys)
  }

  function reject(key: string, note?: string): Promise<void> {
    return task.reject(key, note)
  }

  function clearReceipt(): void {
    task.clearReceipt()
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
    task.reset()
    panelVisible.value = false
  }

  return {
    entryVisible,
    gateReason,
    canLaunch,
    canAdopt,
    panelVisible,
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
    launchOptimize,
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
