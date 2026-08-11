import type { AiGeneratedNode } from '@/types'
import type { Minder, MinderNode } from '../types'

/**
 * AI 挂载执行器（智能用例生成详细设计 4.2）：
 * 「生成子树」「补全步骤」「文本导入」「对话式编辑新增节点」共用。
 * 纯函数（预览键分配/勾选过滤/节点查找）与 minder 写入分离，便于单测。
 */

/** 预览树节点：为脑图预览勾选维护稳定 key（生成结果无 id，用路径键；既有节点用真实 id） */
export interface AiPreviewNode {
  key: string
  title: string
  type: AiGeneratedNode['type'] | null
  priority: string | null
  /** true=本次 AI 生成（带勾选框可勾选挂载）；false=文档既有节点（只读，无勾选框） */
  aiGenerated: boolean
  /** AI 节点勾选态（默认 true 全选，随脑图点击级联联动）；既有节点恒 false 不参与挂载 */
  aiSelected: boolean
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
      aiGenerated: true,
      aiSelected: true,
      children: buildPreviewTree(node.children ?? [], key),
    }
  })
}

/**
 * 组装完整文档树预览（交互设计 2.2 纯预览约束）：
 * 读取脑图活树快照（只读遍历，不写编辑内核/不产生撤销），既有节点 aiGenerated=false 只读展示，
 * 本次生成节点以 AI 徽标树插入到挂载目标节点下，供用户核对生成内容在文档全貌中的位置后勾选取舍。
 * @returns 完整预览树；目标节点已不存在（协同删除）返回 null，由调用方回退为仅展示生成节点树
 */
export function buildDocumentPreviewTree(
  root: MountTargetSource | null,
  targetNodeId: string,
  generatedNodes: AiGeneratedNode[],
): AiPreviewNode[] | null {
  if (!root) return null
  let found = false

  function walk(source: MountTargetSource): AiPreviewNode {
    const node: AiPreviewNode = {
      key: (source.data.id as string) ?? '',
      title: (source.data.text as string) ?? '',
      type: (source.data.type as AiGeneratedNode['type']) ?? null,
      priority: (source.data.priority as string) ?? null,
      aiGenerated: false,
      aiSelected: false,
      children: [],
    }
    // 挂载目标节点：既有子节点照常保留，生成节点追加为末尾新子节点（与 appendGeneratedTree 挂载位置一致）
    if (node.key === targetNodeId) {
      found = true
      node.children = [
        ...source.getChildren().map(walk),
        ...buildPreviewTree(generatedNodes),
      ]
    } else {
      node.children = source.getChildren().map(walk)
    }
    return node
  }

  const tree = walk(root)
  return found ? [tree] : null
}

/**
 * 勾选过滤（4.2 取舍规则）：父节点未勾选则其子孙一并排除——
 * 天然覆盖「case 取消时其前置/步骤/预期子节点整组排除」（脑图点击级联 + 子树剪枝双保险）。
 * 完整文档树预览下，文档既有节点（aiGenerated=false）为只读容器：递归穿透查找其下嵌套的 AI 子孙，
 * 仅提取勾选（aiSelected=true）的 AI 节点参与挂载，既有节点本身不输出。
 */
export function filterCheckedTree(preview: AiPreviewNode[]): AiGeneratedNode[] {
  const result: AiGeneratedNode[] = []
  for (const node of preview) {
    if (!node.aiGenerated) {
      // 既有节点：只读容器，穿透递归到其下嵌套的 AI 子孙
      result.push(...filterCheckedTree(node.children))
      continue
    }
    if (!node.aiSelected) continue
    result.push({
      type: node.type ?? 'normal',
      title: node.title,
      priority: node.priority,
      children: filterCheckedTree(node.children),
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
