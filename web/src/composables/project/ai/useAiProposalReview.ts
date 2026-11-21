import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmAiProposals, fetchAiProposal } from '@/services/ai'
import { fetchProjectModuleTree } from '@/services/project'
import type {
  AiProposalAction,
  AiProposalConfirmItem,
  AiProposalConfirmResult,
  AiProposalConfirmTarget,
  AiTaskProposalSummary,
  ProjectModule,
} from '@/types'
import {
  aiProposalConfirmMeta,
  aiProposalKindLabel,
  aiProposalProcessed,
} from '@/composables/project/ai/taskPresentation'
import {
  reviewProfile,
  type ProposalParsed,
} from '@/composables/project/ai/reviewProfiles'

/** 单次确认上限（详设 3.6.5 items @Size(max=200)），超限分批顺序提交 */
const CONFIRM_MAX_ITEMS = 200
const NOTE_MAX_LENGTH = 500
/** 预取提案内容的并发上限，避免长提案列表打爆同域连接 */
const PRELOAD_CONCURRENCY = 6

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function emptyParsed(title: string): ProposalParsed {
  return {
    description: '',
    fields: [],
    compare: [],
    rankItems: [],
    sourceRefs: [],
    warnings: [],
    editValues: { title },
  }
}

/** 需求挂在模块目录上，选择树只留目录（文档不承载需求） */
function stripDocuments(nodes: ProjectModule[]): ProjectModule[] {
  return nodes
    .filter((node) => node.type === 'directory')
    .map((node) => ({ ...node, children: stripDocuments(node.children) }))
}

function readString(source: Record<string, unknown>, key: string): string {
  const value = source[key]
  return typeof value === 'string' ? value : ''
}

/** 提案内容（提案详情 content 结构不定型，按需取字段，缺省回退摘要标题） */
export interface AiProposalContent {
  title: string
  description: string
  moduleId: string
  priority: string
  sourceRef: string
}

const EMPTY_CONTENT: AiProposalContent = {
  title: '',
  description: '',
  moduleId: '',
  priority: '',
  sourceRef: '',
}

/** 审核行：提案摘要 + 派生展示字段与已加载内容 */
export interface ProposalRow extends AiTaskProposalSummary {
  /** 派生展示字段 */
  kindLabel: string
  confirmMeta: ReturnType<typeof aiProposalConfirmMeta>
  content: AiProposalContent
  contentLoaded: boolean
  /** 原始 content 与按任务类型策略解析的结构化视图（方案驱动预览与编辑） */
  rawContent: Record<string, unknown>
  parsed: ProposalParsed
}

/** 逐项回执行（AiConfirmReceipt 展示与单项重试载荷，非任务重试） */
export interface ConfirmItemResult {
  key: string
  title: string
  action: string
  success: boolean
  errorCode: number | null
  errorMsg: string
  /** 落库资源 id（承接服务返回新建资源时用于成功跳转等后续动作） */
  createdId: string | null
}

export function useAiProposalReview(
  taskId: string,
  proposals: () => AiTaskProposalSummary[],
  canConfirm: () => boolean,
  onConfirmed: () => void,
  taskType?: string | (() => string),
) {
  const profile = computed(() =>
    reviewProfile(typeof taskType === 'function' ? taskType() : (taskType ?? '')),
  )

  const rows = ref<ProposalRow[]>([])
  const selectedKeys = ref<string[]>([])
  const activeKey = ref('')
  const loadingContent = ref(false)
  const moduleTree = ref<ProjectModule[]>([])
  const confirming = ref(false)
  const receipt = ref<ConfirmItemResult[]>([])

  /** 已处理 N / 共 M（交互 2.3 头部） */
  const processed = computed(() => aiProposalProcessed(rows.value))

  const activeRow = computed(
    () => rows.value.find((row) => row.key === activeKey.value) ?? null,
  )

  const selectableRows = computed(() =>
    profile.value.allowAdopt
      ? rows.value.filter((row) => row.confirmStatus === 'pending')
      : [],
  )

  /** 审核文案、编辑字段与动作门禁由任务类型策略决定 */
  const reviewTitle = computed(() => profile.value.title)
  const editFields = computed(() => profile.value.editFields)
  const canEdit = computed(() => profile.value.allowEdit)
  const canReject = computed(() => profile.value.allowReject)
  const canBatch = computed(() => profile.value.allowBatch)
  const needModuleTree = computed(() => profile.value.needModuleTree)
  const needSystemVersion = computed(() => profile.value.needSystemVersion)

  /** 编辑弹窗初值统一取策略解析结果，避免组件重复解析原始 content */
  function editValuesOf(row: ProposalRow): Record<string, unknown> {
    return { ...row.parsed.editValues }
  }

  const allSelected = computed(
    () =>
      selectableRows.value.length > 0 &&
      selectedKeys.value.length === selectableRows.value.length,
  )

  function syncRows(): void {
    const incoming = proposals()
    rows.value = incoming.map((proposal) => {
      const existing = rows.value.find((row) => row.key === proposal.key)
      return {
        ...proposal,
        kindLabel: aiProposalKindLabel(proposal.kind),
        confirmMeta: aiProposalConfirmMeta(proposal.confirmStatus),
        content: existing?.content ?? { ...EMPTY_CONTENT },
        contentLoaded: existing?.contentLoaded ?? false,
        rawContent: existing?.rawContent ?? {},
        parsed: existing?.parsed ?? emptyParsed(proposal.title ?? ''),
      }
    })
    // 已确认项移出选择集，避免残留选择态
    selectedKeys.value = selectedKeys.value.filter((key) =>
      rows.value.some((row) => row.key === key && row.confirmStatus === 'pending'),
    )
    if (activeKey.value && !rows.value.some((row) => row.key === activeKey.value)) {
      activeKey.value = ''
    }
  }

  // ==================== 内容加载 ====================
  async function loadContent(row: ProposalRow): Promise<void> {
    if (row.contentLoaded) return
    loadingContent.value = true
    try {
      const payload = await fetchAiProposal(taskId, row.key)
      const raw = payload.content ?? {}
      row.rawContent = raw
      row.parsed = profile.value.parse(raw, row.title || '')
      row.content = {
        // 不同任务以 title 或 name 表示标题，缺省回退摘要标题
        title: readString(raw, 'title') || readString(raw, 'name') || row.title || '',
        description: readString(raw, 'description'),
        moduleId: readString(raw, 'moduleId'),
        priority: readString(raw, 'priority'),
        sourceRef: readString(raw, 'sourceRef'),
      }
      row.contentLoaded = true
    } catch (err) {
      // 内容加载失败保留只读占位，不阻塞其余动作
      ElMessage.error(errorMessage(err, '加载提案内容失败'))
    } finally {
      loadingContent.value = false
    }
  }

  function select(key: string): void {
    activeKey.value = key
    const row = rows.value.find((item) => item.key === key)
    if (row) void loadContent(row)
  }

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

  // ==================== 确认动作 ====================
  /** 批量确认：items 超过单次上限时分批顺序提交，前序批次落库后再提交后续批次 */
  async function submitItems(
    items: AiProposalConfirmItem[],
    target?: AiProposalConfirmTarget,
  ): Promise<boolean> {
    if (items.length === 0) {
      ElMessage.warning('请先选择要处理的提案')
      return false
    }
    confirming.value = true
    try {
      const results: AiProposalConfirmResult[] = []
      for (let start = 0; start < items.length; start += CONFIRM_MAX_ITEMS) {
        const chunk = items.slice(start, start + CONFIRM_MAX_ITEMS)
        const resp = await confirmAiProposals(taskId, { items: chunk, target })
        results.push(...resp.results)
      }
      receipt.value = renderReceipt(results, items)
      const failure = results.filter((item) => !item.success)
      if (failure.length === 0) {
        ElMessage.success(
          items.every((item) => item.action === 'rejected')
            ? '已全部驳回，未创建任何数据'
            : '确认完成',
        )
      }
      // 服务端确认状态覆盖本地缓存，刷新提案清单（状态管理 4）
      onConfirmed()
      return true
    } catch (err) {
      ElMessage.error(errorMessage(err, '确认提案失败'))
      return false
    } finally {
      confirming.value = false
    }
  }

  function renderReceipt(
    results: AiProposalConfirmResult[],
    items: AiProposalConfirmItem[],
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
        createdId: result.createdId ?? null,
      }
    })
  }

  function itemsFor(keys: string[], action: AiProposalAction): AiProposalConfirmItem[] {
    return keys.map((key) => ({ key, action }))
  }

  function handleAdopt(row: ProposalRow, target?: AiProposalConfirmTarget): Promise<boolean> {
    return submitItems(itemsFor([row.key], 'adopted'), target)
  }

  function handleReject(
    row: ProposalRow,
    note: string | undefined,
    target?: AiProposalConfirmTarget,
  ): Promise<boolean> {
    const item: AiProposalConfirmItem = { key: row.key, action: 'rejected' }
    if (note && note.trim()) item.note = note.trim()
    return submitItems([item], target)
  }

  function handleBatchAdopt(target?: AiProposalConfirmTarget): Promise<boolean> {
    return submitItems(itemsFor(selectedKeys.value, 'adopted'), target)
  }

  /** 编辑后采纳：载荷由任务类型策略组装，避免不同承接器收到错位字段 */
  function handleAdoptEdited(
    row: ProposalRow,
    values: Record<string, unknown>,
    target?: AiProposalConfirmTarget,
  ): Promise<boolean> {
    const content = profile.value.buildEditedContent(values, row.rawContent)
    return submitItems([{ key: row.key, action: 'adopted_edited', content }], target)
  }

  async function handleBatchReject(target?: AiProposalConfirmTarget): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `将驳回所选 ${selectedKeys.value.length} 条提案，不创建任何数据。`,
        '批量驳回',
        { type: 'warning', confirmButtonText: '全部驳回', cancelButtonText: '取消' },
      )
    } catch {
      return false
    }
    return submitItems(itemsFor(selectedKeys.value, 'rejected'), target)
  }

  /** 回执失败项单项重试（交互 2.3 回执，重试的是采纳动作而非任务） */
  function retryResult(result: ConfirmItemResult): Promise<boolean> {
    const item = result.action as AiProposalAction
    return submitItems([{ key: result.key, action: item }])
  }

  function clearReceipt(): void {
    receipt.value = []
  }

  async function loadModuleTree(): Promise<void> {
    try {
      moduleTree.value = stripDocuments(await fetchProjectModuleTree())
    } catch {
      // 模块树失败降级为纯标题/描述审核，不阻塞确认动作
      moduleTree.value = []
    }
  }

  /** 并发预取未确认提案内容，避免长提案列表串行加载 */
  async function preloadContents(): Promise<void> {
    const targets = rows.value.filter(
      (row) => row.confirmStatus === 'pending' && !row.contentLoaded,
    )
    let cursor = 0
    const worker = async (): Promise<void> => {
      while (cursor < targets.length) {
        const row = targets[cursor]
        cursor += 1
        await loadContent(row)
      }
    }
    await Promise.all(
      Array.from({ length: Math.min(PRELOAD_CONCURRENCY, targets.length) }, () => worker()),
    )
  }

  function init(): void {
    syncRows()
    if (needModuleTree.value) void loadModuleTree()
  }

  return {
    rows,
    selectedKeys,
    activeKey,
    activeRow,
    loadingContent,
    moduleTree,
    confirming,
    receipt,
    processed,
    selectableRows,
    allSelected,
    canConfirm: computed(() => canConfirm()),
    profile,
    reviewTitle,
    editFields,
    editValuesOf,
    canEdit,
    canReject,
    canBatch,
    needModuleTree,
    needSystemVersion,
    CONFIRM_MAX_ITEMS,
    NOTE_MAX_LENGTH,
    syncRows,
    init,
    select,
    toggle,
    toggleAll,
    loadContent,
    preloadContents,
    submitItems,
    handleAdopt,
    handleAdoptEdited,
    handleReject,
    handleBatchAdopt,
    handleBatchReject,
    retryResult,
    clearReceipt,
  }
}
