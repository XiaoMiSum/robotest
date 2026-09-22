import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MountTargetSource } from '@/components/project/functional-testing/minder/ai/aiMount'
import type { Minder } from '@/components/project/functional-testing/minder/types'

const mocks = vi.hoisted(() => ({
  recommendPriority: vi.fn<(title: string, ancestorTitles: string[]) => Promise<{ priority: string | null; source: 'rule' | 'llm' }>>(),
  useAiStore: vi.fn(),
  copySelected: vi.fn<(minder: Minder) => boolean>(),
  cutSelected: vi.fn<(minder: Minder) => 'ok' | 'no-node' | 'root'>(),
  pasteToSelected: vi.fn<(minder: Minder) => boolean>(),
  hasClipboard: { value: false },
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/ai', () => ({
  recommendPriority: mocks.recommendPriority,
}))

vi.mock('@/stores/ai', () => ({
  useAiStore: mocks.useAiStore,
}))

vi.mock('@/components/project/functional-testing/minder/clipboard', () => ({
  copySelected: mocks.copySelected,
  cutSelected: mocks.cutSelected,
  pasteToSelected: mocks.pasteToSelected,
  hasClipboard: mocks.hasClipboard,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useMindmapNodeOps } from './useMindmapNodeOps'

function buildNode(id: string, text: string, children: MountTargetSource[] = []): MountTargetSource {
  return {
    data: { id, text },
    getChildren: () => children,
  }
}

describe('useMindmapNodeOps', () => {
  let root: MountTargetSource
  let child1: MountTargetSource
  let child2: MountTargetSource
  let minder: Minder
  let kmEditor: { minder: Minder; history: { undo: ReturnType<typeof vi.fn>; redo: ReturnType<typeof vi.fn>; hasUndo: ReturnType<typeof vi.fn>; hasRedo: ReturnType<typeof vi.fn> }; destroy: ReturnType<typeof vi.fn>; editText: ReturnType<typeof vi.fn> }
  let selectedData: Record<string, unknown> | null

  beforeEach(() => {
    vi.clearAllMocks()
    child2 = buildNode('n3', 'Leaf')
    child1 = buildNode('n2', 'Child', [child2])
    root = buildNode('n1', 'Root', [child1])
    minder = {
      getRoot: () => root,
      getSelectedNode: vi.fn(),
      select: vi.fn(),
      refresh: vi.fn(),
      fire: vi.fn(),
      execCommand: vi.fn(),
      createNode: vi.fn(),
    } as unknown as Minder
    kmEditor = {
      minder,
      history: { undo: vi.fn(), redo: vi.fn(), hasUndo: vi.fn(), hasRedo: vi.fn() },
      destroy: vi.fn(),
      editText: vi.fn(),
    }
    selectedData = null
    mocks.useAiStore.mockReturnValue({ aiEnabled: false })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function makeSut(overrides?: {
    getMinder?: () => Minder | null
    getSelectedNodeData?: () => Record<string, unknown> | null
    updateSelectedState?: ReturnType<typeof vi.fn>
    kmEditorRef?: { value: typeof kmEditor | null }
  }) {
    const getMinder = overrides?.getMinder ?? (() => minder)
    const getSelectedNodeData = overrides?.getSelectedNodeData ?? (() => selectedData)
    const updateSelectedState = overrides?.updateSelectedState ?? vi.fn()
    const kmEditorRef = overrides?.kmEditorRef ?? { value: kmEditor }
    return useMindmapNodeOps(getMinder, getSelectedNodeData, updateSelectedState, kmEditorRef)
  }

  describe('初始状态', () => {
    it('selectedPriority 默认为空', () => {
      const sut = makeSut()
      expect(sut.selectedPriority.value).toBe('')
    })

    it('selectedAiGenerated 默认为 false', () => {
      const sut = makeSut()
      expect(sut.selectedAiGenerated.value).toBe(false)
    })

    it('canUndo 默认为 false', () => {
      const sut = makeSut()
      expect(sut.canUndo.value).toBe(false)
    })

    it('canRedo 默认为 false', () => {
      const sut = makeSut()
      expect(sut.canRedo.value).toBe(false)
    })

    it('priorityRecommendation 默认为 null', () => {
      const sut = makeSut()
      expect(sut.priorityRecommendation.value).toBeNull()
    })
  })

  describe('exec', () => {
    it('调用 minder.execCommand 并触发 receiverfocus', () => {
      const sut = makeSut()
      sut.exec('AppendChildNode', 'text')
      expect(minder.execCommand).toHaveBeenCalledWith('AppendChildNode', 'text')
      expect(minder.fire).toHaveBeenCalledWith('receiverfocus')
    })

    it('minder 为 null 时不报错', () => {
      const sut = makeSut({ getMinder: () => null })
      sut.exec('AppendChildNode')
      expect(minder.execCommand).not.toHaveBeenCalled()
    })
  })

  describe('editSelectedText', () => {
    it('调用 kmEditor.editText', () => {
      const sut = makeSut()
      sut.editSelectedText()
      expect(kmEditor.editText).toHaveBeenCalled()
    })

    it('kmEditor 为 null 时不报错', () => {
      const sut = makeSut({ kmEditorRef: { value: null } })
      sut.editSelectedText()
      expect(kmEditor.editText).not.toHaveBeenCalled()
    })
  })

  describe('markAs', () => {
    it('selectedData 为 null 时静默返回', () => {
      const sut = makeSut({ getSelectedNodeData: () => null })
      sut.markAs('case')
      expect(minder.refresh).not.toHaveBeenCalled()
    })

    it('标记为 case 时设置 type 和默认 priority', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      expect(data.type).toBe('case')
      expect(data.priority).toBe('P2')
    })

    it('标记为 case 已有 priority 时保留', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test', priority: 'P0' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      expect(data.priority).toBe('P0')
    })

    it('标记为非 case 时删除 priority', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test', priority: 'P1' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('module')
      expect(data.type).toBe('module')
      expect(data.priority).toBeUndefined()
    })

    it('标记后调用 refresh 和 updateSelectedState', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const updateSelectedState = vi.fn()
      const sut = makeSut({ getSelectedNodeData: () => data, updateSelectedState })
      sut.markAs('case')
      expect(minder.refresh).toHaveBeenCalled()
      expect(updateSelectedState).toHaveBeenCalled()
    })

    it('标记为 case 且 aiEnabled 时触发优先级推荐', () => {
      mocks.useAiStore.mockReturnValue({ aiEnabled: true })
      mocks.recommendPriority.mockResolvedValue({ priority: 'P1', source: 'rule' })
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      expect(mocks.recommendPriority).toHaveBeenCalledWith('Test', [])
    })

    it('标记为 case 但 aiDisabled 时不触发推荐', () => {
      mocks.useAiStore.mockReturnValue({ aiEnabled: false })
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      expect(mocks.recommendPriority).not.toHaveBeenCalled()
    })
  })

  describe('markPriority', () => {
    it('selectedData 为 null 时静默返回', () => {
      const sut = makeSut({ getSelectedNodeData: () => null })
      sut.markPriority('P0')
      expect(minder.refresh).not.toHaveBeenCalled()
    })

    it('设置 priority 并确保 type 为 case', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markPriority('P1')
      expect(data.priority).toBe('P1')
      expect(data.type).toBe('case')
    })

    it('type 已是 case 时保留', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test', type: 'case' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markPriority('P3')
      expect(data.type).toBe('case')
    })

    it('清空 pending 推荐', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markPriority('P0')
      expect(sut.priorityRecommendation.value).toBeNull()
    })

    it('标记后调用 refresh 和 updateSelectedState', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const updateSelectedState = vi.fn()
      const sut = makeSut({ getSelectedNodeData: () => data, updateSelectedState })
      sut.markPriority('P2')
      expect(minder.refresh).toHaveBeenCalled()
      expect(updateSelectedState).toHaveBeenCalled()
    })
  })

  describe('applyPriorityRecommendation', () => {
    it('无推荐时不调用 markPriority', () => {
      const sut = makeSut()
      sut.applyPriorityRecommendation()
      expect(minder.refresh).not.toHaveBeenCalled()
    })

    it('推荐 priority 为 null 时不调用', () => {
      const sut = makeSut()
      sut.priorityRecommendation.value = { priority: null, source: 'rule' }
      sut.applyPriorityRecommendation()
      expect(minder.refresh).not.toHaveBeenCalled()
    })

    it('有推荐时调用 markPriority', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.priorityRecommendation.value = { priority: 'P0', source: 'llm' }
      sut.applyPriorityRecommendation()
      expect(data.priority).toBe('P0')
    })
  })

  describe('clearMark', () => {
    it('selectedData 为 null 时静默返回', () => {
      const sut = makeSut({ getSelectedNodeData: () => null })
      sut.clearMark()
      expect(minder.refresh).not.toHaveBeenCalled()
    })

    it('清除 type 和 priority', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test', type: 'case', priority: 'P1' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.clearMark()
      expect(data.type).toBe('normal')
      expect(data.priority).toBeUndefined()
    })

    it('调用 refresh 和 updateSelectedState', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const updateSelectedState = vi.fn()
      const sut = makeSut({ getSelectedNodeData: () => data, updateSelectedState })
      sut.clearMark()
      expect(minder.refresh).toHaveBeenCalled()
      expect(updateSelectedState).toHaveBeenCalled()
    })
  })

  describe('removeAiFlag', () => {
    it('selectedData 为 null 时静默返回', () => {
      const sut = makeSut({ getSelectedNodeData: () => null })
      sut.removeAiFlag()
      expect(minder.refresh).not.toHaveBeenCalled()
    })

    it('aiGenerated 非 true 时不操作', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test', aiGenerated: false }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.removeAiFlag()
      expect(minder.refresh).not.toHaveBeenCalled()
    })

    it('aiGenerated 为 true 时置为 false', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test', aiGenerated: true }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.removeAiFlag()
      expect(data.aiGenerated).toBe(false)
      expect(minder.refresh).toHaveBeenCalled()
    })
  })

  describe('addChild / addSibling / deleteNode', () => {
    it('addChild 调用 AppendChildNode', () => {
      const sut = makeSut()
      sut.addChild()
      expect(minder.execCommand).toHaveBeenCalledWith('AppendChildNode', '分支主题')
    })

    it('addSibling 调用 AppendSiblingNode', () => {
      const sut = makeSut()
      sut.addSibling()
      expect(minder.execCommand).toHaveBeenCalledWith('AppendSiblingNode', '分支主题')
    })

    it('deleteNode 调用 RemoveNode', () => {
      const sut = makeSut()
      sut.deleteNode()
      expect(minder.execCommand).toHaveBeenCalledWith('RemoveNode')
    })
  })

  describe('copyNode', () => {
    it('editor 存在时调用 copySelected', () => {
      mocks.copySelected.mockReturnValue(true)
      const sut = makeSut()
      sut.copyNode()
      expect(mocks.copySelected).toHaveBeenCalledWith(minder)
    })

    it('editor 为 null 时不调用', () => {
      const sut = makeSut({ kmEditorRef: { value: null } })
      sut.copyNode()
      expect(mocks.copySelected).not.toHaveBeenCalled()
    })
  })

  describe('cutNode', () => {
    it('剪切成功时无警告', () => {
      mocks.cutSelected.mockReturnValue('ok')
      const sut = makeSut()
      sut.cutNode()
      expect(mocks.cutSelected).toHaveBeenCalledWith(minder)
      expect(mocks.ElMessage.warning).not.toHaveBeenCalled()
    })

    it('根节点剪切时显示警告', () => {
      mocks.cutSelected.mockReturnValue('root')
      const sut = makeSut()
      sut.cutNode()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('根节点不能剪切')
    })

    it('无选中节点时静默返回', () => {
      mocks.cutSelected.mockReturnValue('no-node')
      const sut = makeSut()
      sut.cutNode()
      expect(mocks.ElMessage.warning).not.toHaveBeenCalled()
    })

    it('editor 为 null 时不调用', () => {
      const sut = makeSut({ kmEditorRef: { value: null } })
      sut.cutNode()
      expect(mocks.cutSelected).not.toHaveBeenCalled()
    })
  })

  describe('pasteNode', () => {
    it('editor 存在时调用 pasteToSelected', () => {
      mocks.pasteToSelected.mockReturnValue(true)
      const sut = makeSut()
      sut.pasteNode()
      expect(mocks.pasteToSelected).toHaveBeenCalledWith(minder)
    })

    it('editor 为 null 时不调用', () => {
      const sut = makeSut({ kmEditorRef: { value: null } })
      sut.pasteNode()
      expect(mocks.pasteToSelected).not.toHaveBeenCalled()
    })
  })

  describe('undo / redo', () => {
    it('undo 调用 history.undo', () => {
      const sut = makeSut()
      sut.undo()
      expect(kmEditor.history.undo).toHaveBeenCalled()
    })

    it('redo 调用 history.redo', () => {
      const sut = makeSut()
      sut.redo()
      expect(kmEditor.history.redo).toHaveBeenCalled()
    })
  })

  describe('onSelectionChange', () => {
    it('data 为 null 时重置状态', () => {
      const sut = makeSut()
      sut.onSelectionChange(null)
      expect(sut.selectedPriority.value).toBe('')
      expect(sut.selectedAiGenerated.value).toBe(false)
    })

    it('提取 priority 和 aiGenerated', () => {
      const sut = makeSut()
      sut.onSelectionChange({ priority: 'P1', aiGenerated: true })
      expect(sut.selectedPriority.value).toBe('P1')
      expect(sut.selectedAiGenerated.value).toBe(true)
    })

    it('priority 不存在时置为空字符串', () => {
      const sut = makeSut()
      sut.onSelectionChange({ text: 'test' })
      expect(sut.selectedPriority.value).toBe('')
    })

    it('递增 priorityRecSeq 并清空推荐', () => {
      const sut = makeSut()
      sut.priorityRecommendation.value = { priority: 'P0', source: 'llm' }
      sut.onSelectionChange({ text: 'test' })
      expect(sut.priorityRecommendation.value).toBeNull()
    })
  })

  describe('triggerPriorityRecommend', () => {
    it('root 为 null 时静默返回', () => {
      const sut = makeSut({ getMinder: () => null })
      sut.markAs('case')
      expect(mocks.recommendPriority).not.toHaveBeenCalled()
    })

    it('data 为 null 时静默返回', () => {
      const mocks2 = { ...mocks }
      const sut = makeSut({ getSelectedNodeData: () => null })
      sut.markAs('case')
      expect(mocks2.recommendPriority).not.toHaveBeenCalled()
    })

    it('nodeId 为空时静默返回', () => {
      const data: Record<string, unknown> = { id: '', text: 'Test' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      expect(mocks.recommendPriority).not.toHaveBeenCalled()
    })

    it('title 为空白时静默返回', () => {
      const data: Record<string, unknown> = { id: 'n1', text: '   ' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      expect(mocks.recommendPriority).not.toHaveBeenCalled()
    })

    it('成功时更新 priorityRecommendation', async () => {
      mocks.useAiStore.mockReturnValue({ aiEnabled: true })
      mocks.recommendPriority.mockResolvedValue({ priority: 'P0', source: 'rule' })
      const data: Record<string, unknown> = { id: 'n2', text: 'Child' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      await vi.waitFor(() => {
        expect(sut.priorityRecommendation.value).toEqual({ priority: 'P0', source: 'rule' })
      })
    })

    it('推荐 priority 为 null 时不更新', async () => {
      mocks.useAiStore.mockReturnValue({ aiEnabled: true })
      mocks.recommendPriority.mockResolvedValue({ priority: null, source: 'llm' })
      const data: Record<string, unknown> = { id: 'n2', text: 'Child' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      await vi.waitFor(() => {
        expect(mocks.recommendPriority).toHaveBeenCalled()
      })
      expect(sut.priorityRecommendation.value).toBeNull()
    })

    it('请求被新选择覆盖时不更新（seq 不匹配）', async () => {
      vi.useFakeTimers()
      mocks.useAiStore.mockReturnValue({ aiEnabled: true })
      let resolveFirst: ((v: { priority: string | null; source: 'rule' | 'llm' }) => void) = () => {}
      mocks.recommendPriority.mockImplementationOnce(() => new Promise((r) => { resolveFirst = r as (v: { priority: string | null; source: 'rule' | 'llm' }) => void }))
      const data1: Record<string, unknown> = { id: 'n1', text: 'First' }
      const sut = makeSut({ getSelectedNodeData: () => data1 })
      sut.markAs('case')

      sut.onSelectionChange({ text: 'Second' })

      mocks.recommendPriority.mockResolvedValue({ priority: 'P2', source: 'rule' })
      const data2: Record<string, unknown> = { id: 'n2', text: 'Second' }
      selectedData = data2
      sut.markAs('case')

      resolveFirst({ priority: 'P1', source: 'llm' })
      await vi.advanceTimersByTimeAsync(0)
      expect(sut.priorityRecommendation.value).toEqual({ priority: 'P2', source: 'rule' })

      vi.useRealTimers()
    })

    it('recommendPriority 失败时静默忽略', async () => {
      mocks.useAiStore.mockReturnValue({ aiEnabled: true })
      mocks.recommendPriority.mockRejectedValue(new Error('network'))
      const data: Record<string, unknown> = { id: 'n2', text: 'Child' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      await vi.waitFor(() => {
        expect(mocks.recommendPriority).toHaveBeenCalled()
      })
      expect(sut.priorityRecommendation.value).toBeNull()
    })

    it('路径传递给 recommendPriority', async () => {
      mocks.useAiStore.mockReturnValue({ aiEnabled: true })
      mocks.recommendPriority.mockResolvedValue({ priority: 'P1', source: 'rule' })
      const data: Record<string, unknown> = { id: 'n3', text: 'Leaf' }
      const sut = makeSut({ getSelectedNodeData: () => data })
      sut.markAs('case')
      await vi.waitFor(() => {
        expect(mocks.recommendPriority).toHaveBeenCalledWith('Leaf', ['Root', 'Child'])
      })
    })
  })

  describe('hasClipboard', () => {
    it('导出 hasClipboard 引用', () => {
      const sut = makeSut()
      expect(sut.hasClipboard).toBe(mocks.hasClipboard)
    })
  })
})
