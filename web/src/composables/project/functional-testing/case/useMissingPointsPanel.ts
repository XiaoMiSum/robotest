import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useStageTimer } from '@/composables/ai/useStageTimer'
import { useAiStore } from '@/stores/ai'
import { analyzeMissingPoints, type AiMissingPointReq } from '@/services/ai'
import { fetchProjectModuleTree, getDocumentRequirements } from '@/services/project'
import type { AiMissingPoint, AiMissingPointResult, RequirementSummary } from '@/types'
import {
  buildMissingPointText,
  collectDocumentOptions,
  pickPreselectDocument,
  type MissingPointDocumentOption,
} from '@/composables/project/functional-testing/case/missingPoints'

export function useMissingPointsPanel(docId: () => string, visible: Ref<boolean>) {
  const router = useRouter()
  const aiStore = useAiStore()
  // 操作行阶段秒表与分析请求同起停，保证关闭重开后文案不残留
  const stage = useStageTimer()

  const text = ref('')
  const requirementIds = ref<string[]>([])
  const requirementTitles = ref<RequirementSummary[]>([])
  const reqSelectorVisible = ref(false)

  /** 本会话已发起过分析：重开抽屉时展示「已恢复上次会话」（交互设计 56 §1.2） */
  const hasSession = ref(false)
  const resumeVisible = ref(false)
  /** 输入组折叠态：分析完成自动折叠，[展开输入]/[收起输入] 手动切换（交互设计 56 §1.1） */
  const inputsCollapsed = ref(false)
  /** 文档关联仅首开带入一次：会话内改选不被后续打开覆盖（交互设计 52 §1.3） */
  const requirementsSeeded = ref(false)

  const analyzing = ref(false)
  const result = ref<AiMissingPointResult | null>(null)
  const checkedIndexes = ref<Set<number>>(new Set())

  let controller: AbortController | null = null

  const hasAnyInput = computed(() => text.value.trim() !== '' || requirementIds.value.length > 0)

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

  function clearRequirements(): void {
    requirementIds.value = []
    requirementTitles.value = []
  }

  /** 输入组折叠开关：仅收纳两组输入，操作行与结果保留（交互设计 56 §1.1） */
  function toggleInputs(): void {
    inputsCollapsed.value = !inputsCollapsed.value
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
    if (!open) return
    resumeVisible.value = hasSession.value
    // 文档关联条目仅会话首开带入一次，之后的改选不被重新打开覆盖（交互设计 52 §1.3）
    if (!requirementsSeeded.value) {
      requirementsSeeded.value = true
      void loadDocumentRequirements()
    }
  })

  watch(docId, () => {
    cancelAnalyze()
    text.value = ''
    requirementIds.value = []
    requirementTitles.value = []
    result.value = null
    checkedIndexes.value = new Set()
    hasSession.value = false
    resumeVisible.value = false
    inputsCollapsed.value = false
    requirementsSeeded.value = false
  })

  function buildReq(): AiMissingPointReq | null {
    if (!hasAnyInput.value) {
      ElMessage.warning('请输入需求文本或选择需求')
      return null
    }
    const req: AiMissingPointReq = {
      documentIds: [docId()],
      text: text.value.trim() || undefined,
      requirementIds: requirementIds.value.length ? requirementIds.value : undefined,
      modelId: aiStore.effectiveModelId() ?? undefined,
    }
    return req
  }

  async function analyze(): Promise<void> {
    if (analyzing.value) return
    const req = buildReq()
    if (!req) return
    analyzing.value = true
    hasSession.value = true
    resumeVisible.value = false
    result.value = null
    stage.start()
    const { controller: c, promise } = analyzeMissingPoints(req)
    controller = c
    try {
      const resp = await promise
      result.value = resp
      checkedIndexes.value = new Set(resp.points.map((_, index) => index))
      // 完成后输入组自动折叠，把可视区让给结果列表（交互设计 56 §1.1）
      inputsCollapsed.value = true
    } catch (err) {
      if (c.signal.aborted) return
      ElMessage.error(err instanceof Error ? err.message : '分析失败')
    } finally {
      analyzing.value = false
      controller = null
      stage.stop()
    }
  }

  function cancelAnalyze(): void {
    controller?.abort()
    controller = null
    analyzing.value = false
    stage.stop()
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
    // 目标页 AI 抽屉随跳转打开，toast 语义为「内容已带入」，先于抽屉动画出现不影响理解
    ElMessage.success(`已带入勾选内容（${points.length} 条），可直接开始生成`)
  }

  onBeforeUnmount(() => controller?.abort())

  return {
    text,
    requirementIds,
    requirementTitles,
    reqSelectorVisible,
    stage,
    hasSession,
    resumeVisible,
    inputsCollapsed,
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
    clearRequirements,
    toggleInputs,
    analyze,
    cancelAnalyze,
    documentOptions,
    docSelectVisible,
    targetDocId,
    openTargetSelect,
    toCaseGenerate,
  }
}
