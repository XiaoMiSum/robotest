// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { useTableColumns, type TableColumnMeta } from './useTableColumns'

const COLUMNS: TableColumnMeta[] = [
  { key: 'title', label: '标题' },
  { key: 'status', label: '状态' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'actions', label: '操作', pinned: true },
]

function make(userId = 'user-1', key = 'bug-list') {
  return useTableColumns({ pageKey: key, columns: COLUMNS, getUserId: () => userId })
}

beforeEach(() => {
  localStorage.clear()
})

describe('useTableColumns', () => {
  it('默认全部可选列与固定列可见', () => {
    const c = make()
    expect(c.visibleKeys.value).toEqual(['actions', 'title', 'status', 'createdAt'])
    expect(c.isVisible('title')).toBe(true)
    expect(c.isVisible('actions')).toBe(true)
  })

  it('隐藏列后写入本地偏好，重新创建实例按偏好恢复', () => {
    const c = make()
    c.setVisible('status', false)
    expect(c.isVisible('status')).toBe(false)
    expect(make().visibleKeys.value).toEqual(['actions', 'title', 'createdAt'])
  })

  it('固定列不可隐藏', () => {
    const c = make()
    c.setVisible('actions', false)
    expect(c.isVisible('actions')).toBe(true)
  })

  it('全选与恢复默认均得到初始列集合', () => {
    const c = make()
    c.setVisible('title', false)
    c.selectAll()
    expect(c.visibleKeys.value).toEqual(['actions', 'title', 'status', 'createdAt'])
    c.setVisible('status', false)
    c.reset()
    expect(c.visibleKeys.value).toEqual(['actions', 'title', 'status', 'createdAt'])
  })

  it('读取时过滤已下线列并强制补回固定列', () => {
    localStorage.setItem('robotest_list_columns:bug-list:user-1', JSON.stringify(['title', 'legacy', 'actions']))
    expect(make().visibleKeys.value).toEqual(['actions', 'title'])
  })

  it('不同页面与不同用户之间偏好隔离', () => {
    const c = make('user-1')
    c.setVisible('status', false)
    expect(make('user-2').isVisible('status')).toBe(true)
    expect(make('user-1', 'review-list').isVisible('status')).toBe(true)
  })

  it('本地内容损坏时回退默认列集合，不抛异常', () => {
    localStorage.setItem('robotest_list_columns:bug-list:user-1', 'not-json')
    expect(make().visibleKeys.value).toEqual(['actions', 'title', 'status', 'createdAt'])
  })

  it('未注入 getUserId 时按 localStorage 中的当前用户隔离', () => {
    localStorage.setItem('robotest_user', JSON.stringify({ id: 'user-9' }))
    const c = useTableColumns({ pageKey: 'bug-list', columns: COLUMNS })
    c.setVisible('status', false)
    expect(localStorage.getItem('robotest_list_columns:bug-list:user-9')).toContain('title')
    expect(localStorage.getItem('robotest_list_columns:bug-list:anonymous')).toBeNull()
  })
})
