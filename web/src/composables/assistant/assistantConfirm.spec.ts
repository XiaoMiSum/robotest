import { describe, expect, it } from 'vitest'
import {
  formatCountdown,
  parseConfirmPreview,
  remainingMs,
  resolveConfirmStatus,
  type ConfirmCardState,
} from './assistantConfirm'

function card(overrides: Partial<ConfirmCardState> = {}): ConfirmCardState {
  return {
    confirmToken: 'tok-1',
    toolName: 'create_bug',
    preview: '{"projectId":"p1","title":"登录超时"}',
    expiresAt: '2026-08-04T12:00:00.000Z',
    status: 'waiting',
    ...overrides,
  }
}

describe('resolveConfirmStatus', () => {
  it('待确认且未到期保持 waiting', () => {
    expect(resolveConfirmStatus(card(), Date.parse('2026-08-04T11:59:59.000Z'))).toBe('waiting')
  })

  it('倒计时归零（含等号边界）置为 expired', () => {
    expect(resolveConfirmStatus(card(), Date.parse('2026-08-04T12:00:00.000Z'))).toBe('expired')
  })

  it('已超时后不再回退为 waiting', () => {
    const expired = card({ status: 'expired' })
    expect(resolveConfirmStatus(expired, 0)).toBe('expired')
  })

  it('approved / cancelled / failed 不被倒计时推导覆盖', () => {
    expect(resolveConfirmStatus(card({ status: 'approved' }), Number.MAX_SAFE_INTEGER)).toBe('approved')
    expect(resolveConfirmStatus(card({ status: 'cancelled' }), Number.MAX_SAFE_INTEGER)).toBe('cancelled')
    expect(resolveConfirmStatus(card({ status: 'failed' }), Number.MAX_SAFE_INTEGER)).toBe('failed')
  })
})

describe('remainingMs', () => {
  it('等待中返回正剩余毫秒', () => {
    const remain = remainingMs(card(), Date.parse('2026-08-04T11:59:30.000Z'))
    expect(remain).toBe(30_000)
  })

  it('非等待状态返回 0', () => {
    expect(remainingMs(card({ status: 'approved' }), 0)).toBe(0)
  })

  it('负数剩余按 0 截断', () => {
    expect(remainingMs(card(), Date.parse('2026-08-04T12:00:01.000Z'))).toBe(0)
  })
})

describe('formatCountdown', () => {
  it('不足 1 小时显示 mm:ss', () => {
    expect(formatCountdown(65_000)).toBe('01:05')
  })

  it('1 小时以上显示 hh:mm:ss', () => {
    expect(formatCountdown(3_661_000)).toBe('01:01:01')
  })

  it('0 显示 00:00', () => {
    expect(formatCountdown(0)).toBe('00:00')
  })
})

describe('parseConfirmPreview', () => {
  it('解析合法 JSON 为明细对象', () => {
    expect(parseConfirmPreview('{"title":"登录超时","severity":"P1"}')).toEqual({
      title: '登录超时',
      severity: 'P1',
    })
  })

  it('非法 JSON 返回空对象', () => {
    expect(parseConfirmPreview('not-json')).toEqual({})
  })

  it('非对象 JSON（数组/标量）返回空对象', () => {
    expect(parseConfirmPreview('[1,2]')).toEqual({})
    expect(parseConfirmPreview('"str"')).toEqual({})
  })
})
