import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchComponents } from '@/services/project/component'
import { extractorsFromComponents, processorFromComponent } from '@/components/api-testing/processorFormModel'
import type { ApiComponentListItem, ApiComponentType } from '@/types'
import type { SceneProcessorElement } from './useSceneProcessors'

type AssetKind = 'pre' | 'post' | 'extractor'

const ASSET_TYPE: Record<AssetKind, ApiComponentType> = {
  pre: 'preprocessor',
  post: 'postprocessor',
  extractor: 'extractor',
}

const ASSET_TITLE: Record<AssetKind, string> = {
  pre: '从公共组件引入前置处理器',
  post: '从公共组件引入后置处理器',
  extractor: '从公共组件引入提取器',
}

const ASSET_NAME: Record<AssetKind, string> = {
  pre: '处理器',
  post: '处理器',
  extractor: '提取器',
}

/**
 * 处理器/提取器公共组件引入（从 SceneEditorPage 提取）。
 * 依赖 editProcessors ref，接收并修改处理器数组。
 */
export function useAssetPicker(editProcessors: Ref<SceneProcessorElement[]>) {
  const assetPickerVisible = ref(false)
  const assetPickerLoading = ref(false)
  const assetPickerItems = ref<ApiComponentListItem[]>([])
  const assetPickerKeyword = ref('')
  const assetPickerKind = ref<AssetKind>('pre')
  const assetTargetIdx = ref<number | null>(null)

  async function loadAssetPicker(): Promise<void> {
    assetPickerLoading.value = true
    try {
      const resp = await fetchComponents({ type: ASSET_TYPE[assetPickerKind.value], pageNo: 1, pageSize: 200 })
      assetPickerItems.value = resp.list
    } catch { /* 静默 */ } finally { assetPickerLoading.value = false }
  }

  function openAssetPicker(kind: AssetKind) {
    assetPickerKind.value = kind
    assetPickerKeyword.value = ''
    assetTargetIdx.value = null
    assetPickerVisible.value = true
    void loadAssetPicker()
  }

  function openExtractorPickerForProcessor(idx: number) {
    assetPickerKind.value = 'extractor'
    assetPickerKeyword.value = ''
    assetTargetIdx.value = idx
    assetPickerVisible.value = true
    void loadAssetPicker()
  }

  function handleAssetPicked(rows: ApiComponentListItem[]) {
    const kind = assetPickerKind.value
    const targetIdx = assetTargetIdx.value
    if (kind === 'extractor' && targetIdx !== null) {
      const existing = editProcessors.value[targetIdx]
      if (!existing) return
      const arr = (Array.isArray(existing.extractors) ? existing.extractors : []) as Record<string, unknown>[]
      existing.extractors = [...arr, ...extractorsFromComponents(rows)]
    } else if (kind === 'pre' || kind === 'post') {
      rows.forEach((r) => editProcessors.value.push(processorFromComponent(r, kind) as SceneProcessorElement))
    }
    assetPickerVisible.value = false
    ElMessage.success(`已引入 ${rows.length} 个${ASSET_NAME[kind]}`)
  }

  return {
    assetPickerVisible,
    assetPickerLoading,
    assetPickerItems,
    assetPickerKeyword,
    assetPickerKind,
    assetTargetIdx,
    ASSET_TITLE,
    loadAssetPicker,
    openAssetPicker,
    openExtractorPickerForProcessor,
    handleAssetPicked,
  }
}
