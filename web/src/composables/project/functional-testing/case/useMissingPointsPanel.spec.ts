import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AiMissingPointResult, ProjectModule, RequirementSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  analyzeMissingPoints: vi.fn<() => { controller: AbortController; promise: Promise<AiMissingPointResult> }>(),
  fetchProjectModuleTree: vi.fn<() => Promise<ProjectModule[]>>(),
  getDocumentRequirements: vi.fn<() => Promise<RequirementSummary[]>>(),
  buildMissingPointText: vi.fn<() => string>(),
  collectDocumentOptions: vi.fn<() => { id: string; name: string; path: string }[]>(),
  pickPreselectDocument: vi.fn<() => string>(),
  useRouter: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('vue-router', () => ({
  useRouter: mocks.useRouter,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/services/ai', () => ({
  analyzeMissingPoints: mocks.analyzeMissingPoints,
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
  getDocumentRequirements: mocks.getDocumentRequirements,
}))

vi.mock('@/components/project/missingPoints', () => ({
  buildMissingPointText: mocks.buildMissingPointText,
  collectDocumentOptions: mocks.collectDocumentOptions,
  pickPreselectDocument: mocks.pickPreselectDocument,
}))

import { nextTick, ref } from 'vue'
import { useMissingPointsPanel } from './useMissingPointsPanel'

function makeResult(points: { title: string; description: string; suggestedModulePath: string | null; relatedCaseTitles: string[] }[] = []): AiMissingPointResult {
  return {
    semanticDegraded: false,
    points: points.length ? points : [{ title: '点1', description: 'desc1', suggestedModulePath: null, relatedCaseTitles: [] }],
  }
}

function setupMocks(overrides?: { requirements?: RequirementSummary[]; tree?: ProjectModule[] }) {
  mocks.getDocumentRequirements.mockResolvedValue(overrides?.requirements ?? [])
  mocks.fetchProjectModuleTree.mockResolvedValue(overrides?.tree ?? [])
  mocks.analyzeMissingPoints.mockReturnValue({
    controller: new AbortController(),
    promise: Promise.resolve(makeResult()),
  })
  mocks.buildMissingPointText.mockReturnValue('built text')
  mocks.collectDocumentOptions.mockReturnValue([])
  mocks.pickPreselectDocument.mockReturnValue('')
}

describe('useMissingPointsPanel', () => {
  let routerPush: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    routerPush = vi.fn()
    mocks.useRouter.mockReturnValue({ push: routerPush })
    setupMocks()
  })

  function init() {
    const docId = ref('doc-1')
    const visible = ref(false)
    const panel = useMissingPointsPanel(() => docId.value, visible)
    return { docId, visible, panel }
  }

  describe('初始状态', () => {
    it('keywords 为空数组', () => {
      const { panel } = init()
      expect(panel.keywords.value).toEqual([])
    })

    it('text 为空字符串', () => {
      const { panel } = init()
      expect(panel.text.value).toBe('')
    })

    it('requirementIds 为空数组', () => {
      const { panel } = init()
      expect(panel.requirementIds.value).toEqual([])
    })

    it('requirementTitles 为空数组', () => {
      const { panel } = init()
      expect(panel.requirementTitles.value).toEqual([])
    })

    it('reqSelectorVisible 为 false', () => {
      const { panel } = init()
      expect(panel.reqSelectorVisible.value).toBe(false)
    })

    it('analyzing 为 false', () => {
      const { panel } = init()
      expect(panel.analyzing.value).toBe(false)
    })

    it('result 为 null', () => {
      const { panel } = init()
      expect(panel.result.value).toBeNull()
    })

    it('checkedIndexes 为空 Set', () => {
      const { panel } = init()
      expect(panel.checkedIndexes.value.size).toBe(0)
    })

    it('documentOptions 为空数组', () => {
      const { panel } = init()
      expect(panel.documentOptions.value).toEqual([])
    })

    it('docSelectVisible 为 false', () => {
      const { panel } = init()
      expect(panel.docSelectVisible.value).toBe(false)
    })

    it('targetDocId 为空字符串', () => {
      const { panel } = init()
      expect(panel.targetDocId.value).toBe('')
    })
  })

  describe('computed hasAnyInput', () => {
    it('全部为空时为 false', () => {
      const { panel } = init()
      expect(panel.hasAnyInput.value).toBe(false)
    })

    it('有 keywords 时为 true', () => {
      const { panel } = init()
      panel.keywords.value = ['kw1']
      expect(panel.hasAnyInput.value).toBe(true)
    })

    it('text 非空时为 true', () => {
      const { panel } = init()
      panel.text.value = 'some text'
      expect(panel.hasAnyInput.value).toBe(true)
    })

    it('有 requirementIds 时为 true', () => {
      const { panel } = init()
      panel.requirementIds.value = ['req-1']
      expect(panel.hasAnyInput.value).toBe(true)
    })

    it('text 全空格时为 false', () => {
      const { panel } = init()
      panel.text.value = '   '
      expect(panel.hasAnyInput.value).toBe(false)
    })
  })

  describe('computed checkedPoints', () => {
    it('result 为空时返回空数组', () => {
      const { panel } = init()
      expect(panel.checkedPoints.value).toEqual([])
    })

    it('返回勾选的点', () => {
      const { panel } = init()
      panel.result.value = makeResult([
        { title: 'a', description: 'd1', suggestedModulePath: null, relatedCaseTitles: [] },
        { title: 'b', description: 'd2', suggestedModulePath: null, relatedCaseTitles: [] },
      ])
      panel.checkedIndexes.value = new Set([0])
      expect(panel.checkedPoints.value).toHaveLength(1)
      expect(panel.checkedPoints.value[0].title).toBe('a')
    })
  })

  describe('computed allChecked', () => {
    it('result 为 null 时为 false', () => {
      const { panel } = init()
      expect(panel.allChecked.value).toBe(false)
    })

    it('全部勾选时为 true', () => {
      const { panel } = init()
      panel.result.value = makeResult([
        { title: 'a', description: 'd1', suggestedModulePath: null, relatedCaseTitles: [] },
        { title: 'b', description: 'd2', suggestedModulePath: null, relatedCaseTitles: [] },
      ])
      panel.checkedIndexes.value = new Set([0, 1])
      expect(panel.allChecked.value).toBe(true)
    })

    it('未全选时为 false', () => {
      const { panel } = init()
      panel.result.value = makeResult([
        { title: 'a', description: 'd1', suggestedModulePath: null, relatedCaseTitles: [] },
        { title: 'b', description: 'd2', suggestedModulePath: null, relatedCaseTitles: [] },
      ])
      panel.checkedIndexes.value = new Set([0])
      expect(panel.allChecked.value).toBe(false)
    })
  })

  describe('toggleAll', () => {
    it('checked 为 true 时勾选全部', () => {
      const { panel } = init()
      panel.result.value = makeResult([
        { title: 'a', description: 'd1', suggestedModulePath: null, relatedCaseTitles: [] },
        { title: 'b', description: 'd2', suggestedModulePath: null, relatedCaseTitles: [] },
      ])
      panel.toggleAll(true)
      expect(panel.checkedIndexes.value).toEqual(new Set([0, 1]))
    })

    it('checked 为 false 时清空', () => {
      const { panel } = init()
      panel.result.value = makeResult([
        { title: 'a', description: 'd1', suggestedModulePath: null, relatedCaseTitles: [] },
      ])
      panel.checkedIndexes.value = new Set([0])
      panel.toggleAll(false)
      expect(panel.checkedIndexes.value.size).toBe(0)
    })

    it('result 为 null 时不报错', () => {
      const { panel } = init()
      panel.toggleAll(true)
      expect(panel.checkedIndexes.value.size).toBe(0)
    })
  })

  describe('toggleItem', () => {
    it('checked 为 true 时添加索引', () => {
      const { panel } = init()
      panel.toggleItem(2, true)
      expect(panel.checkedIndexes.value.has(2)).toBe(true)
    })

    it('checked 为 false 时移除索引', () => {
      const { panel } = init()
      panel.checkedIndexes.value = new Set([2])
      panel.toggleItem(2, false)
      expect(panel.checkedIndexes.value.has(2)).toBe(false)
    })

    it('不修改原始 Set', () => {
      const { panel } = init()
      const original = new Set([0])
      panel.checkedIndexes.value = original
      panel.toggleItem(1, true)
      expect(original.has(1)).toBe(false)
    })
  })

  describe('handleRequirementConfirm', () => {
    it('设置 requirementIds 和 requirementTitles', () => {
      const { panel } = init()
      const selected: RequirementSummary[] = [
        { id: 'r1', title: '需求1' },
        { id: 'r2', title: '需求2' },
      ]
      panel.handleRequirementConfirm(selected)
      expect(panel.requirementIds.value).toEqual(['r1', 'r2'])
      expect(panel.requirementTitles.value).toEqual(selected)
    })

    it('已有 title 时保留', () => {
      const { panel } = init()
      panel.requirementTitles.value = [{ id: 'r1', title: '旧标题' }]
      panel.handleRequirementConfirm([{ id: 'r1', title: '' }])
      expect(panel.requirementTitles.value).toEqual([{ id: 'r1', title: '旧标题' }])
    })

    it('无 title 且之前不存在时保留原对象', () => {
      const { panel } = init()
      const selected = [{ id: 'r1', title: '' }] as RequirementSummary[]
      panel.handleRequirementConfirm(selected)
      expect(panel.requirementTitles.value[0].id).toBe('r1')
    })
  })

  describe('removeRequirement', () => {
    it('移除指定 id 的需求', () => {
      const { panel } = init()
      panel.requirementIds.value = ['r1', 'r2']
      panel.requirementTitles.value = [{ id: 'r1', title: 't1' }, { id: 'r2', title: 't2' }]
      panel.removeRequirement('r1')
      expect(panel.requirementIds.value).toEqual(['r2'])
      expect(panel.requirementTitles.value).toEqual([{ id: 'r2', title: 't2' }])
    })

    it('id 不存在时无变化', () => {
      const { panel } = init()
      panel.requirementIds.value = ['r1']
      panel.requirementTitles.value = [{ id: 'r1', title: 't1' }]
      panel.removeRequirement('r99')
      expect(panel.requirementIds.value).toEqual(['r1'])
    })
  })

  describe('analyze', () => {
    it('无输入时显示警告', async () => {
      const { panel } = init()
      await panel.analyze()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请至少输入关键词、需求文本或选择需求')
      expect(mocks.analyzeMissingPoints).not.toHaveBeenCalled()
    })

    it('有 keywords 时发起分析', async () => {
      const { panel } = init()
      panel.keywords.value = ['kw1']
      await panel.analyze()
      expect(mocks.analyzeMissingPoints).toHaveBeenCalledWith({ keywords: ['kw1'] })
    })

    it('有 text 时发起分析', async () => {
      const { panel } = init()
      panel.text.value = 'some text'
      await panel.analyze()
      expect(mocks.analyzeMissingPoints).toHaveBeenCalledWith({ text: 'some text' })
    })

    it('有 requirementIds 时发起分析', async () => {
      const { panel } = init()
      panel.requirementIds.value = ['r1']
      await panel.analyze()
      expect(mocks.analyzeMissingPoints).toHaveBeenCalledWith({ requirementIds: ['r1'] })
    })

    it('text trim 后为空时提交 undefined', async () => {
      const { panel } = init()
      panel.keywords.value = ['kw1']
      panel.text.value = '   '
      await panel.analyze()
      expect(mocks.analyzeMissingPoints).toHaveBeenCalledWith({ keywords: ['kw1'] })
    })

    it('成功时设置 result 并默认全选', async () => {
      const result = makeResult([
        { title: 'a', description: 'd1', suggestedModulePath: null, relatedCaseTitles: [] },
        { title: 'b', description: 'd2', suggestedModulePath: null, relatedCaseTitles: [] },
      ])
      mocks.analyzeMissingPoints.mockReturnValue({
        controller: new AbortController(),
        promise: Promise.resolve(result),
      })
      const { panel } = init()
      panel.keywords.value = ['kw1']
      await panel.analyze()
      expect(panel.result.value).toEqual(result)
      expect(panel.checkedIndexes.value).toEqual(new Set([0, 1]))
    })

    it('失败时显示错误消息', async () => {
      mocks.analyzeMissingPoints.mockReturnValue({
        controller: new AbortController(),
        promise: Promise.reject(new Error('分析失败')),
      })
      const { panel } = init()
      panel.keywords.value = ['kw1']
      await panel.analyze()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('分析失败')
    })

    it('非 Error 异常显示通用消息', async () => {
      mocks.analyzeMissingPoints.mockReturnValue({
        controller: new AbortController(),
        promise: Promise.reject('string err'),
      })
      const { panel } = init()
      panel.keywords.value = ['kw1']
      await panel.analyze()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('分析失败')
    })

    it('analyzing 在请求期间为 true，完成后为 false', async () => {
      let resolve!: (v: AiMissingPointResult) => void
      mocks.analyzeMissingPoints.mockReturnValue({
        controller: new AbortController(),
        promise: new Promise<AiMissingPointResult>((r) => { resolve = r }),
      })
      const { panel } = init()
      panel.keywords.value = ['kw1']
      const p = panel.analyze()
      expect(panel.analyzing.value).toBe(true)
      resolve(makeResult())
      await p
      expect(panel.analyzing.value).toBe(false)
    })

    it('analyzing 在失败后为 false', async () => {
      mocks.analyzeMissingPoints.mockReturnValue({
        controller: new AbortController(),
        promise: Promise.reject(new Error('fail')),
      })
      const { panel } = init()
      panel.keywords.value = ['kw1']
      await panel.analyze()
      expect(panel.analyzing.value).toBe(false)
    })

    it('abort 后不显示错误消息', async () => {
      const controller = new AbortController()
      mocks.analyzeMissingPoints.mockReturnValue({
        controller,
        promise: Promise.reject(Object.assign(new Error('aborted'), { name: 'AbortError' })),
      })
      const { panel } = init()
      panel.keywords.value = ['kw1']
      controller.abort()
      await panel.analyze()
      expect(mocks.ElMessage.error).not.toHaveBeenCalled()
    })
  })

  describe('cancelAnalyze', () => {
    it('终止请求并重置 analyzing', async () => {
      const controller = new AbortController()
      let resolve!: (v: AiMissingPointResult) => void
      mocks.analyzeMissingPoints.mockReturnValue({
        controller,
        promise: new Promise<AiMissingPointResult>((r) => { resolve = r }),
      })
      const { panel } = init()
      panel.keywords.value = ['kw1']
      const p = panel.analyze()
      panel.cancelAnalyze()
      expect(panel.analyzing.value).toBe(false)
      resolve(makeResult())
      await p
    })

    it('无 controller 时不报错', () => {
      const { panel } = init()
      panel.cancelAnalyze()
      expect(panel.analyzing.value).toBe(false)
    })
  })

  describe('openTargetSelect', () => {
    it('无勾选点时显示警告', async () => {
      const { panel } = init()
      panel.result.value = makeResult()
      panel.checkedIndexes.value = new Set()
      await panel.openTargetSelect()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请至少勾选一个遗漏测试点')
    })

    it('加载模块树并设置文档选项', async () => {
      mocks.collectDocumentOptions.mockReturnValue([{ id: 'doc-1', name: '文档1', path: '文档1' }])
      mocks.pickPreselectDocument.mockReturnValue('doc-1')
      const { panel } = init()
      panel.result.value = makeResult()
      panel.checkedIndexes.value = new Set([0])
      await panel.openTargetSelect()
      expect(mocks.fetchProjectModuleTree).toHaveBeenCalledWith('testcase')
      expect(panel.documentOptions.value).toEqual([{ id: 'doc-1', name: '文档1', path: '文档1' }])
      expect(panel.targetDocId.value).toBe('doc-1')
      expect(panel.docSelectVisible.value).toBe(true)
    })

    it('无文档时显示警告', async () => {
      mocks.collectDocumentOptions.mockReturnValue([])
      const { panel } = init()
      panel.result.value = makeResult()
      panel.checkedIndexes.value = new Set([0])
      await panel.openTargetSelect()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('项目暂无文档，无法生成用例')
      expect(panel.docSelectVisible.value).toBe(false)
    })

    it('加载失败时显示错误', async () => {
      mocks.fetchProjectModuleTree.mockRejectedValue(new Error('加载失败'))
      const { panel } = init()
      panel.result.value = makeResult()
      panel.checkedIndexes.value = new Set([0])
      await panel.openTargetSelect()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载失败')
    })

    it('非 Error 异常显示通用消息', async () => {
      mocks.fetchProjectModuleTree.mockRejectedValue(42)
      const { panel } = init()
      panel.result.value = makeResult()
      panel.checkedIndexes.value = new Set([0])
      await panel.openTargetSelect()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载模块树失败')
    })
  })

  describe('toCaseGenerate', () => {
    it('无勾选点时不做任何操作', () => {
      const { panel } = init()
      panel.result.value = makeResult()
      panel.checkedIndexes.value = new Set()
      panel.toCaseGenerate()
      expect(routerPush).not.toHaveBeenCalled()
    })

    it('无 targetDocId 时不做任何操作', () => {
      const { panel } = init()
      panel.result.value = makeResult()
      panel.checkedIndexes.value = new Set([0])
      panel.targetDocId.value = ''
      panel.toCaseGenerate()
      expect(routerPush).not.toHaveBeenCalled()
    })

    it('有勾选点和 targetDocId 时跳转', () => {
      const { panel, visible } = init()
      panel.result.value = makeResult()
      panel.checkedIndexes.value = new Set([0])
      panel.targetDocId.value = 'doc-target'
      visible.value = true
      panel.toCaseGenerate()
      expect(routerPush).toHaveBeenCalledWith({
        name: 'FunctionalTesting',
        query: { tab: 'cases', documentId: 'doc-target', aiGenerate: 'built text' },
      })
      expect(panel.docSelectVisible.value).toBe(false)
      expect(visible.value).toBe(false)
    })
  })

  describe('watch visible', () => {
    it('visible 变为 true 时加载文档需求', async () => {
      const { visible } = init()
      visible.value = true
      await vi.dynamicImportSettled()
      expect(mocks.getDocumentRequirements).toHaveBeenCalledWith('doc-1')
    })

    it('visible 变为 false 时不加载', async () => {
      const { visible } = init()
      visible.value = false
      await vi.dynamicImportSettled()
      expect(mocks.getDocumentRequirements).not.toHaveBeenCalled()
    })

    it('加载成功时填充 requirementIds 和 requirementTitles', async () => {
      mocks.getDocumentRequirements.mockResolvedValue([
        { id: 'r1', title: '需求1' },
        { id: 'r2', title: '需求2' },
      ])
      const { visible, panel } = init()
      visible.value = true
      await vi.dynamicImportSettled()
      expect(panel.requirementIds.value).toEqual(['r1', 'r2'])
      expect(panel.requirementTitles.value).toEqual([
        { id: 'r1', title: '需求1' },
        { id: 'r2', title: '需求2' },
      ])
    })

    it('加载失败时显示错误', async () => {
      mocks.getDocumentRequirements.mockRejectedValue(new Error('加载失败'))
      const { visible } = init()
      visible.value = true
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载失败')
    })

    it('非 Error 异常显示通用消息', async () => {
      mocks.getDocumentRequirements.mockRejectedValue(42)
      const { visible } = init()
      visible.value = true
      await vi.dynamicImportSettled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载文档关联需求失败')
    })
  })

  describe('watch docId', () => {
    it('docId 变化时重置所有状态', async () => {
      const { docId, panel } = init()
      panel.keywords.value = ['kw1']
      panel.text.value = 'text'
      panel.requirementIds.value = ['r1']
      panel.result.value = makeResult()
      panel.checkedIndexes.value = new Set([0])
      docId.value = 'doc-2'
      await nextTick()
      expect(panel.keywords.value).toEqual([])
      expect(panel.text.value).toBe('')
      expect(panel.requirementIds.value).toEqual([])
      expect(panel.requirementTitles.value).toEqual([])
      expect(panel.result.value).toBeNull()
      expect(panel.checkedIndexes.value.size).toBe(0)
    })

    it('docId 变化时取消正在进行的分析', async () => {
      const controller = new AbortController()
      let resolve!: (v: AiMissingPointResult) => void
      mocks.analyzeMissingPoints.mockReturnValue({
        controller,
        promise: new Promise<AiMissingPointResult>((r) => { resolve = r }),
      })
      const { docId, panel } = init()
      panel.keywords.value = ['kw1']
      panel.analyze()
      docId.value = 'doc-2'
      await nextTick()
      expect(panel.analyzing.value).toBe(false)
      resolve(makeResult())
    })
  })

  describe('onBeforeUnmount', () => {
    it('卸载时取消请求', async () => {
      const controller = new AbortController()
      mocks.analyzeMissingPoints.mockReturnValue({
        controller,
        promise: new Promise(() => {}),
      })
      const { panel } = init()
      panel.keywords.value = ['kw1']
      panel.analyze()
      expect(controller.signal.aborted).toBe(false)
    })
  })
})
