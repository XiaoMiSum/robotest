import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAiStatus } from '@/services/ai'
import { useAssistTask } from '@/composables/project/ai/useAssistTask'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import { caseGenerationNodeOf, isCaseGenerationSuggestion } from '@/composables/project/ai/assistPresentation'
import type { RequirementPick } from '@/composables/project/requirement/useRequirementPicker'
import type { AiProposalConfirmResult } from '@/types'
import type { AiGeneratedCase, CaseGenerationInsertResult } from '@/composables/project/functional-testing/mindmap/useCaseGenerationInsert'

export interface CaseGenerationEntryOptions {
  /** 当前脑图文档，作为任务输入的 documentId（C4：项目上下文走请求头） */
  documentId: () => string
  /** 文档只读时入口置灰（详设 4.4） */
  isReadonly?: () => boolean
  /** 勾选提案后在当前脑图树插入节点，由宿主注入以访问 Minder 与持久化 */
  insertCases: (cases: AiGeneratedCase[]) => CaseGenerationInsertResult
}

/**
 * 脑图工具栏「AI测试用例」入口（详设 5.1 / 5.3）：需求单选 → 新任务 → 轮询提案 →
 * 提案映射为可勾选节点树 → 插入后回传确认状态。本类型不复用节点级 /latest 任务。
 */
export function useCaseGenerationEntry(options: CaseGenerationEntryOptions) {
  const aiAvailable = ref(false)

  async function loadAvailability(): Promise<void> {
    try {
      aiAvailable.value = (await fetchAiStatus()).available
    } catch {
      aiAvailable.value = false
    }
  }

  onMounted(loadAvailability)

  const entryVisible = computed(() => aiAvailable.value)
  const gateReason = computed(() => {
    if (!options.documentId()) return '请先打开用例文档'
    if (options.isReadonly?.() === true) return '当前文档只读，不可生成用例'
    return ''
  })
  const canLaunch = computed(() => entryVisible.value && gateReason.value === '')

  const pickerVisible = ref(false)
  const panelVisible = ref(false)
  const selectedRequirement = ref<RequirementPick | null>(null)
  const pickerPicks = computed<RequirementPick[]>(() =>
    selectedRequirement.value ? [selectedRequirement.value] : [],
  )
  const lastInput = ref<Record<string, string>>({})

  const task = useAssistTask('mindmap_case_generation', {
    target: () => ({ documentId: options.documentId() }),
  })

  const rows = computed<AssistRow[]>(() => task.rows.value)
  const loading = computed(() => task.loading.value)
  const confirming = computed(() => task.confirming.value)
  const receipt = computed<AiProposalConfirmResult[]>(() => task.receipt.value)
  const running = computed(() => task.submitting.value || task.isRunning.value)
  const failed = computed(() => task.isFailed.value)
  const failedReason = computed(() => task.failedReason.value)
  const retryable = computed(() => rows.value.length > 0)
  const progressText = computed(() => task.executionSummary.value?.text ?? '')
  const loadError = computed(() => task.loadError.value)

  // 面板关闭统一在这条 watch 收口：X / ESC / 页脚按钮都会置 false，需停轮询并清空输入
  watch(panelVisible, (visible) => {
    if (!visible) {
      task.reset()
      selectedRequirement.value = null
      lastInput.value = {}
    }
  })

  watch(
    () => options.documentId(),
    () => closePanel(),
  )

  function openPicker(): void {
    if (!entryVisible.value) return
    const reason = gateReason.value
    if (reason) {
      ElMessage.warning(reason)
      return
    }
    pickerVisible.value = true
  }

  async function confirmRequirement(ids: string[], picks: RequirementPick[]): Promise<void> {
    if (ids.length !== 1) {
      ElMessage.warning('请选择一条需求')
      return
    }
    selectedRequirement.value = picks.find((pick) => pick.id === ids[0]) ?? {
      id: ids[0],
      code: '',
      title: '',
    }
    pickerVisible.value = false
    panelVisible.value = true
    lastInput.value = { documentId: options.documentId(), requirementId: ids[0] }
    await task.launch({ ...lastInput.value })
  }

  function retry(): Promise<void> {
    // 已关闭任务后端不允许再执行，按同一输入发起新任务
    if (task.task.value?.status === 'closed' && Object.keys(lastInput.value).length > 0) {
      return task.launch({ ...lastInput.value })
    }
    return task.retry()
  }

  /** 先创建脑图节点，成功后才回传提案采纳状态（详设 4.4：确认协议不执行节点变更） */
  async function insert(keys: string[]): Promise<void> {
    const cases: AiGeneratedCase[] = []
    for (const key of keys) {
      const row = rows.value.find((item) => item.key === key)
      if (!row) continue
      const view = row.suggestion
      if (!view || !isCaseGenerationSuggestion(view)) continue
      cases.push({
        key,
        node: caseGenerationNodeOf(row.key, row.title, view),
        tags: view.tags,
      })
    }
    if (cases.length === 0) {
      ElMessage.warning('请先勾选要插入的用例')
      return
    }
    const result = options.insertCases(cases)
    if (result.inserted === 0) {
      ElMessage.warning('未能在脑图中插入用例')
      return
    }
    await task.submitItems(
      cases.map((item) => ({ key: item.key, action: 'adopted' as const })),
      { documentId: options.documentId() },
    )
  }

  /** 回执单项重试只重传确认状态：节点已在首次插入时创建，重传不得再次插入 */
  function confirmAgain(key: string): Promise<void> {
    return task.submitItems([{ key, action: 'adopted' }], { documentId: options.documentId() })
  }

  function refresh(): Promise<void> {
    return task.refresh()
  }

  function clearReceipt(): void {
    task.clearReceipt()
  }

  function closePanel(): void {
    panelVisible.value = false
  }

  return {
    entryVisible,
    gateReason,
    canLaunch,
    pickerVisible,
    pickerPicks,
    selectedRequirement,
    panelVisible,
    rows,
    loading,
    confirming,
    receipt,
    running,
    failed,
    failedReason,
    retryable,
    progressText,
    loadError,
    openPicker,
    confirmRequirement,
    insert,
    retry,
    confirmAgain,
    refresh,
    clearReceipt,
    closePanel,
  }
}
