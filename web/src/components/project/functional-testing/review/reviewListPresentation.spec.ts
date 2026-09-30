import { describe, expect, it } from 'vitest'
import {
  REVIEW_STATUS_META,
  canReopenReview,
  canRejectReview,
  isActiveReview,
  reviewAvatars,
  reviewListAction,
  reviewPassRate,
  reviewProgressStatus,
  reviewProgressText,
  reviewStatusLabel,
  reviewStatusMeta,
} from './reviewListPresentation'

const row = {
  initiator: { id: 'i1', name: '张明' },
  participants: [
    { id: 'p1', name: '李华', avatarUrl: 'https://cdn.example.com/a.png' },
    { id: 'p2', name: '王芳', avatarUrl: null },
    { id: 'i1', name: '张明', avatarUrl: null },
    { id: 'p3', name: '陈晨', avatarUrl: null },
    { id: 'p4', name: '周敏', avatarUrl: null },
  ],
}

describe('reviewStatusMeta', () => {
  it('四态标签与语义色与演示稿一致', () => {
    expect(REVIEW_STATUS_META.new).toEqual({ label: '待评审', tagType: 'info', modifier: 'neutral' })
    expect(REVIEW_STATUS_META.in_progress).toEqual({ label: '进行中', tagType: 'warning', modifier: 'running' })
    expect(REVIEW_STATUS_META.completed).toEqual({ label: '已通过', tagType: 'success', modifier: 'success' })
    expect(REVIEW_STATUS_META.rejected).toEqual({ label: '已驳回', tagType: 'danger', modifier: 'danger' })
  })

  it('未知状态兜底为占位符', () => {
    expect(reviewStatusLabel('paused')).toBe('—')
    expect(reviewStatusMeta('paused').tagType).toBe('info')
    expect(reviewStatusMeta('new').label).toBe('待评审')
  })
})

describe('状态驱动的入口', () => {
  it('活跃态可标记/完成/驳回，终态只读', () => {
    expect(isActiveReview('new')).toBe(true)
    expect(isActiveReview('in_progress')).toBe(true)
    expect(isActiveReview('completed')).toBe(false)
    expect(isActiveReview('rejected')).toBe(false)
    expect(canRejectReview('in_progress')).toBe(true)
    expect(canRejectReview('rejected')).toBe(false)
    expect(canReopenReview('rejected')).toBe(true)
    expect(canReopenReview('new')).toBe(false)
  })

  it('列表主操作：活跃态进入，终态查看', () => {
    expect(reviewListAction('new')).toBe('enter')
    expect(reviewListAction('in_progress')).toBe('enter')
    expect(reviewListAction('completed')).toBe('view')
    expect(reviewListAction('rejected')).toBe('view')
  })
})

describe('reviewPassRate', () => {
  it('待评审展示占位符，进行中常规展示百分比', () => {
    expect(reviewPassRate('new', 0)).toEqual({ text: '—', tone: 'muted' })
    expect(reviewPassRate('in_progress', 60)).toEqual({ text: '60%', tone: 'default' })
    expect(reviewPassRate('in_progress', 94.64)).toEqual({ text: '94.6%', tone: 'default' })
  })

  it('已通过绿、已驳回红，百分比保留一位小数', () => {
    expect(reviewPassRate('completed', 96.25)).toEqual({ text: '96.3%', tone: 'success' })
    expect(reviewPassRate('completed', 100)).toEqual({ text: '100%', tone: 'success' })
    expect(reviewPassRate('rejected', 62.9)).toEqual({ text: '62.9%', tone: 'danger' })
  })
})

describe('reviewProgressText / reviewProgressTone', () => {
  it('进度文案为已评审/总数且不越界', () => {
    expect(reviewProgressText(18, 30)).toBe('18/30')
    expect(reviewProgressText(0, 48)).toBe('0/48')
    expect(reviewProgressText(52, 30)).toBe('30/30')
    expect(reviewProgressText(-1, 30)).toBe('0/30')
  })

  it('进行中橙、已驳回红，其余默认色', () => {
    expect(reviewProgressStatus('in_progress')).toBe('warning')
    expect(reviewProgressStatus('rejected')).toBe('exception')
    expect(reviewProgressStatus('new')).toBeUndefined()
    expect(reviewProgressStatus('completed')).toBeUndefined()
  })
})

describe('reviewAvatars', () => {
  it('发起人置顶品牌色并按 id 去重', () => {
    const stack = reviewAvatars(row, 3)
    expect(stack.visible).toHaveLength(3)
    expect(stack.visible[0]).toMatchObject({ key: 'i1', label: '张', brand: true })
    expect(stack.overflow).toBe(2)
    // 参与者中的发起人重复项被折叠，总数 = 发起人 + 4 位参与者
    expect(stack.overflow + stack.visible.length).toBe(5)
  })

  it('头像图缺失时回退到姓名首字', () => {
    const stack = reviewAvatars({
      initiator: { id: 'i1', name: '张明' },
      participants: [{ id: 'p1', name: 'member_a', avatarUrl: null }],
    }, 5)
    expect(stack.visible[1]).toEqual({
      key: 'p1', label: 'm', avatarUrl: null, brand: false,
    })
    expect(stack.overflow).toBe(0)
  })
})
