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
}

export interface CaseGenerationInsertResult {
  inserted: number
  skipped: number
}

export function useCaseGenerationInsert(
  getMinder: () => Minder | null,
  flushPersistence: () => void,
) {
  function execute(cases: AiGeneratedCase[]): CaseGenerationInsertResult {
    const minder = getMinder()
    const root = minder?.getRoot?.()
    if (!minder || !root || cases.length === 0) {
      return { inserted: 0, skipped: cases.length }
    }

    const created: Array<{ node: MinderNode; tags: string[] }> = []
    for (const item of cases) {
      const source = item.node
      const caseNode = minder.createNode(
        { id: uuidv7(), text: source.title, type: 'case', priority: source.priority ?? 'P2' },
        root,
        root.getChildren().length,
      )
      source.children.forEach((child, index) => {
        minder.createNode({ id: uuidv7(), text: child.title, type: child.type }, caseNode, index)
      })
      created.push({ node: caseNode, tags: item.tags })
    }

    // add_node 协议只带基础属性；先落一次建立新节点基线，随后按 diff 补写标签与 AI 标识
    minder.refresh()
    minder.fire('contentchange')
    flushPersistence()

    for (const { node, tags } of created) {
      node.setData('tags', tags)
      node.setData('aiGenerated', true)
    }
    if (created.length > 0) {
      minder.refresh()
      minder.fire('contentchange')
      flushPersistence()
    }
    return { inserted: created.length, skipped: cases.length - created.length }
  }

  return { execute }
}
