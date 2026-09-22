import { get, post, put, del } from '@/services'
import type { DocumentNodes, TestCaseDocument, TestCaseNode } from '@/types'

// ==================== 测试用例文档（V1.2 用例管理） ====================

export function createTestCase(data: {
  moduleId: string | null
  name: string
}): Promise<TestCaseDocument> {
  return post('/project/testcases', data)
}

export function updateTestCase(
  id: string,
  data: { name?: string; moduleId?: string | null; targetIndex?: number },
): Promise<TestCaseDocument> {
  return put(`/project/testcases/${id}`, data)
}

export function deleteTestCase(id: string): Promise<void> {
  return del(`/project/testcases/${id}`)
}

// ==================== 测试用例节点 ====================

export function fetchDocumentNodes(docId: string): Promise<DocumentNodes> {
  return get(`/project/documents/${docId}/nodes`)
}

export function getCaseDetail(caseId: string): Promise<TestCaseNode> {
  return get(`/project/cases/${caseId}`)
}
