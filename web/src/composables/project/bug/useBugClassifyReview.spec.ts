import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AiArtifactSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  confirmAiArtifacts: vi.fn(),
  fetchAiArtifact: vi.fn(),
  getBugDetail: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  confirmAiArtifacts: mocks.confirmAiArtifacts,
  fetchAiArtifact: mocks.fetchAiArtifact,
}))

vi.mock('@/services/project', () => ({
  getBugDetail: mocks.getBugDetail,
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useBugClassifyReview } from './useBugClassifyReview'

function makeArtifact(overrides: Partial<AiArtifactSummary> = {}): AiArtifactSummary {
  return {
    key: 'cs-1',
    kind: 'classify_suggestion',
    title: 'BUG-0001',
    parentKey: null,
    confirmStatus: 'pending',
    ...overrides,
  }
}

function classifyContent(): Record<string, unknown> {
  return {
    bugId: '11111111-1111-1111-1111-111111111111',
    suggestions: {
      bugType: { value: 'code_error', reason: '功能失效' },
      severity: { value: 'fatal', reason: '阻断主流程' },
      priority: { value: 'high', reason: '紧急' },
      moduleId: { value: 'm-1', reason: '登录模块' },
      keywords: { value: ['登录', '无响应'], reason: '检索词' },
    },
    assigneeCandidates: [{ userId: 'u1', name: '李四', reason: '', memberValid: true }],
    sourceRefs: [{ type: 'bug', id: 'b9', title: '来源缺陷' }],
  }
}

function setup(artifacts: AiArtifactSummary[], canConfirm = true, onConfirmed = vi.fn()) {
  return useBugClassifyReview('t1', () => artifacts, () => canConfirm, onConfirmed)
}

describe('composables/project/bug/useBugClassifyReview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchProjectModuleTree.mockResolvedValue([
      { id: 'm-1', name: '登录模块', children: [] },
    ])
  })

  it('syncRows 按产物清单重建行并保留已加载内容', async () => {
    mocks.fetchAiArtifact.mockResolvedValue({ content: classifyContent() })
    mocks.getBugDetail.mockResolvedValue({
      bugType: 'other',
      severity: 'minor',
      priority: 'low',
      moduleId: null,
      keywords: '',
      assignee: null,
    })
    const review = setup([makeArtifact()])
    review.init()
    await vi.waitFor(() => expect(review.rows.value[0]?.loaded).toBe(true))

    review.syncRows()
    expect(review.rows.value).toHaveLength(1)
    expect(review.rows.value[0]?.content?.bugId).toBe('11111111-1111-1111-1111-111111111111')
    expect(review.rows.value[0]?.current?.severity).toBe('minor')
  })

  it('内容解析：keywords 为数组，memberValid=false 的候选被剔除', async () => {
    mocks.fetchAiArtifact.mockResolvedValue({
      content: {
        ...classifyContent(),
        assigneeCandidates: [
          { userId: 'u1', name: '李四', reason: '', memberValid: true },
          { userId: 'u2', name: '外部', reason: '', memberValid: false },
        ],
      },
    })
    mocks.getBugDetail.mockResolvedValue(null)
    const review = setup([makeArtifact()])
    review.init()
    await vi.waitFor(() => expect(review.rows.value[0]?.loaded).toBe(true))

    expect(review.rows.value[0]?.content?.suggestions.keywords?.value).toEqual(['登录', '无响应'])
    expect(review.rows.value[0]?.content?.assigneeCandidates).toHaveLength(1)
    // 缺陷详情加载失败降级：current 保持 null，不阻塞审核
    expect(review.rows.value[0]?.current).toBeNull()
  })

  it('duplicate_group 行解析分组内容且不取缺陷详情', async () => {
    mocks.fetchAiArtifact.mockResolvedValue({
      content: {
        canonicalBugId: '22222222-2222-2222-2222-222222222222',
        items: [{ bugId: '33333333-3333-3333-3333-333333333333', similarity: 0.92, reason: '标题相近' }],
      },
    })
    const review = setup([makeArtifact({ key: 'dg-1', kind: 'duplicate_group' })])
    review.init()
    await vi.waitFor(() => expect(review.rows.value[0]?.loaded).toBe(true))

    expect(review.duplicateRows.value).toHaveLength(1)
    expect(review.classifyRows.value).toHaveLength(0)
    expect(review.rows.value[0]?.group?.items[0]?.similarity).toBe(0.92)
    expect(mocks.getBugDetail).not.toHaveBeenCalled()
  })

  it('kind 分组：classify / duplicate / 只读互斥', () => {
    const review = setup([
      makeArtifact(),
      makeArtifact({ key: 'dg-1', kind: 'duplicate_group' }),
      makeArtifact({ key: 'sum-1', kind: 'summary' }),
    ])
    review.syncRows()
    expect(review.classifyRows.value).toHaveLength(1)
    expect(review.duplicateRows.value).toHaveLength(1)
    expect(review.readonlyRows.value.map((row) => row.key)).toEqual(['sum-1'])
  })

  it('选择：全选仅覆盖 pending 行，确认后残留选择被清理', () => {
    const review = setup([
      makeArtifact(),
      makeArtifact({ key: 'cs-2' }),
      makeArtifact({ key: 'cs-3', confirmStatus: 'adopted' }),
    ])
    review.syncRows()
    expect(review.selectableRows.value).toHaveLength(2)

    review.toggleAll(true)
    expect(review.allSelected.value).toBe(true)
    expect(review.selectedKeys.value).toHaveLength(2)

    review.toggle('cs-2', false)
    expect(review.allSelected.value).toBe(false)

    // 已处理项不再可选：重建后选择集剔除非 pending
    const next = [
      makeArtifact({ confirmStatus: 'adopted' }),
      makeArtifact({ key: 'cs-2', confirmStatus: 'adopted' }),
      makeArtifact({ key: 'cs-3', confirmStatus: 'adopted' }),
    ]
    const review2 = setup(next)
    review2.syncRows()
    review2.toggle('cs-1', true)
    review2.syncRows()
    expect(review2.selectedKeys.value).toEqual([])
  })

  it('批量采纳按 200 分批提交并生成回执', async () => {
    const artifacts = Array.from({ length: 250 }, (_, index) =>
      makeArtifact({ key: `cs-${index}`, title: `BUG-${index}` }))
    const onConfirmed = vi.fn()
    const review = useBugClassifyReview('t1', () => artifacts, () => true, onConfirmed)
    review.syncRows()
    for (let index = 0; index < 250; index += 1) review.toggle(`cs-${index}`, true)

    mocks.confirmAiArtifacts.mockResolvedValueOnce({ results: [] })
    mocks.confirmAiArtifacts.mockResolvedValueOnce({ results: [] })
    await review.handleBatchAdopt()

    expect(mocks.confirmAiArtifacts).toHaveBeenCalledTimes(2)
    expect(mocks.confirmAiArtifacts.mock.calls[0]?.[1]).toMatchObject({
      items: expect.arrayContaining([{ key: 'cs-0', action: 'adopted' }]),
    })
    expect(
      (mocks.confirmAiArtifacts.mock.calls[0]?.[1] as { items: unknown[] }).items,
    ).toHaveLength(200)
    expect(
      (mocks.confirmAiArtifacts.mock.calls[1]?.[1] as { items: unknown[] }).items,
    ).toHaveLength(50)
    expect(onConfirmed).toHaveBeenCalledOnce()
  })

  it('未选择时批量动作给出提示且不发请求', async () => {
    const review = setup([makeArtifact()])
    review.syncRows()
    await review.handleBatchAdopt()
    expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先选择要处理的产物')
    expect(mocks.confirmAiArtifacts).not.toHaveBeenCalled()
  })

  it('修改后采纳交 bugId + 编辑后 suggestions，keywords 为数组且截断 5 个', async () => {
    mocks.fetchAiArtifact.mockResolvedValue({ content: classifyContent() })
    mocks.getBugDetail.mockResolvedValue(null)
    const review = setup([makeArtifact()])
    review.init()
    await vi.waitFor(() => expect(review.rows.value[0]?.loaded).toBe(true))

    mocks.confirmAiArtifacts.mockResolvedValue({
      results: [{ key: 'cs-1', action: 'adopted_edited', success: true, createdId: null, errorCode: null, errorMsg: null }],
    })
    await review.handleAdoptEdited(review.rows.value[0]!, {
      bugType: 'performance',
      severity: 'serious',
      priority: 'medium',
      moduleId: 'm-2',
      keywords: 'a b c d e f g',
      assigneeId: '',
    })

    const payload = mocks.confirmAiArtifacts.mock.calls[0]?.[1] as {
      items: Array<{ action: string; content: { bugId: string; suggestions: Record<string, { value: unknown }> } }>
    }
    expect(payload.items[0]?.action).toBe('adopted_edited')
    expect(payload.items[0]?.content.bugId).toBe('11111111-1111-1111-1111-111111111111')
    expect(payload.items[0]?.content.suggestions.keywords?.value).toEqual(['a', 'b', 'c', 'd', 'e'])
    expect(payload.items[0]?.content.suggestions.severity?.value).toBe('serious')
    expect(mocks.ElMessage.success).toHaveBeenCalled()
  })

  it('驳回附反馈携带 note，回执失败项映射错误信息', async () => {
    const review = setup([makeArtifact()])
    review.syncRows()
    mocks.confirmAiArtifacts.mockResolvedValue({
      results: [{
        key: 'cs-1',
        action: 'rejected',
        success: false,
        createdId: null,
        errorCode: 1000018208,
        errorMsg: '无缺陷编辑权限',
      }],
    })
    await review.handleReject(review.rows.value[0]!, '  建议不合理  ')

    expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
      items: [{ key: 'cs-1', action: 'rejected', note: '建议不合理' }],
    })
    // 部分失败不弹成功提示，回执供单项重试
    expect(mocks.ElMessage.success).not.toHaveBeenCalled()
    expect(review.receipt.value[0]?.success).toBe(false)
    expect(review.receipt.value[0]?.errorMsg).toBe('无缺陷编辑权限')
    expect(review.receipt.value[0]?.title).toBe('BUG-0001')
  })

  it('批量驳回取消时终止，确认时整批 rejected', async () => {
    const review = setup([makeArtifact(), makeArtifact({ key: 'cs-2' })])
    review.syncRows()
    review.toggleAll(true)

    mocks.ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
    await review.handleBatchReject()
    expect(mocks.confirmAiArtifacts).not.toHaveBeenCalled()

    mocks.ElMessageBox.confirm.mockResolvedValueOnce('confirm')
    mocks.confirmAiArtifacts.mockResolvedValue({ results: [] })
    await review.handleBatchReject()
    const payload = mocks.confirmAiArtifacts.mock.calls[0]?.[1] as { items: unknown[] }
    expect(payload.items).toHaveLength(2)
  })

  it('重复组确认：adopted 无 note 不带 note 字段，rejected 带 note 留痕', async () => {
    const review = setup([makeArtifact({ key: 'dg-1', kind: 'duplicate_group' })])
    review.syncRows()
    mocks.confirmAiArtifacts.mockResolvedValue({ results: [] })

    await review.handleGroupConfirm(review.rows.value[0]!, 'adopted', '')
    expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
      items: [{ key: 'dg-1', action: 'adopted' }],
    })

    await review.handleGroupConfirm(review.rows.value[0]!, 'rejected', '误报')
    expect(mocks.confirmAiArtifacts).toHaveBeenLastCalledWith('t1', {
      items: [{ key: 'dg-1', action: 'rejected', note: '误报' }],
    })
  })

  it('请求级失败落错误提示，确认态复位', async () => {
    const review = setup([makeArtifact()])
    review.syncRows()
    mocks.confirmAiArtifacts.mockRejectedValue(new Error('boom'))
    await review.handleAdopt(review.rows.value[0]!)
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('boom')
    expect(review.confirming.value).toBe(false)
  })

  it('moduleNameOf：树内返回名称，未命中返回 id，空值返回未指定', async () => {
    const review = setup([makeArtifact()])
    review.init()
    await vi.waitFor(() => expect(review.moduleTree.value).toHaveLength(1))
    expect(review.moduleNameOf('m-1')).toBe('登录模块')
    expect(review.moduleNameOf('m-x')).toBe('m-x')
    expect(review.moduleNameOf(null)).toBe('未指定模块')
  })

  it('retryResult 以回执 action 重新提交', async () => {
    const review = setup([makeArtifact()])
    review.syncRows()
    mocks.confirmAiArtifacts.mockResolvedValue({ results: [] })
    await review.retryResult({
      key: 'cs-1',
      title: 'BUG-0001',
      action: 'adopted',
      success: true,
      errorCode: null,
      errorMsg: '',
      createdId: null,
    })
    expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
      items: [{ key: 'cs-1', action: 'adopted' }],
    })
    review.clearReceipt()
    expect(review.receipt.value).toHaveLength(0)
  })
})
