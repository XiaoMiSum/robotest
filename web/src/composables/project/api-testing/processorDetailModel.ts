import { EXTRACTOR_SOURCES } from '@/composables/project/api-testing/scene/scenesModel'
import {
  isRecord,
  parseHttpProcessorForm,
  parseJdbcProcessorForm,
} from '@/composables/project/api-testing/processorFormModel'

/** 只读配置行呈现形态：纯文本 / 代码块 / 键值对列表 */
export type ProcessorConfigRowKind = 'text' | 'code' | 'kv'

export interface ProcessorKvPair {
  key: string
  value: string
}

export interface ProcessorConfigRow {
  label: string
  kind: ProcessorConfigRowKind
  /** text / code 行展示值，kv 行恒为空串 */
  value: string
  /** kv 行键值对，其余形态恒为空数组 */
  pairs: ProcessorKvPair[]
}

/** 只读提取器行（来源已映射为展示文案） */
export interface ProcessorExtractorRow {
  source: string
  expression: string
  variableName: string
  description: string
}

/** 请求体类型展示文案：与请求配置编辑器的三态 + raw 子类型推断口径一致 */
const BODY_KIND_LABELS: Record<string, string> = {
  none: 'none',
  form: 'x-www-form-urlencoded',
  json: 'raw · json',
  raw: 'raw · text',
}

function textRow(label: string, value: string): ProcessorConfigRow {
  return { label, kind: 'text', value, pairs: [] }
}

function codeRow(label: string, value: string): ProcessorConfigRow {
  return { label, kind: 'code', value, pairs: [] }
}

function kvRow(label: string, pairs: ProcessorKvPair[]): ProcessorConfigRow {
  return { label, kind: 'kv', value: '', pairs }
}

function rowsToPairs(rows: { key: string; value: string }[]): ProcessorKvPair[] {
  return rows
    .filter((row) => row.key.trim())
    .map((row) => ({ key: row.key.trim(), value: row.value }))
}

function optionLabel(options: { value: string; label: string }[], value: string, fallback: string): string {
  return options.find((o) => o.value === value)?.label ?? fallback
}

/** 处理器元素 → 只读配置行（复用表单解析口径，保证详情与编辑器同源） */
export function buildProcessorDetailRows(element: Record<string, unknown>): ProcessorConfigRow[] {
  const inner = isRecord(element.config) ? element.config : {}
  if (element.testclass === 'jdbc') {
    const form = parseJdbcProcessorForm({ testclass: 'jdbc', config: inner })
    const rows = [
      textRow('处理器类型', 'JDBC'),
      textRow('数据源', form.ref || '—'),
      codeRow('SQL 语句', form.sql || '—'),
    ]
    if (form.args.length > 0) rows.push(textRow('参数', form.args.join(', ')))
    return rows
  }
  const form = parseHttpProcessorForm({ testclass: 'http', config: inner })
  const query = rowsToPairs(form.queryRows)
  const headers = rowsToPairs(form.headerRows)
  const rows = [
    textRow('处理器类型', 'HTTP'),
    textRow('环境 HTTP 配置', form.ref || '—'),
    textRow('请求方法', form.method || '—'),
    textRow('路径', form.path || '—'),
  ]
  if (query.length > 0) rows.push(kvRow('Query 参数', query))
  if (headers.length > 0) rows.push(kvRow('请求头', headers))
  rows.push(textRow('请求体类型', BODY_KIND_LABELS[form.bodyKind] ?? 'none'))
  if (form.bodyKind === 'form') {
    const formRows = rowsToPairs(form.formRows)
    if (formRows.length > 0) rows.push(kvRow('表单参数', formRows))
  } else if (form.bodyText.trim()) {
    rows.push(codeRow('请求体', form.bodyText))
  }
  return rows
}

/** 处理器元素 → 只读提取器行（空字段补占位，避免只读表格空列被误读为缺数据） */
export function buildProcessorExtractorRows(element: Record<string, unknown>): ProcessorExtractorRow[] {
  const raw = element.extractors
  if (!Array.isArray(raw)) return []
  return raw.map((item) => {
    const row = isRecord(item) ? item : {}
    const source = typeof row.source === 'string' ? row.source : ''
    return {
      source: source ? optionLabel(EXTRACTOR_SOURCES, source, source) : '—',
      expression: typeof row.expression === 'string' && row.expression ? row.expression : '—',
      variableName: typeof row.variableName === 'string' && row.variableName ? row.variableName : '—',
      description: typeof row.description === 'string' && row.description ? row.description : '—',
    }
  })
}
