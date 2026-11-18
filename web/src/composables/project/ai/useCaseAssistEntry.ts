import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchAiStatus } from '@/services/ai'
import { useAssistTask } from '@/composables/project/ai/useAssistTask'
import { isCaseOptimizeSuggestion } from '@/composables/project/ai/assistPresentation'
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
 * 脑图用例 AI 入口（详设 5.1 / 交互 2.3 / 2.4）：
 * AI 未启用时入口隐藏；无选中 / 非用例节点 / 只读时置灰并给出原因。
 * 当前文档查看会话内按节点复用未处理结果，切换文档或卸载即清空，避免跨节点与跨文档串数据。
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
  const gateReason = computed(() => gateReasonFor(options.selectedNodeId()))

  function gateReasonFor(nodeId: string): string {
    if (!options.documentId()) return '请先打开用例文档'
    if (!nodeId) return '请先在脑图中选中一个用例节点'
    if (options.selectedType() !== 'case') return '仅用例节点可使用 AI 优化'
    if (options.isReadonly?.() === true) return '当前文档只读，不可发起建议'
    return ''
  }

  const canLaunch = computed(() => entryVisible.value && gateReason.value === '')
  /** 动作区放行条件：文档可写（只读时置灰，详设 3.1） */
  const canAdopt = computed(() => options.isReadonly?.() !== true)

  const task = useAssistTask('case_optimize', {
    target: () => ({ documentId: options.documentId() }),
    onConfirmed: options.onConfirmed,
  })

  const panelVisible = ref(false)
  /** 面板当前承载的用例节点：缓存登记与校验都以它为键，避免跨节点串数据 */
  const activeNodeId = ref('')
  /** 本次文档查看会话内的待处理结果：nodeId → taskId，切换文档或卸载即清空 */
  const pendingTaskByNode = new Map<string, string>()

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

  function hasPendingFor(nodeId: string): boolean {
    return rows.value.some(
      (row) =>
        row.confirmStatus === 'pending' &&
        isCaseOptimizeSuggestion(row.suggestion) &&
        row.suggestion.nodeId === nodeId,
    )
  }

  /** 内容全部就绪才可判定节点归属；仍在加载时按“可能命中”处理，等下一轮再校验 */
  function contentsSettled(): boolean {
    return rows.value.length > 0 && rows.value.every((row) => row.suggestion !== null)
  }

  // 待处理结果登记与失效：结果被采纳 / 驳回后同一节点再次点击应重新发起
  watch(
    [rows, () => task.taskId.value],
    ([currentRows, taskId]) => {
      const nodeId = activeNodeId.value
      if (!taskId || !nodeId || currentRows.length === 0) return
      if (!currentRows.every((row) => row.suggestion !== null)) return
      if (hasPendingFor(nodeId)) {
        pendingTaskByNode.set(nodeId, taskId)
        return
      }
      if (pendingTaskByNode.get(nodeId) === taskId) pendingTaskByNode.delete(nodeId)
    },
    { immediate: true },
  )

  // 终止任务没有可复用结果，释放缓存后下次点击重新发起
  watch(
    () => task.isFailed.value,
    (isFailed) => {
      const nodeId = activeNodeId.value
      if (!isFailed || !nodeId) return
      if (pendingTaskByNode.get(nodeId) === task.taskId.value) pendingTaskByNode.delete(nodeId)
    },
  )

  // 关闭面板（含“后台运行，关闭”）不丢弃结果，但缓存归属需跟随面板切换
  watch(panelVisible, (visible) => {
    if (!visible) activeNodeId.value = ''
  })

  // 切换文档即结束本次查看会话：缓存与面板一并清空，再次进入按新任务计算（交互 2.4）
  watch(
    () => options.documentId(),
    () => {
      pendingTaskByNode.clear()
      activeNodeId.value = ''
      panelVisible.value = false
      task.reset()
    },
  )

  /** 命中缓存的任务若已被处理（如其它端采纳），丢弃缓存并补发一次新任务 */
  function recoverStaleCache(nodeId: string, taskId: string): void {
    const stop = watch(
      [rows, () => task.loadError.value],
      ([currentRows, loadError]) => {
        if (task.taskId.value !== taskId || loadError) {
          stop()
          return
        }
        if (currentRows.length === 0) return
        if (!currentRows.every((row) => row.suggestion !== null)) return
        stop()
        if (hasPendingFor(nodeId)) return
        pendingTaskByNode.delete(nodeId)
        void startOptimize(nodeId)
      },
    )
  }

  async function startOptimize(nodeId: string): Promise<void> {
    panelVisible.value = true
    activeNodeId.value = nodeId
    await task.launch({
      documentId: options.documentId(),
      nodeIds: [nodeId],
    })
    if (task.taskId.value) {
      // 发起即登记，便于任务执行中关闭面板后再次点击仍能回到进度入口
      pendingTaskByNode.set(nodeId, task.taskId.value)
      if (task.isFailed.value) pendingTaskByNode.delete(nodeId)
    }
  }

  async function launchOptimize(nodeId?: string): Promise<void> {
    if (!entryVisible.value) return
    const targetNodeId = nodeId || options.selectedNodeId()
    const reason = gateReasonFor(targetNodeId)
    if (reason) {
      ElMessage.warning(reason)
      return
    }
    const cachedTaskId = pendingTaskByNode.get(targetNodeId)
    if (cachedTaskId) {
      panelVisible.value = true
      activeNodeId.value = targetNodeId
      await task.open(cachedTaskId)
      if (task.taskId.value !== cachedTaskId) return
      if (contentsSettled()) {
        if (!hasPendingFor(targetNodeId)) {
          pendingTaskByNode.delete(targetNodeId)
          await startOptimize(targetNodeId)
        }
        return
      }
      // 任务已结束但没有提案：同样视为无可用结果，重新发起
      if (rows.value.length === 0 && task.task.value?.status === 'closed') {
        pendingTaskByNode.delete(targetNodeId)
        await startOptimize(targetNodeId)
        return
      }
      recoverStaleCache(targetNodeId, cachedTaskId)
      return
    }
    await startOptimize(targetNodeId)
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
    activeNodeId.value = ''
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
