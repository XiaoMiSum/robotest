import * as Y from 'yjs'

/**
 * Yjs 分片数据模型 ⇄ kityminder 画布的双向同步（详细设计 11.1/11.2）。
 *
 * 数据模型：Y.Map('mindmap') 按节点分片——
 *   n:<id>  节点数据快照（node.data 全量字段）
 *   order   Y.Map<父节点id, Y.Array<子节点id>> 兄弟顺序
 *   root / template / theme 标量键
 * 合并粒度即节点粒度：不同节点的并发编辑互不覆盖，网络只传增量分片。
 */

export interface KmJsonNode {
  data: Record<string, unknown>
  children?: KmJsonNode[]
}

export interface KmExportJson {
  root: KmJsonNode
  template?: string
  theme?: string
}

export const ROOT_KEY = 'root'
export const TEMPLATE_KEY = 'template'
export const THEME_KEY = 'theme'
export const ORDER_KEY = 'order'

export function nodeKey(id: string): string {
  return `n:${id}`
}

interface DesiredState {
  rootId: string
  template: string
  theme: string
  nodes: Map<string, Record<string, unknown>>
  order: Map<string, string[]>
}

// 画布树 → 期望状态；id 缺失/重复/成环视为非法，返回 null 由调用方跳过本次发布
function collectDesired(json: KmExportJson): DesiredState | null {
  const rootId = json.root?.data?.id
  if (typeof rootId !== 'string' || !rootId) return null
  const nodes = new Map<string, Record<string, unknown>>()
  const order = new Map<string, string[]>()
  const walk = (node: KmJsonNode): boolean => {
    const id = node.data.id
    if (typeof id !== 'string' || !id || nodes.has(id)) return false
    nodes.set(id, node.data)
    const childIds: string[] = []
    for (const child of node.children ?? []) {
      if (!walk(child)) return false
      childIds.push(child.data.id as string)
    }
    order.set(id, childIds)
    return true
  }
  if (!walk(json.root)) return null
  return {
    rootId,
    template: json.template || 'default',
    theme: json.theme || 'fresh-blue',
    nodes,
    order,
  }
}

// 键序无关的深比较：画布 data 与 Yjs 解码后的分片对象键插入顺序可能不同
export function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (typeof a !== typeof b) return false
  if (a === null || b === null || typeof a !== 'object') return false
  const ka = Object.keys(a as object)
  const kb = Object.keys(b as object)
  if (ka.length !== kb.length) return false
  return ka.every(
    (k) => k in (b as object) && deepEqual((a as Record<string, unknown>)[k], (b as Record<string, unknown>)[k]),
  )
}

function ensureOrderMap(ymap: Y.Map<unknown>): Y.Map<Y.Array<string>> {
  let order = ymap.get(ORDER_KEY)
  if (!(order instanceof Y.Map)) {
    order = new Y.Map()
    ymap.set(ORDER_KEY, order)
  }
  return order as Y.Map<Y.Array<string>>
}

// 公共前后缀裁剪后整体替换中段：把数组写放大降到最小
export function reconcileChildArray(arr: Y.Array<string>, desired: string[]): void {
  const cur = arr.toArray()
  let p = 0
  while (p < cur.length && p < desired.length && cur[p] === desired[p]) p++
  let s = 0
  while (
    s < cur.length - p &&
    s < desired.length - p &&
    cur[cur.length - 1 - s] === desired[desired.length - 1 - s]
  ) {
    s++
  }
  const delCount = cur.length - p - s
  if (delCount > 0) arr.delete(p, delCount)
  const insertIds = desired.slice(p, desired.length - s)
  if (insertIds.length > 0) arr.insert(p, insertIds)
}

// 本地画布 → Yjs：与 Yjs 当前状态 diff 后仅写变化分片，单 transact 原子提交
export function publishCanvasToYjs(ydoc: Y.Doc, json: KmExportJson): void {
  const desired = collectDesired(json)
  if (!desired) return
  const ymap = ydoc.getMap('mindmap')
  ydoc.transact(() => {
    if (ymap.get(ROOT_KEY) !== desired.rootId) ymap.set(ROOT_KEY, desired.rootId)
    if (ymap.get(TEMPLATE_KEY) !== desired.template) ymap.set(TEMPLATE_KEY, desired.template)
    if (ymap.get(THEME_KEY) !== desired.theme) ymap.set(THEME_KEY, desired.theme)

    for (const key of Array.from(ymap.keys())) {
      if (!key.startsWith('n:')) continue
      if (!desired.nodes.has(key.slice(2))) ymap.delete(key)
    }
    for (const [id, data] of desired.nodes) {
      const current = ymap.get(nodeKey(id))
      if (!deepEqual(current, data)) ymap.set(nodeKey(id), { ...data })
    }

    const orderMap = ensureOrderMap(ymap)
    for (const pid of Array.from(orderMap.keys())) {
      if (!desired.order.has(pid)) orderMap.delete(pid)
    }
    for (const [pid, ids] of desired.order) {
      let arr = orderMap.get(pid)
      if (!(arr instanceof Y.Array)) {
        arr = new Y.Array()
        orderMap.set(pid, arr)
      }
      reconcileChildArray(arr as Y.Array<string>, ids)
    }
  })
}

// Yjs 分片 → 完整脑图 JSON；结构损坏（缺分片/成环）返回 null
export function buildJsonFromYjs(ymap: Y.Map<unknown>): KmExportJson | null {
  const rootId = ymap.get(ROOT_KEY)
  if (typeof rootId !== 'string') return null
  const orderMap = ymap.get(ORDER_KEY)
  if (!(orderMap instanceof Y.Map)) return null
  const visiting = new Set<string>()
  const build = (id: string): KmJsonNode | null => {
    if (visiting.has(id)) return null
    visiting.add(id)
    const data = ymap.get(nodeKey(id))
    if (!data || typeof data !== 'object') return null
    const children: KmJsonNode[] = []
    const ids = orderMap.get(id)
    if (ids instanceof Y.Array) {
      for (const cid of ids.toArray()) {
        if (typeof cid !== 'string') return null
        const child = build(cid)
        if (!child) return null
        children.push(child)
      }
    }
    visiting.delete(id)
    return { data: JSON.parse(JSON.stringify(data)) as Record<string, unknown>, children }
  }
  const root = build(rootId)
  if (!root) return null
  return {
    root,
    template: (ymap.get(TEMPLATE_KEY) as string) || 'default',
    theme: (ymap.get(THEME_KEY) as string) || 'fresh-blue',
  }
}

// ==================== 远端增量应用 ====================

export type MinderOp =
  | { kind: 'template'; value: string }
  | { kind: 'theme'; value: string }
  | { kind: 'data'; id: string; key: string; value: unknown }
  | { kind: 'data-remove'; id: string; key: string }
  | { kind: 'remove'; id: string }
  | { kind: 'add'; id: string; parentId: string; index: number; json: KmJsonNode }

export interface MinderNodeLike {
  setData(key: string, value: unknown): void
  getData(): Record<string, unknown>
}

export interface MinderLike {
  getNodeById(id: string): MinderNodeLike | undefined
  createNode(data: unknown, parent: MinderNodeLike | null, index?: number): MinderNodeLike
  removeNode(node: MinderNodeLike): void
  useTemplate(value: string): void
  useTheme(value: string): void
  refresh(): void
}

/**
 * 两棵树按节点 id 对齐 diff。结构变更统一表达为 remove/add（内核无原子 move 原语，
 * 以摘除+重插替代，仅重建被移动子树的 DOM）；返回 null 表示无法对齐（根不一致）。
 */
export function diffKmTrees(oldJson: KmExportJson, newJson: KmExportJson): MinderOp[] | null {
  const oldRootId = oldJson.root?.data?.id
  const newRootId = newJson.root?.data?.id
  if (typeof oldRootId !== 'string' || oldRootId !== newRootId) return null

  const ops: MinderOp[] = []
  if ((oldJson.template || 'default') !== (newJson.template || 'default')) {
    ops.push({ kind: 'template', value: newJson.template || 'default' })
  }
  if ((oldJson.theme || 'fresh-blue') !== (newJson.theme || 'fresh-blue')) {
    ops.push({ kind: 'theme', value: newJson.theme || 'fresh-blue' })
  }

  const walk = (oldNode: KmJsonNode, newNode: KmJsonNode): boolean => {
    const nodeId = newNode.data.id as string
    const od = oldNode.data ?? {}
    const nd = newNode.data ?? {}
    for (const key of Object.keys(od)) {
      if (!(key in nd)) ops.push({ kind: 'data-remove', id: nodeId, key })
    }
    for (const key of Object.keys(nd)) {
      if (!deepEqual(od[key], nd[key])) ops.push({ kind: 'data', id: nodeId, key, value: nd[key] })
    }

    const oldKids = oldNode.children ?? []
    const newKids = newNode.children ?? []
    const oldIds = oldKids.map((c) => c.data.id as string)
    const newIds = newKids.map((c) => c.data.id as string)
    const newIdSet = new Set(newIds)

    // 先摘除：倒序保证按 id 寻址的应用顺序无关紧要
    for (let i = oldIds.length - 1; i >= 0; i--) {
      if (!newIdSet.has(oldIds[i])) ops.push({ kind: 'remove', id: oldIds[i] })
    }

    // 序列对齐：处理后保证 work[0..i] 与期望前缀一致；同父移动=摘除+重插
    const work = oldIds.filter((id) => newIdSet.has(id))
    const jsonById = new Map(newKids.map((c) => [c.data.id as string, c]))
    for (let i = 0; i < newIds.length; i++) {
      const want = newIds[i]
      if (work[i] === want) continue
      const j = work.indexOf(want, i)
      if (j > -1) {
        ops.push({ kind: 'remove', id: want })
        work.splice(j, 1)
      }
      ops.push({ kind: 'add', id: want, parentId: nodeId, index: i, json: jsonById.get(want)! })
      work.splice(i, 0, want)
    }
    for (let i = work.length - 1; i >= newIds.length; i--) ops.push({ kind: 'remove', id: work[i] })

    // 仅递归两侧都存在的配对子节点；新增子树由 add 的整棵 json 覆盖
    const oldById = new Map(oldKids.map((c) => [c.data.id as string, c]))
    for (const child of newKids) {
      const oldChild = oldById.get(child.data.id as string)
      if (oldChild && !walk(oldChild, child)) return false
    }
    return true
  }

  return walk(oldJson.root, newJson.root) ? ops : null
}

function insertSubtree(m: MinderLike, json: KmJsonNode, parent: MinderNodeLike, index: number): void {
  const node = m.createNode(JSON.parse(JSON.stringify(json.data)), parent, index)
  ;(json.children ?? []).forEach((child, i) => insertSubtree(m, child, node, i))
}

/**
 * 把远端 diff 翻译为内核原语增量应用：先删后增再改属性，最后一次 refresh
 * （renderTree + layout + contentchange）统一收尾，全程保留未受影响节点的 DOM 与选中态。
 * 返回 false 表示无法对齐，调用方应兜底 importJson 全量重建。
 */
export function applyRemoteDiff(m: MinderLike, oldJson: KmExportJson, newJson: KmExportJson): boolean {
  const ops = diffKmTrees(oldJson, newJson)
  if (!ops) return false
  for (const op of ops) {
    if (op.kind !== 'remove') continue
    const node = m.getNodeById(op.id)
    if (node) m.removeNode(node)
  }
  for (const op of ops) {
    if (op.kind !== 'add') continue
    const parent = m.getNodeById(op.parentId)
    if (!parent) return false
    insertSubtree(m, op.json, parent, op.index)
  }
  for (const op of ops) {
    if (op.kind === 'data') {
      m.getNodeById(op.id)?.setData(op.key, op.value)
    } else if (op.kind === 'data-remove') {
      const node = m.getNodeById(op.id)
      if (node) delete node.getData()[op.key]
    } else if (op.kind === 'template') {
      m.useTemplate(op.value)
    } else if (op.kind === 'theme') {
      m.useTheme(op.value)
    }
  }
  m.refresh()
  return true
}
