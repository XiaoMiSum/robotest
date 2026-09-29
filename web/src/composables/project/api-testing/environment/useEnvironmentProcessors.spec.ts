import { shallowRef, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiProcessor } from '@/types'

const mocks = vi.hoisted(() => ({
  defaultProcessorConfig: vi.fn<() => Record<string, unknown>>(() => ({ enabled: true, sortOrder: 0 })),
  processorTags: vi.fn<(element: Record<string, unknown>) => { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[]>(),
  isRecord: vi.fn<(value: unknown) => boolean>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))

vi.mock('@/composables/project/api-testing/processorFormModel', () => ({
  defaultProcessorConfig: mocks.defaultProcessorConfig,
  processorTags: mocks.processorTags,
  isRecord: mocks.isRecord,
}))

import { useEnvironmentProcessors } from './useEnvironmentProcessors'

function makeProcessor(id: string, overrides?: Partial<ApiProcessor>): ApiProcessor {
  return {
    id,
    processorType: 'preprocessor',
    name: '',
    config: {},
    enabled: true,
    sortOrder: 0,
    ...overrides,
  }
}

describe('useEnvironmentProcessors', () => {
  let processorRows: Ref<ApiProcessor[]>
  let idCounter: number
  let sortCounter: number
  let localIdFn: ReturnType<typeof vi.fn<() => string>>
  let nextSortOrderFn: ReturnType<typeof vi.fn<() => number>>

  beforeEach(() => {
    vi.clearAllMocks()
    idCounter = 300
    sortCounter = 1
    processorRows = shallowRef<ApiProcessor[]>([])
    localIdFn = vi.fn(() => `local-${idCounter++}`)
    nextSortOrderFn = vi.fn(() => sortCounter++)
    mocks.defaultProcessorConfig.mockReturnValue({ enabled: true, sortOrder: 0 })
    mocks.processorTags.mockReturnValue([])
    mocks.isRecord.mockImplementation(
      (v) => v !== null && typeof v === 'object' && !Array.isArray(v),
    )
  })

  describe('初始状态', () => {
    it('activeProcId 初始为空字符串', () => {
      const { activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(activeProcId.value).toBe('')
    })

    it('selectedProcessor 初始为 null', () => {
      const { selectedProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(selectedProcessor.value).toBeNull()
    })

    it('preProcCount 初始为 0', () => {
      const { preProcCount } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(preProcCount.value).toBe(0)
    })

    it('postProcCount 初始为 0', () => {
      const { postProcCount } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(postProcCount.value).toBe(0)
    })
  })

  describe('procList', () => {
    it('按 processorType 过滤', () => {
      processorRows.value = [
        makeProcessor('1', { processorType: 'preprocessor' }),
        makeProcessor('2', { processorType: 'postprocessor' }),
        makeProcessor('3', { processorType: 'preprocessor' }),
      ]
      const { procList } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procList('preprocessor')).toHaveLength(2)
      expect(procList('postprocessor')).toHaveLength(1)
    })

    it('空列表返回空数组', () => {
      const { procList } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procList('preprocessor')).toEqual([])
      expect(procList('postprocessor')).toEqual([])
    })
  })

  describe('preProcCount / postProcCount', () => {
    it('正确计数前置和后置处理器', () => {
      processorRows.value = [
        makeProcessor('1', { processorType: 'preprocessor' }),
        makeProcessor('2', { processorType: 'preprocessor' }),
        makeProcessor('3', { processorType: 'postprocessor' }),
      ]
      const { preProcCount, postProcCount } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(preProcCount.value).toBe(2)
      expect(postProcCount.value).toBe(1)
    })
  })

  describe('selectedProcessor', () => {
    it('选中后返回匹配的处理器', () => {
      const proc = makeProcessor('p1')
      processorRows.value = [proc]
      const { selectProcessor, selectedProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(selectedProcessor.value).toBe(proc)
    })

    it('id 不匹配时返回 null', () => {
      processorRows.value = [makeProcessor('p1')]
      const { activeProcId, selectedProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      activeProcId.value = 'not-exist'
      expect(selectedProcessor.value).toBeNull()
    })
  })

  describe('selectProcessor', () => {
    it('设置 activeProcId 为处理器 id', () => {
      const proc = makeProcessor('p1')
      processorRows.value = [proc]
      const { selectProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(activeProcId.value).toBe('p1')
    })

    it('处理器 id 为 undefined 时设置空字符串', () => {
      const proc = makeProcessor(undefined as unknown as string)
      processorRows.value = [proc]
      const { selectProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(activeProcId.value).toBe('')
    })
  })

  describe('procElement', () => {
    it('config 为 Record 时返回 config', () => {
      const el = { testclass: 'http', method: 'GET' }
      const proc = makeProcessor('p1', { config: el })
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { procElement } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procElement(proc)).toBe(el)
    })

    it('config 非 Record 时返回空对象', () => {
      const proc = makeProcessor('p1', { config: undefined })
      mocks.isRecord.mockReturnValue(false)
      const { procElement } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procElement(proc)).toEqual({})
    })
  })

  describe('addProcessor', () => {
    it('推入新处理器并选中', () => {
      const { addProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      addProcessor('preprocessor')
      expect(processorRows.value).toHaveLength(1)
      expect(activeProcId.value).toBe('local-300')
      expect(localIdFn).toHaveBeenCalled()
      expect(nextSortOrderFn).toHaveBeenCalled()
    })

    it('新处理器类型正确', () => {
      const { addProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      addProcessor('postprocessor')
      expect(processorRows.value[0].processorType).toBe('postprocessor')
    })

    it('新处理器启用且 sortOrder 由 nextSortOrder 提供', () => {
      const { addProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      addProcessor('preprocessor')
      expect(processorRows.value[0].enabled).toBe(true)
      expect(processorRows.value[0].sortOrder).toBe(1)
    })

    it('多次添加递增 id', () => {
      const { addProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      addProcessor('preprocessor')
      addProcessor('postprocessor')
      expect(processorRows.value).toHaveLength(2)
      expect(processorRows.value[0].id).toBe('local-300')
      expect(processorRows.value[1].id).toBe('local-301')
    })
  })

  describe('removeProcessor', () => {
    it('从列表移除处理器', () => {
      const p1 = makeProcessor('p1')
      const p2 = makeProcessor('p2')
      processorRows.value = [p1, p2]
      const { removeProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      removeProcessor(p1)
      expect(processorRows.value).toHaveLength(1)
      expect(processorRows.value[0]).toBe(p2)
    })

    it('移除后自动修正 activeProcId 为同类型第一个', () => {
      const p1 = makeProcessor('p1', { processorType: 'preprocessor' })
      const p2 = makeProcessor('p2', { processorType: 'preprocessor' })
      processorRows.value = [p1, p2]
      const { selectProcessor, removeProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      selectProcessor(p1)
      removeProcessor(p1)
      expect(activeProcId.value).toBe('p2')
    })

    it('移除后无同类型处理器时 activeProcId 为空', () => {
      const p1 = makeProcessor('p1', { processorType: 'preprocessor' })
      processorRows.value = [p1]
      const { selectProcessor, removeProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      selectProcessor(p1)
      removeProcessor(p1)
      expect(activeProcId.value).toBe('')
    })

    it('移除非选中处理器时 activeProcId 不变', () => {
      const p1 = makeProcessor('p1')
      const p2 = makeProcessor('p2')
      processorRows.value = [p1, p2]
      const { selectProcessor, removeProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      selectProcessor(p2)
      removeProcessor(p1)
      expect(activeProcId.value).toBe('p2')
    })
  })

  describe('moveProcessor', () => {
    it('向下移动交换 sortOrder 和位置', () => {
      const p1 = makeProcessor('p1', { sortOrder: 1 })
      const p2 = makeProcessor('p2', { sortOrder: 2 })
      processorRows.value = [p1, p2]
      const { moveProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      moveProcessor('preprocessor', 0, 1)
      expect(p1.sortOrder).toBe(2)
      expect(p2.sortOrder).toBe(1)
      expect(processorRows.value[0].id).toBe('p2')
      expect(processorRows.value[1].id).toBe('p1')
      expect(activeProcId.value).toBe('p2')
    })

    it('向上移动交换 sortOrder 和位置', () => {
      const p1 = makeProcessor('p1', { sortOrder: 1 })
      const p2 = makeProcessor('p2', { sortOrder: 2 })
      processorRows.value = [p1, p2]
      const { moveProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      moveProcessor('preprocessor', 1, -1)
      expect(p1.sortOrder).toBe(2)
      expect(p2.sortOrder).toBe(1)
      expect(processorRows.value[0].id).toBe('p2')
      expect(processorRows.value[1].id).toBe('p1')
      expect(activeProcId.value).toBe('p1')
    })

    it('目标越界时不操作', () => {
      const p1 = makeProcessor('p1', { sortOrder: 1 })
      processorRows.value = [p1]
      const { moveProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      moveProcessor('preprocessor', 0, 1)
      expect(p1.sortOrder).toBe(1)
      expect(processorRows.value[0].id).toBe('p1')
    })

    it('起始越界时不操作', () => {
      const p1 = makeProcessor('p1')
      processorRows.value = [p1]
      const { moveProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      moveProcessor('preprocessor', 5, 1)
      expect(processorRows.value).toHaveLength(1)
    })

    it('只交换同类型的处理器', () => {
      const pre1 = makeProcessor('pre1', { processorType: 'preprocessor', sortOrder: 1 })
      const post1 = makeProcessor('post1', { processorType: 'postprocessor', sortOrder: 1 })
      const pre2 = makeProcessor('pre2', { processorType: 'preprocessor', sortOrder: 2 })
      processorRows.value = [pre1, post1, pre2]
      const { moveProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      moveProcessor('preprocessor', 0, 1)
      expect(pre1.sortOrder).toBe(2)
      expect(pre2.sortOrder).toBe(1)
      expect(processorRows.value[0].id).toBe('pre2')
      expect(processorRows.value[1].id).toBe('post1')
      expect(processorRows.value[2].id).toBe('pre1')
    })

    it('sortOrder 为 undefined 时默认为 0', () => {
      const p1 = makeProcessor('p1', { sortOrder: undefined })
      const p2 = makeProcessor('p2', { sortOrder: undefined })
      processorRows.value = [p1, p2]
      const { moveProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      moveProcessor('preprocessor', 0, 1)
      expect(p1.sortOrder).toBe(0)
      expect(p2.sortOrder).toBe(0)
    })
  })

  describe('copyProcessor', () => {
    it('在原处理器之后插入深拷贝', () => {
      const el = { testclass: 'http', config: { method: 'GET' } }
      const proc = makeProcessor('p1', { config: el })
      processorRows.value = [proc]
      const { copyProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      copyProcessor(proc)
      expect(processorRows.value).toHaveLength(2)
      expect(processorRows.value[0].id).toBe('p1')
      expect(processorRows.value[1].id).toBe('local-300')
      expect(activeProcId.value).toBe('local-300')
    })

    it('拷贝的 config 与原对象不引用同一个', () => {
      const el = { testclass: 'http', config: { method: 'GET' } }
      const proc = makeProcessor('p1', { config: el })
      processorRows.value = [proc]
      const { copyProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      copyProcessor(proc)
      expect(processorRows.value[1].config).not.toBe(proc.config)
    })

    it('处理器不在列表中时不做操作', () => {
      const proc = makeProcessor('p1')
      processorRows.value = []
      const { copyProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      copyProcessor(proc)
      expect(processorRows.value).toHaveLength(0)
    })

    it('拷贝保持原有 processorType 和 name', () => {
      const proc = makeProcessor('p1', { processorType: 'postprocessor', name: '测试处理器' })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { copyProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      copyProcessor(proc)
      expect(processorRows.value[1].processorType).toBe('postprocessor')
      expect(processorRows.value[1].name).toBe('测试处理器')
    })
  })

  describe('平铺展开式草稿', () => {
    it('初始无展开明细与草稿', () => {
      const { procExpandedId, procDraft, procDraftMode } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procExpandedId.value).toBe('')
      expect(procDraft.value).toBeNull()
      expect(procDraftMode.value).toBe('none')
    })

    it('toggleProcDetail 展开只读明细，再次点击收起', () => {
      const proc = makeProcessor('p1')
      processorRows.value = [proc]
      const { toggleProcDetail, procExpandedId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      toggleProcDetail(proc)
      expect(procExpandedId.value).toBe('p1')
      toggleProcDetail(proc)
      expect(procExpandedId.value).toBe('')
    })

    it('点击其他处理器行时切换展开目标', () => {
      const a = makeProcessor('p1')
      const b = makeProcessor('p2')
      processorRows.value = [a, b]
      const { toggleProcDetail, procExpandedId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      toggleProcDetail(a)
      toggleProcDetail(b)
      expect(procExpandedId.value).toBe('p2')
    })

    it('startProcEdit 深拷贝配置且不改动原行', () => {
      const config = { testclass: 'http', config: { method: 'POST' }, extractors: [] }
      const proc = makeProcessor('p1', { name: '原处理器', config })
      processorRows.value = [proc]
      const { startProcEdit, procDraft, procDraftMode, procExpandedId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      startProcEdit(proc)
      expect(procDraftMode.value).toBe('edit')
      expect(procExpandedId.value).toBe('')
      const draft = procDraft.value
      if (!draft) throw new Error('草稿未创建')
      expect(draft.name).toBe('原处理器')
      expect(draft.config).toEqual(config)
      expect(draft.config).not.toBe(config)
      draft.name = '草稿名'
      expect(proc.name).toBe('原处理器')
    })

    it('startProcAdd 生成未命名草稿且不推入列表', () => {
      const { startProcAdd, procDraft, procDraftMode } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      startProcAdd('postprocessor')
      expect(processorRows.value).toHaveLength(0)
      expect(procDraftMode.value).toBe('add')
      expect(procDraft.value?.id).toBe('')
      expect(procDraft.value?.processorType).toBe('postprocessor')
      expect(mocks.defaultProcessorConfig).toHaveBeenCalled()
    })

    it('编辑中点击自身行不切换，点击其他行收起草稿并展开该行', () => {
      const a = makeProcessor('p1')
      const b = makeProcessor('p2')
      processorRows.value = [a, b]
      const { startProcEdit, toggleProcDetail, procDraft, procExpandedId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      startProcEdit(a)
      toggleProcDetail(a)
      expect(procDraft.value).not.toBeNull()
      expect(procExpandedId.value).toBe('')
      toggleProcDetail(b)
      expect(procDraft.value).toBeNull()
      expect(procExpandedId.value).toBe('p2')
    })

    it('新增草稿期间点击任意行收起草稿', () => {
      const proc = makeProcessor('p1')
      processorRows.value = [proc]
      const { startProcAdd, toggleProcDetail, procDraft, procExpandedId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      startProcAdd('preprocessor')
      toggleProcDetail(proc)
      expect(procDraft.value).toBeNull()
      expect(procExpandedId.value).toBe('p1')
    })

    it('cancelProcDraft 丢弃草稿并回到 none', () => {
      const { startProcEdit, cancelProcDraft, procDraft, procDraftMode } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      startProcEdit(makeProcessor('p1'))
      cancelProcDraft()
      expect(procDraft.value).toBeNull()
      expect(procDraftMode.value).toBe('none')
    })

    it('removeProcessor 清理关联草稿与展开态，不影响他行草稿', () => {
      const a = makeProcessor('p1')
      const b = makeProcessor('p2')
      processorRows.value = [a, b]
      const { startProcEdit, toggleProcDetail, removeProcessor, procDraft, procExpandedId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      toggleProcDetail(a)
      startProcEdit(b)
      removeProcessor(a)
      expect(procDraft.value?.id).toBe('p2')
      expect(procExpandedId.value).toBe('')
      removeProcessor(b)
      expect(procDraft.value).toBeNull()
      expect(processorRows.value).toHaveLength(0)
    })

    it('名称为空时拒绝保存并保留草稿', () => {
      const { startProcAdd, commitProcDraft, procDraft, procDraftMode } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      startProcAdd('preprocessor')
      const draft = procDraft.value
      if (!draft) throw new Error('草稿未创建')
      draft.name = '   '
      expect(commitProcDraft()).toBe(false)
      expect(processorRows.value).toHaveLength(0)
      expect(procDraftMode.value).toBe('add')
      expect(mocks.ElMessage.success).not.toHaveBeenCalled()
    })

    it('无草稿时保存直接返回 false', () => {
      const { commitProcDraft } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(commitProcDraft()).toBe(false)
      expect(mocks.ElMessage.success).not.toHaveBeenCalled()
    })

    it('保存新增：推入列表并回写名称与配置', () => {
      const { startProcAdd, commitProcDraft, procDraft, procDraftMode, procExpandedId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      startProcAdd('preprocessor')
      const draft = procDraft.value
      if (!draft) throw new Error('草稿未创建')
      draft.name = ' 新处理器 '
      draft.config = { enabled: true, testclass: 'jdbc', config: { sql: 'select 1' }, extractors: [] }
      expect(commitProcDraft()).toBe(true)
      expect(processorRows.value).toHaveLength(1)
      expect(processorRows.value[0].id).toBe('local-300')
      expect(processorRows.value[0].sortOrder).toBe(1)
      expect(processorRows.value[0].name).toBe('新处理器')
      expect(processorRows.value[0].config).toEqual({
        enabled: true, testclass: 'jdbc', config: { sql: 'select 1' }, extractors: [], sortOrder: 1,
      })
      expect(procDraft.value).toBeNull()
      expect(procDraftMode.value).toBe('none')
      expect(procExpandedId.value).toBe('local-300')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('处理器已添加')
    })

    it('保存编辑：回写原行并展开该行明细', () => {
      const proc = makeProcessor('p1', { name: '原处理器', config: { testclass: 'http' } })
      processorRows.value = [proc]
      const { startProcEdit, commitProcDraft, procDraft, procDraftMode, procExpandedId } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      startProcEdit(proc)
      const draft = procDraft.value
      if (!draft) throw new Error('草稿未创建')
      draft.name = '改名'
      draft.config = { testclass: 'jdbc', config: { sql: 'select 1' } }
      expect(commitProcDraft()).toBe(true)
      expect(processorRows.value).toHaveLength(1)
      expect(proc.name).toBe('改名')
      expect(proc.config).toEqual({ testclass: 'jdbc', config: { sql: 'select 1' }, sortOrder: 0 })
      expect(procDraft.value).toBeNull()
      expect(procDraftMode.value).toBe('none')
      expect(procExpandedId.value).toBe('p1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('处理器已保存')
    })

    it('selectedProcessor 在草稿期间指向草稿', () => {
      const proc = makeProcessor('p1', { name: '原处理器' })
      processorRows.value = [proc]
      const { startProcEdit, cancelProcDraft, selectProcessor, selectedProcessor } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      startProcEdit(proc)
      expect(selectedProcessor.value?.name).toBe('原处理器')
      expect(selectedProcessor.value).not.toBe(proc)
      cancelProcDraft()
      expect(selectedProcessor.value).toBe(proc)
    })
  })

  describe('procTags', () => {
    it('委托共享标签模型并回传处理器元素', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'http' } })
      mocks.processorTags.mockReturnValue([
        { text: 'HTTP', type: 'info' },
        { text: 'GET', type: 'success' },
      ])
      const { procTags } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procTags(proc)).toEqual([
        { text: 'HTTP', type: 'info' },
        { text: 'GET', type: 'success' },
      ])
      expect(mocks.processorTags).toHaveBeenCalledWith(proc.config)
    })

    it('共享模型返回空数组时透传空数组', () => {
      const proc = makeProcessor('p1', { config: { testclass: '' } })
      mocks.processorTags.mockReturnValue([])
      const { procTags } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procTags(proc)).toEqual([])
    })
  })

  describe('procDisplayName', () => {
    it('有名称时返回名称', () => {
      const proc = makeProcessor('p1', { name: '我的处理器' })
      const { procDisplayName } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procDisplayName(proc, 0)).toBe('我的处理器')
    })

    it('名称为空白时返回默认名称', () => {
      const proc = makeProcessor('p1', { name: '   ' })
      const { procDisplayName } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procDisplayName(proc, 0)).toBe('处理器 1')
    })

    it('名称为空字符串时返回默认名称', () => {
      const proc = makeProcessor('p1', { name: '' })
      const { procDisplayName } = useEnvironmentProcessors(
        processorRows, localIdFn, nextSortOrderFn,
      )
      expect(procDisplayName(proc, 2)).toBe('处理器 3')
    })
  })

})
