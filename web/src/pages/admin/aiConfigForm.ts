import type { AiProviderPreset, AiProviderUniqueParam, AiSettingSchemaItem } from '@/types'

/**
 * AI 配置页供应商切换与 extraParams 合并的纯逻辑（抽离以便单测，见 5.3）。
 */

/** 按点号路径写入嵌套对象（如 thinking.type → { thinking: { type } }） */
export function setByPath(target: Record<string, unknown>, dotPath: string, value: unknown): void {
  const segments = dotPath.split('.')
  let cursor = target
  for (let i = 0; i < segments.length - 1; i++) {
    const key = segments[i]
    const next = cursor[key]
    if (typeof next !== 'object' || next === null) {
      cursor[key] = {}
    }
    cursor = cursor[key] as Record<string, unknown>
  }
  cursor[segments[segments.length - 1]] = value
}

/** 按点号路径读取嵌套值，缺失返回 undefined */
export function getByPath(source: Record<string, unknown>, dotPath: string): unknown {
  const segments = dotPath.split('.')
  let cursor: unknown = source
  for (const segment of segments) {
    if (typeof cursor !== 'object' || cursor === null) return undefined
    cursor = (cursor as Record<string, unknown>)[segment]
  }
  return cursor
}

/** 独有配置项模板的默认值集合（供应商切换后初始化表单控件） */
export function buildDefaultUniqueParams(
  params: AiProviderUniqueParam[] | undefined,
): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  if (!params) return result
  for (const param of params) {
    result[param.key] = param.defaultValue
  }
  return result
}

/**
 * 合并独有配置项控件值与高级自定义参数为最终 extraParams。
 * 模板键（含点号路径）优先展开，自定义键随后浅合并（不覆盖模板路径的顶层键）。
 */
export function mergeExtraParams(
  uniqueValues: Record<string, unknown>,
  customParams: Record<string, unknown>,
): Record<string, unknown> {
  const result: Record<string, unknown> = { ...customParams }
  for (const [key, value] of Object.entries(uniqueValues)) {
    if (value === undefined || value === null || value === '') continue
    if (key.includes('.')) {
      setByPath(result, key, value)
    } else {
      result[key] = value
    }
  }
  return result
}

/** 供应商切换后该组默认服务地址（允许用户修改） */
export function resolveDefaultBaseUrl(
  preset: AiProviderPreset | undefined,
  scope: 'chat' | 'embedding',
): string {
  return preset?.defaultBaseUrl?.[scope] ?? ''
}

/** 供应商在指定组的模型名提示 */
export function resolveModelHints(
  preset: AiProviderPreset | undefined,
  scope: 'chat' | 'embedding',
): string[] {
  return preset?.modelHints?.[scope] ?? []
}

/** 供应商在指定组的独有配置项模板 */
export function resolveUniqueParams(
  preset: AiProviderPreset | undefined,
  scope: 'chat' | 'embedding',
): AiProviderUniqueParam[] {
  return preset?.uniqueParams?.[scope] ?? []
}

/**
 * 系统配置项表单纯逻辑（抽离以便单测，见详细设计 5.2 系统配置项表单）。
 */

/** planOrder.weights 三权重之和（非法值按 0 计） */
export function weightsSum(value: unknown): number {
  if (typeof value !== 'object' || value === null) return 0
  const weights = value as Record<string, unknown>
  return ['w1', 'w2', 'w3'].reduce((sum, key) => sum + (Number(weights[key]) || 0), 0)
}

/** 配置项当前值是否偏离内置默认值（数值按数值比较，其余按结构比较） */
export function isSettingModified(item: AiSettingSchemaItem, value: unknown): boolean {
  if (item.type === 'int' || item.type === 'number') {
    return Number(value) !== Number(item.defaultValue)
  }
  return JSON.stringify(value) !== JSON.stringify(item.defaultValue)
}

/** 单项即时校验，返回错误文案或 null（越界/类型/权重之和） */
export function validateSetting(item: AiSettingSchemaItem, value: unknown): string | null {
  if (item.type === 'int' || item.type === 'number') {
    const num = Number(value)
    if (value === null || value === undefined || value === '' || Number.isNaN(num)) {
      return `${item.label}必须为数字`
    }
    if (item.type === 'int' && !Number.isInteger(num)) {
      return `${item.label}必须为整数`
    }
    if (item.min !== null && num < item.min) {
      return `${item.label}不能小于 ${item.min}`
    }
    if (item.max !== null && num > item.max) {
      return `${item.label}不能大于 ${item.max}`
    }
    return null
  }
  if (item.type === 'object') {
    const sum = weightsSum(value)
    if (Math.abs(sum - 1) > 0.001) {
      return `${item.label}三项之和须为 1（当前 ${sum.toFixed(3)}）`
    }
    return null
  }
  return null
}

