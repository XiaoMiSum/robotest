import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmAiArtifacts, fetchAiArtifact } from '@/services/ai'
import { fetchProjectModuleTree } from '@/services/project'
import type {
  AiArtifactAction,
  AiArtifactConfirmItem,
  AiArtifactConfirmResult,
  AiArtifactConfirmTarget,
  AiArtifactSourceRef,
  AiArtifactSummary,
  AiCaseAttributes,
  ProjectModule,
} from '@/types'
import {
  aiArtifactConfirmMeta,
  aiArtifactKindLabel,
  aiArtifactProcessed,
} from '@/composables/project/ai/taskPresentation'

/** 单次确认上限（详设 3.6.5 items @Size(max=200)），超限按父先子序分批顺序提交 */
const CONFIRM_MAX_ITEMS = 200
const NOTE_MAX_LENGTH = 500
/** 预取产物内容的并发上限，避免长产物树打爆同域连接 */
const PRELOAD_CONCURRENCY = 6

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
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

function readStringList(raw: unknown): string[] {
  if (!Array.isArray(raw)) return []
  return raw.filter((item): item is string => typeof item === 'string')
}

/** attributes 结构不定型（生成链阶段 5），缺字段回退空值而非猜测 */
function readAttributes(raw: unknown): AiCaseAttributes {
  const source = (typeof raw === 'object' && raw !== null ? raw : {}) as Record<string, unknown>
  return {
    priority: readString(source, 'priority'),
    precondition: readString(source, 'precondition'),
    steps: readStringList(source['steps']),
    expected: readStringList(source['expected']),
    tags: readStringList(source['tags']),
  }
}

function readSourceRefs(raw: unknown): AiArtifactSourceRef[] {
  if (!Array.isArray(raw)) return []
  const refs: AiArtifactSourceRef[] = []
  for (const item of raw) {
    if (typeof item !== 'object' || item === null) continue
    const source = item as Record<string, unknown>
    refs.push({
      requirementId: readString(source, 'requirementId'),
      quote: readString(source, 'quote'),
      changed: source['changed'] === true,
    })
  }
  return refs
}

/** 产物内容（getArtifact 返回结构不定型，按需取字段，缺省回退 summary） */
export interface AiArtifactContent {
  title: string
  description: string
  moduleId: string
  priority: string
  sourceRef: string
  /** 生成链：是否用例节点（false 为分组节点） */
  isTestCase: boolean
  /** 生成链：文档内父节点引用（空为挂根，详设 3.3 层级约束） */
  parentRef: string
  /** 生成链：用例属性（非用例节点为默认空值） */
  attributes: AiCaseAttributes
  /** 生成链：来源需求引用（含回读比对的 changed 标注） */
  sourceRefs: AiArtifactSourceRef[]
  /** 生成链：疑似重复指向的既有资源 ID，非空时仅改名后可采纳 */
  suspectedDuplicateOf: string
}

const EMPTY_CONTENT: AiArtifactContent = {
  title: '',
  description: '',
  moduleId: '',
  priority: '',
  sourceRef: '',
  isTestCase: false,
  parentRef: '',
  attributes: { priority: '', precondition: '', steps: [], expected: [], tags: [] },
  sourceRefs: [],
  suspectedDuplicateOf: '',
}

export interface ArtifactRow extends AiArtifactSummary {
  /** 派生展示字段 */
  kindLabel: string
  confirmMeta: ReturnType<typeof aiArtifactConfirmMeta>
  content: AiArtifactContent
  contentLoaded: boolean
}

export interface ConfirmItemResult {
  key: string
  title: string
  action: string
  success: boolean
  errorCode: number | null
  errorMsg: string
  /** 落库资源 id（圈选确认返回新建的评审 / 计划 id，用于成功跳转） */
  createdId: string | null
}

export function useAiArtifactReview(
  taskId: string,
  artifacts: () => AiArtifactSummary[],
  canConfirm: () => boolean,
  onConfirmed: () => void,
) {
  const rows = ref<ArtifactRow[]>([])
  const selectedKeys = ref<string[]>([])
  const activeKey = ref('')
  const loadingContent = ref(false)
  const moduleTree = ref<ProjectModule[]>([])
  const confirming = ref(false)
  const receipt = ref<ConfirmItemResult[]>([])

  /** 已处理 N / 共 M（交互 2.3 头部） */
  const processed = computed(() => aiArtifactProcessed(rows.value))

  const activeRow = computed(
    () => rows.value.find((row) => row.key === activeKey.value) ?? null,
  )

  const selectableRows = computed(
    () => rows.value.filter((row) => row.confirmStatus === 'pending'),
  )

  const allSelected = computed(
    () =>
      selectableRows.value.length > 0 &&
      selectedKeys.value.length === selectableRows.value.length,
  )

  function syncRows(): void {
    const incoming = artifacts()
    rows.value = incoming.map((artifact) => {
      const existing = rows.value.find((row) => row.key === artifact.key)
      return {
        ...artifact,
        kindLabel: aiArtifactKindLabel(artifact.kind),
        confirmMeta: aiArtifactConfirmMeta(artifact.confirmStatus),
        content: existing?.content ?? { ...EMPTY_CONTENT },
        contentLoaded: existing?.contentLoaded ?? false,
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
  async function loadContent(row: ArtifactRow): Promise<void> {
    if (row.contentLoaded) return
    loadingContent.value = true
    try {
      const data = await fetchAiArtifact(taskId, row.key)
      const raw = (data['content'] ?? {}) as Record<string, unknown>
      const attributes = readAttributes(raw['attributes'])
      row.content = {
        // 模块 / 文档用 name、用例用 title（生成链 3.3），缺省回退 summary 标题
        title: readString(raw, 'title') || readString(raw, 'name') || row.title || '',
        description: readString(raw, 'description'),
        moduleId: readString(raw, 'moduleId'),
        priority: readString(raw, 'priority') || attributes.priority,
        sourceRef: typeof data['sourceRef'] === 'string' ? data['sourceRef'] : '',
        isTestCase: raw['isTestCase'] === true,
        parentRef: readString(raw, 'parentRef'),
        attributes,
        sourceRefs: readSourceRefs(raw['sourceRefs']),
        suspectedDuplicateOf: readString(raw, 'suspectedDuplicateOf'),
      }
      row.contentLoaded = true
    } catch (err) {
      // 内容加载失败保留只读占位，不阻塞其余动作
      ElMessage.error(errorMessage(err, '加载产物内容失败'))
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
  /**
   * 批量确认：items 超过单次上限时按传入顺序分批顺序提交（生成链整树采纳
   * 依赖父先子序，前序批次落库后后续批次才能通过父级校验，1000018210）。
   */
  async function submitItems(
    items: AiArtifactConfirmItem[],
    target?: AiArtifactConfirmTarget,
  ): Promise<void> {
    if (items.length === 0) {
      ElMessage.warning('请先选择要处理的产物')
      return
    }
    confirming.value = true
    try {
      const results: AiArtifactConfirmResult[] = []
      for (let start = 0; start < items.length; start += CONFIRM_MAX_ITEMS) {
        const chunk = items.slice(start, start + CONFIRM_MAX_ITEMS)
        const resp = await confirmAiArtifacts(taskId, { items: chunk, target })
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
      // 服务端确认状态覆盖本地缓存，刷新产物清单（状态管理 4）
      onConfirmed()
    } catch (err) {
      ElMessage.error(errorMessage(err, '确认产物失败'))
    } finally {
      confirming.value = false
    }
  }

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
        createdId: result.createdId ?? null,
      }
    })
  }

  function itemsFor(keys: string[], action: AiArtifactAction): AiArtifactConfirmItem[] {
    return keys.map((key) => ({ key, action }))
  }

  function handleAdopt(row: ArtifactRow, target?: AiArtifactConfirmTarget): Promise<void> {
    return submitItems(itemsFor([row.key], 'adopted'), target)
  }

  function handleReject(
    row: ArtifactRow,
    note: string | undefined,
    target?: AiArtifactConfirmTarget,
  ): Promise<void> {
    const item: AiArtifactConfirmItem = { key: row.key, action: 'rejected' }
    if (note && note.trim()) item.note = note.trim()
    return submitItems([item], target)
  }

  function handleBatchAdopt(target?: AiArtifactConfirmTarget): Promise<void> {
    return submitItems(itemsFor(selectedKeys.value, 'adopted'), target)
  }

  /** 编辑后采纳：content 覆盖产物原内容（详设 3.6.5 adopted_edited） */
  function handleAdoptEdited(
    row: ArtifactRow,
    content: { title: string; description: string; moduleId: string; priority: string },
    target?: AiArtifactConfirmTarget,
  ): Promise<void> {
    const payload: Record<string, unknown> = {}
    if (content.title.trim()) payload['title'] = content.title.trim()
    if (content.description.trim()) payload['description'] = content.description.trim()
    if (content.moduleId) payload['moduleId'] = content.moduleId
    if (content.priority) payload['priority'] = content.priority
    return submitItems([{ key: row.key, action: 'adopted_edited', content: payload }], target)
  }

  async function handleBatchReject(target?: AiArtifactConfirmTarget): Promise<void> {
    try {
      await ElMessageBox.confirm(
        `将驳回所选 ${selectedKeys.value.length} 条产物，不创建任何数据。`,
        '批量驳回',
        { type: 'warning', confirmButtonText: '全部驳回', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    await submitItems(itemsFor(selectedKeys.value, 'rejected'), target)
  }

  /** 回执失败项单项重试（交互 2.3 回执） */
  function retryResult(result: ConfirmItemResult): Promise<void> {
    const item = result.action as AiArtifactAction
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

  /** 并发预取未确认产物内容：生成链树需要行内展示属性徽标与来源警示 */
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
    void loadModuleTree()
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
