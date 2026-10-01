import type {
  ApiBuiltinFunctionGroup,
  ApiCustomFunctionListItem,
  ApiFunctionScope,
} from '@/types'

// ==================== 错误码映射 ====================

const FUNCTION_ERROR_MESSAGES: Record<number, string> = {
  1000017021: '函数不存在或不属于当前可见范围',
  1000017022: '函数名与内置函数重名或同作用域已存在同名函数',
  1000017023: 'Groovy 脚本编译失败',
  1000017024: '函数试算执行失败',
}

export function resolveFunctionError(err: unknown): string {
  if (err instanceof Error) {
    const code = (err as Error & { code?: number }).code
    if (code && FUNCTION_ERROR_MESSAGES[code]) {
      return FUNCTION_ERROR_MESSAGES[code]
    }
    return err.message
  }
  return '操作失败，请重试'
}

// ==================== 作用域 ====================

const SCOPE_LABELS: Record<ApiFunctionScope, string> = {
  project: '项目',
  workspace: '空间',
  global: '公共',
}

export function formatScopeLabel(scope: ApiFunctionScope): string {
  return SCOPE_LABELS[scope] ?? scope
}

const SCOPE_OPTIONS: { value: ApiFunctionScope; label: string }[] = [
  { value: 'project', label: '项目' },
  { value: 'workspace', label: '空间' },
  { value: 'global', label: '公共' },
]

export { SCOPE_OPTIONS }

// ==================== 表达式构建 ====================

/** 根据函数名和参数值构建调用表达式 */
export function buildEvaluateExpression(name: string, params: Record<string, string>): string {
  const args = Object.values(params)
    .filter((v) => v !== '')
    .join(', ')
  return `\${${name}(${args})}`
}

// ==================== 函数列表过滤 ====================

export function filterFunctions(
  builtinGroups: ApiBuiltinFunctionGroup[],
  customList: ApiCustomFunctionListItem[],
  keyword: string,
): { builtin: ApiBuiltinFunctionGroup[]; custom: ApiCustomFunctionListItem[] } {
  const kw = keyword.trim().toLowerCase()
  if (!kw) {
    return { builtin: builtinGroups, custom: customList }
  }
  const filteredBuiltin = builtinGroups
    .map((group) => ({
      ...group,
      functions: group.functions.filter(
        (fn) =>
          fn.name.toLowerCase().includes(kw) ||
          fn.description.toLowerCase().includes(kw),
      ),
    }))
    .filter((group) => group.functions.length > 0)
  const filteredCustom = customList.filter(
    (fn) =>
      fn.name.toLowerCase().includes(kw) ||
      (fn.description?.toLowerCase().includes(kw) ?? false),
  )
  return { builtin: filteredBuiltin, custom: filteredCustom }
}

// ==================== 标签页 ====================

export type FunctionTab = 'all' | 'builtin' | 'custom'

export const FUNCTION_TAB_OPTIONS: { value: FunctionTab; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'builtin', label: '内置函数' },
  { value: 'custom', label: '自定义函数' },
]

// ==================== 函数参数（paramsDesc 序列化口径，docs/05-interaction-design/07-project-settings/04-project-settings-ui-function.md 1.3） ====================

export interface FunctionParamRow {
  name: string
  required: boolean
  description: string
}

/** 解析参数说明：逗号分隔，段内 `name:描述`，`name?` 为可选参数，描述取首个冒号后文本 */
export function parseParamsDesc(desc: string | null | undefined): FunctionParamRow[] {
  if (!desc) return []
  return desc
    .split(',')
    .map((seg) => seg.trim())
    .filter(Boolean)
    .map((seg) => {
      const colon = seg.indexOf(':')
      const raw = colon === -1 ? seg : seg.slice(0, colon).trim()
      const required = !raw.endsWith('?')
      return {
        name: (required ? raw : raw.slice(0, -1)).trim(),
        required,
        description: colon === -1 ? '' : seg.slice(colon + 1).trim(),
      }
    })
    .filter((row) => row.name !== '')
}

/** 按行序序列化参数说明：必填 `name:描述`、可选 `name?:描述`，参数间 `, ` 分隔 */
export function serializeParamsDesc(rows: FunctionParamRow[]): string {
  return rows
    .filter((row) => row.name.trim() !== '')
    .map((row) => `${row.name.trim()}${row.required ? '' : '?'}:${row.description.trim()}`)
    .join(', ')
}

export interface ParamRowError {
  index: number
  message: string
}

/** 参数行校验：名称必填且函数内唯一，名称禁 `,` `:` `?`，描述禁 `,` */
export function validateParamRows(rows: FunctionParamRow[]): ParamRowError[] {
  const errors: ParamRowError[] = []
  const seen = new Set<string>()
  rows.forEach((row, index) => {
    const name = row.name.trim()
    if (name === '') {
      errors.push({ index, message: '请输入参数名' })
      return
    }
    if (/[,:?]/.test(name) || /,/.test(row.description)) {
      errors.push({ index, message: '参数名与描述不支持英文逗号，参数名另不支持冒号与问号' })
      return
    }
    if (seen.has(name)) {
      errors.push({ index, message: `参数「${name}」重复` })
      return
    }
    seen.add(name)
  })
  return errors
}

/** 按行序拼接参数名生成调用签名：`${函数名(参数1, 参数2)}` */
export function buildFunctionSignature(name: string, params: { name: string }[]): string {
  const args = params.map((p) => p.name.trim()).filter(Boolean).join(', ')
  return `\${${name}(${args})}`
}

// ==================== 函数助手：统一函数项（供下拉选择） ====================

export interface UnifiedFunctionItem {
  name: string
  description: string
  signature: string
  params: { name: string; required: boolean; description: string }[]
  example: string
  type: 'builtin' | 'custom'
  scope?: ApiFunctionScope
}

/** 合并内置函数和自定义函数为统一列表，供函数助手选择器使用 */
export function unifyFunctionList(
  builtinGroups: ApiBuiltinFunctionGroup[],
  customList: ApiCustomFunctionListItem[],
): UnifiedFunctionItem[] {
  const items: UnifiedFunctionItem[] = []
  for (const group of builtinGroups) {
    for (const fn of group.functions) {
      items.push({
        name: fn.name,
        description: fn.description,
        signature: fn.signature,
        params: fn.params,
        example: fn.example,
        type: 'builtin',
      })
    }
  }
  for (const fn of customList) {
    const params = parseParamsDesc(fn.paramsDesc)
    const signature = buildFunctionSignature(fn.name, params)
    items.push({
      name: fn.name,
      description: fn.description ?? '',
      signature,
      params,
      example: signature,
      type: 'custom',
      scope: fn.scope,
    })
  }
  return items
}
