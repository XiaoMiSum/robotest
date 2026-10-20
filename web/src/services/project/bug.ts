import api, { get, post, put, patch, del, getContextHeaders, getAccessToken } from '@/services'
import { createSseFrameParser } from '@/utils/sse'
import type {
  BugAttachment,
  BugDetail,
  BugDuplicateCheckReq,
  BugDuplicateCheckResp,
  BugDraftSuggestReq,
  BugDraftSuggestResp,
  BugListItem,
  BugLog,
  BugPriority,
  BugResolution,
  BugSeverity,
  BugStatus,
  BugType,
  BugAnalysisQuery,
  BugMetricsResp,
  BugTrendsResp,
  PageResult,
} from '@/types'

// ==================== 缺陷管理 ====================

export function fetchBugs(params: {
  status?: BugStatus | ''
  severity?: BugSeverity | ''
  priority?: BugPriority | ''
  bugType?: BugType | ''
  assigneeId?: string
  reporterId?: string
  resolvedBy?: string
  closedBy?: string
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<BugListItem>> {
  return get('/project/bugs', { ...params })
}

export function createBug(data: {
  title: string
  severity: BugSeverity
  priority: BugPriority
  bugType: BugType
  reproSteps?: string
  moduleId?: string
  keywords?: string
  dueDate?: string
  assigneeId?: string
  relatedCaseId?: string
  relatedPlanId?: string
}): Promise<string> {
  return post('/project/bugs', data)
}

export function getBugDetail(id: string): Promise<BugDetail> {
  return get(`/project/bugs/${id}`)
}

export function updateBug(
  id: string,
  data: {
    title?: string
    severity?: BugSeverity
    priority?: BugPriority
    bugType?: BugType
    reproSteps?: string
    moduleId?: string
    keywords?: string
    dueDate?: string
    assigneeId?: string | null
    // 三态语义：undefined=不修改、空串=清空、UUID 串=更新
    relatedCaseId?: string
    relatedPlanId?: string
  },
): Promise<void> {
  return put(`/project/bugs/${id}`, data)
}

export function getBugLogs(id: string): Promise<BugLog[]> {
  return get(`/project/bugs/${id}/logs`)
}

export function changeBugStatus(
  id: string,
  data: {
    status: BugStatus
    comment?: string
    resolution?: BugResolution
    duplicateOfBugId?: string
  },
): Promise<void> {
  return patch(`/project/bugs/${id}/status`, data)
}

export function confirmBug(id: string): Promise<void> {
  return patch(`/project/bugs/${id}/confirm`)
}

export function assignBug(id: string, assigneeId: string): Promise<void> {
  return put(`/project/bugs/${id}/assign`, { assigneeId })
}

// ==================== 缺陷分析（详设 3.1–3.3、3.8） ====================

/** 趋势查询：统计实时计算，日期为 UTC 日历日，缺省最近 30 天 */
export function fetchBugTrends(query: BugAnalysisQuery = {}): Promise<BugTrendsResp> {
  return get('/project/bugs/analysis/trends', { ...query })
}

/** 质量度量：比率与分布分母统一为区间内新增缺陷 */
export function fetchBugMetrics(query: BugAnalysisQuery = {}): Promise<BugMetricsResp> {
  return get('/project/bugs/analysis/metrics', { ...query })
}

/** 录入时重复检测：同步向量检索，向量未就绪回执 1000018258（入口置灰） */
export function checkBugDuplicates(data: BugDuplicateCheckReq): Promise<BugDuplicateCheckResp> {
  return post('/project/bugs/duplicates/check', data)
}

/** 建议 SSE 流错误：前置校验失败（1000018287 / 1000018118）、流内 error 事件与连接中断统一承载 */
export class BugSuggestStreamError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'BugSuggestStreamError'
    this.code = code
  }
}

function readSuggestError(body: string): BugSuggestStreamError | null {
  try {
    const parsed = JSON.parse(body) as { code?: number; msg?: string }
    if (typeof parsed.code === 'number') {
      return new BugSuggestStreamError(parsed.code, parsed.msg || '请求失败')
    }
  } catch {
    // 非 Result 包装（如网关 HTML），走 HTTP 状态兜底
  }
  return null
}

/**
 * 新建缺陷实时建议（详设 3.5）：fetch 直读 SSE 事件流，done 终态一次给出全部建议。
 *
 * 注意：fetch 路径不经过 axios 拦截器，401 不会触发刷新链，以错误码抛出由调用方提示；
 * 无客户端总超时——事件到达与后端 15s 心跳即保活（progress 不承载 UI 语义，仅示连接存活）。
 */
export async function suggestBugDraft(data: BugDraftSuggestReq): Promise<BugDraftSuggestResp> {
  const path = '/project/bugs/analysis/suggest'
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    ...getContextHeaders({ url: path, method: 'post' }),
  }
  const token = getAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
    })
  } catch {
    throw new BugSuggestStreamError(0, '连接中断，请重试')
  }

  const contentType = response.headers.get('Content-Type') ?? ''
  if (!response.ok || !contentType.includes('text/event-stream')) {
    const body = await response.text()
    throw (
      readSuggestError(body) ??
      new BugSuggestStreamError(response.status, `请求失败（HTTP ${response.status}）`)
    )
  }

  const reader = response.body?.getReader()
  if (!reader) throw new BugSuggestStreamError(0, '连接中断，请重试')

  const outcome: { resp?: BugDraftSuggestResp; error?: BugSuggestStreamError } = {}
  const decoder = new TextDecoder()
  const parser = createSseFrameParser((frame) => {
    if (frame.name === 'done') {
      outcome.resp = frame.data as BugDraftSuggestResp
    } else if (frame.name === 'error') {
      const payload = frame.data as { code?: number; msg?: string }
      outcome.error = new BugSuggestStreamError(payload.code ?? 0, payload.msg || '建议生成失败')
    }
  })
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      parser.push(decoder.decode(value, { stream: true }))
    }
    parser.flush()
  } catch {
    throw new BugSuggestStreamError(0, '连接中断，请重试')
  } finally {
    reader.releaseLock()
  }

  if (outcome.error) throw outcome.error
  if (outcome.resp) return outcome.resp
  throw new BugSuggestStreamError(0, '建议流异常结束，请重试')
}

// ==================== 缺陷附件 ====================

export function uploadBugAttachment(bugId: string, file: File): Promise<BugAttachment> {
  const formData = new FormData()
  formData.append('file', file)
  // 覆盖默认的 application/json，由浏览器自动生成 multipart 边界
  return api.post(`/project/bugs/${bugId}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as unknown as Promise<BugAttachment>
}

export function fetchBugAttachments(bugId: string): Promise<BugAttachment[]> {
  return get(`/project/bugs/${bugId}/attachments`)
}

// 下载为文件流，拦截器对 Blob 透传，此处触发浏览器保存
export async function downloadBugAttachment(attachmentId: string, fileName: string): Promise<void> {
  const blob = (await api.get(`/project/bugs/attachments/${attachmentId}/download`, {
    responseType: 'blob',
  })) as unknown as Blob
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

export function deleteBugAttachment(attachmentId: string): Promise<void> {
  return del(`/project/bugs/attachments/${attachmentId}`)
}
