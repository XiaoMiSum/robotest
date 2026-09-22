import type { AiGeneratedNode, AiMinderCommand, AiMinderSelector, AiPriority, CaseNodeType } from '@/types'
import type { Minder, MinderNode } from '../types'
import { appendGeneratedTree, findNodeById, type MountTargetSource, type MinderLike } from './aiMount'

/**
 * 脑图操作指令集（DSL）确定性执行器（智能用例生成与脑图智能编辑详细设计 4.4.2）：
 * 标题引用解析、命中计算与合法性过滤均为纯函数（§5.1，便于单测），
 * 批量执行在单个 contentchange 内完成——history 对整批只生成一条撤销补丁（单撤销组，同 4.2 挂载执行器）。
 * highlight 视觉态与预览弹窗由调用方组件维护（§5.1）。
 */

/** 标题引用保留值：LLM 表达「当前/选中节点」时的占位（4.4.1），解析阶段替换为选中节点 */
const SELECTED_MARKER = '@selected'

/** 指令数量上限（4.4.1：commands 数组按序执行，上限 10 条） */
const MAX_COMMANDS = 10

/** 类型中文标签（预览弹窗「将跳过」清单原因展示，同时供 DslPreviewDialog 复用） */
export const TYPE_LABELS: Record<CaseNodeType, string> = {
  case: '用例',
  normal: '普通',
  precondition: '前置条件',
  step: '步骤',
  expected: '预期结果',
}

/** 执行器工作节点：kity MinderNode 运行时持有 parent，合法性判断需读取父节点，类型在此收口 */
export interface DslTreeNode extends MountTargetSource {
  parent: DslTreeNode | null
}

/** 执行阶段树变更能力（removeChild/appendChild/render/expand 未在 types.ts MinderNode 声明） */
interface MutableDslNode extends DslTreeNode {
  appendChild(node: MutableDslNode): unknown
  removeChild(node: MutableDslNode): unknown
  render(): unknown
  expand(): unknown
}

/** 整批解析中止原因（4.4.1：任一标题引用零命中/多义/@selected 无选中即整批不进预览） */
export type DslAbortReason =
  | { kind: 'too-many'; count: number }
  | { kind: 'zero-hit' | 'ambiguous'; field: 'subtreeRootTitle' | 'targetParentTitle'; title: string }
  | { kind: 'no-selected'; field: 'subtreeRootTitle' | 'targetParentTitle' }

/** 单命令解析结果：标题引用解析后的范围与目标父节点 */
export interface TitleResolution {
  /** subtreeRootTitle 解析出的子树范围根节点（null = 未限定，全文档） */
  subtreeRoot: DslTreeNode | null
  /** move 的目标父节点（null = 非 move 命令） */
  targetParent: DslTreeNode | null
}

export type TitleResolveResult =
  | { ok: true; resolved: TitleResolution[] }
  | { ok: false; commandIndex: number; reason: DslAbortReason }

/** 单命令执行计划 */
export interface DslCommandPlan {
  command: AiMinderCommand
  /** 命中且通过合法性过滤的节点（执行集合） */
  execute: MountTargetSource[]
  /** 合法性过滤跳过的节点及原因（预览弹窗「将跳过」清单） */
  skipped: { node: MountTargetSource; reason: string }[]
  /** move：解析出的目标父节点（仅 move 命令非空） */
  targetParent: MountTargetSource | null
  /** add_child：待挂载节点树（每个命中节点各挂独立副本） */
  addNodes: AiGeneratedNode[] | null
}

/** 整批预览计划 */
export interface DslPlan {
  commands: DslCommandPlan[]
  totalHits: number
  totalSkipped: number
}

export type DslPlanResult = { ok: true; plan: DslPlan } | { ok: false; commandIndex: number; reason: DslAbortReason }

export interface DslApplyResult {
  /** 实际执行的节点数（mark 类逐节点计 1，add_child 计新增节点数） */
  applied: number
  /** 合法性过滤跳过的节点数 */
  skipped: number
}

// ==================== 标题引用解析（预览阶段一次性解析） ====================

function resolveTitleRef(
  root: DslTreeNode,
  title: string,
  field: 'subtreeRootTitle' | 'targetParentTitle',
  selectedNodeId: string | null | undefined,
): { ok: true; node: DslTreeNode } | { ok: false; reason: DslAbortReason } {
  if (title === SELECTED_MARKER) {
    if (!selectedNodeId) return { ok: false, reason: { kind: 'no-selected', field } }
    const node = findNodeById(root, selectedNodeId)
    if (!node) return { ok: false, reason: { kind: 'no-selected', field } }
    return { ok: true, node: node as DslTreeNode }
  }
  const trimmed = title.trim()
  const matched: DslTreeNode[] = []
  const walk = (node: DslTreeNode): void => {
    if (((node.data.text as string | undefined) ?? '').trim() === trimmed) matched.push(node)
    for (const child of node.getChildren()) walk(child as DslTreeNode)
  }
  walk(root)
  if (matched.length === 0) return { ok: false, reason: { kind: 'zero-hit', field, title } }
  if (matched.length > 1) return { ok: false, reason: { kind: 'ambiguous', field, title } }
  return { ok: true, node: matched[0] }
}

/**
 * 一次性解析全部标题引用（4.4.1 解析中止粒度）：
 * 任一命令零命中/多义/@selected 无选中即整批失败，不做部分解析。
 */
export function resolveTitleRefs(
  root: MountTargetSource | null,
  commands: AiMinderCommand[],
  selectedNodeId?: string | null,
): TitleResolveResult {
  if (!root) return { ok: false, commandIndex: 0, reason: { kind: 'zero-hit', field: 'subtreeRootTitle', title: '' } }
  if (commands.length > MAX_COMMANDS) {
    return { ok: false, commandIndex: 0, reason: { kind: 'too-many', count: commands.length } }
  }
  const treeRoot = root as DslTreeNode
  const resolved: TitleResolution[] = []
  for (let i = 0; i < commands.length; i++) {
    const command = commands[i]
    const resolution: TitleResolution = { subtreeRoot: null, targetParent: null }
    if (command.selector.subtreeRootTitle) {
      const ref = resolveTitleRef(treeRoot, command.selector.subtreeRootTitle, 'subtreeRootTitle', selectedNodeId)
      if (!ref.ok) return { ok: false, commandIndex: i, reason: ref.reason }
      resolution.subtreeRoot = ref.node
    }
    if (command.action.type === 'move') {
      const ref = resolveTitleRef(treeRoot, command.action.params.targetParentTitle, 'targetParentTitle', selectedNodeId)
      if (!ref.ok) return { ok: false, commandIndex: i, reason: ref.reason }
      resolution.targetParent = ref.node
    }
    resolved.push(resolution)
  }
  return { ok: true, resolved }
}

// ==================== 命中计算（selector 各条件 AND） ====================

function matchesSelector(node: MountTargetSource, selector: AiMinderSelector): boolean {
  const data = node.data
  if (selector.types && !selector.types.includes(data.type as CaseNodeType)) return false
  // priorities 仅对 case 生效（4.4.1）：非 case 节点的优先级字段无业务意义，不参与筛选
  if (selector.priorities) {
    if (data.type !== 'case' || !selector.priorities.includes(data.priority as AiPriority)) return false
  }
  if (selector.keyword) {
    const text = String(data.text ?? '').toLowerCase()
    if (!text.includes(selector.keyword.toLowerCase())) return false
  }
  if (selector.aiGenerated !== undefined && data.aiGenerated !== selector.aiGenerated) return false
  return true
}

/** 收集范围根及全部子孙（子树范围含根自身，保证根节点可被直接命中） */
function collectScope(root: MountTargetSource): MountTargetSource[] {
  const out: MountTargetSource[] = [root]
  for (const child of root.getChildren()) out.push(...collectScope(child))
  return out
}

/** 计算单命令命中集合：subtreeRootTitle 限定范围，其余条件 AND（4.4.1） */
export function computeHits(
  root: MountTargetSource | null,
  command: AiMinderCommand,
  resolution: TitleResolution | null,
): MountTargetSource[] {
  if (!root) return []
  const scopeRoot = resolution?.subtreeRoot ?? root
  return collectScope(scopeRoot).filter((node) => matchesSelector(node, command.selector))
}

// ==================== 合法性过滤（2.2 父子约束 + move 环检测） ====================

function isWithin(node: DslTreeNode, target: DslTreeNode): boolean {
  if (node === target) return true
  for (const child of node.getChildren()) {
    if (isWithin(child as DslTreeNode, target)) return true
  }
  return false
}

function markTypeReason(node: DslTreeNode, nodeType: CaseNodeType): string | null {
  const parentType = node.parent?.data.type as CaseNodeType | undefined
  const children = node.getChildren()
  if (nodeType === 'case') {
    // case 下不得再嵌套 case：父节点为 case、或自身已有 case 子节点均非法
    if (parentType === 'case') return '父节点为用例：case 下不得再嵌套 case'
    if (children.some((c) => (c.data.type as CaseNodeType) === 'case')) return '存在用例子节点：case 下不得再嵌套 case'
    return null
  }
  if (nodeType === 'precondition' || nodeType === 'step' || nodeType === 'expected') {
    // precondition/step/expected 只能是 case 的直接子节点且自身无子节点
    if (parentType !== 'case') return `${TYPE_LABELS[nodeType]}只能是 case 的直接子节点`
    if (children.length > 0) return `${TYPE_LABELS[nodeType]}自身不得有子节点`
    return null
  }
  // normal：normal 可嵌套 normal/case，但子节点中的前置/步骤/预期只能挂在 case 下
  if (children.some((c) => {
    const t = c.data.type as CaseNodeType
    return t === 'precondition' || t === 'step' || t === 'expected'
  })) return '前置/步骤/预期只能作为用例直接子节点'
  return null
}

function moveReason(node: DslTreeNode, targetParent: DslTreeNode): string | null {
  if (!node.parent) return '根节点不可移动'
  // 环检测：目标父节点不得位于被移动节点自身子树内（4.4.2）
  if (isWithin(node, targetParent)) return '目标父节点位于待移动节点子树内（成环）'
  const targetType = targetParent.data.type as CaseNodeType
  // precondition/step/expected 自身不得有子节点，因此不能作为目标父
  if (targetType === 'precondition' || targetType === 'step' || targetType === 'expected') {
    return '前置/步骤/预期自身不得有子节点，不能作为目标父'
  }
  const nodeType = node.data.type as CaseNodeType
  if (nodeType === 'precondition' || nodeType === 'step' || nodeType === 'expected') {
    if (targetType !== 'case') return `${TYPE_LABELS[nodeType]}只能作为 case 的直接子节点`
    if (node.getChildren().length > 0) return `${TYPE_LABELS[nodeType]}自身不得有子节点`
    return null
  }
  if (nodeType === 'case' && targetType === 'case') return 'case 下不得再嵌套 case'
  return null
}

/** 2.2 结构断言：precondition/step/expected 无子节点；case 不嵌套 case */
function hasIllegalTree(node: AiGeneratedNode): boolean {
  const children = node.children ?? []
  if ((node.type === 'precondition' || node.type === 'step' || node.type === 'expected') && children.length > 0) {
    return true
  }
  if (node.type === 'case' && children.some((c) => c.type === 'case')) return true
  return children.some(hasIllegalTree)
}

function addChildReason(target: DslTreeNode, nodes: AiGeneratedNode[]): string | null {
  const targetType = target.data.type as CaseNodeType
  // precondition/step/expected 自身不得有子节点，不能作为挂载目标
  if (targetType === 'precondition' || targetType === 'step' || targetType === 'expected') {
    return '前置/步骤/预期自身不得有子节点，不能挂载子节点'
  }
  if (nodes.some(hasIllegalTree)) return '指令节点树违反父子约束'
  if (targetType === 'case' && nodes.some((n) => n.type === 'case')) return 'case 下不得再嵌套 case'
  // normal 可嵌套 normal/case；前置/步骤/预期只能挂 case 下
  if (targetType === 'normal' && nodes.some((n) => n.type === 'precondition' || n.type === 'step' || n.type === 'expected')) {
    return '前置/步骤/预期只能作为 case 的直接子节点'
  }
  return null
}

/** 单命令合法性过滤：逐命中节点校验，非法者进「将跳过」清单（4.4.2） */
export function filterCommand(
  command: AiMinderCommand,
  hits: MountTargetSource[],
  resolution: TitleResolution | null,
): DslCommandPlan {
  const execute: MountTargetSource[] = []
  const skipped: { node: MountTargetSource; reason: string }[] = []
  const action = command.action
  if (action.type === 'highlight') {
    // highlight 为本地临时视觉态，无合法性约束，全部命中执行
    return { command, execute: hits, skipped, targetParent: null, addNodes: null }
  }
  const targetParent = action.type === 'move' ? (resolution?.targetParent ?? null) : null
  for (const hit of hits) {
    const node = hit as DslTreeNode
    let reason: string | null = null
    switch (action.type) {
      case 'mark_type':
        reason = markTypeReason(node, action.params.nodeType)
        break
      case 'mark_priority':
        // 优先级仅对 case 生效（4.4.1）：非 case 命中不改变类型（编辑器手工标记的自动标 case 联动不适用于 DSL）
        if (node.data.type !== 'case') reason = '优先级仅对用例节点生效'
        break
      case 'move':
        reason = targetParent ? moveReason(node, targetParent as DslTreeNode) : '目标父节点未解析'
        break
      case 'add_child':
        reason = addChildReason(node, action.params.nodes)
        break
    }
    if (reason) skipped.push({ node: hit, reason })
    else execute.push(hit)
  }
  return {
    command,
    execute,
    skipped,
    targetParent,
    addNodes: action.type === 'add_child' ? action.params.nodes : null,
  }
}

/** 整批预览计划：解析 → 逐命令命中 → 合法性过滤（解析失败即整批中止，4.4.1） */
export function buildDslPlan(
  root: MountTargetSource | null,
  commands: AiMinderCommand[],
  selectedNodeId?: string | null,
): DslPlanResult {
  const resolvedResult = resolveTitleRefs(root, commands, selectedNodeId)
  if (!resolvedResult.ok) return { ok: false, commandIndex: resolvedResult.commandIndex, reason: resolvedResult.reason }
  const plans: DslCommandPlan[] = []
  let totalHits = 0
  let totalSkipped = 0
  for (let i = 0; i < commands.length; i++) {
    const hits = computeHits(root, commands[i], resolvedResult.resolved[i])
    const plan = filterCommand(commands[i], hits, resolvedResult.resolved[i])
    totalHits += plan.execute.length + plan.skipped.length
    totalSkipped += plan.skipped.length
    plans.push(plan)
  }
  return { ok: true, plan: { commands: plans, totalHits, totalSkipped } }
}

// ==================== 批量执行（单撤销组，同 4.2） ====================

/**
 * 批量执行预览计划：所有编辑操作在单个 contentchange 内完成——
 * history 只生成一条撤销补丁（单撤销组），并搭上 Yjs 同步与防抖落库管道（同挂载执行器）。
 * mark_type 到 case 不触发 4.3 优先级推荐（4.4.2：推荐仅由手工单节点标记触发）。
 */
export function applyDslPlan(minder: Minder, plan: DslPlan): DslApplyResult {
  let applied = 0
  let skipped = 0
  const affected: MinderNode[] = []
  for (const entry of plan.commands) {
    const { command } = entry
    skipped += entry.skipped.length
    switch (command.action.type) {
      case 'mark_type': {
        const nodeType = command.action.params.nodeType
        for (const hit of entry.execute) {
          const data = hit.data
          const wasCase = data.type === 'case'
          data.type = nodeType
          // 标记联动规则（同编辑器 markAs）：case 缺省 P2；改非用例类型连带清除优先级
          if (nodeType === 'case') {
            if (!data.priority) data.priority = 'P2'
          } else if (wasCase) {
            delete data.priority
          }
          affected.push(hit as unknown as MinderNode)
          applied++
        }
        break
      }
      case 'mark_priority': {
        for (const hit of entry.execute) {
          hit.data.priority = command.action.params.priority
          affected.push(hit as unknown as MinderNode)
          applied++
        }
        break
      }
      case 'highlight':
        // 本地临时视觉态由调用方维护（§5.1），此处不改数据不产生撤销历史
        break
      case 'move': {
        const target = entry.targetParent as MutableDslNode | null
        if (!target) break
        for (const hit of entry.execute) {
          const node = hit as MutableDslNode
          const parent = node.parent as MutableDslNode | null
          if (!parent) continue
          parent.removeChild(node)
          target.appendChild(node)
          node.render()
          affected.push(hit as unknown as MinderNode)
          applied++
        }
        target.expand()
        break
      }
      case 'add_child': {
        const nodes = entry.addNodes
        if (!nodes) break
        for (const hit of entry.execute) {
          // 多目标语义（4.4.2）：每个命中节点各挂载一份独立副本（节点 ID 各异）
          const copy = JSON.parse(JSON.stringify(nodes)) as AiGeneratedNode[]
          applied += appendGeneratedTree(minder as unknown as MinderLike, hit, copy)
          affected.push(hit as unknown as MinderNode)
        }
        break
      }
    }
  }
  if (affected.length > 0) {
    minder.select(affected, true)
    minder.refresh()
    // 单次 contentchange：history 对整批生成单条撤销补丁（4.2 同款）
    minder.fire('contentchange')
  }
  return { applied, skipped }
}
