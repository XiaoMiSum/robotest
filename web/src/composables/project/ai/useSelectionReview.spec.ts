// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchAiArtifact: vi.fn(),
  getCaseDetail: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  fetchAiArtifact: mocks.fetchAiArtifact,
}))

vi.mock('@/services/project', () => ({
  getCaseDetail: mocks.getCaseDetail,
}))

vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))

import { useSelectionReview } from './useSelectionReview'

function artifactWith(items: unknown): Record<string, unknown> {
  return { key: 'sel-1', content: { items, round: null } }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getCaseDetail.mockImplementation(async (caseId: string) => ({
    id: caseId,
    title: `用例-${caseId}`,
    documentId: `doc-${caseId}`,
  }))
})

describe('useSelectionReview', () => {
  describe('产物读取', () => {
    it('解析推荐卡片并跳过缺 caseId 的脏数据', async () => {
      mocks.fetchAiArtifact.mockResolvedValue(
        artifactWith([
          { caseId: 'c1', title: '登录成功', reason: '核心链路', round: 1 },
          { caseId: '', title: '脏数据', reason: '' },
          { title: '缺 caseId' },
          { caseId: 'c2', title: '越权', reason: '风险', round: null },
        ]),
      )
      const sel = useSelectionReview('t1')

      await sel.load()

      expect(sel.items.value).toHaveLength(2)
      expect(sel.items.value[0]).toMatchObject({
        caseId: 'c1',
        title: '登录成功',
        reason: '核心链路',
        round: 1,
        documentId: null,
        manual: false,
      })
      expect(sel.items.value[1].round).toBeNull()
      expect(sel.loadFailed.value).toBe(false)
    })

    it('读取失败置 loadFailed 并统一提示', async () => {
      mocks.fetchAiArtifact.mockRejectedValue(new Error('产物不存在'))
      const sel = useSelectionReview('t1')

      await sel.load()

      expect(sel.loadFailed.value).toBe(true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('产物不存在')
      expect(sel.items.value).toHaveLength(0)
    })
  })

  describe('卡片调整', () => {
    it('移除卡片按 caseId 过滤', async () => {
      mocks.fetchAiArtifact.mockResolvedValue(
        artifactWith([
          { caseId: 'c1', title: 'A', reason: 'r' },
          { caseId: 'c2', title: 'B', reason: 'r' },
        ]),
      )
      const sel = useSelectionReview('t1')
      await sel.load()

      sel.removeItem('c1')

      expect(sel.items.value.map((item) => item.caseId)).toEqual(['c2'])
    })

    it('手动添加去重合并并回填标题与文档', async () => {
      mocks.fetchAiArtifact.mockResolvedValue(
        artifactWith([{ caseId: 'c1', title: 'A', reason: 'r' }]),
      )
      const sel = useSelectionReview('t1')
      await sel.load()

      await sel.addCases([
        { documentId: 'doc-x', caseIds: ['c2', 'c1'] },
        { documentId: 'doc-y', caseIds: ['c3'] },
      ])

      expect(mocks.getCaseDetail).toHaveBeenCalledTimes(2)
      const added = sel.items.value.filter((item) => item.manual)
      expect(added.map((item) => item.caseId)).toEqual(['c2', 'c3'])
      expect(added[0]).toMatchObject({
        title: '用例-c2',
        documentId: 'doc-c2',
        reason: '',
      })
    })

    it('明细解析失败的手动项文档置 null', async () => {
      mocks.getCaseDetail.mockRejectedValue(new Error('用例已删除'))
      const sel = useSelectionReview('t1')

      await sel.addCases([{ documentId: 'doc-x', caseIds: ['c9'] }])

      expect(sel.items.value[0].documentId).toBeNull()
      expect(sel.items.value[0].title).toBe('c9')
    })
  })

  describe('documentId 补齐与载荷组装', () => {
    it('推荐项解析后按文档分组，失败项计入 missing', async () => {
      mocks.fetchAiArtifact.mockResolvedValue(
        artifactWith([
          { caseId: 'c1', title: 'A', reason: 'r' },
          { caseId: 'c2', title: 'B', reason: 'r' },
          { caseId: 'c3', title: 'C', reason: 'r' },
        ]),
      )
      mocks.getCaseDetail.mockImplementation(async (caseId: string) => {
        if (caseId === 'c3') throw new Error('已删除')
        return { id: caseId, title: `用例-${caseId}`, documentId: caseId === 'c1' ? 'doc-1' : 'doc-2' }
      })
      const sel = useSelectionReview('t1')
      await sel.load()

      await sel.resolveDocumentIds()
      const { nodes, missing } = sel.selectedNodes()

      expect(missing).toBe(1)
      expect(nodes).toEqual([
        { documentId: 'doc-1', caseIds: ['c1'] },
        { documentId: 'doc-2', caseIds: ['c2'] },
      ])
    })

    it('全部解析失败时 nodes 为空且 missing 等于卡片数', async () => {
      mocks.fetchAiArtifact.mockResolvedValue(
        artifactWith([{ caseId: 'c1', title: 'A', reason: 'r' }]),
      )
      mocks.getCaseDetail.mockRejectedValue(new Error('x'))
      const sel = useSelectionReview('t1')
      await sel.load()

      await sel.resolveDocumentIds()
      const { nodes, missing } = sel.selectedNodes()

      expect(nodes).toHaveLength(0)
      expect(missing).toBe(1)
    })
  })
})
