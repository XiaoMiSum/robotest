/**
 * 解析后端时间字符串为 Date。
 * 后端返回的是 UTC 时间但无时区标识（如 `2026-07-29T02:00:00`），
 * 直接 new Date() 会被 JS 误解析为本地时间，因此无时区标识时补 `Z` 按 UTC 解析。
 */
function parseUtc(value: string): Date {
  const timePart = value.indexOf('T') >= 0 ? value.slice(value.indexOf('T') + 1) : ''
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(timePart)
  return new Date(timePart && !hasZone ? `${value}Z` : value)
}

/** 将后端 UTC 时间字符串按本地时区格式化为 `YYYY-MM-DD HH:mm`，空值返回占位符 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '-'
  const date = parseUtc(value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 仅格式化日期部分 `YYYY-MM-DD`，同样按本地时区输出 */
export function formatDate(value?: string | null): string {
  if (!value) return '-'
  const date = parseUtc(value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 省略年份的紧凑格式 `MM-dd HH:mm`，用于列表等窄列场景，同样按本地时区输出 */
export function formatShortDateTime(value?: string | null): string {
  if (!value) return '-'
  const date = parseUtc(value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
