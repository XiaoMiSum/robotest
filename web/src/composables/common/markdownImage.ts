import { ElMessage } from 'element-plus'
import { fetchFileAccessUrl, uploadFile } from '@/services/files'

// 单张上限与缺陷附件口径一致；大小与类型校验放在编辑器前置执行（文件管理详设 5.2），
// 绕过前端的请求由模块入口的 20MB 与白名单兜底
export const MAX_BODY_IMAGE_SIZE = 10 * 1024 * 1024

const IMAGE_TYPE = /^image\//

const FILE_ID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'

// 正文只持久化稳定下载路径，presigned 地址过期即裂图，禁止回写正文（文件管理详设 3.3）
const DOWNLOAD_PATH = new RegExp(`^/api/files/(${FILE_ID})/download/?$`, 'i')
// 正文/HTML 片段中的稳定下载路径（出现位置不定，供批量换签用）
const DOWNLOAD_PATH_IN_TEXT = new RegExp(`/api/files/(${FILE_ID})/download/?`, 'gi')

// presigned 形态：签名查询串带 X-Amz-，对象键为 objects/{uuid}{ext}（文件管理详设 3.2），
// ext 不在下载地址中，还原时只取资源 ID
const PRESIGNED_URL = new RegExp(`^https?://\\S+?/(${FILE_ID})(?:\\.[a-z0-9]+)?\\?\\S*X-Amz-`, 'i')

// 提前失效，避免图片显示后签名在用户仍在阅读时过期
const EXPIRY_MARGIN_MS = 60_000

export interface BodyImage {
  name: string
  downloadUrl: string
}

interface SignedUrl {
  url: string
  expiresAt: number
}

// 换签按资源缓存：同一张图在编辑预览与详情渲染中复用，跨组件实例共享
const signedUrlCache = new Map<string, SignedUrl>()
// 签名地址 → 资源 ID 反查：编辑器 DOM 被换签后，读取边界据此归一化回稳定路径
const fileIdBySignedUrl = new Map<string, string>()
// 同一张图并发换签去重（MutationObserver 与初始扫描可能同时命中）
const pendingImages = new WeakSet<HTMLImageElement>()

/** 上传正文图片，逐个提交；单张失败只提示并跳过，不阻断其余图片 */
export async function uploadMarkdownImages(files: File[]): Promise<BodyImage[]> {
  const images: BodyImage[] = []
  for (const file of files) {
    if (file.size > MAX_BODY_IMAGE_SIZE) {
      ElMessage.warning(`「${file.name}」超过 10MB，无法插入正文`)
      continue
    }
    if (!IMAGE_TYPE.test(file.type)) {
      ElMessage.warning(`「${file.name}」不是图片，无法插入正文`)
      continue
    }
    try {
      const item = await uploadFile(file)
      images.push({ name: file.name, downloadUrl: item.downloadUrl })
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '图片上传失败')
    }
  }
  return images
}

async function signedUrl(fileId: string): Promise<string> {
  const cached = signedUrlCache.get(fileId)
  if (cached && cached.expiresAt > Date.now()) return cached.url
  const { url, expiresIn } = await fetchFileAccessUrl(fileId)
  signedUrlCache.set(fileId, { url, expiresAt: Date.now() + expiresIn * 1000 - EXPIRY_MARGIN_MS })
  fileIdBySignedUrl.set(url, fileId)
  return url
}

/**
 * 只读渲染前把正文中的稳定下载路径换成签名地址。
 * 仅复用已换签缓存、不同步取签（transform 是同步钩子），未缓存的留给 DOM 换签兜底，
 * 避免先按失效地址加载一次裂图再换签（文件管理详设 3.3）。
 */
export function rewriteToSignedUrls(markdown: string): string {
  return markdown.replace(DOWNLOAD_PATH_IN_TEXT, (path, fileId: string) => {
    const cached = signedUrlCache.get(fileId)
    return cached && cached.expiresAt > Date.now() ? cached.url : path
  })
}

/**
 * 读取边界归一化：把编辑器里被换签的 presigned 地址还原为稳定下载路径。
 * presigned 地址永不入库、永不回写正文（文件管理详设 3.3），
 * 优先走反查表，未换签过的按签名形态识别。
 */
export function normalizeMarkdownImageUrls(markdown: string): string {
  return markdown.replace(/\bhttps?:\/\/[^\s)"'<>]+/g, (url) => {
    const fileId = fileIdBySignedUrl.get(url) ?? PRESIGNED_URL.exec(url)?.[1]
    return fileId ? `/api/files/${fileId}/download` : url
  })
}

// 换签或直连失败时保留文字占位：不留 src 可避免浏览器持续请求失效地址
function markBroken(img: HTMLImageElement): void {
  img.removeAttribute('src')
  img.alt = '图片加载失败'
  img.title = '图片加载失败'
}

async function resolveImage(img: HTMLImageElement): Promise<void> {
  const fileId = DOWNLOAD_PATH.exec(img.getAttribute('src') ?? '')?.[1]
  if (!fileId || pendingImages.has(img)) return
  pendingImages.add(img)
  try {
    const url = await signedUrl(fileId)
    img.src = url
    // 签名地址仍不可达（对象被治理或签名端点不可达）时降级为文字占位
    img.addEventListener('error', () => markBroken(img), { once: true })
  } catch {
    markBroken(img)
  } finally {
    pendingImages.delete(img)
  }
}

function scan(root: HTMLElement): void {
  root.querySelectorAll('img').forEach((img) => void resolveImage(img))
}

/**
 * 解析容器内渲染出的正文图片：把稳定下载路径换成 presigned 地址。
 * 编辑器 DOM 由 Vditor 内核生成、无同步地址转换钩子，只读渲染未命中换签缓存时同理，
 * 故统一按容器观察替换（文件管理详设 3.3）。
 */
export function resolveMarkdownImages(root: HTMLElement): () => void {
  scan(root)
  const observer = new MutationObserver(() => scan(root))
  observer.observe(root, { childList: true, subtree: true })
  return () => observer.disconnect()
}

/** 仅供测试隔离缓存，业务代码不要调用 */
export function clearSignedUrlCache(): void {
  signedUrlCache.clear()
  fileIdBySignedUrl.clear()
}
