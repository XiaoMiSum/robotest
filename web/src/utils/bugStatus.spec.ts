import { describe, expect, it } from 'vitest'
import {
  BUG_RESOLUTION_LABEL,
  BUG_STATUS_LABEL,
  BUG_TYPE_LABEL,
  getValidTargetStatuses,
  isCommentRequired,
  isReopen,
} from './bugStatus'

describe('bugStatus 三态状态机', () => {
  it('按后端状态机返回合法目标状态', () => {
    expect(getValidTargetStatuses('active')).toEqual(['resolved'])
    expect(getValidTargetStatuses('resolved')).toEqual(['closed', 'active'])
    expect(getValidTargetStatuses('closed')).toEqual(['active'])
  })

  it('已解决/已关闭回到激活判定为重开', () => {
    expect(isReopen('resolved', 'active')).toBe(true)
    expect(isReopen('closed', 'active')).toBe(true)
    expect(isReopen('active', 'resolved')).toBe(false)
    expect(isReopen('resolved', 'closed')).toBe(false)
  })

  it('关闭与重开必须填写说明，解决流转选填（由 BugResolveDialog 处理）', () => {
    expect(isCommentRequired('resolved', 'closed')).toBe(true)
    expect(isCommentRequired('resolved', 'active')).toBe(true)
    expect(isCommentRequired('closed', 'active')).toBe(true)
    expect(isCommentRequired('active', 'resolved')).toBe(false)
  })

  it('标签映射覆盖全部枚举值', () => {
    expect(Object.keys(BUG_STATUS_LABEL)).toHaveLength(3)
    expect(Object.keys(BUG_TYPE_LABEL)).toHaveLength(9)
    expect(Object.keys(BUG_RESOLUTION_LABEL)).toHaveLength(7)
    expect(BUG_STATUS_LABEL.active).toBe('激活')
    expect(BUG_TYPE_LABEL.code_error).toBe('代码错误')
    expect(BUG_RESOLUTION_LABEL.wont_fix).toBe('不予解决')
  })
})
