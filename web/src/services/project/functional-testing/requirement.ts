import { get, post, put, del } from '@/services'
import type {
  PageResult,
  RequirementDetail,
  RequirementPoolItem,
  RequirementSummary,
} from '@/types'

// ==================== 需求池（US-AI-004） ====================

export function fetchRequirements(params: {
  keyword?: string
  // 状态筛选：缺省返回全部；除需求池管理页外，取数点必须显式传 'active'（需求规格 3.2.4）
  status?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<RequirementPoolItem>> {
  return get('/project/requirements', { ...params })
}

export function getRequirement(id: string): Promise<RequirementDetail> {
  return get(`/project/requirements/${id}`)
}

export function createRequirement(data: {
  title: string
  content: string
  sourceUrl?: string
}): Promise<string> {
  return post('/project/requirements', data)
}

export function updateRequirement(
  id: string,
  data: {
    title?: string
    content?: string
    // 三态语义：undefined 不修改、空串清空、非空更新
    sourceUrl?: string
  },
): Promise<void> {
  return put(`/project/requirements/${id}`, data)
}

export function deleteRequirement(id: string): Promise<void> {
  return del(`/project/requirements/${id}`)
}

export function archiveRequirement(id: string, archived: boolean): Promise<void> {
  return put(`/project/requirements/${id}/archive`, { archived })
}

// AI 拆分批量入库（US-AI-019，3.1.7）：仅包含已勾选条目，返回实际入库条数
export function batchCreateRequirements(data: {
  items: {
    title: string
    content: string
    sourceUrl?: string
    aiGenerated?: boolean
  }[]
}): Promise<{ count: number }> {
  return post('/project/requirements/batch', data)
}

// 文档关联需求条目（US-AI-004 3.1.5）
export function getDocumentRequirements(docId: string): Promise<RequirementSummary[]> {
  return get(`/project/documents/${docId}/requirements`)
}

export function setDocumentRequirements(docId: string, requirementIds: string[]): Promise<void> {
  return put(`/project/documents/${docId}/requirements`, { requirementIds })
}
