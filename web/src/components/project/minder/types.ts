/**
 * kityminder-core 以经典 script 标签加载（原因见 MindMapEditor.vue 头部说明），
 * 无法从 npm 包获得类型声明，这里为编辑内核维护最小结构化类型，
 * 避免各 runtime 中散落 unknown 断言（C1：禁止 any）。
 */
export interface RenderBox {
  x: number
  y: number
  width: number
  height: number
}

// 节点渲染图形（kity Shape）：原位编辑时需要隐藏节点文本并读取字色
// 注意：core 每次 noderender 会强制 setVisible(true)，隐藏必须用 setOpacity(0)
export interface RenderShape {
  setOpacity(value: number): unknown
  node: SVGElement
}

export interface NodeRenderer {
  getRenderShape(): RenderShape | null
}

export interface MinderNode {
  data: Record<string, unknown>
  getData(key: string): unknown
  getStyle(name: string): unknown
  getText(): string
  getChildren(): MinderNode[]
  getChild(index: number): MinderNode
  getRenderBox(rendererType?: string, refer?: unknown): RenderBox
  getRenderer(rendererType: string): NodeRenderer | null
  render(): MinderNode
}

export interface MinderEvent {
  type: string
  patch?: { express: string; node: MinderNode; index: number }
  zoom?: number
}

export type MinderEventHandler = (e: MinderEvent) => void

export interface JsonPatch {
  op: 'add' | 'remove' | 'replace'
  path: string
  value?: unknown
}

export interface Minder {
  on(events: string, handler: MinderEventHandler): Minder
  off(events: string, handler: MinderEventHandler): Minder
  fire(type: string, params?: Record<string, unknown>): Minder
  execCommand(name: string, ...args: unknown[]): unknown
  queryCommandState(name: string): number
  queryCommandValue(name: string): unknown
  getSelectedNode(): MinderNode | null
  select(node: MinderNode | MinderNode[], single?: boolean): Minder
  exportJson(): Record<string, unknown>
  importJson(json: Record<string, unknown>): Minder
  applyPatches(patches: JsonPatch[]): Minder
  dispatchKeyEvent(e: KeyboardEvent): void
  getRenderTarget(): HTMLElement
  getRoot(): MinderNode
  getStatus(): string
  getZoomValue(): number
  disable(): void
  enable(): void
  refresh(): Minder
  layout(duration?: number): Minder
  destroy(): void
}

// ==================== 导航器（MinderNavigator）所需扩展类型 ====================
// kity 的 Box 实例（getBoundaryBox/getView 返回值），比纯数据 RenderBox 多出几何运算方法
export interface KityBox extends RenderBox {
  intersect(another: KityBox): KityBox
}

export interface KityPoint {
  x: number
  y: number
  offset(dx: number, dy: number): KityPoint
}

// 缩略图遍历所需的节点布局/连线信息（编辑内核的 MinderNode 未覆盖）
export interface NavNode {
  parent: NavNode | null
  traverse(fn: (node: NavNode) => void): void
  getLayoutBox(): RenderBox
  getConnection(): { getPathData(): unknown } | null
  isExpanded(): boolean
}

// 导航器只依赖视图层 API，与编辑内核的 Minder 接口独立维护
export interface NavMinder {
  on(events: string, handler: MinderEventHandler): NavMinder
  off(events: string, handler: MinderEventHandler): NavMinder
  execCommand(name: string, ...args: unknown[]): unknown
  queryCommandState(name: string): number
  getRoot(): NavNode
  getOption(key: string): unknown
  getStyle(name: string): unknown
  getRenderContainer(): { getBoundaryBox(): KityBox }
  getViewDragger(): { getView(): KityBox; moveTo(position: KityPoint, duration?: number): void }
  getPaper(): { getViewPortMatrix(): { transformBox(box: KityBox): KityBox } }
}

export interface KityMinderGlobal {
  Minder: new (options: Record<string, unknown>) => Minder
  Module: { register(name: string, module: () => Record<string, unknown>): void }
  Render: unknown
}

declare global {
  interface Window {
    kity?: unknown
    kityminder?: KityMinderGlobal
  }
}
