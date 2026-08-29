import type { ApiComponentListItem, ApiComponentType } from '@/types'

/** 处理器内嵌提取器行，与提取器资产（ExtractorForm）字段一致，保证从公共组件复制后结构可回读 */
export interface ProcessorExtractor {
  enabled: boolean
  source: string
  expression: string
  variableName: string
  description: string
}

export function isProcessorComponentType(type: ApiComponentType): boolean {
  return type === 'preprocessor' || type === 'postprocessor'
}

/** 处理器基础信息（启用/排序号）与新配置合并时的默认值 */
export function defaultProcessorConfig(): Record<string, unknown> {
  return { enabled: true, sortOrder: 0 }
}

/** 组件基础信息默认值：启用各类型均有，排序号仅处理器类组件 */
export function defaultComponentConfig(type: ApiComponentType): Record<string, unknown> {
  return isProcessorComponentType(type) ? defaultProcessorConfig() : { enabled: true }
}

/** 解析公共组件 config 为对象，无法解析返回空对象（避免引入崩溃） */
export function parseComponentConfig(config: string | null): Record<string, unknown> {
  if (!config) return {}
  try {
    const parsed: unknown = JSON.parse(config)
    if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
    return {}
  } catch {
    return {}
  }
}

/** 提取器资产 → 处理器内嵌提取器行（复制引入，独立副本） */
export function extractorsFromComponents(items: ApiComponentListItem[]): ProcessorExtractor[] {
  return items.map((item) => toExtractorItem(parseComponentConfig(item.config)))
}

function toExtractorItem(raw: Record<string, unknown>): ProcessorExtractor {
  return {
    enabled: raw.enabled !== false,
    source: typeof raw.source === 'string' ? raw.source : '',
    expression: typeof raw.expression === 'string' ? raw.expression : '',
    variableName: typeof raw.variableName === 'string' ? raw.variableName : '',
    description: typeof raw.description === 'string' ? raw.description : '',
  }
}