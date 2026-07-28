import type { MinderNode } from './types'

/**
 * 节点类型/优先级彩色徽标（自定义渲染模块，写法参照 core 自身 priority 模块）：
 * 直接读取节点 data 的 type/priority 绘制圆角色块徽标于节点文本左侧，
 * 三种模式（edit/review/plan）均可见；落库字段格式不变。
 * 不借用 core 内置 priority 渲染（只能显示数字 1-9 且配色不可控），
 * 注册时以同名模块覆盖之，避免其对 'P0'-'P3' 字符串产生的隐形占位图标。
 */
export interface Badge {
  label: string
  color: string
}

// 类型徽标配色遵循详细设计文档 4.1 节：前置蓝 / 步骤绿 / 预期橙；用例取紫与优先级色标区分
const TYPE_BADGES: Record<string, Badge> = {
  case: { label: '用例', color: '#A464FF' },
  precondition: { label: '前置', color: '#409EFF' },
  step: { label: '步骤', color: '#67C23A' },
  expected: { label: '预期', color: '#E6A23C' },
}

const PRIORITY_BADGES: Record<string, Badge> = {
  P0: { label: 'P0', color: '#F56C6C' },
  P1: { label: 'P1', color: '#E6A23C' },
  P2: { label: 'P2', color: '#409EFF' },
  P3: { label: 'P3', color: '#909399' },
}

/** normal 节点与未知类型不显示徽标 */
export function typeBadge(type: unknown): Badge | null {
  return typeof type === 'string' ? (TYPE_BADGES[type] ?? null) : null
}

export function priorityBadge(priority: unknown): Badge | null {
  return typeof priority === 'string' ? (PRIORITY_BADGES[priority] ?? null) : null
}

const BADGE_HEIGHT = 16
const FONT_SIZE = 10
const BADGE_PADDING = 10

/** 估算徽标宽度：中文按字号全宽、拉丁字符按 0.62 估宽（SVG 无法在绘制前精确测量） */
export function badgeWidth(label: string): number {
  let w = 0
  for (const ch of label) w += (ch.codePointAt(0) ?? 0) > 255 ? FONT_SIZE : FONT_SIZE * 0.62
  return Math.round(w) + BADGE_PADDING
}

// ==================== kity 最小结构化类型（C1：禁止 any） ====================
interface KityRect {
  setWidth(width: number): KityRect
  fill(color: string): KityRect
}

interface KityText {
  setX(x: number): KityText
  setY(y: number): KityText
  setTextAnchor(anchor: string): KityText
  setVerticalAlign(align: string): KityText
  setFontSize(size: number): KityText
  setContent(content: string): KityText
  fill(color: string): KityText
}

interface KityGroup {
  addShapes(shapes: unknown[]): KityGroup
  setTranslate(x: number, y: number): KityGroup
}

interface KityStatic {
  createClass(name: string, defines: Record<string, unknown>): unknown
  Group: new () => KityGroup
  Rect: new (width?: number, height?: number, x?: number, y?: number, radius?: number) => KityRect
  Text: new (content?: string) => KityText
  Box: new (box: { x: number; y: number; width: number; height: number }) => unknown
}

// create 返回的 Group 上挂徽标子图形引用，update 时按数据变化改写
type BadgeShape = KityGroup & { badgeRect: KityRect; badgeText: KityText; badgeKey: string }

interface ContentBox {
  left: number
}

function defineBadgeRenderer(kity: KityStatic, base: unknown, getBadge: (node: MinderNode) => Badge | null): unknown {
  return kity.createClass('TestBadgeRenderer', {
    base,
    create(): unknown {
      const group = new kity.Group() as BadgeShape
      const rect = new kity.Rect(0, BADGE_HEIGHT, 0, 0, 3)
      const text = new kity.Text()
        .setFontSize(FONT_SIZE)
        .setTextAnchor('middle')
        .setVerticalAlign('middle')
        .fill('white')
      group.addShapes([rect, text])
      group.badgeRect = rect
      group.badgeText = text
      group.badgeKey = ''
      return group
    },
    shouldRender(node: MinderNode): boolean {
      return Boolean(getBadge(node))
    },
    update(shape: BadgeShape, node: MinderNode, box: ContentBox): unknown {
      const badge = getBadge(node)
      if (!badge) return null
      const width = badgeWidth(badge.label)
      // 数据未变化则跳过图形改写，仅做重定位
      const key = `${badge.label}|${badge.color}`
      if (shape.badgeKey !== key) {
        shape.badgeKey = key
        shape.badgeRect.setWidth(width).fill(badge.color)
        shape.badgeText.setContent(badge.label).setX(width / 2).setY(BADGE_HEIGHT / 2)
      }
      const space = Number(node.getStyle('space-left') ?? 6) || 6
      const x = box.left - width - space
      const y = -BADGE_HEIGHT / 2
      shape.setTranslate(x, y)
      // 返回徽标占用的盒子，参与 contentBox 合并，多个徽标自然向左堆叠
      return new kity.Box({ x, y, width, height: BADGE_HEIGHT })
    },
  })
}

// 幂等粒度必须是"每个 window.kityminder 对象一次"而非进程级布尔：
// HMR 后脚本重新注入会整体替换 kityminder 全局对象（连同其内部模块池），
// 进程级标志不会重置，短路后徽标模块永远进不了新模块池，徽标静默消失
const registeredTargets = new WeakSet<object>()

/**
 * 注册徽标渲染模块（幂等）。Module.register 是全局模块池，
 * 须在 loadLegacyScript 完成后、new Minder 之前调用，对之后创建的所有实例生效。
 */
export function registerBadgesModule(): boolean {
  const km = window.kityminder
  const kity = window.kity as KityStatic | undefined
  if (!km || !kity) return false
  if (registeredTargets.has(km)) return true

  const typeRenderer = defineBadgeRenderer(kity, km.Render, (node) => typeBadge(node.getData('type')))
  const priorityRenderer = defineBadgeRenderer(kity, km.Render, (node) => priorityBadge(node.getData('priority')))

  // 同名覆盖 core 内置 PriorityModule（_modules[name] 纯赋值）：
  // 其 shouldRender 对 'P0' 字符串判真，会渲染无填充色的隐形图标白占 20px 布局空间
  km.Module.register('PriorityModule', () => ({}))
  km.Module.register('TestBadgesModule', () => ({
    renderers: { left: [typeRenderer, priorityRenderer] },
  }))
  registeredTargets.add(km)
  return true
}
