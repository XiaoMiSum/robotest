import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { UseAiStreamOptions } from '@/composables/ai/useAiStream'
import type { AiRequirementSplitResult } from '@/types'

const mocks = vi.hoisted(() => {
  const cancelMock = vi.fn()
  return {
    useAiStream: vi.fn<(opts: UseAiStreamOptions) => { cancel: () => void }>(() => ({ cancel: cancelMock })),
    useAiStore: vi.fn(),
    batchCreateRequirements: vi.fn(),
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    cancelMock,
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/composables/ai/useAiStream', () => ({
  useAiStream: mocks.useAiStream,
}))

vi.mock('@/stores/ai', () => ({
  useAiStore: mocks.useAiStore,
}))

vi.mock('@/services/project', () => ({
  batchCreateRequirements: mocks.batchCreateRequirements,
}))

import { useRequirementSplit } from './useRequirementSplit'

function makeSplitResult(overrides?: Partial<AiRequirementSplitResult>): AiRequirementSplitResult {
  return {
    modules: [
      {
        module: '登录模块',
        items: [
          { title: '需求A', content: '内容A' },
          { title: '需求B', content: '内容B' },
        ],
      },
      {
        module: '搜索模块',
        items: [{ title: '需求C', content: '内容C' }],
      },
    ],
    warnings: [],
    ...overrides,
  }
}

describe('useRequirementSplit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useAiStore.mockReturnValue({ effectiveModelId: vi.fn().mockReturnValue('model-1') })
  })

  function create() {
    const visible = { value: true }
    const emit = vi.fn()
    const split = useRequirementSplit(visible, emit)
    return { ...split, visible, emit }
  }

  describe('初始状态', () => {
    it('phase 初始为 input', () => {
      const s = create()
      expect(s.phase.value).toBe('input')
    })

    it('inputExpanded 初始为 false', () => {
      const s = create()
      expect(s.inputExpanded.value).toBe(false)
    })

    it('previewExpanded 初始为 false', () => {
      const s = create()
      expect(s.previewExpanded.value).toBe(false)
    })

    it('text 初始为空字符串', () => {
      const s = create()
      expect(s.text.value).toBe('')
    })

    it('warnings 初始为空数组', () => {
      const s = create()
      expect(s.warnings.value).toEqual([])
    })

    it('importing 初始为 false', () => {
      const s = create()
      expect(s.importing.value).toBe(false)
    })

    it('entries 初始为空数组', () => {
      const s = create()
      expect(s.entries.value).toEqual([])
    })

    it('totalCount 初始为 0', () => {
      const s = create()
      expect(s.totalCount.value).toBe(0)
    })

    it('checkedCount 初始为 0', () => {
      const s = create()
      expect(s.checkedCount.value).toBe(0)
    })

    it('allChecked 在空数组时返回 false', () => {
      const s = create()
      expect(s.allChecked.value).toBe(false)
    })

    it('partialChecked 在空数组时返回 false', () => {
      const s = create()
      expect(s.partialChecked.value).toBe(false)
    })

    it('groups 初始为空数组', () => {
      const s = create()
      expect(s.groups.value).toEqual([])
    })
  })

  describe('computed: totalCount / checkedCount / allChecked / partialChecked', () => {
    it('entries 填充后 totalCount 正确', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
        { key: '0-1', module: 'A', title: 't2', content: 'c2', checked: false, editing: false },
      ]
      expect(s.totalCount.value).toBe(2)
    })

    it('checkedCount 仅统计 checked 为 true 的条目', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
        { key: '0-1', module: 'A', title: 't2', content: 'c2', checked: false, editing: false },
      ]
      expect(s.checkedCount.value).toBe(1)
    })

    it('allChecked 在全部 checked 时返回 true', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
      ]
      expect(s.allChecked.value).toBe(true)
    })

    it('allChecked 在部分未 checked 时返回 false', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
        { key: '0-1', module: 'A', title: 't2', content: 'c2', checked: false, editing: false },
      ]
      expect(s.allChecked.value).toBe(false)
    })

    it('partialChecked 在部分选中时返回 true', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
        { key: '0-1', module: 'A', title: 't2', content: 'c2', checked: false, editing: false },
      ]
      expect(s.partialChecked.value).toBe(true)
    })

    it('partialChecked 在全部选中时返回 false', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
      ]
      expect(s.partialChecked.value).toBe(false)
    })

    it('partialChecked 在全未选中时返回 false', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: false, editing: false },
      ]
      expect(s.partialChecked.value).toBe(false)
    })
  })

  describe('computed: groups', () => {
    it('按 module 字段分组', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
        { key: '0-1', module: 'A', title: 't2', content: 'c2', checked: false, editing: false },
        { key: '1-0', module: 'B', title: 't3', content: 'c3', checked: true, editing: false },
      ]
      expect(s.groups.value).toHaveLength(2)
      expect(s.groups.value[0].module).toBe('A')
      expect(s.groups.value[0].items).toHaveLength(2)
      expect(s.groups.value[0].checked).toBe(1)
      expect(s.groups.value[1].module).toBe('B')
      expect(s.groups.value[1].items).toHaveLength(1)
      expect(s.groups.value[1].checked).toBe(1)
    })

    it('空 entries 返回空 groups', () => {
      const s = create()
      expect(s.groups.value).toEqual([])
    })
  })

  describe('expandInput', () => {
    it('设置 inputExpanded 为 true, previewExpanded 为 false', () => {
      const s = create()
      s.previewExpanded.value = true
      s.expandInput()
      expect(s.inputExpanded.value).toBe(true)
      expect(s.previewExpanded.value).toBe(false)
    })
  })

  describe('toggleAll', () => {
    it('未全选时全部勾选', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: false, editing: false },
        { key: '0-1', module: 'A', title: 't2', content: 'c2', checked: true, editing: false },
      ]
      s.toggleAll()
      expect(s.entries.value.every((e) => e.checked)).toBe(true)
    })

    it('全选时全部取消', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
        { key: '0-1', module: 'A', title: 't2', content: 'c2', checked: true, editing: false },
      ]
      s.toggleAll()
      expect(s.entries.value.every((e) => !e.checked)).toBe(true)
    })
  })

  describe('removeEntry', () => {
    it('移除指定 entry', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
        { key: '0-1', module: 'A', title: 't2', content: 'c2', checked: true, editing: false },
      ]
      s.removeEntry(s.entries.value[0])
      expect(s.entries.value).toHaveLength(1)
      expect(s.entries.value[0].key).toBe('0-1')
    })
  })

  describe('saveEdit', () => {
    it('标题非空时结束编辑', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: true },
      ]
      s.saveEdit(s.entries.value[0])
      expect(s.entries.value[0].editing).toBe(false)
    })

    it('标题为空时显示警告', () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: '  ', content: 'c1', checked: true, editing: true },
      ]
      s.saveEdit(s.entries.value[0])
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('标题不能为空')
      expect(s.entries.value[0].editing).toBe(true)
    })
  })

  describe('startSplit', () => {
    it('text 为空时显示警告', () => {
      const s = create()
      s.startSplit()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先粘贴需求文档')
      expect(mocks.useAiStream).not.toHaveBeenCalled()
    })

    it('text 仅空格时显示警告', () => {
      const s = create()
      s.text.value = '   '
      s.startSplit()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先粘贴需求文档')
    })

    it('text 非空时设置 phase 为 streaming 并调用 useAiStream', () => {
      const s = create()
      s.text.value = '需求文档内容'
      s.startSplit()
      expect(s.phase.value).toBe('streaming')
      expect(mocks.useAiStream).toHaveBeenCalledTimes(1)
      expect(mocks.useAiStream).toHaveBeenCalledWith(
        expect.objectContaining({
          url: '/project/ai/requirements/split',
          body: expect.objectContaining({ text: '需求文档内容' }),
        }),
      )
    })

    it('done 事件解析结果填充 entries 并切换到 preview', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      const options = mocks.useAiStream.mock.calls[0]![0]
      const result = makeSplitResult({ warnings: ['注意1'] })
      options.onEvent({ event: 'done', data: result })
      expect(s.phase.value).toBe('preview')
      expect(s.inputExpanded.value).toBe(false)
      expect(s.warnings.value).toEqual(['注意1'])
      expect(s.entries.value).toHaveLength(3)
      expect(s.entries.value[0]).toEqual({
        key: '0-0',
        module: '登录模块',
        title: '需求A',
        content: '内容A',
        checked: true,
        editing: false,
      })
      expect(s.entries.value[1]).toEqual({
        key: '0-1',
        module: '登录模块',
        title: '需求B',
        content: '内容B',
        checked: true,
        editing: false,
      })
      expect(s.entries.value[2]).toEqual({
        key: '1-0',
        module: '搜索模块',
        title: '需求C',
        content: '内容C',
        checked: true,
        editing: false,
      })
    })

    it('done 事件中 warnings 为 undefined 时默认为空数组', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      const options = mocks.useAiStream.mock.calls[0]![0]
      const result = { modules: [] }
      options.onEvent({ event: 'done', data: result })
      expect(s.warnings.value).toEqual([])
    })

    it('error 事件显示错误并恢复状态', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      const options = mocks.useAiStream.mock.calls[0]![0]
      options.onEvent({ event: 'error', data: { message: '拆分失败' } })
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('拆分失败')
      expect(s.phase.value).toBe('input')
    })

    it('error 事件无 message 时使用默认消息', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      const options = mocks.useAiStream.mock.calls[0]![0]
      options.onEvent({ event: 'error', data: {} })
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('AI 拆分失败')
    })

    it('onError 回调显示错误并恢复状态', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      const options = mocks.useAiStream.mock.calls[0]![0]
      options.onError!(new Error('网络异常'))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络异常')
      expect(s.phase.value).toBe('input')
    })

    it('onClose 在 streaming 阶段时恢复状态', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      const options = mocks.useAiStream.mock.calls[0]![0]
      options.onClose!()
      expect(s.phase.value).toBe('input')
    })

    it('onClose 在非 streaming 阶段时不恢复', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      const options = mocks.useAiStream.mock.calls[0]![0]
      const result = makeSplitResult()
      options.onEvent({ event: 'done', data: result })
      options.onClose!()
      expect(s.phase.value).toBe('preview')
    })
  })

  describe('stop', () => {
    it('取消 controller 并恢复状态', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      s.stop()
      expect(mocks.cancelMock).toHaveBeenCalled()
      expect(s.phase.value).toBe('input')
    })
  })

  describe('restoreAfterSplitInterrupted', () => {
    it('有 entries 时恢复到 preview 阶段', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      const options = mocks.useAiStream.mock.calls[0]![0]
      const result = makeSplitResult()
      options.onEvent({ event: 'done', data: result })
      s.phase.value = 'streaming'
      options.onClose!()
      expect(s.phase.value).toBe('preview')
      expect(s.inputExpanded.value).toBe(true)
    })

    it('无 entries 时恢复到 input 阶段', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      const options = mocks.useAiStream.mock.calls[0]![0]
      options.onClose!()
      expect(s.phase.value).toBe('input')
      expect(s.inputExpanded.value).toBe(false)
    })
  })

  describe('importChecked', () => {
    it('无勾选项时显示警告', async () => {
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: false, editing: false },
      ]
      await s.importChecked()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请至少勾选一条')
      expect(mocks.batchCreateRequirements).not.toHaveBeenCalled()
    })

    it('entries 为空时显示警告', async () => {
      const s = create()
      await s.importChecked()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请至少勾选一条')
    })

    it('勾选项成功入库时显示成功并 emit imported', async () => {
      mocks.batchCreateRequirements.mockResolvedValue({ count: 2 })
      const s = create()
      s.entries.value = [
        { key: '0-0', module: '登录', title: '需求A', content: '内容A', checked: true, editing: false },
        { key: '0-1', module: '搜索', title: '需求B', content: '内容B', checked: true, editing: false },
      ]
      await s.importChecked()
      expect(mocks.batchCreateRequirements).toHaveBeenCalledWith({
        items: [
          { title: '登录·需求A', content: '内容A', aiGenerated: true },
          { title: '搜索·需求B', content: '内容B', aiGenerated: true },
        ],
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已入库 2 条')
      expect(s.emit).toHaveBeenCalledWith('imported', 2)
      expect(s.visible.value).toBe(false)
    })

    it('部分勾选仅导入勾选项', async () => {
      mocks.batchCreateRequirements.mockResolvedValue({ count: 1 })
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
        { key: '0-1', module: 'A', title: 't2', content: 'c2', checked: false, editing: false },
      ]
      await s.importChecked()
      expect(mocks.batchCreateRequirements).toHaveBeenCalledWith({
        items: [{ title: 'A·t1', content: 'c1', aiGenerated: true }],
      })
    })

    it('批量入库失败时显示 Error 消息', async () => {
      mocks.batchCreateRequirements.mockRejectedValue(new Error('网络超时'))
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
      ]
      await s.importChecked()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络超时')
    })

    it('批量入库失败非 Error 异常显示通用消息', async () => {
      mocks.batchCreateRequirements.mockRejectedValue('unknown')
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
      ]
      await s.importChecked()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('批量入库失败')
    })

    it('importing 在完成后恢复为 false', async () => {
      mocks.batchCreateRequirements.mockResolvedValue({ count: 1 })
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
      ]
      await s.importChecked()
      expect(s.importing.value).toBe(false)
    })

    it('importing 在失败后恢复为 false', async () => {
      mocks.batchCreateRequirements.mockRejectedValue(new Error('fail'))
      const s = create()
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
      ]
      await s.importChecked()
      expect(s.importing.value).toBe(false)
    })
  })

  describe('reset', () => {
    it('重置所有状态到初始值', () => {
      const s = create()
      s.text.value = '需求文档'
      s.phase.value = 'preview'
      s.inputExpanded.value = true
      s.previewExpanded.value = true
      s.warnings.value = ['警告']
      s.entries.value = [
        { key: '0-0', module: 'A', title: 't1', content: 'c1', checked: true, editing: false },
      ]
      s.reset()
      expect(s.text.value).toBe('')
      expect(s.phase.value).toBe('input')
      expect(s.inputExpanded.value).toBe(false)
      expect(s.previewExpanded.value).toBe(false)
      expect(s.warnings.value).toEqual([])
      expect(s.entries.value).toEqual([])
    })

    it('有 controller 时取消', () => {
      const s = create()
      s.text.value = '需求文档'
      s.startSplit()
      s.reset()
      expect(mocks.cancelMock).toHaveBeenCalled()
    })
  })

})
