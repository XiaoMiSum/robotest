/**
 * 用例优化的节点操作执行器（辅助功能详设 4.2）：
 * 在脑图当前树上按 ID 原地改写或新增节点，再走既有 contentchange 管道同步 Yjs 与 WS 持久化。
 */
import { uuidv7 } from '@/minder/adapter'
import type { Minder, MinderNode } from '@/minder/types'
import type { AiCaseOptimizeOperation } from '@/types'

export interface CaseOptimizeExecutionResult {
  updated: number
  added: number
  marked: number
  skipped: number
}

function asValues(raw: string | string[] | undefined): string[] {
  if (Array.isArray(raw)) return raw
  return typeof raw === 'string' && raw ? [raw] : []
}

export function useCaseOptimizeExecution(
  getMinder: () => Minder | null,
  flushPersistence: () => void,
) {
  function findNode(id: string): MinderNode | null {
    const root = getMinder()?.getRoot()
    if (!root || !id) return null
    let found: MinderNode | null = null
    root.traverse((node) => {
      if (found === null && node.data['id'] === id) found = node
    })
    return found
  }

  function applyUpdate(node: MinderNode, op: Extract<AiCaseOptimizeOperation, { op: 'update' }>): void {
    const values = asValues(op.suggested)
    if (op.field === 'tags') {
      node.setData('tags', values)
    } else {
      node.setData(op.field === 'title' ? 'text' : 'priority', values[0] ?? '')
    }
  }

  function applyAdd(
    minder: Minder,
    parent: MinderNode,
    op: Extract<AiCaseOptimizeOperation, { op: 'add' }>,
  ): void {
    minder.createNode(
      { id: uuidv7(), text: op.title, type: op.nodeType, aiGenerated: true },
      parent,
      parent.getChildren().length,
    )
  }

  function execute(operations: AiCaseOptimizeOperation[], caseNodeId?: string | null): CaseOptimizeExecutionResult {
    const result: CaseOptimizeExecutionResult = { updated: 0, added: 0, marked: 0, skipped: 0 }
    const minder = getMinder()
    if (!minder) {
      result.skipped = operations.length + (caseNodeId ? 1 : 0)
      return result
    }
    for (const op of operations) {
      if (op.op === 'update') {
        const node = findNode(op.nodeId)
        if (!node) {
          result.skipped++
          continue
        }
        applyUpdate(node, op)
        result.updated++
      } else {
        const parent = findNode(op.parentId)
        if (!parent) {
          result.skipped++
          continue
        }
        applyAdd(minder, parent, op)
        result.added++
      }
    }
    if (caseNodeId) {
      const caseNode = findNode(caseNodeId)
      if (caseNode) {
        // 复用既有 AI 生成标识：用例被 AI 优化采纳后即带 AI 标记（详设 4.2）
        caseNode.setData('aiGenerated', true)
        result.marked++
      } else {
        result.skipped++
      }
    }
    if (result.updated + result.added + result.marked > 0) {
      // createNode / setData 不走内核命令，补发 contentchange 才能进入 history / Yjs / 防抖落库
      minder.refresh()
      minder.fire('contentchange')
      flushPersistence()
    }
    return result
  }

  return { execute }
}
