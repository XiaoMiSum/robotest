import { describe, expect, it } from 'vitest'
import {
  formatDate,
  formatDateTime,
  parseDateTime,
  formatLocalDateTime,
  formatShortDateTime,
  formatShortId,
  truncateText,
} from './format'

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

describe('parseDateTime UTC 解析', () => {
  it('解析带 Z 的 UTC 时间', () => {
    expect(parseDateTime('2026-01-15T08:30:00Z')?.toISOString()).toBe('2026-01-15T08:30:00.000Z')
  })

  it('兼容无时区字符串并按 UTC 解析', () => {
    expect(parseDateTime('2026-01-15T08:30:00')?.toISOString()).toBe('2026-01-15T08:30:00.000Z')
  })

  it('空值和非法值返回 null', () => {
    expect(parseDateTime(null)).toBeNull()
    expect(parseDateTime('not-a-date')).toBeNull()
  })
})

describe('formatLocalDateTime 业务本地时间', () => {
  it('无时区字符串按本地钟面展示，不做 UTC 转换', () => {
    expect(formatLocalDateTime('2026-09-25T00:00:00')).toBe('2026-09-25 00:00')
  })

  it('带 Z 的历史值仍按真实时刻转换到本地时区', () => {
    const value = '2026-09-24T16:00:00.000Z'
    const date = new Date(value)
    const expected = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`

    expect(formatLocalDateTime(value)).toBe(expected)
  })

  it('空值与非法值返回占位符', () => {
    expect(formatLocalDateTime(null)).toBe('-')
    expect(formatLocalDateTime(undefined)).toBe('-')
    expect(formatLocalDateTime('')).toBe('-')
    expect(formatLocalDateTime('not-a-date')).toBe('-')
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

describe('formatShortId ID 缩略', () => {
  it('长 ID 输出前 4 + ... + 后 4', () => {
    expect(formatShortId('01234567-89ab-cdef-0123-456789abcdef')).toBe('0123...cdef')
  })

  it('长度不足 8 时原样返回', () => {
    expect(formatShortId('abcd1234')).toBe('abcd1234')
    expect(formatShortId('abc')).toBe('abc')
  })

  it('空值返回占位符', () => {
    expect(formatShortId(null)).toBe('-')
    expect(formatShortId(undefined)).toBe('-')
    expect(formatShortId('')).toBe('-')
  })
})

describe('truncateText 文本缩略', () => {
  it('超过上限时截断并追加省略号', () => {
    expect(truncateText('这是一个超过十三个字符的缺陷标题文本', 13)).toBe('这是一个超过十三个字符的缺…')
  })

  it('恰好等于上限时原样返回', () => {
    expect(truncateText('a'.repeat(13), 13)).toBe('a'.repeat(13))
  })

  it('未超过上限时原样返回', () => {
    expect(truncateText('短标题', 13)).toBe('短标题')
  })

  it('空值返回占位符', () => {
    expect(truncateText(null, 13)).toBe('-')
    expect(truncateText(undefined, 13)).toBe('-')
    expect(truncateText('', 13)).toBe('-')
  })
})
