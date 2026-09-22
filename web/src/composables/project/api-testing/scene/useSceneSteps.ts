import { ref, computed, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiSceneDetail, ApiSceneStepItem } from '@/types'
import {
  deleteSceneStep,
  reorderSceneSteps,
  updateSceneStep,
  copySceneStep,
} from '@/services/project/api-testing/scene'
import { sortedSteps, emptyStepDraft } from '@/pages/project/api-testing/scene/scenesModel'

export interface UseSceneStepsOptions {
  sceneId?: string
  detail: Ref<ApiSceneDetail | null>
  bumpAutosave: () => void
}

export function useSceneSteps(options: UseSceneStepsOptions) {
  const { sceneId, detail, bumpAutosave } = options

  // ==================== 步骤操作 ====================
  const showInterfacePicker = ref(false)
  const selectedStep = ref<ApiSceneStepItem | null>(null)
  const draftSteps = ref<ApiSceneStepItem[]>([])

  /** 构造一个未命名的新步骤（新增后立即内联编辑，故使用临时 id 标记） */
  function createDefaultStep(): ApiSceneStepItem {
    const draft = emptyStepDraft()
    return {
      id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      name: '',
      stepType: draft.stepType,
      sortOrder: 0,
      enabled: true,
      sourceType: 'manual',
      sourceId: null,
      requestConfig: draft.requestConfig,
      variables: [],
      processors: [],
      validators: [],
      extractors: [],
    }
  }

  // ==================== 编辑态步骤操作 ====================

  /** 编辑态：直接追加未命名步骤并选中，右侧内联编辑 */
  function handleAddStep() {
    const step = createDefaultStep()
    step.sortOrder = (detail.value?.steps.length ?? 0) + 1
    detail.value?.steps.push(step)
    selectedStep.value = step
  }

  /** 从已有接口添加步骤：打开接口选择器 */
  function handleQuickAddStep() {
    showInterfacePicker.value = true
  }

  /** 接口选择器选中：构造步骤追加到当前列表并选中 */
  function handleInterfaceSelected(step: ApiSceneStepItem, isCreateMode: boolean) {
    const list = isCreateMode ? draftSteps.value : (detail.value?.steps ?? [])
    step.sortOrder = list.length + 1
    list.push(step)
    if (!isCreateMode && detail.value) detail.value.steps = list
    selectedStep.value = step
    showInterfacePicker.value = false
    bumpAutosave()
  }

  /** 步骤关键明细是否完整：名称必填；HTTP 需路径，JDBC 需 SQL */
  function isStepDetailIncomplete(step: ApiSceneStepItem): boolean {
    if (!step.name.trim()) return true
    const cfg = step.requestConfig && typeof step.requestConfig === 'object' ? step.requestConfig : {}
    const value = cfg as Record<string, unknown>
    if (step.stepType === 'jdbc') return !String(value.sql ?? '').trim()
    return !String(value.url ?? '').trim()
  }

  function handleSelectStep(step: ApiSceneStepItem) {
    const current = selectedStep.value
    if (current && current.id !== step.id && isStepDetailIncomplete(current)) {
      ElMessage.warning('请先填写当前步骤的名称 / HTTP 路径 / SQL 语句')
      return
    }
    selectedStep.value = step
  }

  async function handleDeleteStep(step: ApiSceneStepItem) {
    if (!sceneId) return
    ElMessageBox.confirm(`删除步骤「${step.name}」？`, '删除步骤', { type: 'warning' })
      .then(async () => {
        if (step.id.startsWith('new-')) {
          const list = detail.value?.steps ?? []
          const idx = list.findIndex((s) => s.id === step.id)
          if (idx >= 0) list.splice(idx, 1)
          if (selectedStep.value?.id === step.id) selectedStep.value = null
          return
        }
        await deleteSceneStep(sceneId ?? '', step.id)
        ElMessage.success('步骤已删除')
        if (selectedStep.value?.id === step.id) selectedStep.value = null
        // loadDetail will be called by parent
      }).catch(() => {})
  }

  async function handleToggleStep(step: ApiSceneStepItem) {
    if (!sceneId) return
    try {
      await updateSceneStep(sceneId, step.id, {
        name: step.name,
        stepType: step.stepType,
        enabled: !step.enabled,
        requestConfig: step.requestConfig,
        processors: step.processors,
        validators: step.validators,
        extractors: step.extractors,
        sourceType: step.sourceType,
        sourceId: step.sourceId,
      })
      step.enabled = !step.enabled
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '操作失败')
    }
  }

  async function handleReorderSteps(newSteps: ApiSceneStepItem[]) {
    if (!sceneId) return
    try {
      await reorderSceneSteps(sceneId, { stepIds: newSteps.map((s) => s.id) })
      if (detail.value) detail.value.steps = newSteps
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '排序失败')
    }
  }

  async function handleCopyStep(step: ApiSceneStepItem) {
    if (!sceneId) return
    try {
      await copySceneStep(sceneId, step.id)
      ElMessage.success('已复制步骤')
      // loadDetail will be called by parent
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '复制失败')
    }
  }

  // ==================== 创建态草稿步骤 ====================

  function handleDraftAddStep() {
    const step = createDefaultStep()
    step.sortOrder = draftSteps.value.length + 1
    draftSteps.value.push(step)
    selectedStep.value = step
  }

  function handleDraftDeleteStep(step: ApiSceneStepItem) {
    const idx = draftSteps.value.findIndex((s) => s.id === step.id)
    if (idx >= 0) draftSteps.value.splice(idx, 1)
    if (selectedStep.value?.id === step.id) selectedStep.value = null
  }

  function handleDraftToggleStep(step: ApiSceneStepItem) {
    step.enabled = !step.enabled
    bumpAutosave()
  }

  function handleDraftReorderSteps(newSteps: ApiSceneStepItem[]) {
    newSteps.forEach((s, i) => { s.sortOrder = i + 1 })
    draftSteps.value = newSteps
  }

  function handleDraftCopyStep(step: ApiSceneStepItem) {
    draftSteps.value.push({
      ...step,
      id: `draft-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      sortOrder: draftSteps.value.length + 1,
    })
  }

  // ==================== 计算属性 ====================
  const sorted = computed(() => (detail.value ? sortedSteps(detail.value.steps) : []))

  return {
    // State
    showInterfacePicker,
    selectedStep,
    draftSteps,
    sorted,
    // Methods
    createDefaultStep,
    handleAddStep,
    handleQuickAddStep,
    handleInterfaceSelected,
    handleSelectStep,
    handleDeleteStep,
    handleToggleStep,
    handleReorderSteps,
    handleCopyStep,
    handleDraftAddStep,
    handleDraftDeleteStep,
    handleDraftToggleStep,
    handleDraftReorderSteps,
    handleDraftCopyStep,
  }
}
