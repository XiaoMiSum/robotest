import { shallowRef, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiProcessor } from '@/types'

const mocks = vi.hoisted(() => ({
  defaultProcessorConfig: vi.fn<() => Record<string, unknown>>(() => ({ enabled: true, sortOrder: 0 })),
  processorSummaryTag: vi.fn<() => { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' } | null>(),
  isRecord: vi.fn<(value: unknown) => boolean>(),
}))

vi.mock('@/components/project/api-testing/processorFormModel', () => ({
  defaultProcessorConfig: mocks.defaultProcessorConfig,
  processorSummaryTag: mocks.processorSummaryTag,
  isRecord: mocks.isRecord,
}))

import {
  useEnvironmentProcessors,
  type EnvConfigForm,
  type EnvDsForm,
} from './useEnvironmentProcessors'

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

function makeConfigForm(id: string, overrides?: Partial<EnvConfigForm>): EnvConfigForm {
  return { id, refName: `cfg_${id}`, isDefault: false, ...overrides }
}

function makeDsForm(id: string, overrides?: Partial<EnvDsForm>): EnvDsForm {
  return { id, refName: `ds_${id}`, isDefault: false, ...overrides }
}

describe('useEnvironmentProcessors', () => {
  let processorRows: Ref<ApiProcessor[]>
  let orderedConfigForms: Ref<EnvConfigForm[]>
  let orderedDsForms: Ref<EnvDsForm[]>
  let idCounter: number
  let sortCounter: number
  let localIdFn: ReturnType<typeof vi.fn<() => string>>
  let nextSortOrderFn: ReturnType<typeof vi.fn<() => number>>

  beforeEach(() => {
    vi.clearAllMocks()
    idCounter = 300
    sortCounter = 1
    processorRows = shallowRef<ApiProcessor[]>([])
    orderedConfigForms = ref<EnvConfigForm[]>([])
    orderedDsForms = ref<EnvDsForm[]>([])
    localIdFn = vi.fn(() => `local-${idCounter++}`)
    nextSortOrderFn = vi.fn(() => sortCounter++)
    mocks.defaultProcessorConfig.mockReturnValue({ enabled: true, sortOrder: 0 })
    mocks.processorSummaryTag.mockReturnValue(null)
    mocks.isRecord.mockImplementation(
      (v) => v !== null && typeof v === 'object' && !Array.isArray(v),
    )
  })

  describe('初始状态', () => {
    it('activeProcId 初始为空字符串', () => {
      const { activeProcId } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(activeProcId.value).toBe('')
    })

    it('selectedProcessor 初始为 null', () => {
      const { selectedProcessor } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(selectedProcessor.value).toBeNull()
    })

    it('preProcCount 初始为 0', () => {
      const { preProcCount } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(preProcCount.value).toBe(0)
    })

    it('postProcCount 初始为 0', () => {
      const { postProcCount } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procList('preprocessor')).toHaveLength(2)
      expect(procList('postprocessor')).toHaveLength(1)
    })

    it('空列表返回空数组', () => {
      const { procList } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(selectedProcessor.value).toBe(proc)
    })

    it('id 不匹配时返回 null', () => {
      processorRows.value = [makeProcessor('p1')]
      const { activeProcId, selectedProcessor } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(activeProcId.value).toBe('p1')
    })

    it('处理器 id 为 undefined 时设置空字符串', () => {
      const proc = makeProcessor(undefined as unknown as string)
      processorRows.value = [proc]
      const { selectProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procElement(proc)).toBe(el)
    })

    it('config 非 Record 时返回空对象', () => {
      const proc = makeProcessor('p1', { config: undefined })
      mocks.isRecord.mockReturnValue(false)
      const { procElement } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procElement(proc)).toEqual({})
    })
  })

  describe('addProcessor', () => {
    it('推入新处理器并选中', () => {
      const { addProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      addProcessor('preprocessor')
      expect(processorRows.value).toHaveLength(1)
      expect(activeProcId.value).toBe('local-300')
      expect(localIdFn).toHaveBeenCalled()
      expect(nextSortOrderFn).toHaveBeenCalled()
    })

    it('新处理器类型正确', () => {
      const { addProcessor } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      addProcessor('postprocessor')
      expect(processorRows.value[0].processorType).toBe('postprocessor')
    })

    it('新处理器启用且 sortOrder 由 nextSortOrder 提供', () => {
      const { addProcessor } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      addProcessor('preprocessor')
      expect(processorRows.value[0].enabled).toBe(true)
      expect(processorRows.value[0].sortOrder).toBe(1)
    })

    it('多次添加递增 id', () => {
      const { addProcessor } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(p1)
      removeProcessor(p1)
      expect(activeProcId.value).toBe('p2')
    })

    it('移除后无同类型处理器时 activeProcId 为空', () => {
      const p1 = makeProcessor('p1', { processorType: 'preprocessor' })
      processorRows.value = [p1]
      const { selectProcessor, removeProcessor, activeProcId } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      moveProcessor('preprocessor', 0, 1)
      expect(p1.sortOrder).toBe(1)
      expect(processorRows.value[0].id).toBe('p1')
    })

    it('起始越界时不操作', () => {
      const p1 = makeProcessor('p1')
      processorRows.value = [p1]
      const { moveProcessor } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
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
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      copyProcessor(proc)
      expect(processorRows.value[1].config).not.toBe(proc.config)
    })

    it('处理器不在列表中时不做操作', () => {
      const proc = makeProcessor('p1')
      processorRows.value = []
      const { copyProcessor } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      copyProcessor(proc)
      expect(processorRows.value).toHaveLength(0)
    })

    it('拷贝保持原有 processorType 和 name', () => {
      const proc = makeProcessor('p1', { processorType: 'postprocessor', name: '测试处理器' })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { copyProcessor } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      copyProcessor(proc)
      expect(processorRows.value[1].processorType).toBe('postprocessor')
      expect(processorRows.value[1].name).toBe('测试处理器')
    })
  })

  describe('procTestclass', () => {
    it('无选中处理器时返回空字符串', () => {
      const { procTestclass } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procTestclass.value).toBe('')
    })

    it('选中处理器且 testclass 为 http 时返回 http', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'http' } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procTestclass } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(procTestclass.value).toBe('http')
    })

    it('选中处理器且 testclass 为 jdbc 时返回 jdbc', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'jdbc' } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procTestclass } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(procTestclass.value).toBe('jdbc')
    })

    it('testclass 非 http/jdbc 时返回空字符串', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'other' } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procTestclass } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(procTestclass.value).toBe('')
    })

    it('setter 更新 config 中的 testclass 并调用 applyDefaultProcRef', () => {
      const proc = makeProcessor('p1', { config: { testclass: '' } })
      processorRows.value = [proc]
      const { selectProcessor, procTestclass } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      procTestclass.value = 'http'
      expect(procTestclass.value).toBe('http')
    })
  })

  describe('procHttpRefOptions', () => {
    it('从 orderedConfigForms 映射选项', () => {
      orderedConfigForms.value = [
        makeConfigForm('c1', { refName: 'cfg_c1' }),
        makeConfigForm('c2', { refName: undefined }),
      ]
      const { procHttpRefOptions } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procHttpRefOptions.value).toEqual([
        { value: 'cfg_c1', label: 'cfg_c1' },
        { value: '', label: 'c2' },
      ])
    })

    it('空列表返回空数组', () => {
      const { procHttpRefOptions } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procHttpRefOptions.value).toEqual([])
    })
  })

  describe('procDsRefOptions', () => {
    it('从 orderedDsForms 映射选项', () => {
      orderedDsForms.value = [
        makeDsForm('d1', { refName: 'ds_d1' }),
        makeDsForm('d2', { refName: undefined }),
      ]
      const { procDsRefOptions } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procDsRefOptions.value).toEqual([
        { value: 'ds_d1', label: 'ds_d1' },
        { value: '', label: 'd2' },
      ])
    })

    it('空列表返回空数组', () => {
      const { procDsRefOptions } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procDsRefOptions.value).toEqual([])
    })
  })

  describe('procHttpRef', () => {
    it('无选中处理器时返回空字符串', () => {
      const { procHttpRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procHttpRef.value).toBe('')
    })

    it('testclass 非 http 时返回空字符串', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'jdbc', config: { ref: 'val' } } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procHttpRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(procHttpRef.value).toBe('')
    })

    it('testclass 为 http 且 config.ref 为字符串时返回 ref', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'http', config: { ref: 'my_ref' } } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procHttpRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(procHttpRef.value).toBe('my_ref')
    })

    it('testclass 为 http 但 config.ref 非字符串时返回空', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'http', config: {} } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procHttpRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(procHttpRef.value).toBe('')
    })

    it('setter 更新 config.config.ref', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'http', config: {} } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procHttpRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      procHttpRef.value = 'new_ref'
      expect(procHttpRef.value).toBe('new_ref')
    })
  })

  describe('procDsRef', () => {
    it('无选中处理器时返回空字符串', () => {
      const { procDsRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procDsRef.value).toBe('')
    })

    it('testclass 非 jdbc 时返回空字符串', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'http', config: { datasource: 'val' } } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procDsRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(procDsRef.value).toBe('')
    })

    it('testclass 为 jdbc 且 config.datasource 为字符串时返回 datasource', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'jdbc', config: { datasource: 'my_ds' } } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procDsRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(procDsRef.value).toBe('my_ds')
    })

    it('testclass 为 jdbc 但 config.datasource 非字符串时返回空', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'jdbc', config: {} } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procDsRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      expect(procDsRef.value).toBe('')
    })

    it('setter 更新 config.config.datasource', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'jdbc', config: {} } })
      processorRows.value = [proc]
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { selectProcessor, procDsRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      selectProcessor(proc)
      procDsRef.value = 'new_ds'
      expect(procDsRef.value).toBe('new_ds')
    })
  })

  describe('procTags', () => {
    it('http 处理器生成 HTTP 标签和摘要标签', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'http' } })
      mocks.processorSummaryTag.mockReturnValue({ text: 'GET', type: 'success' })
      const { procTags } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      const tags = procTags(proc)
      expect(tags).toEqual([
        { text: 'HTTP', type: 'info' },
        { text: 'GET', type: 'success' },
      ])
    })

    it('jdbc 处理器生成 JDBC 标签和摘要标签', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'jdbc' } })
      mocks.processorSummaryTag.mockReturnValue({ text: 'SELECT', type: 'primary' })
      const { procTags } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      const tags = procTags(proc)
      expect(tags).toEqual([
        { text: 'JDBC', type: 'info' },
        { text: 'SELECT', type: 'primary' },
      ])
    })

    it('testclass 非 http/jdbc 时不生成类型标签', () => {
      const proc = makeProcessor('p1', { config: { testclass: '' } })
      mocks.processorSummaryTag.mockReturnValue(null)
      const { procTags } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      const tags = procTags(proc)
      expect(tags).toEqual([])
    })

    it('摘要标签为 null 时不添加摘要标签', () => {
      const proc = makeProcessor('p1', { config: { testclass: 'http' } })
      mocks.processorSummaryTag.mockReturnValue(null)
      const { procTags } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      const tags = procTags(proc)
      expect(tags).toEqual([{ text: 'HTTP', type: 'info' }])
    })

    it('testclass 非字符串时默认为空', () => {
      const proc = makeProcessor('p1', { config: { testclass: 123 } })
      mocks.processorSummaryTag.mockReturnValue(null)
      const { procTags } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      const tags = procTags(proc)
      expect(tags).toEqual([])
    })
  })

  describe('procDisplayName', () => {
    it('有名称时返回名称', () => {
      const proc = makeProcessor('p1', { name: '我的处理器' })
      const { procDisplayName } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procDisplayName(proc, 0)).toBe('我的处理器')
    })

    it('名称为空白时返回默认名称', () => {
      const proc = makeProcessor('p1', { name: '   ' })
      const { procDisplayName } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procDisplayName(proc, 0)).toBe('处理器 1')
    })

    it('名称为空字符串时返回默认名称', () => {
      const proc = makeProcessor('p1', { name: '' })
      const { procDisplayName } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(procDisplayName(proc, 2)).toBe('处理器 3')
    })
  })

  describe('applyDefaultProcRef', () => {
    it('null 处理器时不操作', () => {
      const { applyDefaultProcRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      expect(() => applyDefaultProcRef(null)).not.toThrow()
    })

    it('http 处理器无 ref 时应用默认配置 ref', () => {
      orderedConfigForms.value = [makeConfigForm('c1', { refName: 'default_cfg', isDefault: true })]
      const proc = makeProcessor('p1', { config: { testclass: 'http', config: {} } })
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { applyDefaultProcRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      applyDefaultProcRef(proc)
      expect(proc.config).toEqual({ testclass: 'http', config: { ref: 'default_cfg' } })
    })

    it('jdbc 处理器无 datasource 时应用默认数据源', () => {
      orderedDsForms.value = [makeDsForm('d1', { refName: 'default_ds', isDefault: true })]
      const proc = makeProcessor('p1', { config: { testclass: 'jdbc', config: {} } })
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { applyDefaultProcRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      applyDefaultProcRef(proc)
      expect(proc.config).toEqual({ testclass: 'jdbc', config: { datasource: 'default_ds' } })
    })

    it('http 处理器已有 ref 时不覆盖', () => {
      orderedConfigForms.value = [makeConfigForm('c1', { refName: 'default_cfg', isDefault: true })]
      const proc = makeProcessor('p1', { config: { testclass: 'http', config: { ref: 'existing' } } })
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { applyDefaultProcRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      applyDefaultProcRef(proc)
      expect(proc.config).toEqual({ testclass: 'http', config: { ref: 'existing' } })
    })

    it('jdbc 处理器已有 datasource 时不覆盖', () => {
      orderedDsForms.value = [makeDsForm('d1', { refName: 'default_ds', isDefault: true })]
      const proc = makeProcessor('p1', { config: { testclass: 'jdbc', config: { datasource: 'existing' } } })
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { applyDefaultProcRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      applyDefaultProcRef(proc)
      expect(proc.config).toEqual({ testclass: 'jdbc', config: { datasource: 'existing' } })
    })

    it('无默认配置时不做操作', () => {
      orderedConfigForms.value = [makeConfigForm('c1', { isDefault: false })]
      const proc = makeProcessor('p1', { config: { testclass: 'http', config: {} } })
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { applyDefaultProcRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      applyDefaultProcRef(proc)
      expect(proc.config).toEqual({ testclass: 'http', config: {} })
    })

    it('默认配置无 refName 时不应用', () => {
      orderedConfigForms.value = [makeConfigForm('c1', { refName: undefined, isDefault: true })]
      const proc = makeProcessor('p1', { config: { testclass: 'http', config: {} } })
      mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
      const { applyDefaultProcRef } = useEnvironmentProcessors(
        processorRows, orderedConfigForms, orderedDsForms, localIdFn, nextSortOrderFn,
      )
      applyDefaultProcRef(proc)
      expect(proc.config).toEqual({ testclass: 'http', config: {} })
    })
  })
})
