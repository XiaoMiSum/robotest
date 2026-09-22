/**
 * 确认卡片状态机（全局智能助手详细设计 5.3 单测点）：
 * 状态流转 待确认 →（倒计时归零）已超时 /（用户操作）已执行 | 已取消。
 * 纯函数化以便单测覆盖渲染分支（C8）。
 */

/** 确认卡片可见状态（与后端令牌生命周期解耦，前端本地推导） */
export type ConfirmStatus = 'waiting' | 'expired' | 'approved' | 'cancelled' | 'failed'

/** 确认卡片视图状态：confirm_required 帧载荷 + 前端维护的状态 */
export interface ConfirmCardState {
  confirmToken: string
  toolName: string
  /** 写工具参数 JSON（arguments），渲染为操作明细表格 */
  preview: string
  /** 倒计时截止时间（ISO 字符串） */
  expiresAt: string
  status: ConfirmStatus
  /** 执行失败原因（仅 status=failed 时展示，5.2） */
  error?: string
}

/**
 * 状态推导：倒计时归零（expiresAt <= now）的待确认卡片置为已超时；
 * 其余状态由用户操作显式置位，不做二次推导。
 */
export function resolveConfirmStatus(card: ConfirmCardState, now: number): ConfirmStatus {
  if (card.status !== 'waiting') return card.status
  return now >= Date.parse(card.expiresAt) ? 'expired' : 'waiting'
}

/**
 * 剩余倒计时毫秒数：已超时/非待确认返回 0（不渲染倒计时）。
 * 负数按 0 截断，避免倒计时显示负值。
 */
export function remainingMs(card: ConfirmCardState, now: number): number {
  if (card.status !== 'waiting') return 0
  const remain = Date.parse(card.expiresAt) - now
  return remain > 0 ? remain : 0
}

/** 倒计时展示文本 mm:ss（>=1 小时显示 hh:mm:ss） */
export function formatCountdown(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const pad = (n: number): string => String(n).padStart(2, '0')
  return hours > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
}

/** 工具参数 JSON 解析容错：非法 JSON 返回空对象（明细表格渲染为空态） */
export function parseConfirmPreview(preview: string): Record<string, unknown> {
  try {
    const parsed: unknown = JSON.parse(preview)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? (parsed as Record<string, unknown>) : {}
  } catch {
    return {}
  }
}
