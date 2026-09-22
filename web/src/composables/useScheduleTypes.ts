import type { ComputedRef, Ref } from 'vue'
import type { FormInstance } from 'element-plus'
import type {
  ApiEnvironmentListItem,
  ApiScheduleExecutionItem,
  ApiScheduleExecutionScope,
  ApiSchedulePageItem,
  ApiScheduleSaveReq,
  ProjectModule,
} from '@/types'
import {
  CRON_PRESETS,
  EXECUTION_SCOPES,
  SCHEDULE_TASK_TYPES,
  taskExecutionSummary,
  execStatusLabel,
  execStatusType,
} from '@/pages/project/schedulesModel'
import { formatDateTime, formatShortDateTime } from '@/utils/format'

export interface UseSchedulesReturn {
  // List
  rows: Ref<ApiSchedulePageItem[]>
  total: Ref<number>
  pageNo: Ref<number>
  loading: Ref<boolean>
  typeFilter: Ref<string>
  loadPage: () => Promise<void>

  // Form dialog
  showFormDialog: Ref<boolean>
  editingId: Ref<string | null>
  formRef: Ref<FormInstance | undefined>
  saving: Ref<boolean>
  form: {
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
  }
  isTestPlanTask: ComputedRef<boolean>
  openCreate: () => void
  openEdit: (item: ApiSchedulePageItem) => void
  handleSave: () => Promise<void>

  // Scope options
  moduleOptions: Ref<ProjectModule[]>
  scopeOptionLoading: Ref<boolean>

  // Scene picker
  scenePickerVisible: Ref<boolean>
  selectedScenes: Ref<{ id: string; name: string }[]>
  visibleSceneTags: ComputedRef<{ id: string; name: string }[]>
  sceneTagsExpanded: Ref<boolean>
  handleSceneConfirm: (selected: { id: string; name: string }[]) => void
  removeScene: (id: string) => void

  // Environment
  environmentOptions: Ref<ApiEnvironmentListItem[]>
  environmentLoading: Ref<boolean>

  // Cron validation
  cronValidation: Ref<{ valid: boolean; description: string | null; nextExecutions: string[] | null } | null>
  cronValidating: Ref<boolean>
  handleValidateCron: () => Promise<void>
  handlePresetSelect: (preset: string) => void

  // Cron builder
  cronBuilder: {
    minute: string
    hour: string
    day: string
    month: string
    weekday: string
  }
  showCronBuilder: Ref<boolean>
  applyCronBuilder: () => void

  // Toggle / Delete / Execute
  handleToggle: (item: ApiSchedulePageItem) => Promise<void>
  handleDelete: (item: ApiSchedulePageItem) => Promise<void>
  handleExecuteNow: (item: ApiSchedulePageItem) => Promise<void>

  // Execution drawer
  showExecutionDrawer: Ref<boolean>
  executionTask: Ref<ApiSchedulePageItem | null>
  executionRows: Ref<ApiScheduleExecutionItem[]>
  executionTotal: Ref<number>
  executionPageNo: Ref<number>
  executionLoading: Ref<boolean>
  openExecutions: (item: ApiSchedulePageItem) => Promise<void>
  loadExecutions: () => Promise<void>

  // Helpers
  triggerTypeLabel: (type: string) => string
  formatDuration: (ms: number | null) => string

  // Constants
  SCHEDULE_TASK_TYPES: typeof SCHEDULE_TASK_TYPES
  EXECUTION_SCOPES: typeof EXECUTION_SCOPES
  CRON_PRESETS: typeof CRON_PRESETS
  taskExecutionSummary: typeof taskExecutionSummary
  execStatusLabel: typeof execStatusLabel
  execStatusType: typeof execStatusType
  formatDateTime: typeof formatDateTime
  formatShortDateTime: typeof formatShortDateTime
  MAX_VISIBLE_SCENE_TAGS: number
}
