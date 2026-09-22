import type { AiPreviewNode } from './aiMount'

/**
 * AI 生成用例预览弹窗（AiPreviewDialog.vue）的脑图渲染支撑：
 * 1) AiPreviewNode[] → kityminder importJson 结构转换（预览快照仅存于弹窗实例内存，纯本地不落库）；
 *    多棵生成子树合并为虚拟根（preview-root），单根保持原样渲染；
 * 2) 勾选框渲染器（仿 badges.ts 的 defineBadgeRenderer）：**仅用例（case）节点**左侧绘制 ☑/☐，
 *    读取节点 data 的 aiSelected；前置/步骤/预期等非用例节点无勾选框（勾选态随所属用例级联）。
 *    经全局模块池注册（registerCheckboxModule），须在 new Minder 之前完成，与 badges 同池
 *    保证 root 与其他节点渲染器数量一致（构造后注入实例 _rendererClasses 会导致 root 越界，
 *    布局中断节点全部叠在原点）。真实文档 AI 节点仅携带 aiGenerated 无 aiSelected，
 *    shouldRender 短路不渲染，天然隔离。
 */

// 勾选框视觉常量
const CHECKBOX_SIZE = 14

// ==================== 快照转换 ====================

interface KityJsonNode {
  data: Record<string, unknown>
  children: KityJsonNode[]
}

/**
 * 预览树 → kityminder JSON（根节点合并为单个 root，供 importJson 渲染）：
 * 预览节点 key 直接作为节点 data.id（快照内唯一，勾选命中据此回查预览树）。
 * 多棵生成子树时合并为虚拟根（纯生成树预览无文档根，直接铺开多棵 case 子树）；
 * 单根时保持原样渲染，避免多余虚拟节点。
 */
export function previewToKityJson(nodes: AiPreviewNode[]): Record<string, unknown> | null {
  const root = nodes.length === 1 ? nodes[0] : mergePreviewRoots(nodes)
  if (!root) return null
  const walk = (node: AiPreviewNode): KityJsonNode => ({
    data: {
      id: node.key,
      text: node.title,
      type: node.type ?? 'normal',
      priority: node.priority ?? undefined,
      aiGenerated: node.aiGenerated,
      // 既有/虚拟节点无勾选框；仅 AI 节点渲染勾选框并携带勾选态与可勾选标记
      aiSelected: node.aiGenerated ? node.aiSelected : undefined,
      aiSelectable: node.aiGenerated ? node.aiSelectable : undefined,
    },
    children: node.children.map(walk),
  })
  return {
    root: walk(root),
    template: 'default',
    theme: 'fresh-blue',
  }
}

/** 多根预览树合并：虚拟根仅作渲染容器（非 AI 节点，无勾选框、点击不响应） */
function mergePreviewRoots(nodes: AiPreviewNode[]): AiPreviewNode {
  return {
    key: 'preview-root',
    title: '生成结果',
    type: null,
    priority: null,
    aiGenerated: false,
    aiSelected: false,
    aiSelectable: false,
    children: nodes,
  }
}

/** 深拷贝：从当前预览快照重新生成独立 JSON，避免 importJson 引用同一对象 */
export function previewToKityJsonDeep(nodes: AiPreviewNode[]): Record<string, unknown> | null {
  const json = previewToKityJson(nodes)
  return json ? JSON.parse(JSON.stringify(json)) : null
}

// ==================== 勾选框渲染器 ====================

// kity 最小结构化类型（C1：禁止 any，写法对齐 badges.ts）
interface KityRect {
  setWidth(width: number): KityRect
  setHeight(height: number): KityRect
  setX(x: number): KityRect
  setY(y: number): KityRect
  setRadius(r: number): KityRect
  stroke(color: string, width: number): KityRect
  fill(color: string | 'none'): KityRect
  setOpacity(value: number): KityRect
}

interface KityText {
  setX(x: number): KityText
  setY(y: number): KityText
  setTextAnchor(anchor: string): KityText
  setVerticalAlign(align: string): KityText
  setFontSize(size: number): KityText
  setContent(content: string): KityText
  fill(color: string): KityText
  // 加粗通过 setAttr 设置（kity.Text 无 setFontWeight 方法）
  setAttr(name: string, value: string): KityText
  setOpacity(value: number): KityText
}

interface KityGroup {
  addShapes(shapes: unknown[]): KityGroup
  setTranslate(x: number, y: number): KityGroup
}

export interface KityStatic {
  createClass(name: string, defines: Record<string, unknown>): unknown
  Group: new () => KityGroup
  Rect: new (width?: number, height?: number, x?: number, y?: number, radius?: number) => KityRect
  Text: new (content?: string) => KityText
  Box: new (box: { x: number; y: number; width: number; height: number }) => unknown
}

type CheckboxShape = KityGroup & { checkRect: KityRect; checkMark: KityText; checkState: string }

interface PreviewNodeLike {
  getData(key: string): unknown
  getStyle(name: string): unknown
}

interface ContentBox {
  left: number
  right: number
}

/** 勾选标记颜色：勾选态用 AI 青色（与 aiBadge 一致），未勾选灰色边框 */
const CHECKED_COLOR = '#13C2C2'
const UNCHECKED_COLOR = '#909399'

/**
 * 勾选框渲染器（经 registerCheckboxModule 注册进全局模块池，new Minder 之前生效）：
 * 读取节点 data.aiSelected 绘制 ☑/☐（勾选青色对勾 / 未勾选灰色空框）。
 * 不做节点透明度淡化：kityminder 事件命中的 getTargetNode 对 getOpacity()<1
 * 的 shape 返回 null，淡化会令未勾选节点无法被点击恢复勾选。
 */
export function createCheckboxRenderer(kity: KityStatic, base: unknown): unknown {
  return kity.createClass('AiPreviewCheckboxRenderer', {
    base,
    create(): unknown {
      const group = new kity.Group() as CheckboxShape
      const rect = new kity.Rect(CHECKBOX_SIZE, CHECKBOX_SIZE, 0, 0, 3)
        .stroke(UNCHECKED_COLOR, 1)
        .fill('none')
      // 对勾文字锚点 middle 仅将文字中心对齐到 (x,y)，须显式 setX/setY 到方框中心，
      // 否则文字中心停在原点 (0,0)，而方框占 (0,0)-(14,14)，对勾整体偏到框外（同 badges.ts 写法）
      const mark = new kity.Text('✓')
        .setFontSize(10)
        .setX(CHECKBOX_SIZE / 2)
        .setY(CHECKBOX_SIZE / 2)
        .setTextAnchor('middle')
        .setVerticalAlign('middle')
        .setAttr('font-weight', 'bold')
        .fill(CHECKED_COLOR)
      group.addShapes([rect, mark])
      group.checkRect = rect
      group.checkMark = mark
      group.checkState = ''
      return group
    },
    // 勾选框渲染以 aiSelectable 为准（预览树组装时按模式打标）：
    // 生成模式仅 case 节点可勾选（勾选单位是「用例及其整棵内部结构」，非用例节点点击不响应、随用例级联）；
    // 补全模式全部生成节点可勾选（逐项取舍）。真实文档实例也会收集本渲染器：真实文档 AI 节点仅携带
    // aiGenerated、无 aiSelected（预览快照独有字段），以此区分隔离，避免真实文档误渲染勾选框
    shouldRender(node: PreviewNodeLike): boolean {
      return (
        node.getData('aiGenerated') === true &&
        typeof node.getData('aiSelected') === 'boolean' &&
        node.getData('aiSelectable') === true
      )
    },
    update(shape: CheckboxShape, node: PreviewNodeLike, box: ContentBox): unknown {
      const selected = node.getData('aiSelected') === true
      const state = selected ? 'checked' : 'unchecked'
      if (shape.checkState !== state) {
        shape.checkState = state
        if (selected) {
          shape.checkRect.stroke(CHECKED_COLOR, 1).fill('none')
          shape.checkMark.setOpacity(1)
        } else {
          shape.checkRect.stroke(UNCHECKED_COLOR, 1).fill('none')
          shape.checkMark.setOpacity(0)
        }
      }
      const space = Number(node.getStyle('space-left') ?? 6) || 6
      const x = box.left - CHECKBOX_SIZE - space
      const y = -CHECKBOX_SIZE / 2
      shape.setTranslate(x, y)
      return new kity.Box({ x, y, width: CHECKBOX_SIZE, height: CHECKBOX_SIZE })
    },
  })
}

// 幂等粒度必须是"每个 window.kityminder 对象一次"而非进程级布尔：
// HMR 后脚本重新注入会整体替换 kityminder 全局对象（连同其内部模块池），
// 进程级标志不会重置，短路后勾选框模块永远进不了新模块池，勾选框静默消失（同 badges.ts）
const registeredTargets = new WeakSet<object>()

/**
 * 注册勾选框渲染模块（幂等）。Module.register 是全局模块池，
 * 须在 loadLegacyScript 完成后、new Minder 之前调用，对之后创建的所有实例生效。
 * 与 badges 同池注册而非构造后注入实例 _rendererClasses 的原因：
 * root 节点的 _renderers 在 Minder 构造期创建，构造后注入会令 root（11 个）与
 * importJson 新建节点（12 个）渲染器数量不一致，renderNodeBatch 按 nodes[0]._renderers.length
 * 遍历到 root 时越界（Cannot set properties of undefined (setting 'contentBox')），
 * 布局中断导致所有节点叠在原点（kityminder.core.js renderNodeBatch）。
 * 真实文档实例同样收集本渲染器，但真实文档 AI 节点无 aiSelected 数据，
 * shouldRender 短路不渲染，不影响真实文档（见 createCheckboxRenderer.shouldRender）。
 */
export function registerCheckboxModule(): boolean {
  const km = window.kityminder
  const kity = window.kity as KityStatic | undefined
  if (!km || !kity) return false
  if (registeredTargets.has(km)) return true
  const checkboxRenderer = createCheckboxRenderer(kity, km.Render)
  km.Module.register('AiPreviewCheckboxModule', () => ({
    renderers: { left: [checkboxRenderer] },
  }))
  registeredTargets.add(km)
  return true
}
