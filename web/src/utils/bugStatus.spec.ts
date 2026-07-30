import { describe, expect, it } from 'vitest'
import {
  BUG_RESOLUTION_LABEL,
  BUG_STATUS_LABEL,
  BUG_STATUS_TAG_TYPE,
  BUG_TYPE_LABEL,
  getValidTargetStatuses,
  isCommentRequired,
  isReopen,
} from './bugStatus'

describe('bugStatus 四态状态机', () => {
  it('按后端状态机返回合法目标状态', () => {
    expect(getValidTargetStatuses('active')).toEqual(['resolved', 'rejected'])
    expect(getValidTargetStatuses('resolved')).toEqual(['closed', 'active'])
    expect(getValidTargetStatuses('rejected')).toEqual(['closed', 'active'])
    expect(getValidTargetStatuses('closed')).toEqual(['active'])
  })

  it('已解决/已拒绝/已关闭回到激活判定为重开', () => {
    expect(isReopen('resolved', 'active')).toBe(true)
    expect(isReopen('rejected', 'active')).toBe(true)
    expect(isReopen('closed', 'active')).toBe(true)
    expect(isReopen('active', 'resolved')).toBe(false)
    expect(isReopen('resolved', 'closed')).toBe(false)
  })

  it('关闭/拒绝与重开必须填写说明，解决流转选填（由 BugResolveDialog 处理）', () => {
    expect(isCommentRequired('resolved', 'closed')).toBe(true)
    expect(isCommentRequired('resolved', 'active')).toBe(true)
    expect(isCommentRequired('closed', 'active')).toBe(true)
    expect(isCommentRequired('active', 'rejected')).toBe(true)
    expect(isCommentRequired('rejected', 'active')).toBe(true)
    expect(isCommentRequired('rejected', 'closed')).toBe(true)
    expect(isCommentRequired('active', 'resolved')).toBe(false)
  })

  it('标签映射覆盖全部枚举值', () => {
    expect(Object.keys(BUG_STATUS_LABEL)).toHaveLength(4)
    expect(Object.keys(BUG_TYPE_LABEL)).toHaveLength(9)
    expect(Object.keys(BUG_RESOLUTION_LABEL)).toHaveLength(7)
    expect(BUG_STATUS_LABEL.active).toBe('激活')
    expect(BUG_STATUS_LABEL.rejected).toBe('已拒绝')
    expect(BUG_TYPE_LABEL.code_error).toBe('代码错误')
    expect(BUG_RESOLUTION_LABEL.wont_fix).toBe('不予解决')
  })

  it('状态颜色映射覆盖全部状态且语义对应', () => {
    expect(Object.keys(BUG_STATUS_TAG_TYPE)).toEqual(Object.keys(BUG_STATUS_LABEL))
    expect(BUG_STATUS_TAG_TYPE.active).toBe('primary')
    expect(BUG_STATUS_TAG_TYPE.resolved).toBe('success')
    expect(BUG_STATUS_TAG_TYPE.rejected).toBe('warning')
    expect(BUG_STATUS_TAG_TYPE.closed).toBe('info')
  })
})
