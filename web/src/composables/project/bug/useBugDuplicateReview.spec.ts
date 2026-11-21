import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AiTaskProposalSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  confirmAiProposals: vi.fn(),
  fetchAiProposal: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  confirmAiProposals: mocks.confirmAiProposals,
  fetchAiProposal: mocks.fetchAiProposal,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useBugDuplicateReview } from './useBugDuplicateReview'

function makeArtifact(overrides: Partial<AiTaskProposalSummary> = {}): AiTaskProposalSummary {
  return {
    key: 'dg-1',
    kind: 'duplicate_group',
    title: '疑似重复组',
    parentKey: null,
    confirmStatus: 'pending',
    note: null,
    ...overrides,
  }
}

function duplicateContent(): Record<string, unknown> {
  return {
    canonicalBugId: '22222222-2222-2222-2222-222222222222',
    items: [
      { bugId: '33333333-3333-3333-3333-333333333333', similarity: 0.92, reason: '标题相近' },
    ],
  }
}

function setup(artifacts: AiTaskProposalSummary[], canConfirm = true, onConfirmed = vi.fn()) {
  return useBugDuplicateReview('t1', () => artifacts, () => canConfirm, onConfirmed)
}

describe('composables/project/bug/useBugDuplicateReview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('syncRows 按产物清单重建行并保留已加载分组内容', async () => {
    mocks.fetchAiProposal.mockResolvedValue({ content: duplicateContent() })
    const review = setup([makeArtifact()])
    review.init()
    await vi.waitFor(() => expect(review.rows.value[0]?.loaded).toBe(true))

    review.syncRows()
    expect(review.rows.value).toHaveLength(1)
    expect(review.rows.value[0]?.group?.canonicalBugId).toBe(
      '22222222-2222-2222-2222-222222222222',
    )
    // 提案详情按 taskId + 提案键拉取，内容读自响应 content 根节点
    expect(mocks.fetchAiProposal).toHaveBeenCalledWith('t1', 'dg-1')
    expect(review.processed.value).toEqual({ processed: 0, total: 1 })
    expect(review.canConfirmState.value).toBe(true)
  })

  it('duplicate_group 行解析分组内容，非重复组跳过加载', async () => {
    mocks.fetchAiProposal.mockResolvedValue({ content: duplicateContent() })
    const review = setup([
      makeArtifact(),
      makeArtifact({ key: 'sum-1', kind: 'summary' }),
    ])
    review.init()
    await vi.waitFor(() => expect(review.rows.value[0]?.loaded).toBe(true))

    expect(review.duplicateRows.value.map((row) => row.key)).toEqual(['dg-1'])
    expect(review.rows.value[0]?.group?.items[0]?.similarity).toBe(0.92)
    expect(review.rows.value[1]?.loaded).toBe(false)
    expect(mocks.fetchAiProposal).toHaveBeenCalledTimes(1)
  })

  it('已确认组不再预取内容', async () => {
    mocks.fetchAiProposal.mockResolvedValue({ content: duplicateContent() })
    const review = setup([makeArtifact({ confirmStatus: 'adopted' })])
    review.init()
    await Promise.resolve()

    expect(mocks.fetchAiProposal).not.toHaveBeenCalled()
  })

  it('加载失败保留未加载态并提示错误', async () => {
    mocks.fetchAiProposal.mockRejectedValue(new Error('网络异常'))
    const review = setup([makeArtifact()])
    review.init()
    await vi.waitFor(() => expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络异常'))

    expect(review.rows.value[0]?.loaded).toBe(false)
    expect(review.rows.value[0]?.group).toBeNull()
  })

  it('重复组确认：adopted 无 note 不带 note 字段，rejected 带 note 留痕', async () => {
    const review = setup([makeArtifact()])
    review.syncRows()
    mocks.confirmAiProposals.mockResolvedValue({ results: [] })

    await review.handleGroupConfirm(review.rows.value[0]!, 'adopted', '')
    expect(mocks.confirmAiProposals).toHaveBeenCalledWith('t1', {
      items: [{ key: 'dg-1', action: 'adopted' }],
    })

    await review.handleGroupConfirm(review.rows.value[0]!, 'rejected', '误报')
    expect(mocks.confirmAiProposals).toHaveBeenLastCalledWith('t1', {
      items: [{ key: 'dg-1', action: 'rejected', note: '误报' }],
    })
    expect(review.confirming.value).toBe(false)
  })

  it('请求级失败落错误提示，确认态复位', async () => {
    const review = setup([makeArtifact()])
    review.syncRows()
    mocks.confirmAiProposals.mockRejectedValue(new Error('boom'))

    await review.handleGroupConfirm(review.rows.value[0]!, 'adopted', '')
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('boom')
    expect(review.confirming.value).toBe(false)
  })

  it('retryResult 以回执 action 重新提交，回执保留标题与错误映射', async () => {
    const review = setup([makeArtifact()])
    review.syncRows()
    mocks.confirmAiProposals.mockResolvedValue({
      results: [{
        key: 'dg-1',
        action: 'rejected',
        success: false,
        createdId: null,
        errorCode: 1000018208,
        errorMsg: '无缺陷编辑权限',
      }],
    })

    await review.retryResult({
      key: 'dg-1',
      title: '疑似重复组',
      action: 'rejected',
      success: true,
      errorCode: null,
      errorMsg: '',
      createdId: null,
    })
    expect(review.receipt.value[0]?.success).toBe(false)
    expect(review.receipt.value[0]?.errorMsg).toBe('无缺陷编辑权限')
    expect(review.receipt.value[0]?.title).toBe('疑似重复组')

    review.clearReceipt()
    expect(review.receipt.value).toHaveLength(0)
  })
})
