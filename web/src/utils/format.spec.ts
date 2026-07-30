import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, formatShortDateTime } from './format'

const pad = (n: number): string => String(n).padStart(2, '0')

/** 由 UTC 时间分量构造 Date，再按本地时区拼期望值，与被测实现的字符串解析路径相互独立 */
function expectedLocal(y: number, mo: number, d: number, h: number, mi: number): string {
  const date = new Date(Date.UTC(y, mo - 1, d, h, mi))
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

describe('formatDateTime UTC 转本地时区', () => {
  it('无时区标识的后端字符串按 UTC 解析后转为本地时间', () => {
    expect(formatDateTime('2026-01-15T08:30:00')).toBe(expectedLocal(2026, 1, 15, 8, 30))
  })

  it('无时区标识与显式 Z 后缀结果一致', () => {
    expect(formatDateTime('2026-07-29T02:00:00')).toBe(formatDateTime('2026-07-29T02:00:00Z'))
  })

  it('带毫秒的无时区字符串同样按 UTC 解析', () => {
    expect(formatDateTime('2026-07-29T02:00:00.123')).toBe(formatDateTime('2026-07-29T02:00:00Z'))
  })

  it('带显式偏移的字符串不重复补 Z', () => {
    expect(formatDateTime('2026-07-29T10:00:00+08:00')).toBe(expectedLocal(2026, 7, 29, 2, 0))
  })

  it('空值与非法值返回占位符', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('')).toBe('-')
    expect(formatDateTime('not-a-date')).toBe('-')
  })
})

describe('formatDate UTC 转本地时区', () => {
  it('无时区标识的后端字符串按 UTC 解析后输出本地日期', () => {
    expect(formatDate('2026-01-15T08:30:00')).toBe(expectedLocal(2026, 1, 15, 8, 30).slice(0, 10))
  })

  it('空值与非法值返回占位符', () => {
    expect(formatDate(null)).toBe('-')
    expect(formatDate(undefined)).toBe('-')
    expect(formatDate('not-a-date')).toBe('-')
  })
})

describe('formatShortDateTime UTC 转本地时区', () => {
  it('无时区标识的后端字符串按 UTC 解析后输出省略年份的本地时间', () => {
    expect(formatShortDateTime('2026-01-15T08:30:00')).toBe(expectedLocal(2026, 1, 15, 8, 30).slice(5))
  })

  it('无时区标识与显式 Z 后缀结果一致', () => {
    expect(formatShortDateTime('2026-07-29T02:00:00')).toBe(formatShortDateTime('2026-07-29T02:00:00Z'))
  })

  it('空值与非法值返回占位符', () => {
    expect(formatShortDateTime(null)).toBe('-')
    expect(formatShortDateTime(undefined)).toBe('-')
    expect(formatShortDateTime('not-a-date')).toBe('-')
  })
})
