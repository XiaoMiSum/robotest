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

export interface MinderNode {
  data: Record<string, unknown>
  getData(key: string): unknown
  getStyle(name: string): unknown
  getText(): string
  getChildren(): MinderNode[]
  getChild(index: number): MinderNode
  getRenderBox(rendererType?: string, refer?: unknown): RenderBox
  render(): MinderNode
}

export interface MinderEvent {
  type: string
  patch?: { express: string; node: MinderNode; index: number }
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
