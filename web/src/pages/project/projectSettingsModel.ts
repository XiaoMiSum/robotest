// 项目设置页纯逻辑（抽离以便单测）：响应映射、payload 构建、保存失败文案兜底

import type { ProjectSettingItem, ProjectSettingUpdateReq } from '@/types'

export const SETTING_KEY_SHARE_ENABLED = 'report.share.enabled'
export const SETTING_KEY_EXPIRE_DAYS = 'report.share.expire-days'

/** 分享链接有效期的合法取值（详细设计 2.1.2 注册表约束） */
export const EXPIRE_DAY_OPTIONS = [1, 7, 30, 90]

export const DEFAULT_EXPIRE_DAYS = 7

export interface ProjectSettingsForm {
  shareEnabled: boolean
  expireDays: number
}

// 后端 msg 已含非法键名等细节，仅在 msg 为通用占位时按业务码兜底（7701/7702 的十位全码）
const SETTING_ERROR_MESSAGES: Record<number, string> = {
  1000017701: '配置项标识非法，请刷新页面重试',
  1000017702: '设置值非法，请检查取值范围',
}

const GENERIC_ERROR_MESSAGE = '请求失败'

export function parseShareEnabled(value: string): boolean {
  return value === 'true'
}

export function parseExpireDays(value: string): number {
  const parsed = Number.parseInt(value, 10)
  return EXPIRE_DAY_OPTIONS.includes(parsed) ? parsed : DEFAULT_EXPIRE_DAYS
}

/** 响应 items → 表单状态；未返回的键保持默认值（交互设计 3.2：未显式配置显示默认值） */
export function mapItemsToForm(items: ProjectSettingItem[]): ProjectSettingsForm {
  let shareEnabled = false
  let expireDays = DEFAULT_EXPIRE_DAYS
  for (const item of items) {
    if (item.settingKey === SETTING_KEY_SHARE_ENABLED) {
      shareEnabled = parseShareEnabled(item.settingValue)
    } else if (item.settingKey === SETTING_KEY_EXPIRE_DAYS) {
      expireDays = parseExpireDays(item.settingValue)
    }
  }
  return { shareEnabled, expireDays }
}

/** 整批提交两键；后端注册表白名单校验任一非法即整批拒绝 */
export function buildUpdatePayload(form: ProjectSettingsForm): ProjectSettingUpdateReq {
  return {
    items: [
      { domain: 'api_test', settingKey: SETTING_KEY_SHARE_ENABLED, settingValue: String(form.shareEnabled) },
      { domain: 'api_test', settingKey: SETTING_KEY_EXPIRE_DAYS, settingValue: String(form.expireDays) },
    ],
  }
}

export function resolveSaveError(err: unknown): string {
  if (err instanceof Error && err.message && err.message !== GENERIC_ERROR_MESSAGE) {
    return err.message
  }
  const code = (err as { code?: unknown } | null | undefined)?.code
  if (typeof code === 'number') {
    const mapped = SETTING_ERROR_MESSAGES[code]
    if (mapped) return mapped
  }
  return '保存失败'
}
