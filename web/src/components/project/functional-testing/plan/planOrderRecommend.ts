import type { TestPlanSnapshotNode } from '@/types'

/**
 * 执行顺序推荐标签页的纯函数集（US-AI-017，交互设计第 5 章）：
 * 后端推荐结果 items 只含 snapshotNodeId，标题/优先级需从计划快照树补齐展示。
 */
export interface PlanCaseMeta {
  title: string
  priority: string | null
}

/** 快照树扁平化为 snapshotNodeId → { title, priority } 的映射（仅 case 节点入表） */
export function collectPlanCaseMeta(nodes: TestPlanSnapshotNode[]): Map<string, PlanCaseMeta> {
  const map = new Map<string, PlanCaseMeta>()
  const walk = (list: TestPlanSnapshotNode[]): void => {
    for (const node of list) {
      if (node.type === 'case') {
        map.set(node.id, { title: node.title, priority: node.priority })
      }
      if (node.children?.length) walk(node.children)
    }
  }
  walk(nodes)
  return map
}

/** 行内标题回退：映射缺失（如快照已更新）时展示节点 ID，避免空行 */
export function resolveCaseTitle(meta: Map<string, PlanCaseMeta>, snapshotNodeId: string): string {
  return meta.get(snapshotNodeId)?.title ?? snapshotNodeId
}

/** 指数展示：score ∈ [0,1] 放大为百分位整数（设计稿「指数 92」语义） */
export function scoreLabel(score: number): string {
  return `指数 ${Math.round(score * 100)}`
}
