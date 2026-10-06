// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import type { AiArtifactSummary } from '@/types'
import type { ArtifactRow } from '@/composables/project/ai/useAiArtifactReview'

const mocks = vi.hoisted(() => ({
  ElMessage: { info: vi.fn(), warning: vi.fn(), success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import {
  useGenerationArtifactTree,
  type GenerationReviewBase,
} from './useGenerationArtifactTree'

interface RowSeed extends Partial<ArtifactRow> {
  key: string
  kind?: string
  title?: string
  parentKey?: string | null
}

function makeRow(seed: RowSeed): ArtifactRow {
  const summary: AiArtifactSummary = {
    key: seed.key,
    kind: seed.kind ?? 'test_case_suggestion',
    title: seed.title ?? seed.key,
    parentKey: seed.parentKey ?? null,
    confirmStatus: seed.confirmStatus ?? 'pending',
  }
  return {
    ...summary,
    kindLabel: '用例',
    confirmMeta: { label: '待确认', tagType: 'warning' as const },
    content: {
      title: summary.title ?? '',
      description: '',
      moduleId: '',
      priority: '',
      sourceRef: '',
      isTestCase: (seed.kind ?? 'test_case_suggestion') === 'test_case_suggestion',
      parentRef: '',
      attributes: { priority: 'medium', precondition: '', steps: [], expected: [], tags: [] },
      sourceRefs: [],
      suspectedDuplicateOf: '',
      ...seed.content,
    },
    contentLoaded: true,
  }
}

function makeBase(seeds: RowSeed[]): GenerationReviewBase & {
  submitItems: ReturnType<typeof vi.fn>
} {
  const submitItems = vi.fn().mockResolvedValue(undefined)
  const base = {
    rows: ref(seeds.map(makeRow)),
    selectedKeys: ref<string[]>([]),
    confirming: ref(false),
    canConfirm: computed(() => true),
    activeKey: ref(''),
    select: vi.fn(),
    submitItems,
  }
  return base as GenerationReviewBase & { submitItems: ReturnType<typeof vi.fn> }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.ElMessageBox.confirm.mockResolvedValue('confirm')
})

describe('useGenerationArtifactTree', () => {
  describe('三层树构建', () => {
    it('按 模块 → 文档 → 用例 重建层级，文档内按 parentRef 嵌套', () => {
      const base = makeBase([
        { key: 'case-2', parentKey: 'd1' },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: 'm1' },
        { key: 'm1', kind: 'module_suggestion', parentKey: null },
        { key: 'case-1', parentKey: 'd1' },
        { key: 'case-3', parentKey: 'd1' },
      ])
      // 内容未预取时 parentRef 缺省：先按扁平挂文档
      const tree = useGenerationArtifactTree(base)

      expect(tree.tree.value).toHaveLength(1)
      expect(tree.tree.value[0].key).toBe('m1')
      expect(tree.tree.value[0].level).toBe(0)
      const doc = tree.tree.value[0].children[0]
      expect(doc.key).toBe('d1')
      expect(doc.level).toBe(1)
      // parentRef 缺省时保持产出顺序扁平挂在文档下
      expect(doc.children.map((node) => node.key)).toEqual(['case-2', 'case-1', 'case-3'])
      expect(doc.children.every((node) => node.level === 2)).toBe(true)
    })

    it('parentRef 载入后形成文档内嵌套层级', () => {
      const base = makeBase([
        { key: 'case-2', parentKey: 'd1', content: { parentRef: 'case-1' } as never },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: 'm1' },
        { key: 'm1', kind: 'module_suggestion', parentKey: null },
        { key: 'case-1', parentKey: 'd1' },
      ])
      const tree = useGenerationArtifactTree(base)

      const doc = tree.tree.value[0].children[0]
      // case-2 嵌套到 case-1 之下，文档根层只剩 case-1
      expect(doc.children.map((node) => node.key)).toEqual(['case-1'])
      expect(doc.children[0].children.map((node) => node.key)).toEqual(['case-2'])
      expect(doc.children[0].children[0].level).toBe(3)
    })

    it('默认全展开，折叠后仅保留该节点', () => {
      const base = makeBase([
        { key: 'm1', kind: 'module_suggestion', parentKey: null },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: 'm1' },
      ])
      const tree = useGenerationArtifactTree(base)

      expect(tree.visibleNodes.value.map((node) => node.key)).toEqual(['m1', 'd1'])
      expect(tree.isCollapsed('m1')).toBe(false)

      tree.toggleCollapse('m1')
      expect(tree.visibleNodes.value.map((node) => node.key)).toEqual(['m1'])
      tree.toggleCollapse('m1')
      expect(tree.visibleNodes.value).toHaveLength(2)
    })
  })

  describe('单条采纳守卫', () => {
    it('父级待确认时拦截并提示，不发请求', async () => {
      const base = makeBase([
        { key: 'm1', kind: 'module_suggestion', parentKey: null },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: 'm1' },
      ])
      const tree = useGenerationArtifactTree(base)
      const doc = tree.tree.value[0].children[0]

      await tree.adoptOne(doc)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先采纳父级节点，或使用整树采纳')
      expect(base.submitItems).not.toHaveBeenCalled()
    })

    it('疑似重复产物只允许修改后采纳或驳回', async () => {
      const base = makeBase([
        {
          key: 'm1',
          kind: 'module_suggestion',
          parentKey: null,
          content: { suspectedDuplicateOf: 'existing-module-id' } as never,
        },
      ])
      const tree = useGenerationArtifactTree(base)

      await tree.adoptOne(tree.tree.value[0])
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('疑似与既有数据重复，请修改后采纳或驳回')
      expect(base.submitItems).not.toHaveBeenCalled()
    })

    it('祖先被驳回时提示无法落库', async () => {
      const base = makeBase([
        { key: 'm1', kind: 'module_suggestion', parentKey: null, confirmStatus: 'rejected' },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: 'm1' },
      ])
      const tree = useGenerationArtifactTree(base)

      await tree.adoptOne(tree.tree.value[0].children[0])
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('父级已驳回，该产物无法落库')
      expect(base.submitItems).not.toHaveBeenCalled()
    })

    it('无守卫命中时直接提交单条', async () => {
      const base = makeBase([{ key: 'm1', kind: 'module_suggestion', parentKey: null }])
      const tree = useGenerationArtifactTree(base)

      await tree.adoptOne(tree.tree.value[0])
      expect(base.submitItems).toHaveBeenCalledWith([{ key: 'm1', action: 'adopted' }])
    })
  })

  describe('整树与批量采纳', () => {
    it('整树采纳按预序父先子序提交', async () => {
      const base = makeBase([
        { key: 'case-2', parentKey: 'd1', content: { parentRef: 'case-1' } as never },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: 'm1' },
        { key: 'm1', kind: 'module_suggestion', parentKey: null },
        { key: 'case-1', parentKey: 'd1' },
      ])
      const tree = useGenerationArtifactTree(base)

      await tree.adoptSubtree(tree.tree.value[0])
      expect(base.submitItems).toHaveBeenCalledWith([
        { key: 'm1', action: 'adopted' },
        { key: 'd1', action: 'adopted' },
        { key: 'case-1', action: 'adopted' },
        { key: 'case-2', action: 'adopted' },
      ])
    })

    it('整树采纳跳过已驳回子树并排除疑似重复', async () => {
      const base = makeBase([
        { key: 'm1', kind: 'module_suggestion', parentKey: null },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: 'm1', confirmStatus: 'rejected' },
        { key: 'd2', kind: 'mindmap_document_suggestion', parentKey: 'm1' },
        {
          key: 'case-9',
          parentKey: 'd2',
          content: { suspectedDuplicateOf: 'node-1' } as never,
        },
      ])
      const tree = useGenerationArtifactTree(base)

      await tree.adoptSubtree(tree.tree.value[0])
      expect(base.submitItems).toHaveBeenCalledWith([
        { key: 'm1', action: 'adopted' },
        { key: 'd2', action: 'adopted' },
      ])
      expect(mocks.ElMessage.info).toHaveBeenCalledWith(
        expect.stringContaining('排除疑似重复 1 条'),
      )
    })

    it('批量采纳自动补入待确认父级并按树序输出', async () => {
      const base = makeBase([
        { key: 'case-1', parentKey: 'd1' },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: 'm1' },
        { key: 'm1', kind: 'module_suggestion', parentKey: null },
      ])
      base.selectedKeys.value = ['case-1']
      const tree = useGenerationArtifactTree(base)

      await tree.adoptSelected()
      expect(base.submitItems).toHaveBeenCalledWith([
        { key: 'm1', action: 'adopted' },
        { key: 'd1', action: 'adopted' },
        { key: 'case-1', action: 'adopted' },
      ])
      expect(mocks.ElMessage.info).toHaveBeenCalledWith('自动补入待确认父级 2 条')
    })

    it('所选全部被守卫排除时提示不发请求', async () => {
      const base = makeBase([
        { key: 'm1', kind: 'module_suggestion', parentKey: null, confirmStatus: 'rejected' },
        { key: 'case-1', parentKey: 'm1' },
      ])
      base.selectedKeys.value = ['case-1']
      const tree = useGenerationArtifactTree(base)

      await tree.adoptSelected()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先选择要采纳的产物')
      expect(base.submitItems).not.toHaveBeenCalled()
    })

    it('批量驳回二次确认后按树序提交，放弃不发请求', async () => {
      const base = makeBase([
        { key: 'm1', kind: 'module_suggestion', parentKey: null },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: 'm1' },
      ])
      const tree = useGenerationArtifactTree(base)

      mocks.ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
      await tree.rejectSelected(['d1', 'm1'])
      expect(base.submitItems).not.toHaveBeenCalled()

      await tree.rejectSelected(['d1', 'm1'])
      expect(base.submitItems).toHaveBeenCalledWith([
        { key: 'm1', action: 'rejected' },
        { key: 'd1', action: 'rejected' },
      ])
    })
  })

  describe('编辑后采纳', () => {
    it('按原样携带编辑内容提交 adopted_edited', async () => {
      const base = makeBase([
        { key: 'case-1', parentKey: 'd1', title: '登录成功' },
        { key: 'd1', kind: 'mindmap_document_suggestion', parentKey: null },
      ])
      const tree = useGenerationArtifactTree(base)
      const node = tree.findNode('case-1')

      await tree.adoptEdited(node!, { title: '登录成功（改名）' })
      expect(base.submitItems).toHaveBeenCalledWith([
        { key: 'case-1', action: 'adopted_edited', content: { title: '登录成功（改名）' } },
      ])
    })
  })
})
