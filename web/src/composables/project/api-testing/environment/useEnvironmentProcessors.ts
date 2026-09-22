import { ref, computed, type Ref } from 'vue'
import { defaultProcessorConfig, processorSummaryTag, isRecord } from '@/components/api-testing/processorFormModel'
import type { ApiProcessor, ApiProcessorType } from '@/types'

export type EnvConfigForm = { id: string; refName?: string; isDefault?: boolean }
export type EnvDsForm = { id: string; refName?: string; isDefault?: boolean }

/**
 * 环境处理器管理（从 EnvironmentDetailPanel 提取）。
 * 依赖 configForms / dsForms 的 orderedForms 用于 ref 下拉选项。
 */
export function useEnvironmentProcessors(
  processorRows: Ref<ApiProcessor[]>,
  orderedConfigForms: Ref<EnvConfigForm[]>,
  orderedDsForms: Ref<EnvDsForm[]>,
  localId: () => string,
  nextSortOrder: () => number,
) {
  const activeProcId = ref('')

  function procList(type: ApiProcessorType): ApiProcessor[] {
    return processorRows.value.filter((p) => p.processorType === type)
  }

  const preProcCount = computed(() => procList('preprocessor').length)
  const postProcCount = computed(() => procList('postprocessor').length)

  const selectedProcessor = computed<ApiProcessor | null>(
    () => processorRows.value.find((p) => p.id === activeProcId.value) ?? null,
  )

  function procElement(processor: ApiProcessor): Record<string, unknown> {
    return isRecord(processor.config) ? processor.config : {}
  }

  function fixActiveProc(type: ApiProcessorType) {
    const list = procList(type)
    if (!list.some((p) => p.id === activeProcId.value)) {
      activeProcId.value = list[0]?.id ?? ''
    }
  }

  function selectProcessor(processor: ApiProcessor) {
    activeProcId.value = processor.id ?? ''
  }

  function addProcessor(type: ApiProcessorType) {
    const sortOrder = nextSortOrder()
    const processor: ApiProcessor = {
      id: localId(),
      processorType: type,
      name: '',
      config: { ...defaultProcessorConfig(), sortOrder, testclass: 'http', config: {}, extractors: [] },
      enabled: true,
      sortOrder,
    }
    processorRows.value.push(processor)
    activeProcId.value = processor.id ?? ''
  }

  function removeProcessor(processor: ApiProcessor) {
    processorRows.value = processorRows.value.filter((item) => item !== processor)
    fixActiveProc(processor.processorType)
  }

  function moveProcessor(type: ApiProcessorType, position: number, dir: -1 | 1) {
    const list = procList(type)
    const from = list[position]
    const to = list[position + dir]
    if (!from || !to) return
    const fromOrder = from.sortOrder ?? 0
    const toOrder = to.sortOrder ?? 0
    from.sortOrder = toOrder
    to.sortOrder = fromOrder
    const arr = processorRows.value
    const fromIdx = arr.indexOf(from)
    const toIdx = arr.indexOf(to)
    ;[arr[fromIdx], arr[toIdx]] = [arr[toIdx], arr[fromIdx]]
    activeProcId.value = to.id ?? ''
  }

  function copyProcessor(processor: ApiProcessor) {
    const index = processorRows.value.indexOf(processor)
    if (index < 0) return
    const element = JSON.parse(JSON.stringify(procElement(processor))) as Record<string, unknown>
    const copy: ApiProcessor = { ...processor, id: localId(), config: element }
    processorRows.value.splice(index + 1, 0, copy)
    activeProcId.value = copy.id ?? ''
  }

  const procTestclass = computed<string>({
    get: () => {
      const p = selectedProcessor.value
      if (!p) return ''
      const klass = procElement(p).testclass
      return klass === 'http' || klass === 'jdbc' ? klass : ''
    },
    set: (value: string) => {
      const p = selectedProcessor.value
      if (!p) return
      p.config = { ...procElement(p), testclass: value }
      applyDefaultProcRef(p)
    },
  })

  const procHttpRefOptions = computed(() =>
    orderedConfigForms.value.map((f) => ({ value: f.refName ?? '', label: f.refName ? `${f.refName}` : f.id })),
  )
  const procDsRefOptions = computed(() =>
    orderedDsForms.value.map((f) => ({ value: f.refName ?? '', label: f.refName ? `${f.refName}` : f.id })),
  )

  const procHttpRef = computed<string>({
    get: () => {
      const p = selectedProcessor.value
      if (!p) return ''
      const el = procElement(p)
      if (el.testclass !== 'http' || !isRecord(el.config)) return ''
      return typeof el.config.ref === 'string' ? el.config.ref : ''
    },
    set: (value: string) => {
      const p = selectedProcessor.value
      if (!p) return
      const el = procElement(p)
      p.config = { ...el, config: { ...(isRecord(el.config) ? el.config : {}), ref: value } }
    },
  })

  const procDsRef = computed<string>({
    get: () => {
      const p = selectedProcessor.value
      if (!p) return ''
      const el = procElement(p)
      if (el.testclass !== 'jdbc' || !isRecord(el.config)) return ''
      return typeof el.config.datasource === 'string' ? el.config.datasource : ''
    },
    set: (value: string) => {
      const p = selectedProcessor.value
      if (!p) return
      const el = procElement(p)
      p.config = { ...el, config: { ...(isRecord(el.config) ? el.config : {}), datasource: value } }
    },
  })

  function applyDefaultProcRef(processor: ApiProcessor | null) {
    if (!processor) return
    const el = procElement(processor)
    const cfg = isRecord(el.config) ? el.config : {}
    if (el.testclass === 'http' && typeof cfg.ref !== 'string') {
      const def = orderedConfigForms.value.find((f) => f.isDefault)
      if (def?.refName) processor.config = { ...el, config: { ...cfg, ref: def.refName } }
      return
    }
    if (el.testclass === 'jdbc' && typeof cfg.datasource !== 'string') {
      const def = orderedDsForms.value.find((f) => f.isDefault)
      if (def?.refName) processor.config = { ...el, config: { ...cfg, datasource: def.refName } }
    }
  }

  function procTags(processor: ApiProcessor): { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[] {
    const el = procElement(processor)
    const tags: { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[] = []
    const klass = typeof el.testclass === 'string' ? el.testclass : ''
    if (klass === 'http' || klass === 'jdbc') tags.push({ text: klass.toUpperCase(), type: 'info' })
    const summary = processorSummaryTag(el)
    if (summary) tags.push(summary)
    return tags
  }

  function procDisplayName(processor: ApiProcessor, index: number): string {
    return processor.name.trim() ? processor.name : `处理器 ${index + 1}`
  }

  return {
    activeProcId,
    selectedProcessor,
    preProcCount,
    postProcCount,
    procList,
    procElement,
    selectProcessor,
    addProcessor,
    removeProcessor,
    moveProcessor,
    copyProcessor,
    procTestclass,
    procHttpRefOptions,
    procDsRefOptions,
    procHttpRef,
    procDsRef,
    procTags,
    procDisplayName,
    applyDefaultProcRef,
  }
}
