import { ref, nextTick } from 'vue'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { ApiSceneDetail, ApiHttpConfig, ApiDataSource } from '@/types'

const mocks = vi.hoisted(() => ({
  isRecord: vi.fn<(value: unknown) => value is Record<string, unknown>>(),
  processorSummaryTag: vi.fn<(element: Record<string, unknown> | null | undefined) => { text: string; type: string } | null>(),
}))

vi.mock('@/composables/project/api-testing/processorFormModel', () => ({
  isRecord: mocks.isRecord,
  processorSummaryTag: mocks.processorSummaryTag,
}))

import { useSceneProcessors, type SceneProcessorElement } from './useSceneProcessors'

function makeProcessor(type: 'pre' | 'post', overrides?: Partial<SceneProcessorElement>): SceneProcessorElement {
  return { type, name: '', enabled: true, testclass: 'http', config: {}, extractors: [], ...overrides }
}

function makeDetail(processors: Record<string, unknown>[] = []): ApiSceneDetail {
  return {
    id: 'scene-1',
    name: '测试场景',
    variables: [],
    processors,
    changeVersion: 1,
    steps: [],
  }
}

function makeHttpConfig(refName: string, name: string): ApiHttpConfig {
  return { name, refName }
}

function makeDs(refName: string, name: string): ApiDataSource {
  return { name, refName }
}

async function createWithProcessors(...processors: SceneProcessorElement[]) {
  const detail = ref<ApiSceneDetail | null>(null)
  const result = useSceneProcessors(detail, ref([]), ref([]), ref('steps'))
  detail.value = makeDetail(processors)
  await nextTick()
  return { detail, ...result }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.isRecord.mockImplementation((v) => v !== null && typeof v === 'object' && !Array.isArray(v))
  mocks.processorSummaryTag.mockReturnValue(null)
})

describe('useSceneProcessors', () => {
  describe('初始状态', () => {
    it('editProcessors 初始为空数组', () => {
      const { editProcessors } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(editProcessors.value).toEqual([])
    })

    it('selectedProcessorIdx 初始为 null', () => {
      const { selectedProcessorIdx } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(selectedProcessorIdx.value).toBeNull()
    })

    it('procDrag 初始为 null', () => {
      const { procDrag } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(procDrag.value).toBeNull()
    })

    it('selectedProcessorEl 初始为 null', () => {
      const { selectedProcessorEl } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(selectedProcessorEl.value).toBeNull()
    })
  })

  describe('detail watcher', () => {
    it('detail 变化时同步 editProcessors', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const { editProcessors } = useSceneProcessors(detail, ref([]), ref([]), ref('steps'))
      detail.value = makeDetail([makeProcessor('pre'), makeProcessor('post')])
      await nextTick()
      expect(editProcessors.value).toHaveLength(2)
      expect(editProcessors.value[0].type).toBe('pre')
    })

    it('processors 中的对象被浅拷贝而非引用', async () => {
      const original = makeProcessor('pre')
      const detail = ref<ApiSceneDetail | null>(null)
      const { editProcessors } = useSceneProcessors(detail, ref([]), ref([]), ref('steps'))
      detail.value = makeDetail([original])
      await nextTick()
      expect(editProcessors.value[0]).not.toBe(original)
    })

    it('detail 为 null 时不修改 editProcessors', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const { editProcessors } = useSceneProcessors(detail, ref([]), ref([]), ref('steps'))
      detail.value = null
      await nextTick()
      expect(editProcessors.value).toEqual([])
    })

    it('选中索引超出新列表长度时重置为 null', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const { selectedProcessorIdx } = useSceneProcessors(detail, ref([]), ref([]), ref('steps'))
      detail.value = makeDetail([makeProcessor('pre'), makeProcessor('post')])
      await nextTick()
      selectedProcessorIdx.value = 1
      detail.value = makeDetail([makeProcessor('pre')])
      await nextTick()
      expect(selectedProcessorIdx.value).toBeNull()
    })

    it('选中索引在新列表范围内时保持不变', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const { selectedProcessorIdx } = useSceneProcessors(detail, ref([]), ref([]), ref('steps'))
      detail.value = makeDetail([makeProcessor('pre'), makeProcessor('post')])
      await nextTick()
      selectedProcessorIdx.value = 0
      detail.value = makeDetail([makeProcessor('pre'), makeProcessor('post')])
      await nextTick()
      expect(selectedProcessorIdx.value).toBe(0)
    })
  })

  describe('processorIndexes', () => {
    it('返回指定类型处理器的扁平索引', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const { processorIndexes } = useSceneProcessors(detail, ref([]), ref([]), ref('steps'))
      detail.value = makeDetail([
        makeProcessor('pre'),
        makeProcessor('post'),
        makeProcessor('pre'),
      ])
      await nextTick()
      expect(processorIndexes('pre')).toEqual([0, 2])
      expect(processorIndexes('post')).toEqual([1])
    })

    it('空列表返回空数组', () => {
      const { processorIndexes } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(processorIndexes('pre')).toEqual([])
      expect(processorIndexes('post')).toEqual([])
    })
  })

  describe('addProcessor', () => {
    it('添加指定类型处理器并选中', async () => {
      const { editProcessors, addProcessor, selectedProcessorIdx } = await createWithProcessors()
      addProcessor('pre')
      expect(editProcessors.value).toHaveLength(1)
      expect(editProcessors.value[0].type).toBe('pre')
      expect(selectedProcessorIdx.value).toBe(0)
    })

    it('新处理器包含默认字段', async () => {
      const { editProcessors, addProcessor } = await createWithProcessors()
      addProcessor('post')
      const el = editProcessors.value[0]
      expect(el.testclass).toBe('http')
      expect(el.config).toEqual({})
      expect(el.extractors).toEqual([])
      expect(el.enabled).toBe(true)
    })

    it('追加后选中最后一个', async () => {
      const { addProcessor, selectedProcessorIdx } = await createWithProcessors(makeProcessor('pre'))
      addProcessor('post')
      expect(selectedProcessorIdx.value).toBe(1)
    })
  })

  describe('removeProcessor', () => {
    it('移除指定类型和位置的处理器', async () => {
      const { removeProcessor, editProcessors } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('post'),
        makeProcessor('pre'),
      )
      removeProcessor('pre', 0)
      expect(editProcessors.value).toHaveLength(2)
    })

    it('移除选中处理器时 selectedProcessorIdx 重置为 null', async () => {
      const { removeProcessor, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('pre'),
      )
      selectedProcessorIdx.value = 0
      removeProcessor('pre', 0)
      expect(selectedProcessorIdx.value).toBeNull()
    })

    it('移除唯一处理器后选中变为 null', async () => {
      const { removeProcessor, selectedProcessorIdx } = await createWithProcessors(makeProcessor('pre'))
      selectedProcessorIdx.value = 0
      removeProcessor('pre', 0)
      expect(selectedProcessorIdx.value).toBeNull()
    })

    it('移除非选中处理器时选中不变', async () => {
      const { removeProcessor, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('pre'),
      )
      selectedProcessorIdx.value = 1
      removeProcessor('pre', 0)
      expect(selectedProcessorIdx.value).toBe(1)
    })

    it('位置越界时不操作', async () => {
      const { removeProcessor, editProcessors } = await createWithProcessors(makeProcessor('pre'))
      removeProcessor('pre', 5)
      expect(editProcessors.value).toHaveLength(1)
    })
  })

  describe('updateProcessor', () => {
    it('替换指定索引的处理器', async () => {
      const { updateProcessor, editProcessors } = await createWithProcessors(makeProcessor('pre'))
      updateProcessor(0, { type: 'pre', name: 'updated', enabled: false })
      expect(editProcessors.value[0].name).toBe('updated')
      expect(editProcessors.value[0].enabled).toBe(false)
    })
  })

  describe('selectedProcessorEl', () => {
    it('selectedProcessorIdx 为 null 时返回 null', async () => {
      const { selectedProcessorEl } = await createWithProcessors(makeProcessor('pre'))
      expect(selectedProcessorEl.value).toBeNull()
    })

    it('选中索引有效时返回对应元素', async () => {
      const { selectedProcessorEl, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('post'),
      )
      selectedProcessorIdx.value = 1
      expect(selectedProcessorEl.value?.type).toBe('post')
    })

    it('选中索引超出范围时返回 null', async () => {
      const { selectedProcessorEl, selectedProcessorIdx } = await createWithProcessors(makeProcessor('pre'))
      selectedProcessorIdx.value = 5
      expect(selectedProcessorEl.value).toBeNull()
    })
  })

  describe('httpRefSelectOptions', () => {
    it('映射 http 配置为下拉选项', () => {
      const httpRefOptions = ref([makeHttpConfig('h1', '配置一'), makeHttpConfig('h2', '配置二')])
      const { httpRefSelectOptions } = useSceneProcessors(ref(null), httpRefOptions, ref([]), ref('steps'))
      expect(httpRefSelectOptions.value).toEqual([
        { value: 'h1', label: '配置一（h1）' },
        { value: 'h2', label: '配置二（h2）' },
      ])
    })

    it('refName 为空时标签只显示 name', () => {
      const httpRefOptions = ref([{ name: '仅名称' } as ApiHttpConfig])
      const { httpRefSelectOptions } = useSceneProcessors(ref(null), httpRefOptions, ref([]), ref('steps'))
      expect(httpRefSelectOptions.value[0].label).toBe('仅名称')
    })

    it('空列表返回空数组', () => {
      const { httpRefSelectOptions } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(httpRefSelectOptions.value).toEqual([])
    })
  })

  describe('dsRefSelectOptions', () => {
    it('映射数据源为下拉选项', () => {
      const dsRefOptions = ref([makeDs('d1', 'MySQL'), makeDs('d2', 'PostgreSQL')])
      const { dsRefSelectOptions } = useSceneProcessors(ref(null), ref([]), dsRefOptions, ref('steps'))
      expect(dsRefSelectOptions.value).toEqual([
        { value: 'd1', label: 'MySQL（d1）' },
        { value: 'd2', label: 'PostgreSQL（d2）' },
      ])
    })

    it('refName 为空时标签只显示 name', () => {
      const dsRefOptions = ref([{ name: '无ref' } as ApiDataSource])
      const { dsRefSelectOptions } = useSceneProcessors(ref(null), ref([]), dsRefOptions, ref('steps'))
      expect(dsRefSelectOptions.value[0].label).toBe('无ref')
    })
  })

  describe('procHttpRef', () => {
    it('get: 返回当前选中 http 处理器的 ref', async () => {
      const { procHttpRef, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'http', config: { ref: 'h1' } }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      expect(procHttpRef.value).toBe('h1')
    })

    it('get: 无选中元素时返回空字符串', () => {
      const { procHttpRef } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(procHttpRef.value).toBe('')
    })

    it('get: 非 http 类型返回空字符串', async () => {
      const { procHttpRef, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'jdbc', config: { ref: 'h1' } }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      expect(procHttpRef.value).toBe('')
    })

    it('get: config 非 record 时返回空字符串', async () => {
      const { procHttpRef, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'http', config: 'bad' }),
      )
      mocks.isRecord.mockReturnValue(false)
      selectedProcessorIdx.value = 0
      await nextTick()
      expect(procHttpRef.value).toBe('')
    })

    it('get: ref 非 string 时返回空字符串', async () => {
      const { procHttpRef, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'http', config: { ref: 123 } }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      expect(procHttpRef.value).toBe('')
    })

    it('set: 更新选中 http 处理器的 config.ref', async () => {
      const { procHttpRef, selectedProcessorIdx, editProcessors } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'http', config: {} }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      procHttpRef.value = 'h2'
      expect(editProcessors.value[0].config).toEqual({ ref: 'h2' })
    })

    it('set: 无选中元素时不报错', async () => {
      const { procHttpRef } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(() => { procHttpRef.value = 'h1' }).not.toThrow()
    })

    it('set: 非 http 类型不修改', async () => {
      const { procHttpRef, selectedProcessorIdx, editProcessors } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'jdbc', config: {} }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      procHttpRef.value = 'h2'
      expect(editProcessors.value[0].config).toEqual({})
    })

    it('set: 合并已有 config', async () => {
      const { procHttpRef, selectedProcessorIdx, editProcessors } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'http', config: { method: 'GET' } }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      procHttpRef.value = 'h3'
      expect(editProcessors.value[0].config).toEqual({ method: 'GET', ref: 'h3' })
    })
  })

  describe('procDsRef', () => {
    it('get: 返回当前选中 jdbc 处理器的 datasource', async () => {
      const { procDsRef, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'jdbc', config: { datasource: 'd1' } }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      expect(procDsRef.value).toBe('d1')
    })

    it('get: 无选中元素时返回空字符串', () => {
      const { procDsRef } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(procDsRef.value).toBe('')
    })

    it('get: 非 jdbc 类型返回空字符串', async () => {
      const { procDsRef, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'http', config: { datasource: 'd1' } }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      expect(procDsRef.value).toBe('')
    })

    it('set: 更新选中 jdbc 处理器的 config.datasource', async () => {
      const { procDsRef, selectedProcessorIdx, editProcessors } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'jdbc', config: {} }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      procDsRef.value = 'd2'
      expect(editProcessors.value[0].config).toEqual({ datasource: 'd2' })
    })

    it('set: 无选中元素时不报错', async () => {
      const { procDsRef } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(() => { procDsRef.value = 'd1' }).not.toThrow()
    })

    it('set: 非 jdbc 类型不修改', async () => {
      const { procDsRef, selectedProcessorIdx, editProcessors } = await createWithProcessors(
        makeProcessor('pre', { testclass: 'http', config: {} }),
      )
      selectedProcessorIdx.value = 0
      await nextTick()
      procDsRef.value = 'd2'
      expect(editProcessors.value[0].config).toEqual({})
    })
  })

  describe('procTags', () => {
    it('包含 testclass 大写标签', async () => {
      const { procTags } = await createWithProcessors(makeProcessor('pre', { testclass: 'http' }))
      const tags = procTags(0)
      expect(tags.some((t) => t.text === 'HTTP' && t.type === 'info')).toBe(true)
    })

    it('无 testclass 时不添加 class 标签', async () => {
      const { procTags } = await createWithProcessors(makeProcessor('pre', { testclass: '' }))
      const tags = procTags(0)
      expect(tags.some((t) => t.text === 'HTTP')).toBe(false)
    })

    it('processorSummaryTag 返回非 null 时添加摘要标签', async () => {
      mocks.processorSummaryTag.mockReturnValue({ text: 'GET', type: 'success' })
      const { procTags } = await createWithProcessors(makeProcessor('pre'))
      const tags = procTags(0)
      expect(tags).toHaveLength(2)
      expect(tags[1]).toEqual({ text: 'GET', type: 'success' })
    })

    it('processorSummaryTag 返回 null 时不添加摘要标签', async () => {
      mocks.processorSummaryTag.mockReturnValue(null)
      const { procTags } = await createWithProcessors(makeProcessor('pre'))
      const tags = procTags(0)
      expect(tags).toHaveLength(1)
    })

    it('索引越界返回空数组', async () => {
      const { procTags } = await createWithProcessors()
      expect(procTags(5)).toEqual([])
    })
  })

  describe('procDisplayName', () => {
    it('有名称时返回名称', async () => {
      const { procDisplayName } = await createWithProcessors(makeProcessor('pre', { name: '我的处理器' }))
      expect(procDisplayName(0)).toBe('我的处理器')
    })

    it('名称为空白时返回默认显示名', async () => {
      const { procDisplayName } = await createWithProcessors(makeProcessor('pre', { name: '   ' }))
      expect(procDisplayName(0)).toBe('处理器 1')
    })

    it('无名称时返回默认显示名', async () => {
      const { procDisplayName } = await createWithProcessors(makeProcessor('pre', { name: '' }))
      expect(procDisplayName(0)).toBe('处理器 1')
    })

    it('多处理器时显示对应序号', async () => {
      const { procDisplayName } = await createWithProcessors(
        makeProcessor('pre', { name: '' }),
        makeProcessor('post', { name: '' }),
      )
      expect(procDisplayName(0)).toBe('处理器 1')
      expect(procDisplayName(1)).toBe('处理器 2')
    })
  })

  describe('selectProcessor', () => {
    it('设置 selectedProcessorIdx', async () => {
      const { selectProcessor, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('post'),
      )
      selectProcessor(1)
      expect(selectedProcessorIdx.value).toBe(1)
    })
  })

  describe('setProcessorType', () => {
    it('更新处理器的 testclass', async () => {
      const { setProcessorType, editProcessors } = await createWithProcessors(makeProcessor('pre'))
      setProcessorType(0, 'jdbc')
      expect(editProcessors.value[0].testclass).toBe('jdbc')
    })

    it('索引越界时不报错', async () => {
      const { setProcessorType } = await createWithProcessors()
      expect(() => setProcessorType(5, 'http')).not.toThrow()
    })
  })

  describe('moveProcessor', () => {
    it('向上移动处理器', async () => {
      const { moveProcessor, editProcessors, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('pre'),
        makeProcessor('post'),
      )
      moveProcessor('pre', 1, -1)
      expect(editProcessors.value[0].type).toBe('pre')
      expect(editProcessors.value[1].type).toBe('pre')
      expect(selectedProcessorIdx.value).toBe(0)
    })

    it('向下移动处理器', async () => {
      const { moveProcessor, editProcessors, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('pre'),
        makeProcessor('post'),
      )
      moveProcessor('pre', 0, 1)
      expect(editProcessors.value[0].type).toBe('pre')
      expect(editProcessors.value[1].type).toBe('pre')
      expect(selectedProcessorIdx.value).toBe(1)
    })

    it('从第一个向上移动时不操作', async () => {
      const { moveProcessor, editProcessors } = await createWithProcessors(makeProcessor('pre'))
      moveProcessor('pre', 0, -1)
      expect(editProcessors.value).toHaveLength(1)
    })

    it('从最后一个向下移动时不操作', async () => {
      const { moveProcessor, editProcessors } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('pre'),
      )
      moveProcessor('pre', 1, 1)
      expect(editProcessors.value[1].type).toBe('pre')
    })
  })

  describe('procOnDragStart', () => {
    it('设置 procDrag 状态', () => {
      const { procOnDragStart, procDrag } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      const e = { dataTransfer: { effectAllowed: '' } } as unknown as DragEvent
      procOnDragStart('pre', 0, e)
      expect(procDrag.value).toEqual({ type: 'pre', flat: 0 })
    })

    it('设置 dataTransfer.effectAllowed', () => {
      const { procOnDragStart } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      const dataTransfer = { effectAllowed: '' }
      const e = { dataTransfer } as unknown as DragEvent
      procOnDragStart('post', 1, e)
      expect(dataTransfer.effectAllowed).toBe('move')
    })

    it('无 dataTransfer 时不报错', () => {
      const { procOnDragStart } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      const e = {} as unknown as DragEvent
      expect(() => procOnDragStart('pre', 0, e)).not.toThrow()
    })
  })

  describe('procOnDragOver', () => {
    it('阻止默认行为并设置 dropEffect', () => {
      const { procOnDragOver } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      const dataTransfer = { dropEffect: '' }
      const e = { preventDefault: vi.fn(), dataTransfer } as unknown as DragEvent
      procOnDragOver(e)
      expect(e.preventDefault).toHaveBeenCalled()
      expect(dataTransfer.dropEffect).toBe('move')
    })
  })

  describe('procOnDrop', () => {
    it('无拖拽状态时返回', () => {
      const { procOnDrop } = useSceneProcessors(ref(null), ref([]), ref([]), ref('steps'))
      expect(() => procOnDrop('pre', 0)).not.toThrow()
    })

    it('类型不匹配时返回', async () => {
      const { procOnDragStart, procOnDrop, editProcessors } = await createWithProcessors(makeProcessor('pre'))
      const e1 = { dataTransfer: { effectAllowed: '' } } as unknown as DragEvent
      procOnDragStart('pre', 0, e1)
      procOnDrop('post', 0)
      expect(editProcessors.value).toHaveLength(1)
    })

    it('同位置拖放不操作', async () => {
      const { procOnDragStart, procOnDrop, editProcessors } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('pre'),
      )
      const e = { dataTransfer: { effectAllowed: '' } } as unknown as DragEvent
      procOnDragStart('pre', 0, e)
      procOnDrop('pre', 0)
      expect(editProcessors.value[0].type).toBe('pre')
      expect(editProcessors.value[1].type).toBe('pre')
    })

    it('拖放后更新 selectedProcessorIdx', async () => {
      const { procOnDragStart, procOnDrop, selectedProcessorIdx } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('post'),
        makeProcessor('pre'),
      )
      const e = { dataTransfer: { effectAllowed: '' } } as unknown as DragEvent
      procOnDragStart('pre', 0, e)
      procOnDrop('pre', 1)
      expect(selectedProcessorIdx.value).toBe(2)
    })

    it('拖放后 procDrag 清空', async () => {
      const { procOnDragStart, procOnDrop, procDrag } = await createWithProcessors(
        makeProcessor('pre'),
        makeProcessor('pre'),
      )
      const e = { dataTransfer: { effectAllowed: '' } } as unknown as DragEvent
      procOnDragStart('pre', 0, e)
      procOnDrop('pre', 1)
      expect(procDrag.value).toBeNull()
    })
  })

  describe('copyProcessor', () => {
    it('在指定索引后插入副本', async () => {
      const { copyProcessor, editProcessors } = await createWithProcessors(
        makeProcessor('pre', { name: '原始' }),
      )
      copyProcessor(0)
      expect(editProcessors.value).toHaveLength(2)
      expect(editProcessors.value[0].name).toBe('原始')
      expect(editProcessors.value[1].name).toBe('原始')
    })

    it('副本是深拷贝而非引用', async () => {
      const { copyProcessor, editProcessors } = await createWithProcessors(
        makeProcessor('pre', { name: '原始', config: { nested: true } }),
      )
      copyProcessor(0)
      editProcessors.value[0].name = '改了'
      expect(editProcessors.value[1].name).toBe('原始')
    })

    it('复制后选中副本', async () => {
      const { copyProcessor, selectedProcessorIdx } = await createWithProcessors(makeProcessor('pre'))
      copyProcessor(0)
      expect(selectedProcessorIdx.value).toBe(1)
    })
  })

  describe('sceneSection watcher', () => {
    it('切换到 pre 区域时自动选中第一个 pre 处理器', async () => {
      const sceneSection = ref<'steps' | 'variables' | 'pre' | 'post'>('steps')
      const detail = ref<ApiSceneDetail | null>(null)
      const { selectedProcessorIdx } = useSceneProcessors(detail, ref([]), ref([]), sceneSection)
      detail.value = makeDetail([makeProcessor('pre'), makeProcessor('post')])
      await nextTick()
      sceneSection.value = 'pre'
      await nextTick()
      expect(selectedProcessorIdx.value).toBe(0)
    })

    it('切换到 post 区域时自动选中第一个 post 处理器', async () => {
      const sceneSection = ref<'steps' | 'variables' | 'pre' | 'post'>('steps')
      const detail = ref<ApiSceneDetail | null>(null)
      const { selectedProcessorIdx } = useSceneProcessors(detail, ref([]), ref([]), sceneSection)
      detail.value = makeDetail([makeProcessor('pre'), makeProcessor('post')])
      await nextTick()
      sceneSection.value = 'post'
      await nextTick()
      expect(selectedProcessorIdx.value).toBe(1)
    })

    it('区域无处理器时 selectedProcessorIdx 为 null', async () => {
      const sceneSection = ref<'steps' | 'variables' | 'pre' | 'post'>('steps')
      const detail = ref<ApiSceneDetail | null>(null)
      const { selectedProcessorIdx } = useSceneProcessors(detail, ref([]), ref([]), sceneSection)
      detail.value = makeDetail([makeProcessor('pre')])
      await nextTick()
      sceneSection.value = 'post'
      await nextTick()
      expect(selectedProcessorIdx.value).toBeNull()
    })

    it('切换到 steps/variables 时不改变 selectedProcessorIdx', async () => {
      const sceneSection = ref<'steps' | 'variables' | 'pre' | 'post'>('steps')
      const detail = ref<ApiSceneDetail | null>(null)
      const { selectedProcessorIdx } = useSceneProcessors(detail, ref([]), ref([]), sceneSection)
      detail.value = makeDetail([makeProcessor('pre')])
      await nextTick()
      selectedProcessorIdx.value = 0
      sceneSection.value = 'variables'
      await nextTick()
      expect(selectedProcessorIdx.value).toBe(0)
    })

    it('当前选中已在目标区域内时保持不变', async () => {
      const sceneSection = ref<'steps' | 'variables' | 'pre' | 'post'>('pre')
      const detail = ref<ApiSceneDetail | null>(null)
      const { selectedProcessorIdx } = useSceneProcessors(detail, ref([]), ref([]), sceneSection)
      detail.value = makeDetail([makeProcessor('pre'), makeProcessor('pre')])
      await nextTick()
      selectedProcessorIdx.value = 1
      sceneSection.value = 'variables'
      await nextTick()
      sceneSection.value = 'pre'
      await nextTick()
      expect(selectedProcessorIdx.value).toBe(1)
    })
  })
})
