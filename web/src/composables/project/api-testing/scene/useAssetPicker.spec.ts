import { ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiComponentListItem } from '@/types'
import type { SceneProcessorElement } from './useSceneProcessors'

const mocks = vi.hoisted(() => ({
  fetchComponents: vi.fn<(params?: { type: string; pageNo: number; pageSize: number }) => Promise<{ list: ApiComponentListItem[] }>>(),
  extractorsFromComponents: vi.fn<(items: ApiComponentListItem[]) => Record<string, unknown>[]>(),
  processorFromComponent: vi.fn<(item: ApiComponentListItem, type: string) => Record<string, unknown>>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/services/project/component', () => ({
  fetchComponents: mocks.fetchComponents,
}))

vi.mock('@/components/api-testing/processorFormModel', () => ({
  extractorsFromComponents: mocks.extractorsFromComponents,
  processorFromComponent: mocks.processorFromComponent,
}))

import { useAssetPicker } from './useAssetPicker'

function makeItem(id: string, overrides?: Partial<ApiComponentListItem>): ApiComponentListItem {
  return {
    id,
    scope: 'global',
    type: 'preprocessor',
    name: `组件 ${id}`,
    description: null,
    sortOrder: 0,
    config: null,
    enabled: true,
    updatedAt: '2025-01-01T00:00:00',
    ...overrides,
  }
}

describe('useAssetPicker', () => {
  let editProcessors: Ref<SceneProcessorElement[]>

  beforeEach(() => {
    vi.clearAllMocks()
    editProcessors = ref<SceneProcessorElement[]>([]) as Ref<SceneProcessorElement[]>
    mocks.fetchComponents.mockResolvedValue({ list: [] })
    mocks.extractorsFromComponents.mockReturnValue([])
    mocks.processorFromComponent.mockImplementation((item, type) => ({
      name: item.name,
      type,
    }))
  })

  describe('初始状态', () => {
    it('assetPickerVisible 初始为 false', () => {
      const { assetPickerVisible } = useAssetPicker(editProcessors)
      expect(assetPickerVisible.value).toBe(false)
    })

    it('assetPickerLoading 初始为 false', () => {
      const { assetPickerLoading } = useAssetPicker(editProcessors)
      expect(assetPickerLoading.value).toBe(false)
    })

    it('assetPickerItems 初始为空数组', () => {
      const { assetPickerItems } = useAssetPicker(editProcessors)
      expect(assetPickerItems.value).toEqual([])
    })

    it('assetPickerKeyword 初始为空字符串', () => {
      const { assetPickerKeyword } = useAssetPicker(editProcessors)
      expect(assetPickerKeyword.value).toBe('')
    })

    it('assetPickerKind 初始为 pre', () => {
      const { assetPickerKind } = useAssetPicker(editProcessors)
      expect(assetPickerKind.value).toBe('pre')
    })

    it('assetTargetIdx 初始为 null', () => {
      const { assetTargetIdx } = useAssetPicker(editProcessors)
      expect(assetTargetIdx.value).toBeNull()
    })
  })

  describe('ASSET_TITLE', () => {
    it('包含 pre/post/extractor 三个 key', () => {
      const { ASSET_TITLE } = useAssetPicker(editProcessors)
      expect(ASSET_TITLE).toHaveProperty('pre')
      expect(ASSET_TITLE).toHaveProperty('post')
      expect(ASSET_TITLE).toHaveProperty('extractor')
    })
  })

  describe('loadAssetPicker', () => {
    it('设置 loading 为 true 后调用 fetchComponents', async () => {
      const { loadAssetPicker, assetPickerLoading } = useAssetPicker(editProcessors)
      const promise = loadAssetPicker()
      expect(assetPickerLoading.value).toBe(true)
      await promise
      expect(assetPickerLoading.value).toBe(false)
    })

    it('使用 preprocessor 类型调用 fetchComponents', async () => {
      const { loadAssetPicker, assetPickerKind } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'pre'
      await loadAssetPicker()
      expect(mocks.fetchComponents).toHaveBeenCalledWith({ type: 'preprocessor', pageNo: 1, pageSize: 200 })
    })

    it('使用 postprocessor 类型调用 fetchComponents', async () => {
      const { loadAssetPicker, assetPickerKind } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'post'
      await loadAssetPicker()
      expect(mocks.fetchComponents).toHaveBeenCalledWith({ type: 'postprocessor', pageNo: 1, pageSize: 200 })
    })

    it('使用 extractor 类型调用 fetchComponents', async () => {
      const { loadAssetPicker, assetPickerKind } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'extractor'
      await loadAssetPicker()
      expect(mocks.fetchComponents).toHaveBeenCalledWith({ type: 'extractor', pageNo: 1, pageSize: 200 })
    })

    it('成功时将结果写入 assetPickerItems', async () => {
      const items = [makeItem('1'), makeItem('2')]
      mocks.fetchComponents.mockResolvedValue({ list: items })
      const { loadAssetPicker, assetPickerItems } = useAssetPicker(editProcessors)
      await loadAssetPicker()
      expect(assetPickerItems.value).toEqual(items)
    })

    it('异常时静默处理，loading 仍被重置', async () => {
      mocks.fetchComponents.mockRejectedValue(new Error('network'))
      const { loadAssetPicker, assetPickerLoading, assetPickerItems } = useAssetPicker(editProcessors)
      await loadAssetPicker()
      expect(assetPickerLoading.value).toBe(false)
      expect(assetPickerItems.value).toEqual([])
    })
  })

  describe('openAssetPicker', () => {
    it('设置 kind 并清空 keyword 和 targetIdx', () => {
      const { openAssetPicker, assetPickerKind, assetPickerKeyword, assetTargetIdx } = useAssetPicker(editProcessors)
      openAssetPicker('post')
      expect(assetPickerKind.value).toBe('post')
      expect(assetPickerKeyword.value).toBe('')
      expect(assetTargetIdx.value).toBeNull()
    })

    it('设置 assetPickerVisible 为 true', () => {
      const { openAssetPicker, assetPickerVisible } = useAssetPicker(editProcessors)
      openAssetPicker('pre')
      expect(assetPickerVisible.value).toBe(true)
    })

    it('触发 loadAssetPicker', async () => {
      const { openAssetPicker } = useAssetPicker(editProcessors)
      openAssetPicker('pre')
      await vi.waitFor(() => {
        expect(mocks.fetchComponents).toHaveBeenCalled()
      })
    })
  })

  describe('openExtractorPickerForProcessor', () => {
    it('设置 kind 为 extractor 并记录 targetIdx', () => {
      const { openExtractorPickerForProcessor, assetPickerKind, assetTargetIdx } = useAssetPicker(editProcessors)
      openExtractorPickerForProcessor(3)
      expect(assetPickerKind.value).toBe('extractor')
      expect(assetTargetIdx.value).toBe(3)
    })

    it('清空 keyword', () => {
      const { openExtractorPickerForProcessor, assetPickerKeyword } = useAssetPicker(editProcessors)
      assetPickerKeyword.value = 'old'
      openExtractorPickerForProcessor(0)
      expect(assetPickerKeyword.value).toBe('')
    })

    it('设置 assetPickerVisible 为 true', () => {
      const { openExtractorPickerForProcessor, assetPickerVisible } = useAssetPicker(editProcessors)
      openExtractorPickerForProcessor(0)
      expect(assetPickerVisible.value).toBe(true)
    })

    it('触发 loadAssetPicker', async () => {
      const { openExtractorPickerForProcessor } = useAssetPicker(editProcessors)
      openExtractorPickerForProcessor(0)
      await vi.waitFor(() => {
        expect(mocks.fetchComponents).toHaveBeenCalled()
      })
    })
  })

  describe('handleAssetPicked', () => {
    it('pre 模式下将处理器推入 editProcessors', () => {
      const rows = [makeItem('1'), makeItem('2')]
      mocks.processorFromComponent
        .mockReturnValueOnce({ name: 'p1' })
        .mockReturnValueOnce({ name: 'p2' })
      const { handleAssetPicked, assetPickerKind } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'pre'
      handleAssetPicked(rows)
      expect(editProcessors.value).toHaveLength(2)
      expect(mocks.processorFromComponent).toHaveBeenCalledWith(rows[0], 'pre')
      expect(mocks.processorFromComponent).toHaveBeenCalledWith(rows[1], 'pre')
    })

    it('post 模式下将处理器推入 editProcessors', () => {
      const rows = [makeItem('1')]
      mocks.processorFromComponent.mockReturnValue({ name: 'p1' })
      const { handleAssetPicked, assetPickerKind } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'post'
      handleAssetPicked(rows)
      expect(editProcessors.value).toHaveLength(1)
      expect(mocks.processorFromComponent).toHaveBeenCalledWith(rows[0], 'post')
    })

    it('extractor 模式下将提取器追加到指定处理器', () => {
      editProcessors.value = [{ name: 'existing', extractors: [] }]
      const rows = [makeItem('1')]
      mocks.extractorsFromComponents.mockReturnValue([{ name: 'ext1' }])
      const { handleAssetPicked, assetPickerKind, assetTargetIdx } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'extractor'
      assetTargetIdx.value = 0
      handleAssetPicked(rows)
      expect(editProcessors.value[0].extractors).toEqual([{ name: 'ext1' }])
    })

    it('extractor 模式下目标处理器无 extractors 时初始化为空数组再合并', () => {
      editProcessors.value = [{ name: 'existing' }]
      const rows = [makeItem('1')]
      mocks.extractorsFromComponents.mockReturnValue([{ name: 'ext1' }])
      const { handleAssetPicked, assetPickerKind, assetTargetIdx } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'extractor'
      assetTargetIdx.value = 0
      handleAssetPicked(rows)
      expect(editProcessors.value[0].extractors).toEqual([{ name: 'ext1' }])
    })

    it('extractor 模式下目标索引不存在时静默返回', () => {
      editProcessors.value = []
      const { handleAssetPicked, assetPickerKind, assetTargetIdx } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'extractor'
      assetTargetIdx.value = 5
      handleAssetPicked([makeItem('1')])
      expect(editProcessors.value).toHaveLength(0)
    })

    it('关闭 assetPickerVisible', () => {
      const { handleAssetPicked, assetPickerVisible, assetPickerKind } = useAssetPicker(editProcessors)
      assetPickerVisible.value = true
      assetPickerKind.value = 'pre'
      handleAssetPicked([makeItem('1')])
      expect(assetPickerVisible.value).toBe(false)
    })

    it('显示 ElMessage.success', () => {
      const { handleAssetPicked, assetPickerKind } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'pre'
      handleAssetPicked([makeItem('1'), makeItem('2')])
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已引入 2 个处理器')
    })

    it('extractor 模式显示提取器名称', () => {
      editProcessors.value = [{ name: 'existing', extractors: [] }]
      const { handleAssetPicked, assetPickerKind, assetTargetIdx } = useAssetPicker(editProcessors)
      assetPickerKind.value = 'extractor'
      assetTargetIdx.value = 0
      handleAssetPicked([makeItem('1')])
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已引入 1 个提取器')
    })
  })
})
