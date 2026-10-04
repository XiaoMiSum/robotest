// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import type { PageResult, RequirementDetail, RequirementListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchRequirements: vi.fn(),
  getRequirement: vi.fn(),
}))

vi.mock('@/services/project', () => ({
  fetchRequirements: mocks.fetchRequirements,
  getRequirement: mocks.getRequirement,
}))

import { useRequirementPicker } from './useRequirementPicker'

type Picker = ReturnType<typeof useRequirementPicker>

function mountPicker(): { picker: Picker; host: ReturnType<typeof mount> } {
  let picker: Picker | null = null
  const host = mount(
    defineComponent({
      setup() {
        picker = useRequirementPicker()
        return () => h('div')
      },
    }),
  )
  return { picker: picker as unknown as Picker, host }
}

function item(id: string, code: string, title: string): RequirementListItem {
  return {
    id,
    code,
    title,
    moduleId: null,
    moduleName: null,
    systemVersion: null,
    status: 'confirmed',
    coverageStatus: null,
    priority: null,
    ownerId: null,
    ownerName: null,
    source: 'manual',
    updatedAt: '2026-10-04T00:00:00Z',
  }
}

function page(list: RequirementListItem[], total = list.length): PageResult<RequirementListItem> {
  return { list, total }
}

describe('useRequirementPicker', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('load 按当前页码与关键字请求并回填', async () => {
    mocks.fetchRequirements.mockResolvedValue(page([item('r1', 'REQ-001', '登录')]))
    const { picker } = mountPicker()

    await picker.load()

    expect(mocks.fetchRequirements).toHaveBeenCalledWith({
      keyword: undefined,
      pageNo: 1,
      pageSize: 10,
    })
    expect(picker.rows.value).toHaveLength(1)
    expect(picker.total.value).toBe(1)
    expect(picker.hasLoaded.value).toBe(true)
    expect(picker.loading.value).toBe(false)
  })

  it('load 失败时记录错误并可重试', async () => {
    mocks.fetchRequirements.mockRejectedValueOnce(new Error('网络异常'))
    const { picker } = mountPicker()

    await picker.load()
    expect(picker.loadError.value).toBe('网络异常')
    expect(picker.loading.value).toBe(false)

    mocks.fetchRequirements.mockResolvedValueOnce(page([item('r1', 'REQ-001', '登录')]))
    picker.retry()
    await vi.waitFor(() => expect(picker.loadError.value).toBe(''))
    expect(picker.hasLoaded.value).toBe(true)
  })

  it('search 立即重置页码并以关键字查询', async () => {
    mocks.fetchRequirements.mockResolvedValue(page([]))
    const { picker } = mountPicker()
    picker.keyword.value = '结算'

    picker.search()

    expect(mocks.fetchRequirements).toHaveBeenCalledWith({
      keyword: '结算',
      pageNo: 1,
      pageSize: 10,
    })
  })

  it('关键字防抖 1s 才触发查询，未变化不重复查询', async () => {
    mocks.fetchRequirements.mockResolvedValue(page([]))
    const { picker } = mountPicker()
    picker.keyword.value = '支'
    expect(mocks.fetchRequirements).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(999)
    expect(mocks.fetchRequirements).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    expect(mocks.fetchRequirements).toHaveBeenCalledTimes(1)

    // 关键词未变化时防抖到点不重复查询
    picker.keyword.value = '支'
    await vi.advanceTimersByTimeAsync(1000)
    expect(mocks.fetchRequirements).toHaveBeenCalledTimes(1)
  })

  it('changePage 切页后重新加载', async () => {
    mocks.fetchRequirements.mockResolvedValue(page([]))
    const { picker } = mountPicker()

    picker.changePage(3)
    await vi.waitFor(() => expect(picker.pageNo.value).toBe(3))
    expect(mocks.fetchRequirements).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 3 }),
    )
  })

  it('toggle 在选中与取消间切换并维护计数', async () => {
    const { picker } = mountPicker()
    const row = item('r1', 'REQ-001', '登录')

    picker.toggle(row)
    expect(picker.selectedCount.value).toBe(1)
    expect(picker.selectedIds.value).toEqual(['r1'])

    picker.toggle(row)
    expect(picker.selectedCount.value).toBe(0)

    picker.toggle(row)
    picker.toggle(item('r2', 'REQ-002', '登出'))
    expect(picker.selectedIds.value).toEqual(['r1', 'r2'])
    expect(picker.selected.value.get('r2')?.title).toBe('登出')

    picker.clearSelected()
    expect(picker.selectedCount.value).toBe(0)
  })

  it('seed 回填既有条目，reset 保留已选但清空筛选', async () => {
    mocks.fetchRequirements.mockResolvedValue(page([]))
    const { picker } = mountPicker()
    picker.seed([{ id: 'r9', code: 'REQ-009', title: '既有' }])
    picker.keyword.value = '临时'

    picker.reset()
    await vi.waitFor(() => expect(mocks.fetchRequirements).toHaveBeenCalled())

    expect(picker.keyword.value).toBe('')
    expect(picker.selectedIds.value).toEqual(['r9'])
    expect(mocks.fetchRequirements).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: undefined, pageNo: 1 }),
    )
  })

  it('openDetail 拉取详情并可关闭', async () => {
    const detail = { id: 'r1', description: '# 正文' } as RequirementDetail
    mocks.getRequirement.mockResolvedValue(detail)
    const { picker } = mountPicker()

    await picker.openDetail({ id: 'r1' })
    expect(mocks.getRequirement).toHaveBeenCalledWith('r1')
    expect(picker.detail.value?.description).toBe('# 正文')

    picker.closeDetail()
    expect(picker.detail.value).toBeNull()
    expect(picker.detailError.value).toBe('')
  })

  it('openDetail 失败时记录错误', async () => {
    mocks.getRequirement.mockRejectedValue(new Error('需求不存在'))
    const { picker } = mountPicker()

    await picker.openDetail({ id: 'bad' })
    expect(picker.detailError.value).toBe('需求不存在')
    expect(picker.detail.value).toBeNull()
  })

  it('openDetail 过期响应被丢弃', async () => {
    let resolveFirst: (value: RequirementDetail) => void = () => undefined
    mocks.getRequirement
      .mockImplementationOnce(() => new Promise<RequirementDetail>((r) => (resolveFirst = r)))
      .mockResolvedValueOnce({ id: 'r2', description: '新正文' } as RequirementDetail)
    const { picker } = mountPicker()

    const first = picker.openDetail({ id: 'r1' })
    await picker.openDetail({ id: 'r2' })
    resolveFirst({ id: 'r1', description: '旧正文' } as RequirementDetail)
    await first

    expect(picker.detail.value?.description).toBe('新正文')
  })

  it('手动 search 覆盖进行中的防抖定时器', async () => {
    mocks.fetchRequirements.mockResolvedValue(page([]))
    const { picker } = mountPicker()
    picker.keyword.value = '退'

    picker.search()
    await vi.advanceTimersByTimeAsync(5000)

    expect(mocks.fetchRequirements).toHaveBeenCalledTimes(1)
  })

  it('组件卸载时清理防抖定时器', async () => {
    mocks.fetchRequirements.mockResolvedValue(page([]))
    const { picker, host } = mountPicker()
    picker.keyword.value = '卸'
    await nextTick()
    host.unmount()
    await vi.advanceTimersByTimeAsync(5000)
    expect(mocks.fetchRequirements).not.toHaveBeenCalled()
  })
})
