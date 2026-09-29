import { ref, watch, computed, type Ref } from 'vue'
import type { ApiSceneDetail } from '@/types'
import { processorTags } from '@/composables/project/api-testing/processorFormModel'

export type SceneProcessorElement = Record<string, unknown> & {
  name?: string
  enabled?: boolean
}

/**
 * 场景处理器管理 + 拖拽排序（从 SceneEditorPage 提取）。
 * 依赖 detail、sceneSection；类型切换与引用选择由 ProcessorConfigEditor 承载。
 */
export function useSceneProcessors(
  detail: Ref<ApiSceneDetail | null>,
  sceneSection: Ref<'steps' | 'variables' | 'pre' | 'post'>,
) {
  const editProcessors = ref<SceneProcessorElement[]>([] as SceneProcessorElement[])
  const selectedProcessorIdx = ref<number | null>(null)

  watch(detail, (d) => {
    if (d?.processors) {
      editProcessors.value = d.processors.map((p) => ({ ...(p as SceneProcessorElement) }))
      const cur = selectedProcessorIdx.value
      if (cur === null || cur >= editProcessors.value.length) selectedProcessorIdx.value = null
    }
  })

  /** 前置/后置处理器在 editProcessors 扁平数组中的下标 */
  function processorIndexes(type: 'pre' | 'post'): number[] {
    return editProcessors.value
      .map((p, i) => ((p.type === 'post') === (type === 'post') ? i : -1))
      .filter((i) => i >= 0)
  }

  function addProcessor(type: 'pre' | 'post') {
    editProcessors.value.push({ type, name: '', enabled: true, testclass: 'http', config: {}, extractors: [] })
    selectedProcessorIdx.value = editProcessors.value.length - 1
  }

  function removeProcessor(type: 'pre' | 'post', position: number) {
    const idx = processorIndexes(type)[position]
    if (idx >= 0) {
      editProcessors.value.splice(idx, 1)
      if (selectedProcessorIdx.value === idx) selectedProcessorIdx.value = null
    }
  }

  function updateProcessor(idx: number, value: Record<string, unknown>) {
    editProcessors.value[idx] = value as SceneProcessorElement
  }

  const selectedProcessorEl = computed<SceneProcessorElement | null>(
    () => (selectedProcessorIdx.value === null ? null : (editProcessors.value[selectedProcessorIdx.value] ?? null)),
  )

  function procTags(idx: number): { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[] {
    const el = editProcessors.value[idx] as SceneProcessorElement | undefined
    if (!el) return []
    return processorTags(el)
  }

  function procDisplayName(idx: number): string {
    const name = (editProcessors.value[idx] as SceneProcessorElement).name
    return name && name.trim() ? name : `处理器 ${idx + 1}`
  }

  function selectProcessor(idx: number) {
    selectedProcessorIdx.value = idx
  }

  watch(sceneSection, (section) => {
    if (section !== 'pre' && section !== 'post') return
    const list = processorIndexes(section)
    const cur = selectedProcessorIdx.value
    if (cur === null || !list.includes(cur)) {
      selectedProcessorIdx.value = list.length > 0 ? list[0] : null
    }
  })

  function moveProcessor(type: 'pre' | 'post', position: number, dir: -1 | 1) {
    const list = processorIndexes(type)
    const from = list[position]
    const to = list[position + dir]
    if (from === undefined || to === undefined) return
    const arr = editProcessors.value
    ;[arr[from], arr[to]] = [arr[to], arr[from]]
    selectedProcessorIdx.value = to
  }

  // ==================== 拖拽排序 ====================
  const procDrag = ref<{ type: 'pre' | 'post'; flat: number } | null>(null)

  function procOnDragStart(type: 'pre' | 'post', flat: number, e: DragEvent) {
    procDrag.value = { type, flat }
    if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
  }

  function procOnDragOver(e: DragEvent) {
    e.preventDefault()
    if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  }

  function procOnDrop(type: 'pre' | 'post', position: number) {
    const drag = procDrag.value
    procDrag.value = null
    if (!drag || drag.type !== type) return
    const list = processorIndexes(type)
    const fromPos = list.indexOf(drag.flat)
    if (fromPos === -1 || fromPos === position) return
    const arr = editProcessors.value
    const moved = arr[drag.flat]
    arr.splice(drag.flat, 1)
    const newList = processorIndexes(type)
    const insertAt = position >= newList.length ? newList[newList.length - 1] + 1 : newList[position]
    arr.splice(insertAt, 0, moved)
    selectedProcessorIdx.value = arr.indexOf(moved)
  }

  function copyProcessor(idx: number) {
    const copy = JSON.parse(JSON.stringify(editProcessors.value[idx])) as SceneProcessorElement
    editProcessors.value.splice(idx + 1, 0, copy)
    selectedProcessorIdx.value = idx + 1
  }

  return {
    editProcessors,
    selectedProcessorIdx,
    selectedProcessorEl,
    procTags,
    procDisplayName,
    processorIndexes,
    addProcessor,
    removeProcessor,
    updateProcessor,
    selectProcessor,
    moveProcessor,
    procDrag,
    procOnDragStart,
    procOnDragOver,
    procOnDrop,
    copyProcessor,
  }
}
