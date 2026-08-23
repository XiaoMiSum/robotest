import type {
  ApiEnvironmentDetail,
  ApiEnvironmentListItem,
  ApiEnvironmentSaveReq,
  ApiHeaderItem,
  ApiHttpConfigPayload,
  ApiImportResult,
  ApiProcessorType,
  ApiVariable,
  ApiVariablePayload,
  ApiVariableType,
} from '@/types'

// ==================== 常量 ====================

/** 敏感值掩码，与后端 SENSITIVE_MASK 对齐；提交时后端据此沿用旧密文 */
export const SENSITIVE_MASK = '******'

/** 变量名仅字母/数字/下划线（详细设计 3.3.1） */
const VARIABLE_NAME_PATTERN = /^[A-Za-z0-9_]+$/

export const VARIABLE_TYPE_OPTIONS: { value: ApiVariableType; label: string }[] = [
  { value: 'text', label: '文本' },
  { value: 'number', label: '数字' },
  { value: 'sensitive', label: '敏感值' },
]

export interface DriverOption {
  /** JDBC 驱动类名；Redis 免驱动存空串，按 redis:// 协议识别（详细设计 3.1.7） */
  driver: string
  label: string
  urlExample: string
}

/** 需求 3.7.1 五种内置数据源；Redis 由 Ryze 内置客户端支持，连接测试走 RESP PING */
export const DRIVER_OPTIONS: DriverOption[] = [
  {
    driver: 'com.mysql.cj.jdbc.Driver',
    label: 'MySQL',
    urlExample: 'jdbc:mysql://localhost:3306/db?user=root&password=123456',
  },
  {
    driver: 'org.postgresql.Driver',
    label: 'PostgreSQL',
    urlExample: 'jdbc:postgresql://localhost:5432/db?user=postgres&password=123456',
  },
  {
    driver: 'oracle.jdbc.OracleDriver',
    label: 'Oracle',
    urlExample: 'jdbc:oracle:thin:@localhost:1521/ORCLPDB1?user=system&password=oracle',
  },
  {
    driver: 'com.microsoft.sqlserver.jdbc.SQLServerDriver',
    label: 'SQLServer',
    urlExample:
      'jdbc:sqlserver://localhost:1433;databaseName=db;encrypt=true;user=sa;password=YourStrong@Passw0rd',
  },
  {
    driver: '',
    label: 'Redis',
    urlExample: 'redis://localhost:6379/0',
  },
]

export const HTTP_METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'] as const

// ==================== 错误码映射 ====================

interface ErrorCodeLike {
  code?: number
  message?: string
}

/** 业务错误码 → 可操作文案（环境管理号段 7401–7410） */
const ENV_ERROR_MESSAGES: Record<number, string> = {
  1000017401: '环境名称重复，请更换名称',
  1000017402: '环境被测试场景引用，请先在场景中解除引用',
  1000017403: '数据源连接失败',
  1000017404: '环境被定时任务绑定，请先解除绑定',
  1000017405: '环境不存在或已被删除',
  1000017406: 'HTTP 配置不存在或已被删除',
  1000017407: '数据源不存在或已被删除',
  1000017408: '处理器不存在或已被删除',
  1000017409: '变量不存在或已被删除',
  1000017410: '变量已存在',
}

/** 校验类错误（1xxx 段）统一提示；7403 提取后端冒号后详情；其余透传后端消息 */
export function resolveEnvironmentError(err: unknown): string {
  const error = err as ErrorCodeLike
  if (!error || typeof error.code !== 'number') {
    return typeof error?.message === 'string' && error.message ? error.message : '操作失败，请稍后重试'
  }
  const mapped = ENV_ERROR_MESSAGES[error.code]
  if (!mapped) return error.message || '操作失败，请稍后重试'
  if (error.code === 1000017403 && error.message) {
    // 后端格式「数据源连接测试失败：<原因>」，截取原因拼接可读文案
    const separatorIndex = error.message.indexOf('：')
    const detail = separatorIndex >= 0 ? error.message.slice(separatorIndex + 1) : error.message
    return detail ? `${mapped}：${detail}` : mapped
  }
  return mapped
}

// ==================== 列表排序与导入结果 ====================

/** 默认环境置顶，其余按 sortOrder 升序（交互设计 2.2） */
export function sortEnvironments(list: ApiEnvironmentListItem[]): ApiEnvironmentListItem[] {
  return [...list].sort((a, b) => {
    if (a.isDefault !== b.isDefault) return a.isDefault ? -1 : 1
    return a.sortOrder - b.sortOrder || a.name.localeCompare(b.name)
  })
}

export function formatImportResult(result: ApiImportResult): string {
  const parts: string[] = []
  if (result.createdCount) parts.push(`新增 ${result.createdCount} 个`)
  if (result.overwrittenCount) parts.push(`覆盖 ${result.overwrittenCount} 个`)
  if (result.skippedCount) parts.push(`跳过 ${result.skippedCount} 个`)
  return parts.length ? `导入完成：${parts.join('、')}` : '未发生任何变更'
}

// ==================== 变量校验 ====================

export function isValidVariableName(name: string): boolean {
  return VARIABLE_NAME_PATTERN.test(name)
}

/**
 * 行级校验：返回错误文案或 null。
 * sensitive 允许值为空（留空沿用旧值），number 必须可解析为数字。
 */
export function validateVariableRow(
  row: Pick<ApiVariable, 'name' | 'value' | 'type'>,
  otherNames: Set<string>,
): string | null {
  if (!row.name) return '变量名不能为空'
  if (!isValidVariableName(row.name)) return '变量名仅允许字母、数字与下划线'
  if (otherNames.has(row.name)) return '变量名已存在'
  if (row.type === 'number' && row.value && Number.isNaN(Number(row.value))) {
    return '数字类型取值非法'
  }
  return null
}

/** 变量子资源提交体：敏感值空值/掩码原样上送，由后端沿用旧密文 */
export function toVariablePayloads(variables: ApiVariable[]): ApiVariablePayload[] {
  return variables.map((v) => ({
    name: v.name,
    type: v.type,
    description: v.description || undefined,
    value: v.value || undefined,
  }))
}

// ==================== 详情 ↔ 表单映射 ====================

export interface EnvironmentEditForm {
  name: string
  description: string
  isDefault: boolean
}

export function detailToForm(detail: ApiEnvironmentDetail): EnvironmentEditForm {
  return { name: detail.name, description: detail.description ?? '', isDefault: detail.isDefault }
}

function normalizeHeaders(headers: ApiHeaderItem[] | undefined): ApiHeaderItem[] {
  return (headers ?? []).map((h) => ({
    key: h.key ?? '',
    value: h.value ?? '',
    enabled: h.enabled !== false,
  }))
}

/** 详情 → 聚合保存体：子资源全量回传，避免未展示段落被意外清空；数据源可传入本地编辑后的列表覆写 */
export function buildSavePayload(
  form: EnvironmentEditForm,
  detail: ApiEnvironmentDetail,
  httpConfigs: ApiHttpConfigPayload[],
  dataSources?: ApiEnvironmentSaveReq['dataSources'],
): ApiEnvironmentSaveReq {
  return {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    isDefault: form.isDefault,
    httpConfigs: httpConfigs.map((config) => ({ ...config, headers: normalizeHeaders(config.headers) })),
    variables: toVariablePayloads(detail.variables),
    dataSources: dataSources ?? detail.dataSources.map((ds) => ({
      name: ds.name,
      refName: ds.refName,
      driver: ds.driver,
      url: ds.url,
      maxPoolSize: ds.maxPoolSize,
    })),
    processors: detail.processors.map((p) => ({
      processorType: p.processorType,
      name: p.name,
      config: p.config,
      sortOrder: p.sortOrder,
      enabled: p.enabled,
    })),
  }
}

export function createEmptyHttpConfig(index: number): ApiHttpConfigPayload & { id?: string } {
  return {
    name: `配置 ${index}`,
    refName: `http_${index}`,
    baseUrl: '',
    defaultMethod: 'GET',
    headers: [],
    timeoutMs: 30000,
    connectTimeoutMs: 10000,
    followRedirects: true,
    verifySsl: true,
    isDefault: false,
  }
}

/** 环境变量批量导入 JSON 解析：仅接受对象数组，字段宽松校验 */
export function parseVariablesJson(raw: string):
  | { ok: true; rows: ApiVariablePayload[] }
  | { ok: false; error: string } {
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return { ok: false, error: 'JSON 格式非法' }
  }
  if (!Array.isArray(parsed)) return { ok: false, error: '应为变量对象数组，如 [{"name":"K","value":"V"}]' }
  const rows: ApiVariablePayload[] = []
  for (const item of parsed as unknown[]) {
    if (typeof item !== 'object' || item === null) return { ok: false, error: '数组中存在非对象元素' }
    const record = item as Record<string, unknown>
    const name = typeof record['name'] === 'string' ? record['name'] : ''
    if (!name || !isValidVariableName(name)) {
      return { ok: false, error: `变量名非法：${String(record['name'] ?? '(空)')}（仅字母/数字/下划线）` }
    }
    const type = (typeof record['type'] === 'string' ? record['type'] : 'text') as ApiVariableType
    rows.push({
      name,
      type: VARIABLE_TYPE_OPTIONS.some((option) => option.value === type) ? type : 'text',
      value: typeof record['value'] === 'string' ? record['value'] : undefined,
      description: typeof record['description'] === 'string' ? record['description'] : undefined,
    })
  }
  return { ok: true, rows }
}

/** 处理器类别标签 */
export function processorTypeLabel(type: ApiProcessorType): string {
  return type === 'preprocessor' ? '前置' : '后置'
}
