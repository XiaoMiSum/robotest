import type { ApiScheduleExecStatus, ApiScheduleExecutionScope, ApiSchedulePageItem, ApiScheduleTaskType } from '@/types'

// ==================== Cron 预设（定时任务详细设计 4.1） ====================

export interface CronPreset {
  label: string
  expression: string
}

export const CRON_PRESETS: CronPreset[] = [
  { label: '每小时', expression: '0 * * * *' },
  { label: '每天凌晨 2:00', expression: '0 2 * * *' },
  { label: '每周一 2:00', expression: '0 2 * * 1' },
  { label: '每月 1 号 2:00', expression: '0 2 1 * *' },
  { label: '工作日 2:00', expression: '0 2 * * 1-5' },
]

// ==================== 任务类型 ====================

export interface ScheduleTaskTypeOption {
  value: ApiScheduleTaskType
  label: string
}

export const SCHEDULE_TASK_TYPES: ScheduleTaskTypeOption[] = [
  // 值沿用旧契约 scene_execute/import_swagger，展示文案为「测试计划/接口同步」
  { value: 'scene_execute', label: '测试计划' },
  { value: 'import_swagger', label: '接口同步' },
]

// ==================== 执行方式 ====================

export interface ExecutionScopeOption {
  value: ApiScheduleExecutionScope
  label: string
}

export const EXECUTION_SCOPES: ExecutionScopeOption[] = [
  { value: 'all', label: '全部' },
  { value: 'modules', label: '指定模块' },
  { value: 'scenes', label: '指定场景' },
]

/** 列表「执行范围」列摘要：测试计划展示圈选口径，接口同步展示文档 URL */
export function taskExecutionSummary(item: ApiSchedulePageItem): string {
  if (item.taskType === 'import_swagger') return item.openapiUrl ?? '-'
  const count = item.moduleIds?.length ?? item.sceneIds?.length ?? 0
  switch (item.executionScope) {
    case 'all':
      return '全部场景'
    case 'modules':
      return `指定模块×${item.moduleIds?.length ?? 0}`
    case 'scenes':
      return `指定场景×${item.sceneIds?.length ?? 0}`
    default:
      return count > 0 ? `执行范围×${count}` : '旧版绑定对象'
  }
}

// ==================== 执行状态 ====================

export function execStatusLabel(status: ApiScheduleExecStatus | null): string {
  if (!status) return '未执行'
  const map: Record<ApiScheduleExecStatus, string> = {
    success: '成功',
    failed: '失败',
    skipped: '已跳过',
    running: '执行中',
  }
  return map[status] ?? status
}

export function execStatusType(status: ApiScheduleExecStatus | null): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'running') return 'warning'
  return 'info'
}

// ==================== Cron 构建器 ====================

/** 将五段数字字段拼接为 Cron 表达式 */
export function buildCronExpression(fields: {
  minute: string
  hour: string
  day: string
  month: string
  weekday: string
}): string {
  return `${fields.minute} ${fields.hour} ${fields.day} ${fields.month} ${fields.weekday}`
}
