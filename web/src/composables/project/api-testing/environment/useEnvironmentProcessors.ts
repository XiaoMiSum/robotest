import { ref, computed, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { defaultProcessorConfig, processorTags, isRecord } from '@/composables/project/api-testing/processorFormModel'
import type { ApiProcessor, ApiProcessorType } from '@/types'

/** add 为列表末尾新增表单、edit 为行下编辑表单；两者均不落列表直至保存（docs34 §1.3） */
export type ProcDraftMode = 'none' | 'add' | 'edit'

/**
 * 环境处理器管理（从 EnvironmentDetailPanel 提取）。
 * 默认引用补选与类型切换后的引用补选由 ProcessorConfigEditor 统一完成。
 */
export function useEnvironmentProcessors(
  processorRows: Ref<ApiProcessor[]>,
  localId: () => string,
  nextSortOrder: () => number,
) {
  const activeProcId = ref('')
  const procExpandedId = ref('')
  const procDraft = ref<ApiProcessor | null>(null)
  const procDraftMode = ref<ProcDraftMode>('none')

  function procList(type: ApiProcessorType): ApiProcessor[] {
    return processorRows.value.filter((p) => p.processorType === type)
  }

  const preProcCount = computed(() => procList('preprocessor').length)
  const postProcCount = computed(() => procList('postprocessor').length)

  // 编辑/新增期间读写对象是草稿，提取器引入与默认引用都要落在草稿上，保存时才回写列表
  const selectedProcessor = computed<ApiProcessor | null>(() => {
    if (procDraft.value) return procDraft.value
    return processorRows.value.find((p) => p.id === activeProcId.value) ?? null
  })

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
    const id = processor.id ?? ''
    if (procDraft.value?.id === id) cancelProcDraft()
    if (procExpandedId.value === id) procExpandedId.value = ''
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

  function cancelProcDraft() {
    procDraft.value = null
    procDraftMode.value = 'none'
  }

  function toggleProcDetail(processor: ApiProcessor) {
    const id = processor.id ?? ''
    if (procDraft.value) {
      // 表单展开中点击自身行不切换，避免误丢编辑（docs34 §1.3）
      if (procDraftMode.value === 'edit' && procDraft.value.id === id) return
      cancelProcDraft()
    }
    selectProcessor(processor)
    procExpandedId.value = procExpandedId.value === id ? '' : id
  }

  function startProcEdit(processor: ApiProcessor) {
    procExpandedId.value = ''
    procDraftMode.value = 'edit'
    // 深拷贝：取消时原行须原样还原
    procDraft.value = { ...processor, id: processor.id ?? '', config: cloneElement(processor.config) }
    selectProcessor(processor)
  }

  function startProcAdd(type: ApiProcessorType) {
    procExpandedId.value = ''
    procDraftMode.value = 'add'
    procDraft.value = {
      id: '',
      processorType: type,
      name: '',
      config: { ...defaultProcessorConfig(), testclass: 'http', config: {}, extractors: [] },
      enabled: true,
    }
  }

  function commitProcDraft(): boolean {
    const draft = procDraft.value
    const mode = procDraftMode.value
    if (!draft || mode === 'none') return false
    const name = draft.name.trim()
    if (!name) return false
    let target: ApiProcessor | undefined
    if (mode === 'add') {
      addProcessor(draft.processorType)
      target = processorRows.value.find((item) => item.id === activeProcId.value)
    } else {
      target = processorRows.value.find((item) => item.id === draft.id)
    }
    if (!target) {
      cancelProcDraft()
      return false
    }
    target.name = name
    target.config = { ...procElement(draft), sortOrder: target.sortOrder }
    cancelProcDraft()
    procExpandedId.value = target.id ?? ''
    ElMessage.success(mode === 'add' ? '处理器已添加' : '处理器已保存')
    return true
  }

  function cloneElement(source: ApiProcessor['config']): Record<string, unknown> {
    if (!isRecord(source)) return {}
    return JSON.parse(JSON.stringify(source)) as Record<string, unknown>
  }

  function procTags(processor: ApiProcessor): { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[] {
    return processorTags(procElement(processor))
  }

  function procDisplayName(processor: ApiProcessor, index: number): string {
    return processor.name.trim() ? processor.name : `处理器 ${index + 1}`
  }

  return {
    activeProcId,
    procExpandedId,
    procDraft,
    procDraftMode,
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
    toggleProcDetail,
    startProcEdit,
    startProcAdd,
    cancelProcDraft,
    commitProcDraft,
    procTags,
    procDisplayName,
  }
}
