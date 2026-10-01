import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RequirementSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  getDocumentRequirements: vi.fn<() => Promise<RequirementSummary[]>>(),
  setDocumentRequirements: vi.fn<() => Promise<void>>(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  getDocumentRequirements: mocks.getDocumentRequirements,
  setDocumentRequirements: mocks.setDocumentRequirements,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useMindmapRequirementLink } from './useMindmapRequirementLink'

describe('useMindmapRequirementLink', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始状态：选择器关闭且无关联', () => {
    const s = useMindmapRequirementLink(() => 'doc-1')
    expect(s.reqSelectorVisible.value).toBe(false)
    expect(s.associatedReqIds.value).toEqual([])
  })

  it('openRequirementSelector 加载已有关联并打开选择器', async () => {
    mocks.getDocumentRequirements.mockResolvedValue([{ id: 'r1' }, { id: 'r2' }] as RequirementSummary[])
    const s = useMindmapRequirementLink(() => 'doc-1')
    await s.openRequirementSelector()
    expect(s.associatedReqIds.value).toEqual(['r1', 'r2'])
    expect(s.reqSelectorVisible.value).toBe(true)
    expect(mocks.getDocumentRequirements).toHaveBeenCalledWith('doc-1')
  })

  it('openRequirementSelector 失败时显示错误且不打开选择器', async () => {
    mocks.getDocumentRequirements.mockRejectedValue(new Error('网络错误'))
    const s = useMindmapRequirementLink(() => 'doc-1')
    await s.openRequirementSelector()
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
    expect(s.reqSelectorVisible.value).toBe(false)
  })

  it('openRequirementSelector 非 Error 异常显示通用消息', async () => {
    mocks.getDocumentRequirements.mockRejectedValue('string err')
    const s = useMindmapRequirementLink(() => 'doc-1')
    await s.openRequirementSelector()
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载关联需求失败')
  })

  it('handleRequirementConfirm 保存并更新关联 ID', async () => {
    mocks.setDocumentRequirements.mockResolvedValue(undefined)
    const s = useMindmapRequirementLink(() => 'doc-1')
    await s.handleRequirementConfirm([{ id: 'r1' }, { id: 'r3' }] as RequirementSummary[])
    expect(mocks.setDocumentRequirements).toHaveBeenCalledWith('doc-1', ['r1', 'r3'])
    expect(s.associatedReqIds.value).toEqual(['r1', 'r3'])
    expect(mocks.ElMessage.success).toHaveBeenCalledWith('已更新文档关联需求')
  })

  it('handleRequirementConfirm 失败时显示错误', async () => {
    mocks.setDocumentRequirements.mockRejectedValue(new Error('保存失败'))
    const s = useMindmapRequirementLink(() => 'doc-1')
    await s.handleRequirementConfirm([{ id: 'r1' }] as RequirementSummary[])
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
  })

  it('handleRequirementConfirm 非 Error 异常显示通用消息', async () => {
    mocks.setDocumentRequirements.mockRejectedValue(42)
    const s = useMindmapRequirementLink(() => 'doc-1')
    await s.handleRequirementConfirm([{ id: 'r1' }] as RequirementSummary[])
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存关联失败')
  })
})
