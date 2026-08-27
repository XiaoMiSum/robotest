import { describe, expect, it } from 'vitest'
import {
  buildCronExpression,
  CRON_PRESETS,
  execStatusLabel,
  execStatusType,
  SCHEDULE_TASK_TYPES,
} from './schedulesModel'

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
})
