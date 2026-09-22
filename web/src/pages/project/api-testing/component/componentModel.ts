import type { ApiComponentScope, ApiComponentType } from '@/types'

// ==================== 常量 ====================

export const COMPONENT_TYPE_OPTIONS: { value: ApiComponentType; label: string }[] = [
  { value: 'preprocessor', label: '前置处理器' },
  { value: 'postprocessor', label: '后置处理器' },
  { value: 'validator', label: '验证器' },
  { value: 'extractor', label: '提取器' },
]

export const COMPONENT_SCOPE_OPTIONS: { value: ApiComponentScope; label: string }[] = [
  { value: 'global', label: '公共' },
  { value: 'workspace', label: '空间' },
  { value: 'project', label: '项目' },
]

/** scope 标签颜色映射 */
export const SCOPE_TAG_TYPE: Record<ApiComponentScope, 'success' | 'warning' | 'info' | undefined> = {
  global: undefined,
  workspace: 'success',
  project: 'info',
}

// ==================== 错误码映射 ====================

interface ErrorCodeLike {
  code?: number
  message?: string
}

/** 业务错误码 → 可操作文案（公共组件号段 17321–17322） */
const COMPONENT_ERROR_MESSAGES: Record<number, string> = {
  1000017321: '公共组件不存在或不属于当前可见范围',
  1000017322: '同作用域下已存在同名公共组件',
}

export function resolveComponentError(err: unknown): string {
  const error = err as ErrorCodeLike
  if (!error || typeof error.code !== 'number') {
    return typeof error?.message === 'string' && error.message ? error.message : '操作失败，请稍后重试'
  }
  return COMPONENT_ERROR_MESSAGES[error.code] ?? error.message ?? '操作失败，请稍后重试'
}

// ==================== 辅助函数 ====================

export function componentTypeLabel(type: ApiComponentType): string {
  return COMPONENT_TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type
}

export function componentScopeLabel(scope: ApiComponentScope): string {
  return COMPONENT_SCOPE_OPTIONS.find((o) => o.value === scope)?.label ?? scope
}
