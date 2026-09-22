import { describe, expect, it } from 'vitest'
import {
  buildCronExpression,
  CRON_PRESETS,
  EXECUTION_SCOPES,
  execStatusLabel,
  execStatusType,
  SCHEDULE_TASK_TYPES,
  taskExecutionSummary,
} from './schedulesModel'
import type { ApiSchedulePageItem } from '@/types'

describe('schedulesModel', () => {
  // ==================== execStatusLabel ====================

  it('returns placeholder for null status', () => {
    expect(execStatusLabel(null)).toBe('未执行')
  })

  it('maps known statuses to Chinese labels', () => {
    expect(execStatusLabel('success')).toBe('成功')
    expect(execStatusLabel('failed')).toBe('失败')
    expect(execStatusLabel('skipped')).toBe('已跳过')
    expect(execStatusLabel('running')).toBe('执行中')
  })

  // ==================== execStatusType ====================

  it('returns correct Element Plus tag types', () => {
    expect(execStatusType(null)).toBe('info')
    expect(execStatusType('success')).toBe('success')
    expect(execStatusType('failed')).toBe('danger')
    expect(execStatusType('running')).toBe('warning')
    expect(execStatusType('skipped')).toBe('info')
  })

  // ==================== buildCronExpression ====================

  it('joins five fields into a cron expression', () => {
    expect(buildCronExpression({ minute: '0', hour: '2', day: '*', month: '*', weekday: '*' })).toBe('0 2 * * *')
    expect(buildCronExpression({ minute: '*/5', hour: '*', day: '*', month: '*', weekday: '*' })).toBe('*/5 * * * *')
  })

  // ==================== constants ====================

  it('has valid preset expressions', () => {
    expect(CRON_PRESETS.length).toBeGreaterThanOrEqual(5)
    for (const preset of CRON_PRESETS) {
      expect(preset.label).toBeTruthy()
      const segments = preset.expression.split(' ')
      expect(segments).toHaveLength(5)
    }
  })

  it('has valid task types', () => {
    expect(SCHEDULE_TASK_TYPES).toHaveLength(2)
    const values = SCHEDULE_TASK_TYPES.map((t) => t.value)
    expect(values).toContain('scene_execute')
    expect(values).toContain('import_swagger')
  })

  it('has all three execution scopes', () => {
    expect(EXECUTION_SCOPES.map((s) => s.value)).toEqual(['all', 'modules', 'scenes'])
  })

  // ==================== taskExecutionSummary ====================

  function item(partial: Partial<ApiSchedulePageItem>): ApiSchedulePageItem {
    return {
      id: 'task-1',
      taskType: 'scene_execute',
      name: '任务',
      description: null,
      boundObjectId: null,
      boundObjectName: null,
      executionScope: 'all',
      moduleIds: null,
      sceneIds: null,
      openapiUrl: null,
      environmentId: null,
      environmentName: null,
      cronExpression: '0 2 * * *',
      enabled: true,
      lastExecutionStatus: null,
      lastExecutionAt: null,
      nextExecutions: [],
      createdAt: '2026-08-17T00:00:00',
      ...partial,
    }
  }

  it('summarizes import_swagger with document URL', () => {
    expect(taskExecutionSummary(item({ taskType: 'import_swagger', openapiUrl: 'https://e.com/api-docs' })))
      .toBe('https://e.com/api-docs')
  })

  it('summarizes scene_execute all scope', () => {
    expect(taskExecutionSummary(item({ executionScope: 'all' }))).toBe('全部场景')
  })

  it('summarizes module and scene scopes with counts', () => {
    expect(taskExecutionSummary(item({ executionScope: 'modules', moduleIds: ['a', 'b'] })))
      .toBe('指定模块×2')
    expect(taskExecutionSummary(item({ executionScope: 'scenes', sceneIds: ['x'] })))
      .toBe('指定场景×1')
  })

  it('falls back to legacy label for historical tasks', () => {
    expect(taskExecutionSummary(item({ executionScope: null }))).toBe('旧版绑定对象')
  })
})
