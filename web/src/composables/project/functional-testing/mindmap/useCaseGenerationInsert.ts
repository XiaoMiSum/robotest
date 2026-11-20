/**
 * 脑图生成用例的插入执行器（辅助功能详设 4.4）：
 * 把勾选提案对应的用例节点追加到当前文档根节点下，再走既有 contentchange + WS 持久化管道落库。
 */
import { uuidv7 } from '@/minder/adapter'
import type { Minder, MinderNode } from '@/minder/types'
import type { TestCaseNode } from '@/types'

export interface AiGeneratedCase {
  /** 提案 key：插入成功后据此回传采纳状态 */
  key: string
  node: TestCaseNode
  tags: string[]
  /** 来源需求 id：写入用例节点 data，随 add_node 帧一并落库 */
  requirementId?: string
}

export interface CaseGenerationInsertResult {
  inserted: number
  skipped: number
  /** 按 cases 顺序的新建用例节点 id */
  caseIds: string[]
}

export function useCaseGenerationInsert(
  getMinder: () => Minder | null,
  flushPersistence: () => void,
) {
  function execute(cases: AiGeneratedCase[]): CaseGenerationInsertResult {
    const minder = getMinder()
    const root = minder?.getRoot?.()
    if (!minder || !root || cases.length === 0) {
      return { inserted: 0, skipped: cases.length, caseIds: [] }
    }

    const created: Array<{ node: MinderNode; tags: string[] }> = []
    const caseIds: string[] = []
    for (const item of cases) {
      const source = item.node
      const caseId = uuidv7()
      const caseNode = minder.createNode(
        {
          id: caseId,
          text: source.title,
          type: 'case',
          priority: source.priority ?? 'P2',
          aiGenerated: true,
          requirementId: item.requirementId ?? null,
        },
        root,
        root.getChildren().length,
      )
      source.children.forEach((child, index) => {
        minder.createNode(
          { id: uuidv7(), text: child.title, type: child.type, aiGenerated: true },
          caseNode,
          index,
        )
      })
      created.push({ node: caseNode, tags: item.tags })
      caseIds.push(caseId)
    }

    // AI 标识与来源需求随 add_node 落库；标签不在 add_node 协议内，先落节点基线再按 diff 补写
    minder.refresh()
    minder.fire('contentchange')
    flushPersistence()

    for (const { node, tags } of created) {
      node.setData('tags', tags)
    }
    if (created.length > 0) {
      minder.refresh()
      minder.fire('contentchange')
      flushPersistence()
    }
    return { inserted: created.length, skipped: cases.length - created.length, caseIds }
  }

  return { execute }
}
