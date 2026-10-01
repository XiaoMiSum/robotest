import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Minder } from '@/minder/types'

const mocks = vi.hoisted(() => ({
  copySelected: vi.fn<(minder: Minder) => boolean>(),
  cutSelected: vi.fn<(minder: Minder) => 'ok' | 'no-node' | 'root'>(),
  pasteToSelected: vi.fn<(minder: Minder) => boolean>(),
  hasClipboard: { value: false },
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))


vi.mock('@/minder/clipboard', () => ({
  copySelected: mocks.copySelected,
  cutSelected: mocks.cutSelected,
  pasteToSelected: mocks.pasteToSelected,
  hasClipboard: mocks.hasClipboard,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useMindmapNodeOps } from './useMindmapNodeOps'


describe('useMindmapNodeOps', () => {
  let minder: Minder
  let kmEditor: { minder: Minder; history: { undo: ReturnType<typeof vi.fn>; redo: ReturnType<typeof vi.fn>; hasUndo: ReturnType<typeof vi.fn>; hasRedo: ReturnType<typeof vi.fn> }; destroy: ReturnType<typeof vi.fn>; editText: ReturnType<typeof vi.fn> }
  let selectedData: Record<string, unknown> | null

  beforeEach(() => {
    vi.clearAllMocks()
    minder = {
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

    it('canUndo 默认为 false', () => {
      const sut = makeSut()
      expect(sut.canUndo.value).toBe(false)
    })

    it('canRedo 默认为 false', () => {
      const sut = makeSut()
      expect(sut.canRedo.value).toBe(false)
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

    it('标记后调用 refresh 和 updateSelectedState', () => {
      const data: Record<string, unknown> = { id: 'n1', text: 'Test' }
      const updateSelectedState = vi.fn()
      const sut = makeSut({ getSelectedNodeData: () => data, updateSelectedState })
      sut.markPriority('P2')
      expect(minder.refresh).toHaveBeenCalled()
      expect(updateSelectedState).toHaveBeenCalled()
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
    })

    it('priority 不存在时置为空字符串', () => {
      const sut = makeSut()
      sut.onSelectionChange({ text: 'test' })
      expect(sut.selectedPriority.value).toBe('')
    })

  })

  describe('hasClipboard', () => {
    it('导出 hasClipboard 引用', () => {
      const sut = makeSut()
      expect(sut.hasClipboard).toBe(mocks.hasClipboard)
    })
  })
})
