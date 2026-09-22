import { ref, computed, watch, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiSceneDetail, ApiSceneVariableItem, ApiSceneStepItem, ApiEnvironmentListItem, ProjectModule } from '@/types'
import type { CascaderOption } from 'element-plus'
import {
  createScene,
  fetchSceneDetail,
  updateScene,
  deleteScene,
  executeScene,
  executeDraftScene,
} from '@/services/project/scene'
import { fetchEnvironments } from '@/services/project/environment'
import { fetchProjectModuleTree } from '@/services/project'
import { toSelectableModuleOptions } from '@/pages/project/interfacesModel'
import type { SceneProcessorElement } from './useSceneProcessors'

export interface UseSceneEditorOptions {
  sceneId?: string
  createMode?: boolean
  moduleId?: string
  copyFromId?: string
}

export interface SceneEditorState {
  loading: Ref<boolean>
  saving: Ref<boolean>
  running: Ref<boolean>
  detail: Ref<ApiSceneDetail | null>
  dirty: Ref<boolean>
  sceneSection: Ref<'steps' | 'variables' | 'pre' | 'post'>
}

export interface SceneBasicInfo {
  editName: Ref<string>
  editDescription: Ref<string>
  editModuleId: Ref<string | null>
  editEnvironmentId: Ref<string | null>
  editPriority: Ref<string | null>
  editStatus: Ref<string>
  showDescription: Ref<boolean>
}

export interface SceneModuleEnv {
  moduleTree: Ref<ProjectModule[]>
  moduleOptions: Ref<CascaderOption[]>
  environmentOptions: Ref<ApiEnvironmentListItem[]>
}

export function useSceneEditor(
  props: UseSceneEditorOptions,
  emit: (e: 'back' | 'title-update' | 'dirty-change', ...args: unknown[]) => void,
) {
  // ==================== 场景数据 ====================
  const loading = ref(false)
  const saving = ref(false)
  const running = ref(false)
  const detail = ref<ApiSceneDetail | null>(null)
  const dirty = ref(false)
  const sceneSection = ref<'steps' | 'variables' | 'pre' | 'post'>('steps')

  const autoSaveLabel = ref('')
  let autosaveTimer: ReturnType<typeof setTimeout> | null = null
  function bumpAutosave() {
    autoSaveLabel.value = `自动保存已开启 ${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`
    if (autosaveTimer) clearTimeout(autosaveTimer)
    autosaveTimer = setTimeout(() => { autoSaveLabel.value = '' }, 5000)
  }

  // ==================== 场景基础信息 ====================
  const editName = ref('')
  const showDescription = ref(false)
  const editDescription = ref('')
  const editModuleId = ref<string | null>(null)
  const editEnvironmentId = ref<string | null>(null)
  const editPriority = ref<string | null>('P2')
  const editStatus = ref<string>('draft')

  const SCENE_PRIORITY_OPTIONS = [
    { value: 'P0', label: 'P0', color: 'var(--color-priority-p0)' },
    { value: 'P1', label: 'P1', color: 'var(--color-priority-p1)' },
    { value: 'P2', label: 'P2', color: 'var(--color-priority-p2)' },
    { value: 'P3', label: 'P3', color: 'var(--color-priority-p3)' },
  ] as const

  // ==================== 模块与环境 ====================
  const moduleTree = ref<ProjectModule[]>([])
  const moduleOptions = computed<CascaderOption[]>(() =>
    toSelectableModuleOptions(moduleTree.value) as CascaderOption[],
  )
  const environmentOptions = ref<ApiEnvironmentListItem[]>([])

  async function loadModules() {
    try {
      moduleTree.value = await fetchProjectModuleTree('scene')
    } catch {
      moduleTree.value = []
    }
  }

  async function loadEnvironments() {
    try {
      environmentOptions.value = await fetchEnvironments()
      if (isCreateMode.value && editEnvironmentId.value == null) {
        const def = environmentOptions.value.find((env) => env.isDefault)
        if (def) editEnvironmentId.value = def.id
      }
    } catch {
      environmentOptions.value = []
    }
  }

  // ==================== 计算属性 ====================
  const isCreateMode = computed(() => props.createMode || !props.sceneId)
  const currentPriorityColor = computed(() =>
    SCENE_PRIORITY_OPTIONS.find((o) => o.value === editPriority.value)?.color,
  )

  // ==================== 监听变更 ====================
  watch([editName, editDescription, editEnvironmentId, editPriority], () => {
    dirty.value = true
    emit('dirty-change', true)
  })

  watch(editName, (name) => emit('title-update', name.trim() || '新场景'))

  // ==================== 加载场景 ====================
  async function loadDetail() {
    if (!props.sceneId) return
    loading.value = true
    try {
      detail.value = await fetchSceneDetail(props.sceneId)
      editName.value = detail.value.name
      editDescription.value = detail.value.description ?? ''
      editModuleId.value = detail.value.moduleId ?? null
      editEnvironmentId.value = detail.value.environmentId ?? null
      editPriority.value = detail.value.priority ?? null
      editStatus.value = detail.value.status ?? 'draft'
      dirty.value = false
      emit('title-update', detail.value.name)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '场景加载失败')
    } finally {
      loading.value = false
    }
  }

  // ==================== 复制预填 ====================
  async function prefillFromCopy(
    editVariables: Ref<{ key: string; value: string; description: string; enabled: boolean }[]>,
    editProcessors: Ref<SceneProcessorElement[]>,
    draftSteps: Ref<ApiSceneStepItem[]>,
    prefillDraftStepsFn: (steps: ApiSceneStepItem[]) => ApiSceneStepItem[],
  ) {
    if (!props.copyFromId) return
    loading.value = true
    try {
      const src = await fetchSceneDetail(props.copyFromId)
      editName.value = `${src.name}（副本）`
      editDescription.value = src.description ?? ''
      editModuleId.value = src.moduleId ?? null
      editEnvironmentId.value = src.environmentId ?? null
      editPriority.value = src.priority ?? null
      editVariables.value = src.variables.map((v: ApiSceneVariableItem) => ({ key: v.name, value: v.value ?? '', description: v.description ?? '', enabled: true }))
      editProcessors.value = src.processors.map((p) => ({ ...(p as SceneProcessorElement) }))
      draftSteps.value = prefillDraftStepsFn(src.steps)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '复制预填失败')
    } finally {
      loading.value = false
    }
  }

  // ==================== 保存场景 ====================
  async function handleSave(options: {
    status?: string
    sceneVariablePayload?: () => ApiSceneVariableItem[]
    editProcessors?: Ref<SceneProcessorElement[]>
    draftSteps?: Ref<ApiSceneStepItem[]>
  }): Promise<boolean> {
    const { status, sceneVariablePayload, editProcessors, draftSteps } = options
    if (status) editStatus.value = status
    if (!editName.value.trim()) { ElMessage.warning('请填写场景名称'); return false }
    const moduleId = editModuleId.value ?? props.moduleId
    if (!moduleId) { ElMessage.warning('请选择所属模块'); return false }
    saving.value = true
    try {
      if (props.sceneId && detail.value) {
        await updateScene(props.sceneId, {
          name: editName.value.trim(),
          description: editDescription.value.trim() || undefined,
          moduleId: editModuleId.value,
          environmentId: editEnvironmentId.value,
          priority: editPriority.value,
          status: editStatus.value,
          variables: sceneVariablePayload?.() ?? [],
          processors: editProcessors?.value ?? [],
          steps: detail.value.steps
            .slice()
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .map((s) => ({
              id: s.id.startsWith('new-') ? undefined : s.id,
              name: s.name,
              stepType: s.stepType,
              enabled: s.enabled,
              sourceType: s.sourceType,
              sourceId: s.sourceId ?? null,
              requestConfig: s.requestConfig as Record<string, unknown>,
              processors: s.processors,
              validators: s.validators,
              extractors: s.extractors,
              sortOrder: s.sortOrder,
            })),
          changeVersion: detail.value.changeVersion,
        })
        ElMessage.success('已保存')
        await loadDetail()
        return true
      } else {
        await createScene({
          name: editName.value.trim(),
          description: editDescription.value.trim() || undefined,
          moduleId: editModuleId.value ?? props.moduleId,
          environmentId: editEnvironmentId.value,
          priority: editPriority.value,
          status: editStatus.value,
          variables: sceneVariablePayload?.() ?? [],
          processors: editProcessors?.value ?? [],
          steps: (draftSteps?.value ?? []).map((s) => ({
            name: s.name,
            stepType: s.stepType,
            enabled: s.enabled,
            sourceType: s.sourceType,
            sourceId: s.sourceId ?? null,
            requestConfig: s.requestConfig as Record<string, unknown>,
            processors: s.processors,
            validators: s.validators,
            extractors: s.extractors,
          })),
        })
        ElMessage.success('已创建')
        emit('back')
        return true
      }
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '保存失败')
      return false
    } finally {
      saving.value = false
    }
  }

  // ==================== 场景删除 ====================
  async function handleDeleteScene() {
    if (!props.sceneId) return
    try {
      await ElMessageBox.confirm('删除场景后不可恢复，确定删除？', '删除场景', { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' })
    } catch { return }
    try {
      await deleteScene(props.sceneId)
      ElMessage.success('已删除')
      emit('back')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  // ==================== 运行场景 ====================
  async function handleRun(options: {
    sceneVariablePayload?: () => ApiSceneVariableItem[]
    draftSteps?: Ref<ApiSceneStepItem[]>
    saveFn?: () => Promise<boolean>
  }) {
    const { sceneVariablePayload, draftSteps, saveFn } = options
    if (isCreateMode.value) {
      if (!draftSteps || draftSteps.value.length === 0) { ElMessage.warning('请先添加步骤'); return }
      running.value = true
      try {
        const resp = await executeDraftScene({
          name: editName.value.trim() || undefined,
          environmentId: editEnvironmentId.value,
          sceneVariables: sceneVariablePayload?.() ?? [],
          steps: draftSteps.value.map((s) => ({
            name: s.name,
            stepType: s.stepType,
            sourceType: s.sourceType,
            sourceId: s.sourceId ?? null,
            enabled: s.enabled,
            requestConfig: s.requestConfig as Record<string, unknown>,
            validators: s.validators,
            extractors: s.extractors,
            stepVariables: s.variables ?? [],
          })),
        })
        ElMessage.success(`草稿运行完成：通过 ${resp.passed} · 失败 ${resp.failed} · 跳过 ${resp.skipped}`)
      } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : '运行失败')
      } finally {
        running.value = false
      }
      return
    }
    if (!props.sceneId) return
    if (dirty.value && saveFn) {
      const ok = await saveFn()
      if (!ok) return
    }
    running.value = true
    try {
      const resp = await executeScene(props.sceneId, { environmentId: editEnvironmentId.value })
      ElMessage.success(`场景已触发执行（${resp.executionId}）`)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '运行失败')
    } finally {
      running.value = false
    }
  }

  return {
    // State
    loading,
    saving,
    running,
    detail,
    dirty,
    sceneSection,
    autoSaveLabel,
    bumpAutosave,
    // Basic info
    editName,
    showDescription,
    editDescription,
    editModuleId,
    editEnvironmentId,
    editPriority,
    editStatus,
    SCENE_PRIORITY_OPTIONS,
    // Module & env
    moduleTree,
    moduleOptions,
    environmentOptions,
    // Computed
    isCreateMode,
    currentPriorityColor,
    // Methods
    loadModules,
    loadEnvironments,
    loadDetail,
    prefillFromCopy,
    handleSave,
    handleDeleteScene,
    handleRun,
  }
}
