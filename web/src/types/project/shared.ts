/** 模块树节点类型 */
export type ModuleType = 'directory' | 'document'

/** 项目级统一模块树节点（V1.2 起，目录与文档混合树） */
export interface ProjectModule {
  id: string
  parentId: string | null
  /** 节点类型：directory = 目录，document = 文档 */
  type: ModuleType
  name: string
  sortOrder: number
  children: ProjectModule[]
}

/** 测试用例文档（V1.2 起，挂载在 ProjectModule 节点下的脑图型用例资产） */
export interface TestCaseDocument {
  id: string
  /** null 表示未分组（文档管理详细设计 2.1） */
  moduleId: string | null
  name: string
  sortOrder: number
  nodeCount: number
  updatedAt: string
}

/** 评审/计划模块快照树节点（目录/文档层级，供详情页左侧文档切换） */
export interface SnapshotModule {
  id: string
  parentId: string | null
  name: string
  type: ModuleType
  sortOrder: number
  children: SnapshotModule[]
}

/** 评审/计划规划的用例选择（原始 documentId/caseId 维度，创建与调整共用） */
export interface PlannedCases {
  documentId: string
  caseIds: string[]
}

/** 用例节点类型 */
export type CaseNodeType = 'case' | 'normal' | 'precondition' | 'step' | 'expected'

/** 测试用例脑图节点 */
export interface TestCaseNode {
  id: string
  /** 所属文档 id，用例明细接口用于定位所在文档 */
  documentId?: string | null
  parentId: string | null
  type: CaseNodeType
  title: string
  priority: string | null
  sortOrder: number
  version: number
  /** AI 生成标识（挂载执行器写入，可手动移除，V1.1） */
  aiGenerated?: boolean
  children: TestCaseNode[]
}

/** 文档布局：模板名 + 各节点自由拖拽偏移（键为节点 data 中的 layout_*_offset 原始键名） */
export interface DocumentLayout {
  template?: string
  offsets?: Record<string, Record<string, { x: number; y: number }>>
}

/** 文档节点响应（脑图根节点 + 布局） */
export interface DocumentNodes {
  node: TestCaseNode
  layout: DocumentLayout | null
}

/** 用例列表项 */
export interface CaseListItem {
  id: string
  documentId: string
  title: string
  type: CaseNodeType
  priority: string | null
  createdAt: string
}
