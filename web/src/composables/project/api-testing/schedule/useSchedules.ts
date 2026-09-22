import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ApiSchedulePageItem,
} from '@/types'
import {
  deleteSchedule,
  executeSchedule,
  fetchSchedulePage,
  toggleSchedule,
} from '@/services/project/api-testing/schedule'
import { CRON_PRESETS, EXECUTION_SCOPES, SCHEDULE_TASK_TYPES, taskExecutionSummary, execStatusLabel, execStatusType } from '@/composables/project/api-testing/schedule/schedulesModel'
import { formatDateTime, formatShortDateTime } from '@/utils/format'
import { useScheduleForm } from './useScheduleForm'
import { useScheduleExecutions } from './useScheduleExecutions'
import type { UseSchedulesReturn } from './useScheduleTypes'

const PAGE_SIZE = 20

export function useSchedules(): UseSchedulesReturn {
  // ==================== 列表 ====================

  const rows = ref<ApiSchedulePageItem[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const loading = ref(false)
  const typeFilter = ref<string>('')

  async function loadPage() {
    loading.value = true
    try {
      const params: { pageNo: number; pageSize: number; taskType?: string } = { pageNo: pageNo.value, pageSize: PAGE_SIZE }
      if (typeFilter.value) params.taskType = typeFilter.value
      const page = await fetchSchedulePage(params)
      rows.value = page.list
      total.value = page.total
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '定时任务列表加载失败')
    } finally {
      loading.value = false
    }
  }

  watch(typeFilter, () => {
    pageNo.value = 1
    void loadPage()
  })

  // ==================== 子 composable ====================

  const form = useScheduleForm(loadPage)
  const executions = useScheduleExecutions()

  // ==================== 启停 ====================

  async function handleToggle(item: ApiSchedulePageItem) {
    const newEnabled = !item.enabled
    const label = newEnabled ? '启用' : '停用'
    try {
      await toggleSchedule(item.id, { enabled: newEnabled })
      ElMessage.success(`已${label}`)
      await loadPage()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : `${label}失败`)
    }
  }

  // ==================== 删除 ====================

  async function handleDelete(item: ApiSchedulePageItem) {
    await ElMessageBox.confirm(`删除定时任务「${item.name}」？删除不影响已产生的执行记录与报告。`, '删除定时任务', {
      type: 'warning',
      confirmButtonText: '删除',
      confirmButtonClass: 'el-button--danger',
    })
    try {
      await deleteSchedule(item.id)
      ElMessage.success('已删除')
      if (!rows.value.length && pageNo.value > 1) pageNo.value -= 1
      else await loadPage()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  // ==================== 立即执行 ====================

  async function handleExecuteNow(item: ApiSchedulePageItem) {
    if (item.lastExecutionStatus === 'running') {
      ElMessage.warning('上一次执行未结束，请稍后再试')
      return
    }
    await ElMessageBox.confirm(`立即执行定时任务「${item.name}」？`, '立即执行', { type: 'info' })
    try {
      await executeSchedule(item.id)
      ElMessage.success('已触发执行')
      await loadPage()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '执行失败')
    }
  }

  function triggerTypeLabel(type: string): string {
    return type === 'manual' ? '手动' : '定时'
  }

  function formatDuration(ms: number | null): string {
    if (ms == null) return '-'
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(1)}s`
  }

  // ==================== 初始化 ====================

  onMounted(() => {
    void loadPage()
  })

  return {
    // List
    rows,
    total,
    pageNo,
    loading,
    typeFilter,
    loadPage,
    // Form dialog
    showFormDialog: form.showFormDialog,
    editingId: form.editingId,
    formRef: form.formRef,
    saving: form.saving,
    form: form.form,
    isTestPlanTask: form.isTestPlanTask,
    openCreate: form.openCreate,
    openEdit: form.openEdit,
    handleSave: form.handleSave,
    // Scope options
    moduleOptions: form.moduleOptions,
    scopeOptionLoading: form.scopeOptionLoading,
    // Scene picker
    scenePickerVisible: form.scenePickerVisible,
    selectedScenes: form.selectedScenes,
    visibleSceneTags: form.visibleSceneTags,
    sceneTagsExpanded: form.sceneTagsExpanded,
    handleSceneConfirm: form.handleSceneConfirm,
    removeScene: form.removeScene,
    // Environment
    environmentOptions: form.environmentOptions,
    environmentLoading: form.environmentLoading,
    // Cron validation
    cronValidation: form.cronValidation,
    cronValidating: form.cronValidating,
    handleValidateCron: form.handleValidateCron,
    handlePresetSelect: form.handlePresetSelect,
    // Cron builder
    cronBuilder: form.cronBuilder,
    showCronBuilder: form.showCronBuilder,
    applyCronBuilder: form.applyCronBuilder,
    // Toggle / Delete / Execute
    handleToggle,
    handleDelete,
    handleExecuteNow,
    // Execution drawer
    showExecutionDrawer: executions.showExecutionDrawer,
    executionTask: executions.executionTask,
    executionRows: executions.executionRows,
    executionTotal: executions.executionTotal,
    executionPageNo: executions.executionPageNo,
    executionLoading: executions.executionLoading,
    openExecutions: executions.openExecutions,
    loadExecutions: executions.loadExecutions,
    // Helpers
    triggerTypeLabel,
    formatDuration,
    // Constants
    SCHEDULE_TASK_TYPES,
    EXECUTION_SCOPES,
    CRON_PRESETS,
    taskExecutionSummary,
    execStatusLabel,
    execStatusType,
    formatDateTime,
    formatShortDateTime,
    MAX_VISIBLE_SCENE_TAGS: form.MAX_VISIBLE_SCENE_TAGS,
  }
}
