import type { AiGeneratedNode } from '@/types'
import type { Minder, MinderNode } from '../types'

/**
 * AI 挂载执行器（智能用例生成详细设计 4.2）：
 * 「生成子树」「补全步骤」「文本导入」「对话式编辑新增节点」共用。
 * 纯函数（预览键分配/勾选过滤/节点查找）与 minder 写入分离，便于单测。
 */

/** 预览树节点：为 el-tree 勾选分配稳定 key（生成结果无 id，用路径键） */
export interface AiPreviewNode {
  key: string
  title: string
  type: AiGeneratedNode['type']
  priority: string | null
  children: AiPreviewNode[]
}

export function buildPreviewTree(nodes: AiGeneratedNode[], parentKey = 'ai'): AiPreviewNode[] {
  return nodes.map((node, index) => {
    const key = `${parentKey}-${index}`
    return {
      key,
      title: node.title,
      type: node.type,
      priority: node.priority ?? null,
      children: buildPreviewTree(node.children ?? [], key),
    }
  })
}

/**
 * 勾选过滤（4.2 取舍规则）：父节点未勾选则其子孙一并排除——
 * 天然覆盖「case 取消时其前置/步骤/预期子节点整组排除」（el-tree 级联取消 + 子树剪枝双保险）。
 */
export function filterCheckedTree(
  preview: AiPreviewNode[],
  checkedKeys: ReadonlySet<string>,
): AiGeneratedNode[] {
  const result: AiGeneratedNode[] = []
  for (const node of preview) {
    if (!checkedKeys.has(node.key)) continue
    result.push({
      type: node.type,
      title: node.title,
      priority: node.priority,
      children: filterCheckedTree(node.children, checkedKeys),
    })
  }
  return result
}

/** 结构化递归源（与 clipboard.SubtreeSource 同构，便于单测伪造） */
export interface MountTargetSource {
  data: Record<string, unknown>
  getChildren(): MountTargetSource[]
}

/** 按节点 id 在画布树中查找（挂载前的目标存在性校验，4.1） */
export function findNodeById(root: MountTargetSource | null, id: string): MountTargetSource | null {
  if (!root) return null
  if (root.data.id === id) return root
  for (const child of root.getChildren()) {
    const found = findNodeById(child, id)
    if (found) return found
  }
  return null
}

/** 深度优先创建子树；case 缺省优先级补 P2（与编辑器手工标记联动规则一致） */
export interface MinderLike {
  createNode(data: Record<string, unknown>, parent?: MountTargetSource): MountTargetSource
}

export function appendGeneratedTree(
  minder: MinderLike,
  parent: MountTargetSource,
  nodes: AiGeneratedNode[],
): number {
  let count = 0
  for (const node of nodes) {
    const data: Record<string, unknown> = {
      text: node.title,
      type: node.type,
      aiGenerated: true,
    }
    if (node.type === 'case') data.priority = node.priority ?? 'P2'
    else if (node.priority) data.priority = node.priority
    const created = minder.createNode(data, parent)
    count += 1 + appendGeneratedTree(minder, created, node.children ?? [])
  }
  return count
}

/**
 * 挂载入口：批量 createNode 后补发一次 contentchange——
 * history 对整批生成单条撤销补丁（单撤销组），并搭上 Yjs 同步与防抖落库管道（同 clipboard 粘贴）。
 *
 * @returns 挂载的节点总数；目标节点已不存在返回 null（由调用方走重选流程）
 */
export function mountGeneratedNodes(
  minder: Minder,
  targetNodeId: string,
  nodes: AiGeneratedNode[],
): number | null {
  const root = minder.getRoot() as unknown as MountTargetSource | null
  const target = findNodeById(root, targetNodeId)
  if (!target) return null
  const count = appendGeneratedTree(
    minder as unknown as MinderLike,
    target,
    nodes,
  )
  minder.select(target as unknown as MinderNode, true)
  minder.refresh()
  minder.fire('contentchange')
  return count
}
