import type { ApiDebugKeyValue, ApiInterfaceCreateReq, ApiInterfaceDetail, ApiInterfaceStepPayload, ApiInterfaceUpdateReq, ApiInterfaceImportResult, ApiInterfaceVariablePayload, ProjectModule } from '@/types'

/** 列表查询参数：空值不发送，避免后端把空串当过滤条件 */
export function buildInterfaceListQuery(input: {
  pageNo: number
  pageSize: number
  moduleId?: string | null
  search?: string
  status?: string
  view?: string
}): Record<string, unknown> {
  const query: Record<string, unknown> = { pageNo: input.pageNo, pageSize: input.pageSize }
  if (input.moduleId) query.moduleId = input.moduleId
  if (input.search) query.search = input.search
  if (input.status) query.status = input.status
  if (input.view && input.view !== 'all') query.view = input.view
  return query
}

/** el-tag 语义色取值（Element Plus tag type） */
export type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

/** HTTP 方法 → el-tag 语义色 */
export const METHOD_TAG_TYPES: Record<string, TagType> = {
  GET: 'success',
  POST: 'primary',
  PUT: 'warning',
  PATCH: 'warning',
  DELETE: 'danger',
  HEAD: 'info',
  OPTIONS: 'info',
}

export function methodTagType(method: string): TagType {
  return METHOD_TAG_TYPES[method.toUpperCase()] ?? 'info'
}

interface ModuleOption {
  value: string
  label: string
  children: ModuleOption[] | undefined
}

/** 模块树 → el-tree-cascader 数据：叶子文档节点不可选（接口只挂目录） */
export function toSelectableModuleOptions(modules: ProjectModule[]): ModuleOption[] {
  return modules
    .filter((node) => node.type === 'directory')
    .map((node) => {
      const children = node.children?.length ? toSelectableModuleOptions(node.children) : undefined
      return { value: node.id, label: node.name, children }
    })
}

/** 展平模块树为 id→名称映射（列表列展示用，含目录与文档） */
export function flattenModuleNames(modules: ProjectModule[], acc: Map<string, string> = new Map()): Map<string, string> {
  for (const node of modules) {
    acc.set(node.id, node.name)
    if (node.children?.length) flattenModuleNames(node.children, acc)
  }
  return acc
}

/** 公共步骤排序归一化：按数组顺序重排 sortOrder，保证拖拽/移动后序号连续 */
export function reindexSteps(steps: ApiInterfaceStepPayload[]): ApiInterfaceStepPayload[] {
  return steps.map((step, index) => ({ ...step, sortOrder: index }))
}

/** 上移/下移：delta 为 -1/1，越界时返回原数组（引用不变便于调用方短路渲染） */
export function moveStep(steps: ApiInterfaceStepPayload[], id: string, delta: number): ApiInterfaceStepPayload[] {
  const index = steps.findIndex((step) => step.id === id)
  const target = index + delta
  if (index < 0 || target < 0 || target >= steps.length) return steps
  const next = [...steps]
  ;[next[index], next[target]] = [next[target], next[index]]
  return reindexSteps(next)
}

const EMPTY_KEY_VALUE: ApiDebugKeyValue = { key: '', value: '', enabled: true }

function normalizeKeyValues(rows: ApiDebugKeyValue[] | null | undefined): ApiDebugKeyValue[] {
  const list = (rows ?? []).filter((row) => row.key.trim() !== '')
  return list.length ? list : [{ ...EMPTY_KEY_VALUE }]
}

/** 编辑器表单状态：详情回显与新建默认值统一在此构造 */
export interface InterfaceEditorForm {
  name: string
  method: string
  path: string
  description: string
  moduleId: string | null
  status: 'enabled' | 'disabled'
  headers: ApiDebugKeyValue[]
  params: ApiDebugKeyValue[]
  bodyType: 'none' | 'json' | 'form' | 'raw'
  /** json/raw 的文本态；form 用 formRows */
  jsonText: string
  rawText: string
  formRows: ApiDebugKeyValue[]
  responseExampleText: string
}

export function createEditorForm(detail?: ApiInterfaceDetail): InterfaceEditorForm {
  if (!detail) {
    return {
      name: '',
      method: 'GET',
      path: '/',
      description: '',
      moduleId: null,
      status: 'enabled',
      headers: [{ ...EMPTY_KEY_VALUE }],
      params: [{ ...EMPTY_KEY_VALUE }],
      bodyType: 'none',
      jsonText: '',
      rawText: '',
      formRows: [{ ...EMPTY_KEY_VALUE }],
      responseExampleText: '',
    }
  }
  const bodyType = (detail.body?.type as InterfaceEditorForm['bodyType']) ?? 'none'
  let jsonText = ''
  let rawText = ''
  const content = detail.body?.content
  if (bodyType === 'json') jsonText = typeof content === 'string' ? content : JSON.stringify(content ?? {}, null, 2)
  if (bodyType === 'raw') rawText = typeof content === 'string' ? content : ''
  return {
    name: detail.name,
    method: detail.method,
    path: detail.path,
    description: detail.description ?? '',
    moduleId: detail.moduleId ?? null,
    status: detail.status,
    headers: normalizeKeyValues(detail.headers),
    params: normalizeKeyValues(detail.params),
    bodyType,
    jsonText,
    rawText,
    formRows: normalizeKeyValues(formRowsFromBody(content)),
    responseExampleText: detail.responseExample ? JSON.stringify(detail.responseExample, null, 2) : '',
  }
}

function formRowsFromBody(content: unknown): ApiDebugKeyValue[] | null {
  if (!content || typeof content !== 'object') return null
  const record = content as Record<string, unknown>
  if (!Array.isArray(record.content)) return null
  return (record.content as unknown[]).map((entry) => {
    const item = entry as Record<string, unknown>
    return { key: String(item.key ?? ''), value: String(item.value ?? ''), enabled: item.enabled !== false }
  })
}

/** 表单 → 创建请求体；空键行剔除，JSON 文本解析失败返回错误提示而非静默丢弃 */
export function toCreatePayload(form: InterfaceEditorForm): { req: ApiInterfaceCreateReq; error?: string } {
  const req: ApiInterfaceCreateReq = {
    name: form.name.trim(),
    protocol: 'http',
    method: form.method,
    path: form.path.trim(),
    description: form.description.trim() || undefined,
    moduleId: form.moduleId,
    headers: enabledKeyValues(form.headers),
    params: enabledKeyValues(form.params),
    status: form.status,
  }
  switch (form.bodyType) {
    case 'none':
      break
    case 'json': {
      try {
        req.body = { type: 'json', content: form.jsonText.trim() ? JSON.parse(form.jsonText) : {} }
      } catch {
        return { req, error: 'JSON 请求体格式非法，请修正后再保存' }
      }
      break
    }
    case 'form':
      req.body = { type: 'form', content: enabledKeyValues(form.formRows) }
      break
    case 'raw':
      req.body = { type: 'raw', content: form.rawText }
      break
  }
  if (form.responseExampleText.trim()) {
    try {
      req.responseExample = JSON.parse(form.responseExampleText)
    } catch {
      return { req, error: '响应示例 JSON 格式非法，请修正后再保存' }
    }
  }
  return { req }
}

export function toUpdatePayload(form: InterfaceEditorForm, changeVersion: number): { req: ApiInterfaceUpdateReq; error?: string } {
  const base = toCreatePayload(form)
  return { req: { ...base.req, changeVersion }, error: base.error }
}

function enabledKeyValues(rows: ApiDebugKeyValue[]): ApiDebugKeyValue[] {
  return rows
    .filter((row) => row.key.trim() !== '' && row.enabled)
    .map((row) => ({ key: row.key.trim(), value: row.value, enabled: true }))
}

/** 变量全量覆盖载荷：按当前表格顺序重排 sortOrder */
export function buildVariablesPayload(rows: ApiInterfaceVariablePayload[]): ApiInterfaceVariablePayload[] {
  return rows
    .filter((row) => row.name.trim() !== '')
    .map((row, index) => ({ ...row, name: row.name.trim(), sortOrder: index }))
}

/** 导入结果摘要文案：created/updated/failed 为服务端 summary 键 */
export function summarizeImportResult(result: ApiInterfaceImportResult): string {
  const { created = 0, updated = 0, failed = 0 } = result.summary
  return `新建 ${created} · 更新 ${updated} · 失败 ${failed}`
}
