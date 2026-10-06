import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AiArtifactConfirmItem, AiArtifactConfirmTarget } from '@/types'
import type { ArtifactRow } from '@/composables/project/ai/useAiArtifactReview'
import { aiArtifactKindLabel } from '@/composables/project/ai/taskPresentation'

/** 展示缩进封顶层级，防止深链节点把行挤出可视区 */
const MAX_INDENT_LEVEL = 4

const DOCUMENT_KIND = 'mindmap_document_suggestion'

export interface GenerationTreeNode {
  key: string
  row: ArtifactRow
  kindLabel: string
  title: string
  /** 树层级：0 模块 / 1 文档 / 2+ 节点（文档内按 parentRef 嵌套） */
  level: number
  children: GenerationTreeNode[]
}

/** 审核基座提供的行数据与确认能力（由 useAiArtifactReview 实例注入） */
export interface GenerationReviewBase {
  rows: Ref<ArtifactRow[]>
  selectedKeys: Ref<string[]>
  confirming: Ref<boolean>
  canConfirm: ComputedRef<boolean>
  activeKey: Ref<string>
  select: (key: string) => void
  submitItems: (
    items: AiArtifactConfirmItem[],
    target?: AiArtifactConfirmTarget,
  ) => Promise<void>
}

export interface AdoptPlan {
  items: AiArtifactConfirmItem[]
  /** 前置提示（自动补入父级 / 排除疑似重复等），空数组不提示 */
  notes: string[]
}

/**
 * 文档内节点按 content.parentRef 重建嵌套（摘要 parentKey 一律指向文档）：
 * 根节点保持产出顺序，DFS 下钻；发现环（模型异常）时余量挂根兜底，不丢节点。
 */
function orderNodesByParentRef(nodes: GenerationTreeNode[]): GenerationTreeNode[] {
  const byKey = new Map(nodes.map((node) => [node.key, node]))
  const childrenOf = new Map<string, GenerationTreeNode[]>()
  const rootNodes: GenerationTreeNode[] = []
  for (const node of nodes) {
    const parentRef = node.row.content.parentRef
    const parent = parentRef ? byKey.get(parentRef) : undefined
    if (parent && parent.key !== node.key) {
      const bucket = childrenOf.get(parent.key)
      if (bucket) bucket.push(node)
      else childrenOf.set(parent.key, [node])
    } else {
      rootNodes.push(node)
    }
  }

  // 只返回该层根级节点：子树挂载在各自父节点 children 上，返回值即文档根层
  const visited = new Set<string>()
  const visit = (node: GenerationTreeNode): void => {
    if (visited.has(node.key)) return
    visited.add(node.key)
    node.children = childrenOf.get(node.key) ?? []
    node.children.forEach(visit)
  }
  const ordered: GenerationTreeNode[] = []
  for (const root of rootNodes) {
    visit(root)
    ordered.push(root)
  }
  for (const node of nodes) {
    if (!visited.has(node.key)) {
      // 环（模型异常）残余挂根兜底，不丢节点
      visited.add(node.key)
      node.children = []
      ordered.push(node)
    }
  }
  return ordered
}

export function useGenerationArtifactTree(base: GenerationReviewBase) {
  const collapsedKeys = ref<Set<string>>(new Set())

  /** 三层树：模块 → 文档 → 节点（节点内部按 parentRef 嵌套），随行数据 / 内容加载重算 */
  const tree = computed<GenerationTreeNode[]>(() => {
    const rows = base.rows.value
    const nodes = new Map<string, GenerationTreeNode>()
    const built: GenerationTreeNode[] = rows.map((row) => {
      const node: GenerationTreeNode = {
        key: row.key,
        row,
        kindLabel: aiArtifactKindLabel(row.kind),
        title: row.title || row.content.title,
        level: 0,
        children: [],
      }
      nodes.set(row.key, node)
      return node
    })

    const roots: GenerationTreeNode[] = []
    for (const node of built) {
      const parentKey = node.row.parentKey
      const parent = parentKey ? nodes.get(parentKey) : undefined
      if (parent) parent.children.push(node)
      else roots.push(node)
    }
    // 层级随走树下推；文档层 children 是扁平节点，需按 parentRef 重建嵌套
    const build = (node: GenerationTreeNode, level: number): void => {
      node.level = level
      if (node.row.kind === DOCUMENT_KIND && node.children.length > 0) {
        node.children = orderNodesByParentRef(node.children)
      }
      node.children.forEach((child) => build(child, level + 1))
    }
    roots.forEach((root) => build(root, 0))
    return roots
  })

  /** 父 key 索引（整树采纳与祖先校验用），随树重建同步刷新 */
  const parentOf = computed(() => {
    const map = new Map<string, GenerationTreeNode>()
    const walk = (node: GenerationTreeNode): void => {
      for (const child of node.children) {
        map.set(child.key, node)
        walk(child)
      }
    }
    tree.value.forEach(walk)
    return map
  })

  /** 展开态下的可见行（默认全展开，折叠键按 key 记忆） */
  const visibleNodes = computed(() => {
    const out: GenerationTreeNode[] = []
    const walk = (node: GenerationTreeNode): void => {
      out.push(node)
      if (collapsedKeys.value.has(node.key)) return
      node.children.forEach(walk)
    }
    tree.value.forEach(walk)
    return out
  })

  const indentLevel = (node: GenerationTreeNode): number =>
    Math.min(node.level, MAX_INDENT_LEVEL)

  function toggleCollapse(key: string): void {
    if (collapsedKeys.value.has(key)) collapsedKeys.value.delete(key)
    else collapsedKeys.value.add(key)
  }

  function isCollapsed(key: string): boolean {
    return collapsedKeys.value.has(key)
  }

  function isDuplicate(node: GenerationTreeNode): boolean {
    return node.row.content.suspectedDuplicateOf !== ''
  }

  function hasChangedSource(node: GenerationTreeNode): boolean {
    return node.row.content.sourceRefs.some((ref) => ref.changed)
  }

  /** 自身到根的待确认祖先（已驳回祖先会阻断落库，单列标记） */
  function ancestorState(node: GenerationTreeNode): {
    pending: GenerationTreeNode[]
    rejected: GenerationTreeNode[]
  } {
    const pending: GenerationTreeNode[] = []
    const rejected: GenerationTreeNode[] = []
    let cursor = parentOf.value.get(node.key)
    while (cursor) {
      if (cursor.row.confirmStatus === 'pending') pending.push(cursor)
      if (cursor.row.confirmStatus === 'rejected') rejected.push(cursor)
      cursor = parentOf.value.get(cursor.key)
    }
    return { pending, rejected }
  }

  // ==================== 采纳计划 ====================

  /** 预序收集待确认子树：已驳回节点的子树整体跳过（父未落库子级必失败） */
  function collectSubtree(node: GenerationTreeNode, out: Set<string>): void {
    if (node.row.confirmStatus === 'rejected') return
    if (node.row.confirmStatus === 'pending') out.add(node.key)
    node.children.forEach((child) => collectSubtree(child, out))
  }

  /** 按树预序输出确认项，保证父先子序（分批顺序也依此，1000018210） */
  function orderedItems(keys: Set<string>, action: 'adopted' | 'rejected'): AiArtifactConfirmItem[] {
    const items: AiArtifactConfirmItem[] = []
    const walk = (node: GenerationTreeNode): void => {
      if (keys.has(node.key)) items.push({ key: node.key, action })
      node.children.forEach(walk)
    }
    tree.value.forEach(walk)
    return items
  }

  function planAdopt(keys: Set<string>): AdoptPlan {
    const notes: string[] = []
    let autoParent = 0
    let excludedDuplicate = 0
    let blockedByRejected = 0

    // 疑似重复需改名后采纳（1000018204），前端先行排除
    for (const key of [...keys]) {
      const node = findNode(key)
      if (node && isDuplicate(node)) {
        keys.delete(key)
        excludedDuplicate += 1
      }
    }
    // 待确认祖先自动补入；祖先被驳回的节点无法落库，直接排除
    for (const key of [...keys]) {
      const node = findNode(key)
      if (!node) continue
      const { pending, rejected } = ancestorState(node)
      if (rejected.length > 0) {
        keys.delete(key)
        blockedByRejected += 1
        continue
      }
      for (const ancestor of pending) {
        if (!keys.has(ancestor.key)) {
          keys.add(ancestor.key)
          autoParent += 1
        }
      }
    }

    if (autoParent > 0) notes.push(`自动补入待确认父级 ${autoParent} 条`)
    if (excludedDuplicate > 0) {
      notes.push(`排除疑似重复 ${excludedDuplicate} 条（需修改后采纳或驳回）`)
    }
    if (blockedByRejected > 0) notes.push(`排除父级已驳回 ${blockedByRejected} 条`)
    return { items: orderedItems(keys, 'adopted'), notes }
  }

  const nodeIndex = computed(() => {
    const map = new Map<string, GenerationTreeNode>()
    tree.value.forEach((root) => {
      const walk = (node: GenerationTreeNode): void => {
        map.set(node.key, node)
        node.children.forEach(walk)
      }
      walk(root)
    })
    return map
  })

  function findNode(key: string): GenerationTreeNode | undefined {
    return nodeIndex.value.get(key)
  }

  // ==================== 单条动作 ====================

  async function adoptOne(node: GenerationTreeNode): Promise<void> {
    if (isDuplicate(node)) {
      ElMessage.warning('疑似与既有数据重复，请修改后采纳或驳回')
      return
    }
    const { pending, rejected } = ancestorState(node)
    if (rejected.length > 0) {
      ElMessage.warning('父级已驳回，该产物无法落库')
      return
    }
    if (pending.length > 0) {
      ElMessage.warning('请先采纳父级节点，或使用整树采纳')
      return
    }
    await base.submitItems([{ key: node.key, action: 'adopted' }])
  }

  /** 整树采纳：收集待确认子树 → 计划修正（补父级 / 排重）→ 顺序分批提交 */
  async function adoptSubtree(node: GenerationTreeNode): Promise<void> {
    const keys = new Set<string>()
    collectSubtree(node, keys)
    const plan = planAdopt(keys)
    if (plan.items.length === 0) {
      ElMessage.warning('该节点下没有可采纳的待确认产物')
      return
    }
    if (plan.notes.length > 0) ElMessage.info(plan.notes.join('；'))
    await base.submitItems(plan.items)
  }

  async function rejectOne(node: GenerationTreeNode, note: string): Promise<void> {
    const item: AiArtifactConfirmItem = { key: node.key, action: 'rejected' }
    if (note.trim()) item.note = note.trim()
    await base.submitItems([item])
  }

  async function adoptEdited(
    node: GenerationTreeNode,
    content: Record<string, unknown>,
  ): Promise<void> {
    await base.submitItems([{ key: node.key, action: 'adopted_edited', content }])
  }

  // ==================== 批量动作 ====================

  async function adoptSelected(): Promise<void> {
    const keys = new Set(
      base.selectedKeys.value.filter(
        (key) => findNode(key)?.row.confirmStatus === 'pending',
      ),
    )
    const plan = planAdopt(keys)
    if (plan.items.length === 0) {
      ElMessage.warning('请先选择要采纳的产物')
      return
    }
    if (plan.notes.length > 0) ElMessage.info(plan.notes.join('；'))
    await base.submitItems(plan.items)
  }

  async function rejectSelected(keys: string[]): Promise<void> {
    if (keys.length === 0) {
      ElMessage.warning('请先选择要驳回的产物')
      return
    }
    try {
      await ElMessageBox.confirm(
        `将驳回所选 ${keys.length} 条产物，不创建任何数据。`,
        '批量驳回',
        { type: 'warning', confirmButtonText: '全部驳回', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    const set = new Set(keys)
    await base.submitItems(orderedItems(set, 'rejected'))
  }

  return {
    tree,
    visibleNodes,
    indentLevel,
    isCollapsed,
    toggleCollapse,
    findNode,
    isDuplicate,
    hasChangedSource,
    ancestorState,
    adoptOne,
    adoptSubtree,
    rejectOne,
    adoptEdited,
    adoptSelected,
    rejectSelected,
  }
}
