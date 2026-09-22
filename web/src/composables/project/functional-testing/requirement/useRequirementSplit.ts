import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAiStream, type AiStreamController } from '@/composables/ai/useAiStream'
import { useAiStore } from '@/stores/ai'
import { batchCreateRequirements } from '@/services/project'
import type { AiRequirementSplitResult } from '@/types'

export interface PreviewEntry {
  key: string
  module: string
  title: string
  content: string
  checked: boolean
  editing: boolean
}

export interface PreviewGroup {
  module: string
  items: PreviewEntry[]
  checked: number
}

type Phase = 'input' | 'streaming' | 'preview'

export function useRequirementSplit(
  visible: { value: boolean },
  emit: (event: 'imported', count: number) => void,
) {
  const aiStore = useAiStore()

  const phase = ref<Phase>('input')
  const inputExpanded = ref(false)
  const previewExpanded = ref(false)
  const text = ref('')
  const warnings = ref<string[]>([])
  const controller = ref<AiStreamController | null>(null)
  const importing = ref(false)
  const entries = ref<PreviewEntry[]>([])

  const totalCount = computed(() => entries.value.length)
  const checkedCount = computed(() => entries.value.filter((e) => e.checked).length)
  const allChecked = computed(
    () => entries.value.length > 0 && entries.value.every((e) => e.checked),
  )
  const partialChecked = computed(() => checkedCount.value > 0 && !allChecked.value)

  const groups = computed<PreviewGroup[]>(() => {
    const map = new Map<string, PreviewEntry[]>()
    for (const e of entries.value) {
      const list = map.get(e.module)
      if (list) list.push(e)
      else map.set(e.module, [e])
    }
    const result: PreviewGroup[] = []
    for (const [module, items] of map) {
      result.push({ module, items, checked: items.filter((i) => i.checked).length })
    }
    return result
  })

  function restoreAfterSplitInterrupted(): void {
    if (entries.value.length > 0) {
      phase.value = 'preview'
      inputExpanded.value = true
    } else {
      phase.value = 'input'
      inputExpanded.value = false
    }
  }

  function startSplit(): void {
    if (!text.value.trim()) {
      ElMessage.warning('请先粘贴需求文档')
      return
    }
    phase.value = 'streaming'
    controller.value = useAiStream({
      url: '/project/ai/requirements/split',
      body: { text: text.value, modelId: aiStore.effectiveModelId() ?? null },
      onEvent(event) {
        if (event.event === 'done') {
          const result = event.data as AiRequirementSplitResult
          warnings.value = result.warnings ?? []
          const list: PreviewEntry[] = []
          result.modules.forEach((m, mi) => {
            m.items.forEach((it, ii) => {
              list.push({
                key: `${mi}-${ii}`,
                module: m.module,
                title: it.title,
                content: it.content,
                checked: true,
                editing: false,
              })
            })
          })
          entries.value = list
          phase.value = 'preview'
          inputExpanded.value = false
        } else if (event.event === 'error') {
          const data = event.data as { message?: string }
          ElMessage.error(data.message ?? 'AI 拆分失败')
          restoreAfterSplitInterrupted()
        }
      },
      onError(error) {
        ElMessage.error(error.message)
        restoreAfterSplitInterrupted()
      },
      onClose() {
        if (phase.value === 'streaming') restoreAfterSplitInterrupted()
      },
    })
  }

  function stop(): void {
    controller.value?.cancel()
    controller.value = null
    restoreAfterSplitInterrupted()
  }

  function expandInput(): void {
    inputExpanded.value = true
    previewExpanded.value = false
  }

  function toggleAll(): void {
    const target = !allChecked.value
    entries.value.forEach((e) => (e.checked = target))
  }

  function removeEntry(entry: PreviewEntry): void {
    entries.value = entries.value.filter((e) => e !== entry)
  }

  function saveEdit(entry: PreviewEntry): void {
    if (!entry.title.trim()) {
      ElMessage.warning('标题不能为空')
      return
    }
    entry.editing = false
  }

  async function importChecked(): Promise<void> {
    const selected = entries.value.filter((e) => e.checked)
    if (selected.length === 0) {
      ElMessage.warning('请至少勾选一条')
      return
    }
    importing.value = true
    try {
      const resp = await batchCreateRequirements({
        items: selected.map((e) => ({
          title: `${e.module}·${e.title}`,
          content: e.content,
          aiGenerated: true,
        })),
      })
      ElMessage.success(`已入库 ${resp.count} 条`)
      emit('imported', resp.count)
      visible.value = false
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '批量入库失败')
    } finally {
      importing.value = false
    }
  }

  function reset(): void {
    controller.value?.cancel()
    controller.value = null
    phase.value = 'input'
    inputExpanded.value = false
    previewExpanded.value = false
    text.value = ''
    warnings.value = []
    entries.value = []
  }

  onBeforeUnmount(() => {
    controller.value?.cancel()
  })

  return {
    phase,
    inputExpanded,
    previewExpanded,
    text,
    warnings,
    importing,
    entries,
    totalCount,
    checkedCount,
    allChecked,
    partialChecked,
    groups,
    startSplit,
    stop,
    expandInput,
    toggleAll,
    removeEntry,
    saveEdit,
    importChecked,
    reset,
  }
}
