import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import type {
  ApiEnvironmentListItem,
  ApiScheduleExecutionScope,
  ApiSchedulePageItem,
  ApiScheduleSaveReq,
  ProjectModule,
} from '@/types'
import {
  createSchedule,
  updateSchedule,
  validateCron,
} from '@/services/project/api-testing/schedule'
import { fetchScenePage } from '@/services/project/api-testing/scene'
import { fetchEnvironments } from '@/services/project/api-testing/environment'
import { fetchProjectModuleTree } from '@/services/project'

const MAX_VISIBLE_SCENE_TAGS = 8

export function useScheduleForm(loadPage: () => Promise<void>) {
  const showFormDialog = ref(false)
  const editingId = ref<string | null>(null)
  const formRef = ref<FormInstance>()
  const saving = ref(false)

  const form = reactive<{
    taskType: ApiScheduleSaveReq['taskType']
    name: string
    description: string
    executionScope: ApiScheduleExecutionScope
    moduleIds: string[]
    sceneIds: string[]
    openapiUrl: string
    environmentId: string | undefined
    cronExpression: string
    enabled: boolean
  }>({
    taskType: 'scene_execute',
    name: '',
    description: '',
    executionScope: 'all',
    moduleIds: [],
    sceneIds: [],
    openapiUrl: '',
    environmentId: undefined,
    cronExpression: '',
    enabled: true,
  })

  const isTestPlanTask = computed(() => form.taskType === 'scene_execute')

  const moduleOptions = ref<ProjectModule[]>([])
  const scopeOptionLoading = ref(false)

  async function loadScopeOptions(scope: ApiScheduleExecutionScope) {
    scopeOptionLoading.value = true
    try {
      if (scope === 'modules') {
        moduleOptions.value = await fetchProjectModuleTree('scene')
      }
    } catch (error) {
      moduleOptions.value = []
      if (scope === 'modules') {
        ElMessage.error(error instanceof Error ? `模块加载失败：${error.message}` : '模块加载失败')
      }
    } finally {
      scopeOptionLoading.value = false
    }
  }

  const scenePickerVisible = ref(false)
  const selectedScenes = ref<{ id: string; name: string }[]>([])
  const sceneTagsExpanded = ref(false)
  const visibleSceneTags = computed(() =>
    sceneTagsExpanded.value ? selectedScenes.value : selectedScenes.value.slice(0, MAX_VISIBLE_SCENE_TAGS),
  )

  function handleSceneConfirm(selected: { id: string; name: string }[]) {
    selectedScenes.value = selected
    form.sceneIds = selected.map((s) => s.id)
  }

  function removeScene(id: string) {
    form.sceneIds = form.sceneIds.filter((sceneId) => sceneId !== id)
    selectedScenes.value = selectedScenes.value.filter((s) => s.id !== id)
  }

  async function hydrateSelectedSceneNames(): Promise<void> {
    if (!form.sceneIds.length) {
      selectedScenes.value = []
      return
    }
    const names = new Map<string, string>()
    let pNo = 1
    let totalItems = Infinity
    while (names.size < totalItems) {
      const page = await fetchScenePage({ pageNo: pNo, pageSize: 100 })
      for (const s of page.list) names.set(s.id, s.name)
      totalItems = page.total
      if (!page.list.length) break
      pNo += 1
    }
    selectedScenes.value = form.sceneIds.map((id) => ({ id, name: names.get(id) ?? '（已删除场景）' }))
  }

  const environmentOptions = ref<ApiEnvironmentListItem[]>([])
  const environmentLoading = ref(false)

  async function loadEnvironments() {
    environmentLoading.value = true
    try {
      environmentOptions.value = await fetchEnvironments()
    } catch {
      environmentOptions.value = []
    } finally {
      environmentLoading.value = false
    }
  }

  function resetForm() {
    editingId.value = null
    form.taskType = 'scene_execute'
    form.name = ''
    form.description = ''
    form.executionScope = 'all'
    form.moduleIds = []
    form.sceneIds = []
    form.openapiUrl = ''
    form.environmentId = undefined
    form.cronExpression = ''
    form.enabled = true
    moduleOptions.value = []
    selectedScenes.value = []
  }

  function openCreate() {
    resetForm()
    showFormDialog.value = true
    void loadScopeOptions(form.executionScope)
    void loadEnvironments()
  }

  function openEdit(item: ApiSchedulePageItem) {
    editingId.value = item.id
    form.taskType = item.taskType === 'import_swagger' ? 'import_swagger' : 'scene_execute'
    form.name = item.name
    form.description = item.description ?? ''
    form.executionScope = item.executionScope ?? 'all'
    form.moduleIds = item.moduleIds ?? []
    form.sceneIds = item.sceneIds ?? []
    form.openapiUrl = item.openapiUrl ?? ''
    form.environmentId = item.environmentId ?? undefined
    form.cronExpression = item.cronExpression
    form.enabled = item.enabled
    showFormDialog.value = true
    void loadScopeOptions(form.executionScope)
    void loadEnvironments()
    if (form.executionScope === 'scenes') void hydrateSelectedSceneNames()
  }

  watch(() => form.taskType, (type) => {
    if (type === 'import_swagger') {
      form.executionScope = 'all'
      form.moduleIds = []
      form.sceneIds = []
      form.environmentId = undefined
    } else {
      form.openapiUrl = ''
      void loadScopeOptions('all')
    }
  })

  watch(() => form.executionScope, (scope) => {
    void loadScopeOptions(scope)
    if (scope === 'scenes') void hydrateSelectedSceneNames()
  })

  const cronValidation = ref<{ valid: boolean; description: string | null; nextExecutions: string[] | null } | null>(null)
  const cronValidating = ref(false)

  async function handleValidateCron() {
    if (!form.cronExpression.trim()) return
    cronValidating.value = true
    try {
      cronValidation.value = await validateCron({ cronExpression: form.cronExpression.trim() })
    } catch {
      cronValidation.value = { valid: false, description: null, nextExecutions: null }
    } finally {
      cronValidating.value = false
    }
  }

  function handlePresetSelect(preset: string) {
    form.cronExpression = preset
    void handleValidateCron()
  }

  const cronBuilder = reactive({
    minute: '0',
    hour: '2',
    day: '*',
    month: '*',
    weekday: '*',
  })
  const showCronBuilder = ref(false)

  function applyCronBuilder() {
    form.cronExpression = `${cronBuilder.minute} ${cronBuilder.hour} ${cronBuilder.day} ${cronBuilder.month} ${cronBuilder.weekday}`
    showCronBuilder.value = false
    void handleValidateCron()
  }

  async function handleSave() {
    if (!formRef.value) return
    await formRef.value.validate()
    if (!form.cronExpression.trim()) {
      ElMessage.warning('请输入 Cron 表达式')
      return
    }
    if (isTestPlanTask.value) {
      if (!form.environmentId) {
        ElMessage.warning('测试计划任务需选择目标环境')
        return
      }
      if (form.executionScope === 'modules' && !form.moduleIds.length) {
        ElMessage.warning('请选择执行模块')
        return
      }
      if (form.executionScope === 'scenes' && !form.sceneIds.length) {
        ElMessage.warning('请选择执行场景')
        return
      }
    } else if (!form.openapiUrl.trim()) {
      ElMessage.warning('接口同步任务需填写接口文档 URL')
      return
    }
    saving.value = true
    try {
      const req: ApiScheduleSaveReq = {
        taskType: form.taskType,
        name: form.name.trim(),
        description: form.description?.trim() || undefined,
        executionScope: isTestPlanTask.value ? form.executionScope : undefined,
        moduleIds: isTestPlanTask.value && form.executionScope === 'modules' ? form.moduleIds : undefined,
        sceneIds: isTestPlanTask.value && form.executionScope === 'scenes' ? form.sceneIds : undefined,
        openapiUrl: isTestPlanTask.value ? undefined : form.openapiUrl.trim() || undefined,
        environmentId: isTestPlanTask.value ? form.environmentId || undefined : undefined,
        cronExpression: form.cronExpression.trim(),
        enabled: form.enabled,
      }
      if (editingId.value) {
        await updateSchedule(editingId.value, req)
        ElMessage.success('已更新')
      } else {
        await createSchedule(req)
        ElMessage.success('已创建')
      }
      showFormDialog.value = false
      await loadPage()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '保存失败')
    } finally {
      saving.value = false
    }
  }

  return {
    showFormDialog,
    editingId,
    formRef,
    saving,
    form,
    isTestPlanTask,
    openCreate,
    openEdit,
    handleSave,
    moduleOptions,
    scopeOptionLoading,
    scenePickerVisible,
    selectedScenes,
    visibleSceneTags,
    sceneTagsExpanded,
    handleSceneConfirm,
    removeScene,
    environmentOptions,
    environmentLoading,
    cronValidation,
    cronValidating,
    handleValidateCron,
    handlePresetSelect,
    cronBuilder,
    showCronBuilder,
    applyCronBuilder,
    MAX_VISIBLE_SCENE_TAGS,
  }
}
