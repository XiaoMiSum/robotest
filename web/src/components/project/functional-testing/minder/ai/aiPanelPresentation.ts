import type { AiPreviewNode } from '@/minder/ai/aiMount'

/**
 * AI 生成/补全抽屉的纯展示逻辑（交互设计 49 §1.2 需求池收敛、完成态结果卡片）：
 * 与组件解耦便于单测，组件只负责渲染与状态编排。
 */

/** 需求标签平铺上限：超出部分以「+N」收口，余量经选取器管理 */
export const REQ_TAG_VISIBLE_MAX = 4
/** 需求标签标题截断阈值：超长标题只保留前 5 字避免撑爆行宽 */
export const REQ_TAG_TITLE_LIMIT = 5

export function formatReqTagLabel(title: string): string {
  return title.length > REQ_TAG_TITLE_LIMIT ? `${title.slice(0, REQ_TAG_TITLE_LIMIT)}...` : title
}

export interface ReqTagSlice<T> {
  visible: T[]
  overflow: number
}

export function sliceReqTags<T>(items: readonly T[], max = REQ_TAG_VISIBLE_MAX): ReqTagSlice<T> {
  const visible = items.slice(0, max)
  return { visible, overflow: items.length - visible.length }
}

/** 需求池 bar 计数文案：有条目时前置「需求池 · 已选 N 条」，否则仅「需求池」 */
export function reqPoolLabel(count: number): string {
  return count > 0 ? `需求池 · 已选 ${count} 条` : '需求池'
}

export interface AiSummaryCard {
  title: string
  priority: string | null
  /** 步骤摘要：以顿号拼接的 step 子节点文本，无步骤时不渲染 */
  steps: string
}

function toCard(node: AiPreviewNode): AiSummaryCard {
  const steps = node.children
    .filter((child) => child.type === 'step')
    .map((child) => child.title)
    .join('；')
  return { title: node.title, priority: node.priority, steps }
}

/**
 * 完成态结果卡片（49 2026-09-30：标题 + 优先级标签 + 步骤摘要，只读无勾选框）：
 * 正常结构顶层即用例节点；若生成结果不含用例节点则回退展示全部顶层节点，避免空列表。
 */
export function buildSummaryCards(nodes: readonly AiPreviewNode[]): AiSummaryCard[] {
  const cases = nodes.filter((node) => node.type === 'case')
  return (cases.length ? cases : nodes).map(toCard)
}
