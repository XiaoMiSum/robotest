import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCaseRequirementLink } from './useCaseRequirementLink'

const mocks = vi.hoisted(() => ({ fetch: vi.fn(), update: vi.fn(), error: vi.fn(), success: vi.fn() }))
vi.mock('@/services/project', () => ({
  fetchCaseRequirements: mocks.fetch,
  updateCaseRequirements: mocks.update,
}))
vi.mock('element-plus', () => ({ ElMessage: { error: mocks.error, success: mocks.success } }))

describe('useCaseRequirementLink', () => {
  beforeEach(() => vi.resetAllMocks())

  it('保存打开时的用例，避免选择变化后覆盖另一个用例', async () => {
    let selected = 'case-a'
    mocks.fetch.mockResolvedValue([{ id: 'r1', code: 'REQ-1', title: '需求' }])
    mocks.update.mockResolvedValue([])
    const link = useCaseRequirementLink(() => selected)
    await link.open()
    expect(link.picks()).toHaveLength(1)
    selected = 'case-b'
    await link.confirm([])
    expect(mocks.update).toHaveBeenCalledWith('case-a', null)
    expect(link.visible.value).toBe(false)
  })

  it('关闭后忽略未完成的加载', async () => {
    let resolve!: (value: []) => void
    mocks.fetch.mockReturnValue(new Promise<[]>((done) => { resolve = done }))
    const link = useCaseRequirementLink(() => 'case-a')
    const pending = link.open()
    link.close()
    resolve([])
    await pending
    expect(link.visible.value).toBe(false)
  })

  it('加载和保存失败显示错误并结束保存状态', async () => {
    const link = useCaseRequirementLink(() => 'case-a')
    mocks.fetch.mockRejectedValueOnce(new Error('加载失败')).mockResolvedValue([])
    await link.open()
    expect(mocks.error).toHaveBeenCalledWith('加载失败')
    await link.open()
    mocks.update.mockRejectedValue(new Error('保存失败'))
    await link.confirm(['r1'])
    expect(mocks.update).toHaveBeenCalledWith('case-a', 'r1')
    expect(mocks.error).toHaveBeenCalledWith('保存失败')
    expect(link.saving.value).toBe(false)
  })
})
