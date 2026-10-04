import api, { del, get } from '@/services'
import type { FileAccessUrl, FileItem, FileQueryParams, PageResult } from '@/types'

// ==================== 文件管理（详设 4.1） ====================

export function fetchFiles(params: FileQueryParams): Promise<PageResult<FileItem>> {
  return get('/files', { ...params })
}

/** 平台下载：拦截器对 Blob 透传，此处触发浏览器保存（同缺陷附件模式） */
export async function downloadFile(id: string, fileName: string): Promise<void> {
  const blob = (await api.get(`/files/${id}/download`, {
    responseType: 'blob',
  })) as unknown as Blob
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

/** 换取 presigned 临时地址（<img> 渲染等直连场景；禁止持久化，详设 3.3） */
export function fetchFileAccessUrl(id: string): Promise<FileAccessUrl> {
  return get(`/files/${id}/access-url`)
}

export function deleteFile(id: string): Promise<void> {
  return del(`/files/${id}`)
}
