import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmAiArtifacts, fetchAiArtifact } from '@/services/ai'
import { fetchProjectModuleTree, getBugDetail } from '@/services/project'
import { aiArtifactProcessed } from '@/composables/project/ai/taskPresentation'
import type { ConfirmItemResult } from '@/composables/project/ai/useAiArtifactReview'
import type {
  AiArtifactAction,
  AiArtifactConfirmItem,
  AiArtifactConfirmResult,
  AiArtifactConfirmStatus,
  AiArtifactSummary,
  BugClassifyContent,
  BugClassifySuggestions,
  BugDuplicateGroupContent,
  BugPriority,
  BugSeverity,
  BugType,
  ProjectModule,
} from '@/types'

/** 单次确认上限（详设 3.6.5 items @Size(max=200)），超限分批顺序提交 */
const CONFIRM_MAX_ITEMS = 200
/** 产物内容与缺陷当前分类预取并发上限，避免长清单打爆同域连接 */
const PRELOAD_CONCURRENCY = 6

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function readString(raw: unknown): string | null {
  return typeof raw === 'string' && raw.length > 0 ? raw : null
}

/** 建议字段 value 为字符串（详设 3.6 产物示例），keywords 单独按数组解析 */
function readSuggestionField(raw: unknown): { value: string; reason: string } | null {
  if (typeof raw !== 'object' || raw === null) return null
  const source = raw as Record<string, unknown>
  const value = source['value']
  if (typeof value !== 'string' || !value) return null
  return { value, reason: typeof source['reason'] === 'string' ? source['reason'] : '' }
}

function parseClassifyContent(raw: Record<string, unknown>): BugClassifyContent {
  const suggestionsRaw = (raw['suggestions'] ?? {}) as Record<string, unknown>
  const suggestions: BugClassifySuggestions = {}
  const bugType = readSuggestionField(suggestionsRaw['bugType'])
  if (bugType) suggestions.bugType = { value: bugType.value as BugType, reason: bugType.reason }
  const severity = readSuggestionField(suggestionsRaw['severity'])
  if (severity) suggestions.severity = { value: severity.value as BugSeverity, reason: severity.reason }
  const priority = readSuggestionField(suggestionsRaw['priority'])
  if (priority) suggestions.priority = { value: priority.value as BugPriority, reason: priority.reason }
  const moduleId = readSuggestionField(suggestionsRaw['moduleId'])
  if (moduleId) suggestions.moduleId = { value: moduleId.value, reason: moduleId.reason }

  // keywords 的 value 为字符串数组（承接侧 stringList 仅接受 List，交串会解析为空）
  const rawKeywords = suggestionsRaw['keywords']
  if (typeof rawKeywords === 'object' && rawKeywords !== null) {
    const source = rawKeywords as Record<string, unknown>
    const values = Array.isArray(source['value'])
      ? source['value'].filter((item): item is string => typeof item === 'string' && !!item.trim())
      : []
    suggestions.keywords = {
      value: values,
      reason: typeof source['reason'] === 'string' ? source['reason'] : '',
    }
  }

  const candidates = Array.isArray(raw['assigneeCandidates']) ? raw['assigneeCandidates'] : []
  const assigneeCandidates = candidates.flatMap((item) => {
    if (typeof item !== 'object' || item === null) return []
    const source = item as Record<string, unknown>
    const userId = readString(source['userId'])
    if (!userId) return []
    // 服务端已过滤为当前空间成员，memberValid=false 仅作前端防御剔除
    if (source['memberValid'] === false) return []
    return [{
      userId,
      name: typeof source['name'] === 'string' ? source['name'] : userId,
      reason: typeof source['reason'] === 'string' ? source['reason'] : '',
      memberValid: true,
    }]
  })

  const refs = Array.isArray(raw['sourceRefs']) ? raw['sourceRefs'] : []
  const sourceRefs = refs.flatMap((item) => {
    if (typeof item !== 'object' || item === null) return []
    const source = item as Record<string, unknown>
    const id = readString(source['id'])
    if (!id) return []
    return [{
      type: typeof source['type'] === 'string' ? source['type'] : 'bug',
      id,
      title: typeof source['title'] === 'string' ? source['title'] : '',
      quote: typeof source['quote'] === 'string' ? source['quote'] : undefined,
    }]
  })

  return {
    bugId: readString(raw['bugId']) ?? undefined,
    suggestions,
    assigneeCandidates,
    sourceRefs,
  }
}

function parseDuplicateGroupContent(raw: Record<string, unknown>): BugDuplicateGroupContent {
  const rawItems = Array.isArray(raw['items']) ? raw['items'] : []
  return {
    canonicalBugId: readString(raw['canonicalBugId']),
    items: rawItems.flatMap((item) => {
      if (typeof item !== 'object' || item === null) return []
      const source = item as Record<string, unknown>
      const bugId = readString(source['bugId'])
      if (!bugId) return []
      return [{
        bugId,
        similarity: typeof source['similarity'] === 'number' ? source['similarity'] : 0,
        reason: typeof source['reason'] === 'string' ? source['reason'] : '',
      }]
    }),
  }
}

/** 缺陷当前分类（审核区「当前 vs 建议」对照，取自既有缺陷详情） */
export interface BugCurrentClassification {
  bugType: BugType
  severity: BugSeverity
  priority: BugPriority
  moduleId: string | null
  keywords: string
  assigneeName: string
}

/** 审核行：产物内容 + 缺陷当前分类（按 kind 取 content 或 group） */
export interface BugClassifyRow {
  key: string
  kind: string
  title: string
  confirmStatus: AiArtifactConfirmStatus
  content: BugClassifyContent | null
  group: BugDuplicateGroupContent | null
  current: BugCurrentClassification | null
  loaded: boolean
}

/** 编辑后采纳的可编辑字段（详设 3.6.5 adopted_edited：content 覆盖产物原内容） */
export interface BugClassifyEdit {
  bugType: BugType
  severity: BugSeverity
  priority: BugPriority
  moduleId: string
  keywords: string
  assigneeId: string
}

/**
 * 批量分类与重复组审核（详设 3.6 / 3.9 + 交互 2.3）：
 * 分类建议逐条采纳 / 修改后采纳 / 批量采纳 / 驳回附反馈，采纳经既有缺陷服务部分更新字段；
 * 重复组确认只写留痕，不修改任何缺陷，确认后由人工按既有「重复缺陷」流程逐条处理。
 */
export function useBugClassifyReview(
  taskId: string,
  artifacts: () => AiArtifactSummary[],
  canConfirm: () => boolean,
  onConfirmed: () => void,
) {
  const rows = ref<BugClassifyRow[]>([])
  const selectedKeys = ref<string[]>([])
  const confirming = ref(false)
  const receipt = ref<ConfirmItemResult[]>([])
  /** 模块树：对照列映射名称 + 修改后采纳对话框的树选择数据源 */
  const moduleTree = ref<ProjectModule[]>([])

  const processed = computed(() => aiArtifactProcessed(rows.value))
  const canConfirmState = computed(() => canConfirm())

  const classifyRows = computed(() => rows.value.filter((row) => row.kind === 'classify_suggestion'))
  const duplicateRows = computed(() => rows.value.filter((row) => row.kind === 'duplicate_group'))
  /** 只读产物（摘要 / 分诊顺序）走预览，不提供确认动作 */
  const readonlyRows = computed(() =>
    rows.value.filter((row) => row.kind !== 'classify_suggestion' && row.kind !== 'duplicate_group'),
  )
  const selectableRows = computed(() => rows.value.filter((row) => row.confirmStatus === 'pending'))
  const allSelected = computed(
    () => selectableRows.value.length > 0
      && selectedKeys.value.length === selectableRows.value.length,
  )

  function syncRows(): void {
    rows.value = artifacts().map((artifact) => {
      const existing = rows.value.find((row) => row.key === artifact.key)
      return {
        key: artifact.key,
        kind: artifact.kind,
        title: artifact.title ?? artifact.key,
        confirmStatus: artifact.confirmStatus,
        content: existing?.content ?? null,
        group: existing?.group ?? null,
        current: existing?.current ?? null,
        loaded: existing?.loaded ?? false,
      }
    })
    // 确认完成后已处理项移出选择集，避免残留选择态
    selectedKeys.value = selectedKeys.value.filter((key) =>
      rows.value.some((row) => row.key === key && row.confirmStatus === 'pending'),
    )
  }

  // ==================== 内容加载 ====================

  async function loadCurrent(bugId: string): Promise<BugCurrentClassification | null> {
    try {
      const detail = await getBugDetail(bugId)
      return {
        bugType: detail.bugType,
        severity: detail.severity,
        priority: detail.priority,
        moduleId: detail.moduleId,
        keywords: detail.keywords ?? '',
        assigneeName: detail.assignee?.name ?? '',
      }
    } catch {
      // 详情加载失败仅降级对照列为空：采纳仍按产物内容由服务端落库
      return null
    }
  }

  async function loadRow(row: BugClassifyRow): Promise<void> {
    if (row.loaded) return
    try {
      const artifact = await fetchAiArtifact(taskId, row.key)
      const raw = (artifact['content'] ?? {}) as Record<string, unknown>
      if (row.kind === 'duplicate_group') {
        row.group = parseDuplicateGroupContent(raw)
      } else {
        row.content = parseClassifyContent(raw)
        // 批量产物 content 携带 bugId → 并发取当前分类做「当前 vs 建议」对照
        if (row.content.bugId) row.current = await loadCurrent(row.content.bugId)
      }
      row.loaded = true
    } catch (err) {
      // 单行失败保留未加载态供重试，不阻塞其余审核动作
      ElMessage.error(errorMessage(err, '加载产物内容失败'))
    }
  }

  /** 并发预取未确认行内容（与生成链同口径） */
  async function preloadContents(): Promise<void> {
    const targets = rows.value.filter((row) => row.confirmStatus === 'pending' && !row.loaded)
    let cursor = 0
    const worker = async (): Promise<void> => {
      while (cursor < targets.length) {
        const row = targets[cursor]
        cursor += 1
        await loadRow(row)
      }
    }
    await Promise.all(
      Array.from({ length: Math.min(PRELOAD_CONCURRENCY, targets.length) }, () => worker()),
    )
  }

  // ==================== 选择 ====================

  function toggle(key: string, checked: boolean): void {
    if (checked) {
      if (!selectedKeys.value.includes(key)) selectedKeys.value.push(key)
    } else {
      selectedKeys.value = selectedKeys.value.filter((item) => item !== key)
    }
  }

  function toggleAll(checked: boolean): void {
    selectedKeys.value = checked ? selectableRows.value.map((row) => row.key) : []
  }

  // ==================== 确认 ====================

  function renderReceipt(
    results: AiArtifactConfirmResult[],
    items: AiArtifactConfirmItem[],
  ): ConfirmItemResult[] {
    return results.map((result) => {
      const request = items.find((item) => item.key === result.key)
      const row = rows.value.find((item) => item.key === result.key)
      return {
        key: result.key,
        title: row?.title || result.key,
        action: request?.action ?? result.action,
        success: result.success,
        errorCode: result.errorCode,
        errorMsg: result.errorMsg ?? '',
        createdId: result.createdId,
      }
    })
  }

  async function submitItems(items: AiArtifactConfirmItem[]): Promise<void> {
    if (items.length === 0) {
      ElMessage.warning('请先选择要处理的产物')
      return
    }
    confirming.value = true
    try {
      const results: AiArtifactConfirmResult[] = []
      for (let start = 0; start < items.length; start += CONFIRM_MAX_ITEMS) {
        const chunk = items.slice(start, start + CONFIRM_MAX_ITEMS)
        const resp = await confirmAiArtifacts(taskId, { items: chunk })
        results.push(...resp.results)
      }
      receipt.value = renderReceipt(results, items)
      if (results.every((item) => item.success)) {
        ElMessage.success(
          items.every((item) => item.action === 'rejected')
            ? '已全部驳回，未更新任何缺陷'
            : '确认完成',
        )
      }
      onConfirmed()
    } catch (err) {
      ElMessage.error(errorMessage(err, '确认产物失败'))
    } finally {
      confirming.value = false
    }
  }

  /** 单条直接采纳：服务端按产物原内容经既有缺陷服务部分更新字段 */
  function handleAdopt(row: BugClassifyRow): Promise<void> {
    return submitItems([{ key: row.key, action: 'adopted' }])
  }

  /** 所选 pending 项批量采纳（交互 2.3） */
  async function handleBatchAdopt(): Promise<void> {
    await submitItems(
      selectedKeys.value.map((key) => ({ key, action: 'adopted' as AiArtifactAction })),
    )
  }

  /**
   * 修改后采纳：content 携带 bugId + 编辑后 suggestions，
   * 承接侧（BugClassifyAdopter）按编辑值部分更新；keywords 交数组（stringList 口径）。
   */
  function handleAdoptEdited(row: BugClassifyRow, edit: BugClassifyEdit): Promise<void> {
    const original = row.content?.suggestions ?? {}
    const keywords = edit.keywords
      .split(/\s+/)
      .map((item) => item.trim())
      .filter(Boolean)
      .slice(0, 5)
    const suggestions: Record<string, { value: string | string[] | null; reason: string }> = {
      bugType: { value: edit.bugType, reason: original.bugType?.reason ?? '' },
      severity: { value: edit.severity, reason: original.severity?.reason ?? '' },
      priority: { value: edit.priority, reason: original.priority?.reason ?? '' },
      moduleId: { value: edit.moduleId, reason: original.moduleId?.reason ?? '' },
      // 空数组时承接侧不更新该字段（保持原值），与后端口径一致
      keywords: { value: keywords, reason: original.keywords?.reason ?? '' },
      assigneeId: { value: edit.assigneeId, reason: '' },
    }
    return submitItems([{
      key: row.key,
      action: 'adopted_edited',
      content: { bugId: row.content?.bugId, suggestions },
    }])
  }

  /** 单条驳回附反馈：note 记录结论（详设 3.6.5），不更新任何缺陷 */
  function handleReject(row: BugClassifyRow, note: string): Promise<void> {
    const item: AiArtifactConfirmItem = { key: row.key, action: 'rejected' }
    if (note.trim()) item.note = note.trim()
    return submitItems([item])
  }

  /** 批量驳回：确认后整批 rejected */
  async function handleBatchReject(): Promise<void> {
    if (selectedKeys.value.length === 0) {
      ElMessage.warning('请先选择要处理的产物')
      return
    }
    try {
      await ElMessageBox.confirm(
        `将驳回所选 ${selectedKeys.value.length} 条建议，不更新任何缺陷。`,
        '批量驳回',
        { type: 'warning', confirmButtonText: '全部驳回', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    await submitItems(
      selectedKeys.value.map((key) => ({ key, action: 'rejected' as AiArtifactAction })),
    )
  }

  /** 重复组确认：adopted=确认疑似重复（留痕），rejected=排除误报；均不修改缺陷 */
  function handleGroupConfirm(
    row: BugClassifyRow,
    action: Extract<AiArtifactAction, 'adopted' | 'rejected'>,
    note: string,
  ): Promise<void> {
    const item: AiArtifactConfirmItem = { key: row.key, action }
    if (note.trim()) item.note = note.trim()
    return submitItems([item])
  }

  function retryResult(result: ConfirmItemResult): Promise<void> {
    return submitItems([{ key: result.key, action: result.action as AiArtifactAction }])
  }

  function clearReceipt(): void {
    receipt.value = []
  }

  function init(): void {
    syncRows()
    void preloadContents()
    // 模块树加载失败不阻塞审核：对照列降级显示模块 id
    void fetchProjectModuleTree()
      .then((tree) => {
        moduleTree.value = tree
      })
      .catch(() => {})
  }

  /** 模块 id → 名称（建议侧只带 id，对照展示需树映射） */
  function moduleNameOf(moduleId: string | null): string {
    if (!moduleId) return '未指定模块'
    const stack = [...moduleTree.value]
    while (stack.length > 0) {
      const node = stack.pop()
      if (!node) break
      if (node.id === moduleId) return node.name
      stack.push(...node.children)
    }
    return moduleId
  }

  return {
    rows,
    classifyRows,
    duplicateRows,
    readonlyRows,
    selectedKeys,
    confirming,
    receipt,
    processed,
    canConfirmState,
    selectableRows,
    allSelected,
    moduleTree,
    moduleNameOf,
    CONFIRM_MAX_ITEMS,
    init,
    syncRows,
    loadRow,
    toggle,
    toggleAll,
    handleAdopt,
    handleBatchAdopt,
    handleAdoptEdited,
    handleReject,
    handleBatchReject,
    handleGroupConfirm,
    retryResult,
    clearReceipt,
  }
}
