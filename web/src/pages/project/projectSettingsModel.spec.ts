import { describe, expect, it } from 'vitest'
import type { ProjectSettingItem } from '@/types'
import {
  buildUpdatePayload,
  DEFAULT_EXPIRE_DAYS,
  EXPIRE_DAY_OPTIONS,
  mapItemsToForm,
  parseExpireDays,
  parseShareEnabled,
  resolveSaveError,
} from './projectSettingsModel'

function item(settingKey: string, settingValue: string): ProjectSettingItem {
  return { domain: 'api_test', settingKey, settingValue, defaultValue: settingValue, explicit: true }
}

describe('mapItemsToForm（交互设计 3.2：未显式配置显示默认值）', () => {
  it('空列表返回注册表默认值：开关关闭、有效期 7 天', () => {
    expect(mapItemsToForm([])).toEqual({ shareEnabled: false, expireDays: DEFAULT_EXPIRE_DAYS })
  })

  it('显式配置映射字符串值到表单类型', () => {
    const form = mapItemsToForm([item('report.share.enabled', 'true'), item('report.share.expire-days', '30')])
    expect(form).toEqual({ shareEnabled: true, expireDays: 30 })
  })

  it('忽略未注册键，不影响已注册键解析', () => {
    const form = mapItemsToForm([item('future.key', 'x'), item('report.share.expire-days', '1')])
    expect(form).toEqual({ shareEnabled: false, expireDays: 1 })
  })

  it('非法有效期取值回落默认 7 天', () => {
    expect(mapItemsToForm([item('report.share.expire-days', '15')]).expireDays).toBe(7)
  })
})

describe('parseExpireDays / parseShareEnabled', () => {
  it.each(['1', '7', '30', '90'])('合法有效期 %s 原样返回', (value) => {
    expect(parseExpireDays(value)).toBe(Number(value))
  })

  it.each(['0', '15', 'abc', '', '-7'])('非法有效期 %s 回落 %i', (value) => {
    expect(parseExpireDays(value)).toBe(DEFAULT_EXPIRE_DAYS)
  })

  it('仅字符串 "true" 视为开启', () => {
    expect(parseShareEnabled('true')).toBe(true)
    expect(parseShareEnabled('false')).toBe(false)
    expect(parseShareEnabled('True')).toBe(false)
  })
})

describe('buildUpdatePayload（整批两键，布尔值字符串化）', () => {
  it('payload 包含两个注册键且值为字符串', () => {
    const payload = buildUpdatePayload({ shareEnabled: true, expireDays: 30 })
    expect(payload.items).toEqual([
      { domain: 'api_test', settingKey: 'report.share.enabled', settingValue: 'true' },
      { domain: 'api_test', settingKey: 'report.share.expire-days', settingValue: '30' },
    ])
  })

  it('合法取值集合为 1/7/30/90', () => {
    expect(EXPIRE_DAY_OPTIONS).toEqual([1, 7, 30, 90])
  })
})

describe('resolveSaveError（交互设计 3.3：保存失败 Toast 明确原因）', () => {
  it('后端 msg 含细节时优先展示', () => {
    const err = Object.assign(new Error('设置值非法（格式或取值范围不满足注册表约束）：report.share.expire-days'), {
      code: 1000017702,
    })
    expect(resolveSaveError(err)).toContain('report.share.expire-days')
  })

  it('msg 为通用占位时按业务全码兜底文案', () => {
    expect(resolveSaveError(Object.assign(new Error('请求失败'), { code: 1000017701 }))).toBe(
      '配置项标识非法，请刷新页面重试',
    )
    expect(resolveSaveError(Object.assign(new Error('请求失败'), { code: 1000017702 }))).toBe('设置值非法，请检查取值范围')
  })

  it('未知错误返回通用保存失败文案', () => {
    expect(resolveSaveError(new Error('网络错误'))).toBe('网络错误')
    expect(resolveSaveError(undefined)).toBe('保存失败')
  })
})
