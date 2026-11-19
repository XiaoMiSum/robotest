import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchAiStatus, fetchLatestAiTask } from '@/services/ai'
import { useAssistTask } from '@/composables/project/ai/useAssistTask'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import {
  isCaseOptimizeSuggestion,
  readWireOperations,
} from '@/composables/project/ai/assistPresentation'
import type {
  AiAssistSourceRef,
  AiCaseOptimizeOperation,
  AiProposalConfirmResult,
  AiTaskDetail,
} from '@/types'

export interface CaseAssistEntryOptions {
  /** 当前脑图文档（用例优化输入的 documentId） */
  documentId: () => string
  selectedNodeId: () => string
  selectedType: () => string
  /** 文档 / 节点只读时入口置灰（详设 5.3） */
  isReadonly?: () => boolean
  /** 采纳时在当前脑图树上执行节点操作（详设 4.2），由宿主注入以访问 Minder 与持久化 */
  executeOperations?: (operations: AiCaseOptimizeOperation[], caseNodeId: string | null) => void
}

/**
 * 脑图用例 AI 入口（详设 5.1 / 交互 2.3 / 2.4）：
 * AI 未启用时入口隐藏；无选中 / 非用例节点 / 只读时置灰并给出原因。
 * 点击时按节点查询后端最近一次任务（项目内成员共享）：命中按状态回显进度或提案，
 * 无任务才发起；快速切换节点时丢弃过期查询，避免跨节点串数据。
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
  })

  const panelVisible = ref(false)
  /** 面板当前承载的用例节点：重新执行以它为输入，避免跨节点串数据 */
  const activeNodeId = ref('')
  // 快速切换节点时后到的查询结果不得覆盖当前节点
  let queryToken = 0

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
  /** 任务已有提案：展示提案并提供重新执行（交互 2.4） */
  const retryable = computed(() => rows.value.length > 0)
  const progressText = computed(() => task.executionSummary.value?.text ?? '')
  const loadError = computed(() => task.loadError.value)

  // 关闭面板（含“后台运行，关闭”）不丢弃任务，但面板归属需跟随面板切换
  watch(panelVisible, (visible) => {
    if (!visible) activeNodeId.value = ''
  })

  // 切换文档即结束本次查看：面板与任务状态一并清空，避免把上一文档的任务带到新文档
  watch(
    () => options.documentId(),
    () => {
      queryToken++
      activeNodeId.value = ''
      panelVisible.value = false
      task.reset()
    },
  )

  async function startOptimize(nodeId: string): Promise<void> {
    panelVisible.value = true
    activeNodeId.value = nodeId
    await task.launch({
      documentId: options.documentId(),
      nodeIds: [nodeId],
    })
  }

  async function launchOptimize(nodeId?: string): Promise<void> {
    if (!entryVisible.value) return
    const targetNodeId = nodeId || options.selectedNodeId()
    const reason = gateReasonFor(targetNodeId)
    if (reason) {
      ElMessage.warning(reason)
      return
    }
    const documentId = options.documentId()
    const token = ++queryToken
    let latest: AiTaskDetail | null
    try {
      latest = await fetchLatestAiTask('case_optimize', { documentId, nodeId: targetNodeId })
    } catch (err) {
      if (token !== queryToken) return
      // 查询失败不自动发起，避免误耗额度
      ElMessage.error(err instanceof Error && err.message ? err.message : '查询该节点任务失败')
      return
    }
    if (token !== queryToken) return
    if (latest) {
      // 命中最近任务：进行中落进度态，已产出提案 / 已处理落对照面板，终止落失败态
      panelVisible.value = true
      activeNodeId.value = targetNodeId
      await task.open(latest.taskId)
      return
    }
    await startOptimize(targetNodeId)
  }

  async function retry(): Promise<void> {
    const nodeId = activeNodeId.value || options.selectedNodeId()
    // 已处理完毕的任务后端不允许再次执行，按同一输入发起新任务（交互 2.4）
    if (task.task.value?.status === 'closed') {
      await task.launch({
        documentId: options.documentId(),
        nodeIds: [nodeId],
      })
      return
    }
    await task.retry()
  }

  function refresh(): Promise<void> {
    return task.refresh()
  }

  /** 从当前提案取节点操作：整条采纳 / 批量采纳使用提案原值 */
  function operationsFor(key: string): AiCaseOptimizeOperation[] {
    const suggestion = task.rows.value.find((row) => row.key === key)?.suggestion ?? null
    return isCaseOptimizeSuggestion(suggestion) ? suggestion.operations : []
  }

  /** 提案锚点即被优化的用例节点，采纳时据此打 AI 标识 */
  function caseNodeIdFor(key: string): string | null {
    const suggestion = task.rows.value.find((row) => row.key === key)?.suggestion ?? null
    return isCaseOptimizeSuggestion(suggestion) ? suggestion.nodeId : null
  }

  function runOperations(operations: AiCaseOptimizeOperation[], caseNodeId: string | null): void {
    if (operations.length > 0 || caseNodeId) options.executeOperations?.(operations, caseNodeId)
  }

  function adopt(key: string): Promise<void> {
    runOperations(operationsFor(key), caseNodeIdFor(key))
    return task.adopt(key)
  }

  /** 编辑后采纳：执行人工核对后的操作，payload 定位字段与提案一致 */
  function adoptEdited(key: string, content: Record<string, unknown>): Promise<void> {
    runOperations(readWireOperations(content), caseNodeIdFor(key))
    return task.adoptEdited(key, content)
  }

  function batchAdopt(keys: string[]): Promise<void> {
    keys.forEach((key) => runOperations(operationsFor(key), caseNodeIdFor(key)))
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
    retryable,
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
