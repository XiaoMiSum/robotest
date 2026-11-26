// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import type { RequirementSplitRecordItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchRequirementSplitRecords: vi.fn(),
  fetchMembers: vi.fn(),
}))

vi.mock('@/services/project', () => ({
  fetchRequirementSplitRecords: mocks.fetchRequirementSplitRecords,
}))
vi.mock('@/services/workspace', () => ({
  fetchMembers: mocks.fetchMembers,
}))

import {
  useRequirementImportHistory,
  type RequirementImportHistoryRow,
} from './useRequirementImportHistory'

function makeRecord(overrides: Partial<RequirementSplitRecordItem> = {}): RequirementSplitRecordItem {
  return {
    id: 's1',
    sourceType: 'requirement_import',
    sourceFileId: 'f1',
    sourceFileName: 'prd.md',
    aiTaskId: 't1',
    taskTitle: '需求导入',
    taskStatus: 'proposals_ready',
    latestExecution: { executionId: 'e1', seq: 2, status: 'waiting_confirmation' },
    proposalCount: 3,
    adoptedCount: 1,
    status: 'pending',
    adoptResult: null,
    submittedBy: 'u1',
    createdAt: '2026-11-26T06:20:00Z',
    ...overrides,
  }
}

type HistoryState = ReturnType<typeof useRequirementImportHistory>

function mountHistory(): HistoryState {
  let state: HistoryState | null = null
  mount(
    defineComponent({
      setup() {
        state = useRequirementImportHistory()
        return () => h('div')
      },
    }),
  )
  if (state === null) throw new Error('composable 未初始化')
  return state
}

describe('useRequirementImportHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mocks.fetchMembers.mockResolvedValue({
      list: [{ userId: 'u1', name: '张三', username: 'zhangsan' }],
      total: 1,
    })
    mocks.fetchRequirementSplitRecords.mockResolvedValue({ list: [makeRecord()], total: 1 })
  })

  it('加载历史记录并映射原型状态文案与发起人姓名', async () => {
    const s = mountHistory()
    await vi.waitFor(() => expect(s.hasLoaded.value).toBe(true))
    expect(mocks.fetchRequirementSplitRecords).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20 })
    const row = s.rows.value[0] as RequirementImportHistoryRow
    expect(row.taskStatusMeta).toEqual({ label: '待采纳', tagType: 'warning' })
    expect(row.submitterName).toBe('张三')
    expect(s.total.value).toBe(1)
    expect(s.showError.value).toBe(false)
  })

  it('已完成任务映射为已完成，成员接口失败回退用户 ID 不阻塞列表', async () => {
    mocks.fetchMembers.mockRejectedValue(new Error('down'))
    mocks.fetchRequirementSplitRecords.mockResolvedValue({
      list: [makeRecord({ taskStatus: 'closed', submittedBy: 'u2' })],
      total: 1,
    })
    const s = mountHistory()
    await vi.waitFor(() => expect(s.hasLoaded.value).toBe(true))
    expect(s.rows.value[0].taskStatusMeta.label).toBe('已完成')
    expect(s.rows.value[0].submitterName).toBe('u2')
    expect(s.showError.value).toBe(false)
  })

  it('列表加载失败展示错误并可重试', async () => {
    mocks.fetchRequirementSplitRecords.mockRejectedValueOnce(new Error('网络中断'))
    const s = mountHistory()
    await vi.waitFor(() => expect(s.showError.value).toBe(true))
    expect(s.loadError.value).toBe('网络中断')

    await s.load()
    await vi.waitFor(() => expect(s.showError.value).toBe(false))
    expect(s.rows.value).toHaveLength(1)
  })

  it('分页切换按新页码与条数重新加载', async () => {
    const s = mountHistory()
    await vi.waitFor(() => expect(s.hasLoaded.value).toBe(true))
    s.changePage({ pageNo: 2, pageSize: 50 })
    await vi.waitFor(() =>
      expect(mocks.fetchRequirementSplitRecords).toHaveBeenLastCalledWith({ pageNo: 2, pageSize: 50 }),
    )
  })
})
