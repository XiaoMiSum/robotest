// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { AiArtifactSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  confirmAiArtifacts: vi.fn(),
  fetchAiArtifact: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  confirmAiArtifacts: mocks.confirmAiArtifacts,
  fetchAiArtifact: mocks.fetchAiArtifact,
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useAiArtifactReview } from './useAiArtifactReview'

function summary(overrides: Partial<AiArtifactSummary> = {}): AiArtifactSummary {
  return {
    key: 'a1',
    kind: 'requirement_suggestion',
    title: '登录验证码',
    parentKey: null,
    confirmStatus: 'pending',
    ...overrides,
  }
}

function setupArtifacts(
  artifacts: AiArtifactSummary[],
  canConfirm = true,
  onConfirmed = vi.fn(),
): ReturnType<typeof useAiArtifactReview> {
  setActivePinia(createPinia())
  return useAiArtifactReview('t1', () => artifacts, () => canConfirm, onConfirmed)
}

describe('useAiArtifactReview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mocks.fetchProjectModuleTree.mockResolvedValue([
      {
        id: 'm1',
        parentId: null,
        type: 'directory',
        name: '登录模块',
        sortOrder: 1,
        children: [],
      },
    ])
    mocks.confirmAiArtifacts.mockResolvedValue({
      results: [
        { key: 'a1', action: 'adopted', success: true, createdId: 'r9', errorCode: null, errorMsg: null },
      ],
    })
    mocks.ElMessageBox.confirm.mockResolvedValue('confirm')
  })

  describe('产物行同步', () => {
    it('init 同步产物行并映射中文标签与确认状态', () => {
      const artifacts = [summary(), summary({ key: 'a2', title: '订单超时' })]
      const s = setupArtifacts(artifacts)
      s.init()
      expect(s.rows.value).toHaveLength(2)
      expect(s.rows.value[0].kindLabel).toBe('需求建议')
      expect(s.rows.value[0].confirmMeta.label).toBe('待确认')
      expect(s.processed.value).toEqual({ processed: 0, total: 2 })
      expect(s.selectableRows.value).toHaveLength(2)
      expect(s.allSelected.value).toBe(false)
      expect(s.canConfirm.value).toBe(true)
    })

    it('已确认行不可再选，服务端状态覆盖本地', async () => {
      let artifacts = [summary()]
      const review = useAiArtifactReview('t1', () => artifacts, () => true, vi.fn())
      review.init()
      review.toggle('a1', true)
      expect(review.selectedKeys.value).toEqual(['a1'])

      artifacts = [summary({ confirmStatus: 'rejected' })]
      review.syncRows()
      expect(review.rows.value[0].confirmMeta.label).toBe('已驳回')
      expect(review.selectableRows.value).toHaveLength(0)
      expect(review.processed.value.processed).toBe(1)
      expect(review.selectedKeys.value).toEqual([])
      await expect(Promise.resolve()).resolves.toBeUndefined()
    })

    it('产物消失时清空选中与激活态', () => {
      let artifacts = [summary()]
      const review = useAiArtifactReview('t1', () => artifacts, () => true, vi.fn())
      review.init()
      review.select('a1')
      expect(review.activeKey.value).toBe('a1')
      review.toggle('a1', true)

      artifacts = [summary({ key: 'a9' })]
      review.syncRows()
      expect(review.activeKey.value).toBe('')
      expect(review.selectedKeys.value).toEqual([])
    })

    it('全选与取消全选', () => {
      const s = setupArtifacts([summary(), summary({ key: 'a2' })])
      s.init()
      s.toggleAll(true)
      expect(s.selectedKeys.value).toEqual(['a1', 'a2'])
      expect(s.allSelected.value).toBe(true)
      s.toggleAll(false)
      expect(s.selectedKeys.value).toEqual([])
      s.toggle('a1', true)
      s.toggle('a1', false)
      expect(s.selectedKeys.value).toEqual([])
    })
  })

  describe('内容懒加载', () => {
    it('选中产物按需拉取内容并回退 summary 标题', async () => {
      mocks.fetchAiArtifact.mockResolvedValue({
        content: {
          title: '登录验证码',
          description: '校验短信验证码',
          moduleId: 'm1',
          priority: 'high',
        },
        sourceRef: 'P3 §2.1',
      })
      const s = setupArtifacts([summary()])
      s.init()
      s.select('a1')
      await vi.waitFor(() => expect(s.rows.value[0].contentLoaded).toBe(true))
      expect(s.rows.value[0].content).toEqual({
        title: '登录验证码',
        description: '校验短信验证码',
        moduleId: 'm1',
        priority: 'high',
        sourceRef: 'P3 §2.1',
      })
      expect(s.loadingContent.value).toBe(false)

      // 已加载不再重复请求
      s.select('a1')
      expect(mocks.fetchAiArtifact).toHaveBeenCalledTimes(1)
    })

    it('内容缺失时回退标题并保持只读占位', async () => {
      mocks.fetchAiArtifact.mockResolvedValue({})
      const s = setupArtifacts([summary({ title: '订单超时' })])
      s.init()
      s.select('a1')
      await vi.waitFor(() => expect(s.rows.value[0].contentLoaded).toBe(true))
      expect(s.rows.value[0].content.title).toBe('订单超时')
      expect(s.rows.value[0].content.description).toBe('')
    })

    it('内容加载失败提示错误但不阻塞', async () => {
      mocks.fetchAiArtifact.mockRejectedValue(new Error('产物不存在'))
      const s = setupArtifacts([summary()])
      s.init()
      s.select('a1')
      await vi.waitFor(() =>
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('产物不存在'),
      )
      expect(s.rows.value[0].contentLoaded).toBe(false)
    })

    it('模块树失败降级为空树', async () => {
      mocks.fetchProjectModuleTree.mockRejectedValue(new Error('down'))
      const s = setupArtifacts([summary()])
      s.init()
      await vi.waitFor(() => expect(s.moduleTree.value).toEqual([]))
    })
  })

  describe('确认动作', () => {
    it('单条采纳不带 target（版本未触碰不提交）', async () => {
      const s = setupArtifacts([summary()])
      s.init()
      await s.handleAdopt(s.rows.value[0])
      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [{ key: 'a1', action: 'adopted' }],
        target: undefined,
      })
      expect(s.receipt.value[0]).toMatchObject({ key: 'a1', success: true, action: 'adopted' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('确认完成')
      expect(s.confirming.value).toBe(false)
    })

    it('版本触碰后作为 target 提交（含显式清空）', async () => {
      const s = setupArtifacts([summary()])
      s.init()
      await s.handleAdopt(s.rows.value[0], '')
      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [{ key: 'a1', action: 'adopted' }],
        target: { systemVersion: '' },
      })
    })

    it('修改后采纳只发送非空字段', async () => {
      const s = setupArtifacts([summary()])
      s.init()
      await s.handleAdoptEdited(
        s.rows.value[0],
        { title: '  登录验证码  ', description: ' ', moduleId: 'm1', priority: '' },
        'V2.3',
      )
      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [
          {
            key: 'a1',
            action: 'adopted_edited',
            content: { title: '登录验证码', moduleId: 'm1' },
          },
        ],
        target: { systemVersion: 'V2.3' },
      })
    })

    it('驳回附反馈并 trim，空反馈不携带 note', async () => {
      const s = setupArtifacts([summary()])
      s.init()
      await s.handleReject(s.rows.value[0], '  与实际流程不符  ')
      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [{ key: 'a1', action: 'rejected', note: '与实际流程不符' }],
        target: undefined,
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已全部驳回，未创建任何数据')

      await s.handleReject(s.rows.value[0], '   ')
      expect(mocks.confirmAiArtifacts).toHaveBeenLastCalledWith('t1', {
        items: [{ key: 'a1', action: 'rejected' }],
        target: undefined,
      })
    })

    it('批量采纳按选择集提交', async () => {
      const s = setupArtifacts([summary(), summary({ key: 'a2' })])
      s.init()
      s.toggleAll(true)
      await s.handleBatchAdopt()
      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [
          { key: 'a1', action: 'adopted' },
          { key: 'a2', action: 'adopted' },
        ],
        target: undefined,
      })
    })

    it('批量驳回需二次确认，放弃时不提交', async () => {
      const s = setupArtifacts([summary()])
      s.init()
      s.toggle('a1', true)
      mocks.ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
      await s.handleBatchReject()
      expect(mocks.confirmAiArtifacts).not.toHaveBeenCalled()

      await s.handleBatchReject()
      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [{ key: 'a1', action: 'rejected' }],
        target: undefined,
      })
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        '将驳回所选 1 条产物，不创建任何数据。',
        expect.anything(),
        expect.anything(),
      )
    })

    it('未选择产物时提示且不发请求', async () => {
      const s = setupArtifacts([summary()])
      s.init()
      await s.handleBatchAdopt()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先选择要处理的产物')
      expect(mocks.confirmAiArtifacts).not.toHaveBeenCalled()
    })

    it('超过单次上限时拒绝提交', async () => {
      const many = Array.from({ length: 201 }, (_, index) => summary({ key: `k${index}` }))
      const s = setupArtifacts(many)
      s.init()
      s.toggleAll(true)
      await s.handleBatchAdopt()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('单次最多确认 200 项')
      expect(mocks.confirmAiArtifacts).not.toHaveBeenCalled()
    })

    it('部分失败时不发成功提示，回执标记失败项', async () => {
      mocks.confirmAiArtifacts.mockResolvedValue({
        results: [
          { key: 'a1', action: 'adopted', success: false, createdId: null, errorCode: 1000018113, errorMsg: '已确认' },
        ],
      })
      const onConfirmed = vi.fn()
      const s = setupArtifacts([summary()], true, onConfirmed)
      s.init()
      await s.handleAdopt(s.rows.value[0])
      expect(mocks.ElMessage.success).not.toHaveBeenCalled()
      expect(s.receipt.value[0]).toMatchObject({ success: false, errorCode: 1000018113 })
      expect(onConfirmed).toHaveBeenCalled()
    })

    it('回执标题回退产物 key，动作取请求值', async () => {
      mocks.confirmAiArtifacts.mockResolvedValue({
        results: [
          { key: 'unknown', action: 'rejected', success: true, createdId: null, errorCode: null, errorMsg: null },
        ],
      })
      const s = setupArtifacts([summary()])
      s.init()
      await s.handleAdopt(s.rows.value[0])
      expect(s.receipt.value[0].title).toBe('unknown')
      s.clearReceipt()
      expect(s.receipt.value).toEqual([])
    })

    it('确认请求整体失败提示错误', async () => {
      mocks.confirmAiArtifacts.mockRejectedValue(new Error('服务不可用'))
      const s = setupArtifacts([summary()])
      s.init()
      await s.handleAdopt(s.rows.value[0])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('服务不可用')
      expect(s.receipt.value).toEqual([])
      expect(s.confirming.value).toBe(false)
    })

    it('回执失败项按原动作单项重试', async () => {
      const s = setupArtifacts([summary()])
      s.init()
      await s.handleAdopt(s.rows.value[0])
      mocks.confirmAiArtifacts.mockClear()
      await s.retryResult(s.receipt.value[0])
      expect(mocks.confirmAiArtifacts).toHaveBeenCalledWith('t1', {
        items: [{ key: 'a1', action: 'adopted' }],
        target: undefined,
      })
    })
  })
})
