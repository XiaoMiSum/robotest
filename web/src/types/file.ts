// ==================== 文件管理（详设 4.2） ====================

/** 文件资源行（上传响应与列表行共用） */
export interface FileItem {
  /** 泛化附件资源 ID */
  id: string
  /** 原始文件名 */
  fileName: string
  /** 字节数 */
  fileSize: number
  /** 内容类型（后端可能为空） */
  contentType: string | null
  /** 上传者 ID */
  uploaderId: string
  /** 上传者姓名（列表回填，上传响应为 null） */
  uploaderName: string | null
  /** 稳定访问地址（相对路径，使用方按此关联） */
  downloadUrl: string
  /** 上传时间（后端格式化字符串） */
  createdAt: string
}

/** 文件分页查询参数 */
export interface FileQueryParams {
  /** 文件名模糊过滤 */
  fileName?: string
  pageNo: number
  pageSize: number
}

/** presigned 临时访问地址（GET /api/files/{id}/access-url） */
export interface FileAccessUrl {
  /** 签名 URL，时效内可直连对象存储；禁止持久化进业务内容 */
  url: string
  /** 有效期（秒） */
  expiresIn: number
}
