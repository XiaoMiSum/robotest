import type { AiProviderPreset, AiProviderUniqueParam } from '@/types'

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
