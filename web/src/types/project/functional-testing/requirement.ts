/** 需求池条目状态：active 参与关联与引用，archived 归档只读（需求规格 3.2.4） */
export type RequirementStatus = 'active' | 'archived'

/** 需求池条目列表项 */
export interface RequirementPoolItem {
  id: string
  title: string
  sourceUrl: string | null
  status: RequirementStatus
  createdBy: string
  creatorName: string | null
  updatedAt: string
}

/** 需求池条目详情 */
export interface RequirementDetail {
  id: string
  title: string
  content: string
  sourceUrl: string | null
  status: RequirementStatus
  createdBy: string
  creatorName: string | null
  updatedBy: string
  createdAt: string
  updatedAt: string
}

/** 需求池条目摘要（文档关联查询与条目选取器共用） */
export interface RequirementSummary {
  id: string
  title: string
}
