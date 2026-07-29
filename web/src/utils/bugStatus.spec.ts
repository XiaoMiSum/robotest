import { describe, expect, it } from 'vitest'
import { getValidTargetStatuses, isCommentRequired, isReopen } from './bugStatus'
import type { BugStatus } from '@/types'

describe('bugStatus 状态机', () => {
  it('按后端状态机返回合法目标状态', () => {
    expect(getValidTargetStatuses('new')).toEqual(['assigned'])
    expect(getValidTargetStatuses('assigned')).toEqual(['fixing'])
    expect(getValidTargetStatuses('fixing')).toEqual(['fixed'])
    expect(getValidTargetStatuses('fixed')).toEqual(['verified'])
    expect(getValidTargetStatuses('verified')).toEqual(['closed', 'fixing'])
    expect(getValidTargetStatuses('closed')).toEqual(['fixing'])
  })

  it('已验证/已关闭回到修复中判定为重开', () => {
    expect(isReopen('verified', 'fixing')).toBe(true)
    expect(isReopen('closed', 'fixing')).toBe(true)
    expect(isReopen('assigned', 'fixing')).toBe(false)
    expect(isReopen('verified', 'closed')).toBe(false)
  })

  it('关闭与重开必须填写说明，其余流转选填', () => {
    expect(isCommentRequired('verified', 'closed')).toBe(true)
    expect(isCommentRequired('closed', 'fixing')).toBe(true)
    expect(isCommentRequired('verified', 'fixing')).toBe(true)
    const optional: Array<[BugStatus, BugStatus]> = [
      ['new', 'assigned'],
      ['assigned', 'fixing'],
      ['fixing', 'fixed'],
      ['fixed', 'verified'],
    ]
    for (const [from, to] of optional) {
      expect(isCommentRequired(from, to)).toBe(false)
    }
  })
})
