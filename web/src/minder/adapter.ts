import type { TestCaseNode, TestReviewSnapshotNode, TestPlanSnapshotNode } from '@/types'

/**
 * 后端节点树 ⇄ kityminder JSON 的映射（从各模式脑图组件抽出以便单测）。
 * 落库字段格式不变：type 为原枚举、priority 为 'P0'-'P3'。
 */
export function caseNodeToKm(node: TestCaseNode): Record<string, unknown> {
  return {
    data: {
      id: node.id, text: node.title, type: node.type, priority: node.priority,
      aiGenerated: node.aiGenerated === true,
    },
    children: node.children.map(caseNodeToKm),
  }
}

export function reviewNodeToKm(node: TestReviewSnapshotNode): Record<string, unknown> {
  return {
    data: {
      id: node.id, originalNodeId: node.originalNodeId, text: node.title, type: node.type, priority: node.priority,
      isAssociated: node.isAssociated, lastMark: node.lastMark,
      reviewStatus: node.lastMark ? { result: node.lastMark } : null,
      relatedBugIds: [],
      aiGenerated: node.aiGenerated === true,
    },
    children: node.children.map(reviewNodeToKm),
  }
}

export function planNodeToKm(node: TestPlanSnapshotNode): Record<string, unknown> {
  return {
    data: {
      id: node.id, originalNodeId: node.originalNodeId, text: node.title, type: node.type, priority: node.priority,
      isAssociated: node.isAssociated, lastResult: node.lastResult,
      executionStatus: node.lastResult ? { result: node.lastResult } : null,
      relatedBugIds: [],
      aiGenerated: node.aiGenerated === true,
    },
    children: node.children.map(planNodeToKm),
  }
}

// kityminder 新建节点会自行生成短随机 id（如 dk9uc1zn6q00），后端主键是 UUID，非 UUID 一律重发
export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

// UUID v7：时间有序，作数据库主键可保持索引局部性、减少页分裂（randomUUID 是无序的 v4）
export function uuidv7(): string {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  const ts = Date.now()
  // 前 48 位写入毫秒时间戳（大端序）
  for (let i = 5; i >= 0; i--) {
    bytes[i] = Math.floor(ts / 2 ** (8 * (5 - i))) & 0xff
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x70
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
