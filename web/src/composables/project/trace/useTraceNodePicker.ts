import { ref } from 'vue'
import { fetchProjectModuleTree, fetchPlans, fetchRequirements, fetchReviews } from '@/services/project'
import type { ProjectModule, TraceNodeType } from '@/types'

export interface TraceNodeOption {
  id: string
  label: string
}

/** 列表接口支持关键字检索的节点类型，其余类型一次性取回后前端过滤 */
const SEARCHABLE_TYPES: TraceNodeType[] = ['requirement', 'test_review', 'test_plan']

function matches(keyword: string, label: string): boolean {
  const needle = keyword.trim()
  return needle === '' || label.includes(needle)
}

function flattenModules(
  nodes: ProjectModule[],
  wanted: 'module' | 'mindmap_document',
  keyword: string,
  out: TraceNodeOption[],
): void {
  for (const node of nodes) {
    const hit = wanted === 'module' ? node.type === 'directory' : node.type === 'document'
    if (hit && matches(keyword, node.name)) out.push({ id: node.id, label: node.name })
    flattenModules(node.children, wanted, keyword, out)
  }
}

/**
 * 人工建边的节点选取（详设 3.5）：按节点类型复用既有列表接口取候选，
 * 测试用例没有全局列表接口，由链路树节点提供候选（调用方注入）。
 */
export function useTraceNodePicker() {
  const options = ref<TraceNodeOption[]>([])
  const loading = ref(false)

  async function load(type: TraceNodeType, keyword = ''): Promise<void> {
    if (type === 'test_case') {
      options.value = []
      return
    }
    loading.value = true
    try {
      if (type === 'requirement') {
        const page = await fetchRequirements({ keyword: keyword || undefined, pageNo: 1, pageSize: 50 })
        options.value = page.list.map((item) => ({ id: item.id, label: `${item.code} ${item.title}` }))
      } else if (type === 'module' || type === 'mindmap_document') {
        const tree = await fetchProjectModuleTree()
        const flattened: TraceNodeOption[] = []
        flattenModules(tree, type, keyword, flattened)
        options.value = flattened
      } else if (type === 'test_review') {
        const page = await fetchReviews({ keyword: keyword || undefined, pageNo: 1, pageSize: 50 })
        options.value = page.list.map((item) => ({ id: item.id, label: item.title }))
      } else if (type === 'test_plan') {
        const page = await fetchPlans({ keyword: keyword || undefined, pageNo: 1, pageSize: 50 })
        options.value = page.list.map((item) => ({ id: item.id, label: item.name }))
      } else {
        options.value = []
      }
    } catch {
      // 候选加载失败由提交时的业务错误兜底，此处保持下拉可用
      options.value = []
    } finally {
      loading.value = false
    }
  }

  function reset(): void {
    options.value = []
  }

  function isSearchable(type: TraceNodeType): boolean {
    return SEARCHABLE_TYPES.includes(type)
  }

  return { options, loading, load, reset, isSearchable }
}
