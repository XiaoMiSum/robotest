import { ref, watch, computed, type Ref } from 'vue'
import type { ApiSceneDetail, ApiHttpConfig, ApiDataSource } from '@/types'
import { isRecord, processorSummaryTag } from '@/components/api-testing/processorFormModel'

export type SceneProcessorElement = Record<string, unknown> & {
  name?: string
  enabled?: boolean
}

/**
 * 场景处理器管理 + 拖拽排序（从 SceneEditorPage 提取）。
 * 依赖 detail、httpRefOptions、dsRefOptions、sceneSection。
 */
export function useSceneProcessors(
  detail: Ref<ApiSceneDetail | null>,
  httpRefOptions: Ref<ApiHttpConfig[]>,
  dsRefOptions: Ref<ApiDataSource[]>,
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

  const httpRefSelectOptions = computed(() =>
    httpRefOptions.value.map((hc) => ({
      value: String(hc.refName ?? ''),
      label: hc.refName ? `${hc.name}（${hc.refName}）` : hc.name,
    })),
  )
  const dsRefSelectOptions = computed(() =>
    dsRefOptions.value.map((ds) => ({
      value: String(ds.refName ?? ''),
      label: ds.refName ? `${ds.name}（${ds.refName}）` : ds.name,
    })),
  )

  const procHttpRef = computed({
    get: () => {
      const el = selectedProcessorEl.value
      if (!el || el.testclass !== 'http' || !isRecord(el.config)) return ''
      return typeof el.config.ref === 'string' ? el.config.ref : ''
    },
    set: (value: string) => {
      const el = selectedProcessorEl.value
      if (!el || el.testclass !== 'http') return
      el.config = { ...(isRecord(el.config) ? el.config : {}), ref: value }
    },
  })

  const procDsRef = computed({
    get: () => {
      const el = selectedProcessorEl.value
      if (!el || el.testclass !== 'jdbc' || !isRecord(el.config)) return ''
      return typeof el.config.datasource === 'string' ? el.config.datasource : ''
    },
    set: (value: string) => {
      const el = selectedProcessorEl.value
      if (!el || el.testclass !== 'jdbc') return
      el.config = { ...(isRecord(el.config) ? el.config : {}), datasource: value }
    },
  })

  function procTags(idx: number): { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[] {
    const el = editProcessors.value[idx] as SceneProcessorElement | undefined
    const tags: { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[] = []
    const klass = typeof el?.testclass === 'string' ? el.testclass : ''
    if (klass) tags.push({ text: klass.toUpperCase(), type: 'info' })
    const summary = processorSummaryTag(el as Record<string, unknown> | null | undefined)
    if (summary) tags.push(summary)
    return tags
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

  function setProcessorType(idx: number, testclass: string) {
    const el = editProcessors.value[idx] as SceneProcessorElement | undefined
    if (!el) return
    el.testclass = testclass
  }

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
    httpRefSelectOptions,
    dsRefSelectOptions,
    procHttpRef,
    procDsRef,
    procTags,
    procDisplayName,
    processorIndexes,
    addProcessor,
    removeProcessor,
    updateProcessor,
    selectProcessor,
    setProcessorType,
    moveProcessor,
    procDrag,
    procOnDragStart,
    procOnDragOver,
    procOnDrop,
    copyProcessor,
  }
}
