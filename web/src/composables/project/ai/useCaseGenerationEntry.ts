import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAiStatus, fetchLatestAiTask } from '@/services/ai'
import { useAssistTask } from '@/composables/project/ai/useAssistTask'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import { caseGenerationNodeOf, isCaseGenerationSuggestion } from '@/composables/project/ai/assistPresentation'
import type { RequirementPick } from '@/composables/project/requirement/useRequirementPicker'
import type { AiProposalConfirmResult, AiTaskDetail } from '@/types'
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
 * 脑图工具栏「AI覆盖雷达」入口（详设 5.1 / 5.3）：先按文档复用最近一次生成任务，
 * 命中直接回显进度 / 提案，未命中才经需求单选发起新任务；提案映射为可勾选节点树，
 * 插入后回传确认状态。
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
  // 切换文档 / 关闭面板时递增，丢弃尚未返回的 /latest 查询，避免旧任务提案写入新面板
  let queryToken = 0

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

  function closePanel(): void {
    queryToken++
    panelVisible.value = false
    // 文档切换时同步停掉轮询，不等 watch 下个 tick，避免旧任务进度回写新文档面板
    task.reset()
    selectedRequirement.value = null
    lastInput.value = {}
  }

  /** 打开入口：先按文档查最近任务，命中直接回显；未命中才进入需求单选（详设 5.1） */
  async function openRadar(): Promise<void> {
    if (!entryVisible.value) return
    const reason = gateReason.value
    if (reason) {
      ElMessage.warning(reason)
      return
    }
    const documentId = options.documentId()
    const token = ++queryToken
    let latest: AiTaskDetail | null
    try {
      latest = await fetchLatestAiTask('mindmap_case_generation', { documentId })
    } catch (err) {
      if (token !== queryToken) return
      // 查询失败不自动发起，避免误耗额度
      ElMessage.error(err instanceof Error && err.message ? err.message : '查询该文档任务失败')
      return
    }
    if (token !== queryToken) return
    if (latest) {
      panelVisible.value = true
      await task.open(latest.taskId)
      return
    }
    pickerVisible.value = true
  }

  // 提案内容在 open 之后异步加载，须待 suggestion 就绪再还原；已从选择器选定的来源不被覆盖
  function restoreRequirement(): void {
    if (selectedRequirement.value) return
    for (const row of task.rows.value) {
      const view = row.suggestion
      if (!view || !isCaseGenerationSuggestion(view)) continue
      const ref = view.sourceRefs[0]
      if (!ref) continue
      selectedRequirement.value = { id: ref.id, code: ref.title, title: ref.title }
      lastInput.value = { documentId: options.documentId(), requirementId: ref.id }
      return
    }
  }

  watch(rows, restoreRequirement)

  async function confirmRequirement(ids: string[], picks: RequirementPick[]): Promise<void> {
    if (ids.length !== 1) {
      ElMessage.warning('请选择一条需求')
      return
    }
    queryToken++
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
    if (task.task.value?.status !== 'closed') return task.retry()
    if (Object.keys(lastInput.value).length > 0) {
      return task.launch({ ...lastInput.value })
    }
    // 既有任务无提案引用时拿不到来源需求，交由用户重选后再发起
    closePanel()
    pickerVisible.value = true
    return Promise.resolve()
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
    openRadar,
    confirmRequirement,
    insert,
    retry,
    confirmAgain,
    refresh,
    clearReceipt,
    closePanel,
  }
}
