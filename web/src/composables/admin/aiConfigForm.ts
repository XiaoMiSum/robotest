import type {
  AiConfigEmbeddingGroup,
  AiConfigSavePayload,
  AiProviderPreset,
  AiProviderUniqueParam,
  AiSettingSchemaGroup,
  AiSettingSchemaItem,
} from '@/types'

export interface AiChatModelValidationState {
  name: string
  provider: string
  baseUrl: string
  model: string
  apiKey: string
  apiKeyConfigured: boolean
}

export interface AiEmbeddingValidationState {
  provider: string
  baseUrl: string
  model: string
  dimension: number | null
  apiKey: string
  apiKeyConfigured: boolean
}

function isBlankText(value: string): boolean {
  return !value.trim()
}

export function validateChatModelForm(
  form: AiChatModelValidationState,
  mode: 'create' | 'edit',
): string | null {
  if (isBlankText(form.name)) return '请输入显示名'
  if (isBlankText(form.provider)) return '请选择供应商'
  if (isBlankText(form.baseUrl)) return '请输入服务地址'
  if (isBlankText(form.model)) return '请输入模型名'
  if ((mode === 'create' || !form.apiKeyConfigured) && isBlankText(form.apiKey)) {
    return '请输入 API 密钥'
  }
  return null
}

export function validateEmbeddingForm(form: AiEmbeddingValidationState): string | null {
  if (isBlankText(form.provider)) return '请选择供应商'
  if (isBlankText(form.model)) return '请输入模型名'
  if (isBlankText(form.baseUrl)) return '请输入服务地址'
  if (form.dimension === null) return '请输入向量维度'
  if (!Number.isInteger(form.dimension) || form.dimension < 1 || form.dimension > 2000) {
    return '向量维度必须在 1-2000 之间'
  }
  if (!form.apiKeyConfigured && isBlankText(form.apiKey)) return '请输入 API 密钥'
  return null
}

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
 * 按给定供应商预设抽取 extraParams 中的独有配置项编辑值（含点号路径），
 * 缺失项回填默认值以保持与后端 schema 口径一致。
 */
export function extractUniqueValuesForScope(
  extraParams: Record<string, unknown>,
  scope: 'chat' | 'embedding',
  preset: AiProviderPreset | undefined,
): Record<string, unknown> {
  const values: Record<string, unknown> = {}
  for (const param of resolveUniqueParams(preset, scope)) {
    const value = param.key.includes('.')
      ? getByPath(extraParams ?? {}, param.key)
      : (extraParams ?? {})[param.key]
    values[param.key] = value !== undefined ? value : param.defaultValue
  }
  return values
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

/** 系统配置项全量校验，返回首个错误文案，全部通过返回 null */
export function collectSettingErrors(
  groups: AiSettingSchemaGroup[],
  form: Record<string, unknown>,
): string | null {
  for (const group of groups) {
    for (const item of group.items) {
      const error = validateSetting(item, form[item.key])
      if (error) return error
    }
  }
  return null
}

/** 系统配置计数：总数与已修改数（系统配置卡头徽标与 KPI 行共用同一口径） */
export interface SettingsStats {
  total: number
  modified: number
}

export function settingsStats(
  groups: AiSettingSchemaGroup[],
  form: Record<string, unknown>,
): SettingsStats {
  let total = 0
  let modified = 0
  for (const group of groups) {
    for (const item of group.items) {
      total += 1
      if (isSettingModified(item, form[item.key])) modified += 1
    }
  }
  return { total, modified }
}

export interface SettingsGroupFilter {
  query: string
  modifiedOnly: boolean
}

/** 配置项检索面文本与 hint 渲染同源（标签+说明+默认值），保证搜到的就是看到的 */
function settingSearchText(item: AiSettingSchemaItem): string {
  const { defaultValue } = item
  const text =
    typeof defaultValue === 'object' && defaultValue !== null
      ? JSON.stringify(defaultValue)
      : String(defaultValue)
  return `${item.label}${item.description}（默认 ${text}）`.toLowerCase()
}

/**
 * 系统配置检索过滤（对齐 demo applySetFilter 语义）：组名命中整组保留，
 * 字段按检索面文本命中裁剪；组内无命中整组剔除；仅看已修改叠加为与条件。
 */
export function filterSettingGroups(
  groups: AiSettingSchemaGroup[],
  form: Record<string, unknown>,
  filter: SettingsGroupFilter,
): AiSettingSchemaGroup[] {
  const q = filter.query.trim().toLowerCase()
  return groups
    .map((group) => {
      const nameHit = !!q && group.groupLabel.toLowerCase().includes(q)
      const items = group.items.filter(
        (item) =>
          (!q || nameHit || settingSearchText(item).includes(q)) &&
          (!filter.modifiedOnly || isSettingModified(item, form[item.key])),
      )
      return { ...group, items }
    })
    .filter((group) => group.items.length > 0)
}

/** Embedding 组载荷来源：form 取表单编辑值（含高级参数 JSON 解析，失败抛错）；saved 取已保存配置（不再解析） */
export type EmbeddingPayloadSource =
  | {
      kind: 'form'
      group: {
        provider: string
        baseUrl: string
        model: string
        dimension: number | null
        apiKey: string
        uniqueValues: Record<string, unknown>
        customParams: string
      } | null
    }
  | { kind: 'saved'; group: AiConfigEmbeddingGroup | null }

/** 核心项全空即视为未配置（与后端 isEmbeddingGroupEmpty 口径一致） */
export function isEmbeddingGroupEmpty(group: {
  provider: string
  baseUrl: string
  model: string
  dimension: number | null
  apiKey: string
}): boolean {
  return (
    !group.provider &&
    !group.baseUrl &&
    !group.model &&
    group.dimension == null &&
    !group.apiKey
  )
}

/** 组装配置保存载荷；form 源解析高级参数 JSON，非法对象抛错 */
export function buildConfigPayload(input: {
  enabled: boolean
  embedding: EmbeddingPayloadSource
  settings: Record<string, unknown>
}): AiConfigSavePayload {
  let embedding: AiConfigSavePayload['embedding'] = null
  if (input.embedding.kind === 'saved') {
    if (input.embedding.group) {
      embedding = {
        provider: input.embedding.group.provider,
        baseUrl: input.embedding.group.baseUrl,
        model: input.embedding.group.model,
        dimension: input.embedding.group.dimension,
        apiKey: null,
        extraParams: input.embedding.group.extraParams,
      }
    }
  } else if (input.embedding.group && !isEmbeddingGroupEmpty(input.embedding.group)) {
    const text = input.embedding.group.customParams
    let custom: Record<string, unknown> = {}
    if (text.trim()) {
      let parsed: unknown
      try {
        parsed = JSON.parse(text)
      } catch {
        throw new Error('Embedding 高级参数必须为 JSON 对象')
      }
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
        throw new Error('Embedding 高级参数必须为 JSON 对象')
      }
      custom = parsed as Record<string, unknown>
    }
    embedding = {
      provider: input.embedding.group.provider,
      baseUrl: input.embedding.group.baseUrl,
      model: input.embedding.group.model,
      dimension: input.embedding.group.dimension,
      apiKey: input.embedding.group.apiKey || null,
      extraParams: mergeExtraParams(input.embedding.group.uniqueValues, custom),
    }
  }
  return {
    enabled: input.enabled,
    embedding,
    settings: { ...input.settings },
  }
}

