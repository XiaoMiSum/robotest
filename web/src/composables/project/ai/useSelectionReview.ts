import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAiArtifact } from '@/services/ai'
import { getCaseDetail } from '@/services/project'
import type { SelectedCaseNode } from '@/types'

/** 逐条解析用例明细的并发上限，避免圈选卡片多时打爆同域连接 */
const RESOLVE_CONCURRENCY = 6

export interface SelectionReviewItem {
  caseId: string
  title: string
  reason: string
  /** 计划轮次（评审任务恒为 null） */
  round: number | null
  /** 所属文档：null 表示未解析 / 解析失败，失败项不进创建载荷 */
  documentId: string | null
  /** 手动添加（非模型推荐），无推荐理由 */
  manual: boolean
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function readString(source: Record<string, unknown>, key: string): string {
  const value = source[key]
  return typeof value === 'string' ? value : ''
}

async function mapConcurrent<T, R>(
  items: T[],
  worker: (item: T) => Promise<R>,
): Promise<R[]> {
  const results: R[] = new Array(items.length)
  let cursor = 0
  const run = async (): Promise<void> => {
    while (cursor < items.length) {
      const index = cursor
      cursor += 1
      results[index] = await worker(items[index])
    }
  }
  await Promise.all(
    Array.from({ length: Math.min(RESOLVE_CONCURRENCY, items.length) }, () => run()),
  )
  return results
}

/**
 * 圈选审核状态（交互 2.4）：单产物 sel-1 的推荐卡片，可移除 / 手动添加；
 * 确认前需逐条解析 documentId 组装既有创建载荷的关联用例。
 */
export function useSelectionReview(taskId: string) {
  const items = ref<SelectionReviewItem[]>([])
  const loading = ref(false)
  const resolving = ref(false)
  const loadFailed = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    loadFailed.value = false
    try {
      const data = await fetchAiArtifact(taskId, 'sel-1')
      const content = (data['content'] ?? {}) as Record<string, unknown>
      const raw = content['items']
      const parsed: SelectionReviewItem[] = []
      if (Array.isArray(raw)) {
        for (const element of raw) {
          if (typeof element !== 'object' || element === null) continue
          const entry = element as Record<string, unknown>
          const caseId = readString(entry, 'caseId')
          if (!caseId) continue
          const round = entry['round']
          parsed.push({
            caseId,
            title: readString(entry, 'title') || caseId,
            reason: readString(entry, 'reason'),
            round: typeof round === 'number' ? round : null,
            documentId: null,
            manual: false,
          })
        }
      }
      items.value = parsed
    } catch (err) {
      loadFailed.value = true
      ElMessage.error(errorMessage(err, '读取圈选建议失败'))
    } finally {
      loading.value = false
    }
  }

  function removeItem(caseId: string): void {
    items.value = items.value.filter((item) => item.caseId !== caseId)
  }

  /** 取用例明细回填标题与所属文档；失败返回 null 由调用方标记失效 */
  async function fetchCaseMeta(caseId: string): Promise<{
    title: string
    documentId: string | null
  }> {
    try {
      const node = await getCaseDetail(caseId)
      return { title: node.title, documentId: node.documentId ?? null }
    } catch {
      return { title: '', documentId: null }
    }
  }

  /** 手动添加范围内用例（去重合并），逐条回填明细 */
  async function addCases(nodes: SelectedCaseNode[]): Promise<void> {
    const known = new Set(items.value.map((item) => item.caseId))
    const newIds: string[] = []
    for (const node of nodes) {
      for (const caseId of node.caseIds) {
        if (!known.has(caseId)) {
          known.add(caseId)
          newIds.push(caseId)
        }
      }
    }
    if (newIds.length === 0) return
    resolving.value = true
    try {
      const metas = await mapConcurrent(newIds, fetchCaseMeta)
      items.value = [
        ...items.value,
        ...newIds.map((caseId, index) => ({
          caseId,
          title: metas[index].title || caseId,
          reason: '',
          round: null,
          documentId: metas[index].documentId,
          manual: true,
        })),
      ]
    } finally {
      resolving.value = false
    }
  }

  /** 补齐 AI 推荐项的 documentId；失败项保留卡片但标记失效 */
  async function resolveDocumentIds(): Promise<void> {
    const pending = items.value.filter((item) => item.documentId === null)
    if (pending.length === 0) return
    resolving.value = true
    try {
      const metas = await mapConcurrent(pending, (item) => fetchCaseMeta(item.caseId))
      pending.forEach((item, index) => {
        item.documentId = metas[index].documentId
        if (metas[index].title) item.title = metas[index].title
      })
    } finally {
      resolving.value = false
    }
  }

  /** 组装创建载荷的关联用例：按文档分组；解析失败项排除并回报数量 */
  function selectedNodes(): { nodes: SelectedCaseNode[]; missing: number } {
    const byDocument = new Map<string, string[]>()
    let missing = 0
    for (const item of items.value) {
      if (!item.documentId) {
        missing += 1
        continue
      }
      const bucket = byDocument.get(item.documentId)
      if (bucket) bucket.push(item.caseId)
      else byDocument.set(item.documentId, [item.caseId])
    }
    return {
      nodes: [...byDocument.entries()].map(([documentId, caseIds]) => ({
        documentId,
        caseIds,
      })),
      missing,
    }
  }

  return {
    items,
    loading,
    resolving,
    loadFailed,
    load,
    removeItem,
    addCases,
    resolveDocumentIds,
    selectedNodes,
  }
}
