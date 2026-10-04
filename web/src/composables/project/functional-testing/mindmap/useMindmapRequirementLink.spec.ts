// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RequirementSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchDocumentRequirements: vi.fn(),
  setDocumentRequirements: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchDocumentRequirements: mocks.fetchDocumentRequirements,
  setDocumentRequirements: mocks.setDocumentRequirements,
}))

vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))

import { useMindmapRequirementLink } from './useMindmapRequirementLink'

const DOC_ID = 'doc-1'

function summary(id: string, code: string): RequirementSummary {
  return { id, code, title: `标题-${code}` }
}

// 源码无生命周期钩子，无需组件上下文即可直接调用
function createLink(docId: () => string = () => DOC_ID) {
  return useMindmapRequirementLink(docId)
}

describe('useMindmapRequirementLink', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('open 拉取既有关联后才打开选取器', async () => {
    mocks.fetchDocumentRequirements.mockResolvedValue([summary('r1', 'REQ-001')])
    const link = createLink()

    expect(link.visible.value).toBe(false)
    await link.open()

    expect(mocks.fetchDocumentRequirements).toHaveBeenCalledWith(DOC_ID)
    expect(link.linked.value).toHaveLength(1)
    expect(link.visible.value).toBe(true)
  })

  it('open 拉取失败时提示并保持弹窗关闭', async () => {
    mocks.fetchDocumentRequirements.mockRejectedValue(new Error('文档不存在'))
    const link = createLink()

    await link.open()

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('文档不存在')
    expect(link.visible.value).toBe(false)
    expect(link.linked.value).toEqual([])
  })

  it('open 在无文档 ID 时跳过请求', async () => {
    const link = createLink(() => '')
    await link.open()
    expect(mocks.fetchDocumentRequirements).not.toHaveBeenCalled()
  })

  it('close 关闭选取器', async () => {
    mocks.fetchDocumentRequirements.mockResolvedValue([])
    const link = createLink()
    await link.open()
    link.close()
    expect(link.visible.value).toBe(false)
  })

  it('picks 将摘要映射为选取器回填结构', async () => {
    mocks.fetchDocumentRequirements.mockResolvedValue([summary('r1', 'REQ-001')])
    const link = createLink()
    await link.open()
    expect(link.picks()).toEqual([{ id: 'r1', code: 'REQ-001', title: '标题-REQ-001' }])
  })

  it('confirm 全量覆盖保存并回显结果', async () => {
    mocks.setDocumentRequirements.mockResolvedValue([summary('r2', 'REQ-002')])
    const link = createLink()
    link.visible.value = true

    await link.confirm(['r2'])

    expect(mocks.setDocumentRequirements).toHaveBeenCalledWith(DOC_ID, ['r2'])
    expect(link.linked.value).toEqual([summary('r2', 'REQ-002')])
    expect(link.visible.value).toBe(false)
    expect(mocks.ElMessage.success).toHaveBeenCalled()
    expect(link.saving.value).toBe(false)
  })

  it('confirm 失败时提示且弹窗保持打开', async () => {
    mocks.setDocumentRequirements.mockRejectedValue(new Error('需求不存在'))
    const link = createLink()
    link.visible.value = true

    await link.confirm(['bad'])

    expect(mocks.ElMessage.error).toHaveBeenCalledWith('需求不存在')
    expect(link.visible.value).toBe(true)
    expect(link.saving.value).toBe(false)
  })

  it('confirm 在无文档 ID 时跳过请求', async () => {
    const link = createLink(() => '')
    await link.confirm(['r1'])
    expect(mocks.setDocumentRequirements).not.toHaveBeenCalled()
    expect(link.saving.value).toBe(false)
  })

  it('保存期间 saving 置位以驱动按钮 loading', async () => {
    let resolveSave: (value: RequirementSummary[]) => void = () => undefined
    mocks.setDocumentRequirements.mockImplementation(
      () => new Promise<RequirementSummary[]>((resolve) => (resolveSave = resolve)),
    )
    const link = createLink()

    const pending = link.confirm(['r1'])
    expect(link.saving.value).toBe(true)
    resolveSave([])
    await pending
    expect(link.saving.value).toBe(false)
  })
})
