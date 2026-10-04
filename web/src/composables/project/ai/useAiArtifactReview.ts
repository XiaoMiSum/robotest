import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmAiArtifacts, fetchAiArtifact } from '@/services/ai'
import { fetchProjectModuleTree } from '@/services/project'
import type {
  AiArtifactAction,
  AiArtifactConfirmItem,
  AiArtifactConfirmReceipt,
  AiArtifactSummary,
  ProjectModule,
} from '@/types'
import {
  aiArtifactConfirmMeta,
  aiArtifactKindLabel,
  aiArtifactProcessed,
} from '@/composables/project/ai/taskPresentation'

/** 单次确认上限（详设 3.6.5 items @Size(max=200)） */
const CONFIRM_MAX_ITEMS = 200
const NOTE_MAX_LENGTH = 500

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

/** 产物内容（getArtifact 返回结构不定型，按需取字段，缺省回退 summary） */
export interface AiArtifactContent {
  title: string
  description: string
  moduleId: string
  priority: string
  sourceRef: string
}

const EMPTY_CONTENT: AiArtifactContent = {
  title: '',
  description: '',
  moduleId: '',
  priority: '',
  sourceRef: '',
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
      row.content = {
        title: readString(raw, 'title') || row.title || '',
        description: readString(raw, 'description'),
        moduleId: readString(raw, 'moduleId'),
        priority: readString(raw, 'priority'),
        sourceRef: typeof data['sourceRef'] === 'string' ? data['sourceRef'] : '',
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
  async function submit(
    items: AiArtifactConfirmItem[],
    systemVersion?: string,
  ): Promise<void> {
    if (items.length === 0) {
      ElMessage.warning('请先选择要处理的产物')
      return
    }
    if (items.length > CONFIRM_MAX_ITEMS) {
      ElMessage.warning(`单次最多确认 ${CONFIRM_MAX_ITEMS} 项`)
      return
    }
    confirming.value = true
    try {
      const resp = await confirmAiArtifacts(taskId, {
        items,
        target: systemVersion !== undefined ? { systemVersion } : undefined,
      })
      renderReceipt(resp, items)
      const failure = resp.results.filter((item) => !item.success)
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

  function renderReceipt(resp: AiArtifactConfirmReceipt, items: AiArtifactConfirmItem[]): void {
    receipt.value = resp.results.map((result) => {
      const request = items.find((item) => item.key === result.key)
      const row = rows.value.find((item) => item.key === result.key)
      return {
        key: result.key,
        title: row?.title || result.key,
        action: request?.action ?? result.action,
        success: result.success,
        errorCode: result.errorCode,
        errorMsg: result.errorMsg ?? '',
      }
    })
  }

  function itemsFor(keys: string[], action: AiArtifactAction): AiArtifactConfirmItem[] {
    return keys.map((key) => ({ key, action }))
  }

  function handleAdopt(row: ArtifactRow, systemVersion?: string): Promise<void> {
    return submit(itemsFor([row.key], 'adopted'), systemVersion)
  }

  function handleReject(
    row: ArtifactRow,
    note: string | undefined,
    systemVersion?: string,
  ): Promise<void> {
    const item: AiArtifactConfirmItem = { key: row.key, action: 'rejected' }
    if (note && note.trim()) item.note = note.trim()
    return submit([item], systemVersion)
  }

  function handleBatchAdopt(systemVersion?: string): Promise<void> {
    return submit(itemsFor(selectedKeys.value, 'adopted'), systemVersion)
  }

  /** 编辑后采纳：content 覆盖产物原内容（详设 3.6.5 adopted_edited） */
  function handleAdoptEdited(
    row: ArtifactRow,
    content: { title: string; description: string; moduleId: string; priority: string },
    systemVersion?: string,
  ): Promise<void> {
    const payload: Record<string, unknown> = {}
    if (content.title.trim()) payload['title'] = content.title.trim()
    if (content.description.trim()) payload['description'] = content.description.trim()
    if (content.moduleId) payload['moduleId'] = content.moduleId
    if (content.priority) payload['priority'] = content.priority
    return submit([{ key: row.key, action: 'adopted_edited', content: payload }], systemVersion)
  }

  async function handleBatchReject(systemVersion?: string): Promise<void> {
    try {
      await ElMessageBox.confirm(
        `将驳回所选 ${selectedKeys.value.length} 条产物，不创建任何数据。`,
        '批量驳回',
        { type: 'warning', confirmButtonText: '全部驳回', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    await submit(itemsFor(selectedKeys.value, 'rejected'), systemVersion)
  }

  /** 回执失败项单项重试（交互 2.3 回执） */
  function retryResult(result: ConfirmItemResult): Promise<void> {
    const item = result.action as AiArtifactAction
    return submit([{ key: result.key, action: item }])
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
    handleAdopt,
    handleAdoptEdited,
    handleReject,
    handleBatchAdopt,
    handleBatchReject,
    retryResult,
    clearReceipt,
  }
}
