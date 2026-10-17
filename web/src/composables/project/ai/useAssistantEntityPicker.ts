import { ref } from 'vue'
import { fetchProjectModuleTree, fetchPlans, fetchRequirements, fetchReviews } from '@/services/project'
import type { ProjectModule } from '@/types'

/** 可作为附件引用的实体类型（详设 3.4 合法引用类型的可选子集） */
export type AssistantEntityType =
  | 'requirement'
  | 'module'
  | 'mindmap_document'
  | 'test_review'
  | 'test_plan'

export interface AssistantEntityOption {
  id: string
  label: string
}

function matches(keyword: string, label: string): boolean {
  const needle = keyword.trim()
  return needle === '' || label.includes(needle)
}

function flattenModules(
  nodes: ProjectModule[],
  wanted: 'module' | 'mindmap_document',
  keyword: string,
  out: AssistantEntityOption[],
): void {
  for (const node of nodes) {
    const hit = wanted === 'module' ? node.type === 'directory' : node.type === 'document'
    if (hit && matches(keyword, node.name)) out.push({ id: node.id, label: node.name })
    flattenModules(node.children, wanted, keyword, out)
  }
}

/**
 * 助手上下文附件的实体候选：按实体类型复用既有列表接口取候选，
 * 模块树无关键字检索能力，取回后在前端过滤。
 */
export function useAssistantEntityPicker() {
  const options = ref<AssistantEntityOption[]>([])
  const loading = ref(false)

  async function load(type: AssistantEntityType, keyword = ''): Promise<void> {
    loading.value = true
    try {
      if (type === 'requirement') {
        const page = await fetchRequirements({ keyword: keyword || undefined, pageNo: 1, pageSize: 50 })
        options.value = page.list.map((item) => ({ id: item.id, label: `${item.code} ${item.title}` }))
      } else if (type === 'module' || type === 'mindmap_document') {
        const tree = await fetchProjectModuleTree()
        const flattened: AssistantEntityOption[] = []
        flattenModules(tree, type, keyword, flattened)
        options.value = flattened
      } else if (type === 'test_review') {
        const page = await fetchReviews({ keyword: keyword || undefined, pageNo: 1, pageSize: 50 })
        options.value = page.list.map((item) => ({ id: item.id, label: item.title }))
      } else if (type === 'test_plan') {
        const page = await fetchPlans({ keyword: keyword || undefined, pageNo: 1, pageSize: 50 })
        options.value = page.list.map((item) => ({ id: item.id, label: item.name }))
      } else {
        // 未知类型兜底空列表，避免把上一次候选当作本次结果展示
        options.value = []
      }
    } catch {
      // 候选加载失败由发送时的业务错误兜底，此处保持下拉可用
      options.value = []
    } finally {
      loading.value = false
    }
  }

  return { options, loading, load }
}
