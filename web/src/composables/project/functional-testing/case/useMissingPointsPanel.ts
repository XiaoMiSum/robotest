import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { analyzeMissingPoints, type AiMissingPointReq } from '@/services/ai'
import { fetchProjectModuleTree, getDocumentRequirements } from '@/services/project'
import type { AiMissingPoint, AiMissingPointResult, RequirementSummary } from '@/types'
import {
  buildMissingPointText,
  collectDocumentOptions,
  pickPreselectDocument,
  type MissingPointDocumentOption,
} from '@/components/project/functional-testing/case/missingPoints'

export function useMissingPointsPanel(docId: () => string, visible: Ref<boolean>) {
  const router = useRouter()

  const keywords = ref<string[]>([])
  const text = ref('')
  const requirementIds = ref<string[]>([])
  const requirementTitles = ref<RequirementSummary[]>([])
  const reqSelectorVisible = ref(false)

  const analyzing = ref(false)
  const result = ref<AiMissingPointResult | null>(null)
  const checkedIndexes = ref<Set<number>>(new Set())

  let controller: AbortController | null = null

  const hasAnyInput = computed(
    () => keywords.value.length > 0 || text.value.trim() !== '' || requirementIds.value.length > 0,
  )

  const checkedPoints = computed<AiMissingPoint[]>(() =>
    (result.value?.points ?? []).filter((_, index) => checkedIndexes.value.has(index)),
  )
  const allChecked = computed(
    () => result.value !== null && checkedIndexes.value.size === result.value.points.length,
  )

  function toggleAll(checked: boolean): void {
    if (!result.value) return
    checkedIndexes.value = checked
      ? new Set(result.value.points.map((_, index) => index))
      : new Set<number>()
  }

  function toggleItem(index: number, checked: boolean): void {
    const next = new Set(checkedIndexes.value)
    if (checked) next.add(index)
    else next.delete(index)
    checkedIndexes.value = next
  }

  function handleRequirementConfirm(selected: RequirementSummary[]): void {
    requirementIds.value = selected.map((r) => r.id)
    requirementTitles.value = selected.map((r) => {
      if (r.title) return r
      return requirementTitles.value.find((prev) => prev.id === r.id) ?? r
    })
  }

  function removeRequirement(id: string): void {
    requirementIds.value = requirementIds.value.filter((rid) => rid !== id)
    requirementTitles.value = requirementTitles.value.filter((r) => r.id !== id)
  }

  async function loadDocumentRequirements(): Promise<void> {
    try {
      const list = await getDocumentRequirements(docId())
      requirementIds.value = list.map((r) => r.id)
      requirementTitles.value = list
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载文档关联需求失败')
    }
  }

  watch(visible, (open) => {
    if (open) void loadDocumentRequirements()
  })

  watch(docId, () => {
    cancelAnalyze()
    keywords.value = []
    text.value = ''
    requirementIds.value = []
    requirementTitles.value = []
    result.value = null
    checkedIndexes.value = new Set()
  })

  function buildReq(): AiMissingPointReq | null {
    if (!hasAnyInput.value) {
      ElMessage.warning('请至少输入关键词、需求文本或选择需求')
      return null
    }
    const req: AiMissingPointReq = {
      keywords: keywords.value.length ? keywords.value : undefined,
      text: text.value.trim() || undefined,
      requirementIds: requirementIds.value.length ? requirementIds.value : undefined,
    }
    return req
  }

  async function analyze(): Promise<void> {
    const req = buildReq()
    if (!req) return
    analyzing.value = true
    result.value = null
    const { controller: c, promise } = analyzeMissingPoints(req)
    controller = c
    try {
      const resp = await promise
      result.value = resp
      checkedIndexes.value = new Set(resp.points.map((_, index) => index))
    } catch (err) {
      if (controller?.signal.aborted) return
      ElMessage.error(err instanceof Error ? err.message : '分析失败')
    } finally {
      analyzing.value = false
      controller = null
    }
  }

  function cancelAnalyze(): void {
    controller?.abort()
    controller = null
    analyzing.value = false
  }

  const documentOptions = ref<MissingPointDocumentOption[]>([])
  const docSelectVisible = ref(false)
  const targetDocId = ref('')

  async function openTargetSelect(): Promise<void> {
    const points = checkedPoints.value
    if (!points.length) {
      ElMessage.warning('请至少勾选一个遗漏测试点')
      return
    }
    try {
      const tree = await fetchProjectModuleTree('testcase')
      documentOptions.value = collectDocumentOptions(tree)
      if (!documentOptions.value.length) {
        ElMessage.warning('项目暂无文档，无法生成用例')
        return
      }
      targetDocId.value = pickPreselectDocument(documentOptions.value, points)
      docSelectVisible.value = true
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载模块树失败')
    }
  }

  function toCaseGenerate(): void {
    const points = checkedPoints.value
    if (!points.length || !targetDocId.value) return
    docSelectVisible.value = false
    visible.value = false
    router.push({
      name: 'FunctionalTesting',
      query: { tab: 'cases', documentId: targetDocId.value, aiGenerate: buildMissingPointText(points) },
    })
  }

  onBeforeUnmount(() => controller?.abort())

  return {
    keywords,
    text,
    requirementIds,
    requirementTitles,
    reqSelectorVisible,
    analyzing,
    result,
    checkedIndexes,
    hasAnyInput,
    checkedPoints,
    allChecked,
    toggleAll,
    toggleItem,
    handleRequirementConfirm,
    removeRequirement,
    analyze,
    cancelAnalyze,
    documentOptions,
    docSelectVisible,
    targetDocId,
    openTargetSelect,
    toCaseGenerate,
  }
}
