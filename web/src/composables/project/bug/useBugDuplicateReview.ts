import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmAiProposals, fetchAiProposal } from '@/services/ai'
import { aiProposalProcessed } from '@/composables/project/ai/taskPresentation'
import type { ConfirmItemResult } from '@/composables/project/ai/useAiProposalReview'
import type {
  AiProposalAction,
  AiProposalConfirmItem,
  AiProposalConfirmResult,
  AiProposalConfirmStatus,
  AiTaskProposalSummary,
  BugDuplicateGroupContent,
} from '@/types'

/** 单次确认上限（详设 3.9 items @Size(max=200)），超限分批顺序提交 */
const CONFIRM_MAX_ITEMS = 200
/** 分组内容预取并发上限，避免长清单打爆同域连接 */
const PRELOAD_CONCURRENCY = 6

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function readString(raw: unknown): string | null {
  return typeof raw === 'string' && raw.length > 0 ? raw : null
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

/** 审核行：产物清单 + 解析后的重复分组内容 */
export interface BugDuplicateRow {
  key: string
  kind: string
  title: string
  confirmStatus: AiProposalConfirmStatus
  group: BugDuplicateGroupContent | null
  loaded: boolean
}

/**
 * 重复组审核（详设 3.9 + 交互 2.3）：
 * adopted=确认疑似重复留痕，rejected=排除误报；两种结论都不修改任何缺陷，
 * 确认后由人工按既有「重复缺陷」流程逐条处理。
 */
export function useBugDuplicateReview(
  taskId: string,
  proposals: () => AiTaskProposalSummary[],
  canConfirm: () => boolean,
  onConfirmed: () => void,
) {
  const rows = ref<BugDuplicateRow[]>([])
  const confirming = ref(false)
  const receipt = ref<ConfirmItemResult[]>([])

  const processed = computed(() => aiProposalProcessed(rows.value))
  const canConfirmState = computed(() => canConfirm())
  const duplicateRows = computed(() => rows.value.filter((row) => row.kind === 'duplicate_group'))

  function syncRows(): void {
    rows.value = proposals().map((proposal) => {
      const existing = rows.value.find((row) => row.key === proposal.key)
      return {
        key: proposal.key,
        kind: proposal.kind,
        title: proposal.title ?? proposal.key,
        confirmStatus: proposal.confirmStatus,
        group: existing?.group ?? null,
        loaded: existing?.loaded ?? false,
      }
    })
  }

  // ==================== 内容加载 ====================

  async function loadRow(row: BugDuplicateRow): Promise<void> {
    if (row.loaded || row.kind !== 'duplicate_group') return
    try {
      const proposal = await fetchAiProposal(taskId, row.key)
      // 提案详情响应为 { ...摘要, content }（详设 3.6.3），内容取 content 根节点
      const raw = (proposal.content ?? {}) as Record<string, unknown>
      row.group = parseDuplicateGroupContent(raw)
      row.loaded = true
    } catch (err) {
      // 单行失败保留未加载态供重试，不阻塞其余审核动作
      ElMessage.error(errorMessage(err, '加载分组内容失败'))
    }
  }

  /** 并发预取未确认的重复组内容 */
  async function preloadContents(): Promise<void> {
    const targets = rows.value.filter(
      (row) => row.kind === 'duplicate_group' && row.confirmStatus === 'pending' && !row.loaded,
    )
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

  // ==================== 确认 ====================

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
        createdId: result.createdId,
      }
    })
  }

  async function submitItems(items: AiProposalConfirmItem[]): Promise<void> {
    if (items.length === 0) {
      ElMessage.warning('请先选择要确认的分组')
      return
    }
    confirming.value = true
    try {
      const results: AiProposalConfirmResult[] = []
      for (let start = 0; start < items.length; start += CONFIRM_MAX_ITEMS) {
        const chunk = items.slice(start, start + CONFIRM_MAX_ITEMS)
        const resp = await confirmAiProposals(taskId, { items: chunk })
        results.push(...resp.results)
      }
      receipt.value = renderReceipt(results, items)
      if (results.every((item) => item.success)) {
        ElMessage.success(
          items.every((item) => item.action === 'rejected')
            ? '已排除误报，未修改任何缺陷'
            : '确认完成，已记录重复组结论',
        )
      }
      onConfirmed()
    } catch (err) {
      ElMessage.error(errorMessage(err, '确认分组失败'))
    } finally {
      confirming.value = false
    }
  }

  /** adopted=确认疑似重复留痕，rejected=排除误报；均不修改缺陷 */
  function handleGroupConfirm(
    row: BugDuplicateRow,
    action: Extract<AiProposalAction, 'adopted' | 'rejected'>,
    note: string,
  ): Promise<void> {
    const item: AiProposalConfirmItem = { key: row.key, action }
    if (note.trim()) item.note = note.trim()
    return submitItems([item])
  }

  function retryResult(result: ConfirmItemResult): Promise<void> {
    return submitItems([{ key: result.key, action: result.action as AiProposalAction }])
  }

  function clearReceipt(): void {
    receipt.value = []
  }

  function init(): void {
    syncRows()
    void preloadContents()
  }

  return {
    rows,
    duplicateRows,
    confirming,
    receipt,
    processed,
    canConfirmState,
    CONFIRM_MAX_ITEMS,
    init,
    syncRows,
    loadRow,
    handleGroupConfirm,
    retryResult,
    clearReceipt,
  }
}
