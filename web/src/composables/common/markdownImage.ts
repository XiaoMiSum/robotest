import { ElMessage } from 'element-plus'
import { fetchFileAccessUrl, uploadFile } from '@/services/files'

// 单张上限与缺陷附件口径一致；大小与类型校验放在编辑器前置执行（文件管理详设 5.2），
// 绕过前端的请求由模块入口的 20MB 与白名单兜底
export const MAX_BODY_IMAGE_SIZE = 10 * 1024 * 1024

const IMAGE_TYPE = /^image\//

// 正文只持久化稳定下载路径，presigned 地址过期即裂图，禁止回写正文（文件管理详设 3.3）
const DOWNLOAD_PATH =
  /^\/api\/files\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/download\/?$/i

// 提前失效，避免图片显示后签名在用户仍在阅读时过期
const EXPIRY_MARGIN_MS = 60_000

interface SignedUrl {
  url: string
  expiresAt: number
}

// 换签按资源缓存：同一张图在编辑预览与详情渲染中复用，跨组件实例共享
const signedUrlCache = new Map<string, SignedUrl>()
// 同一张图并发换签去重（MutationObserver 与初始扫描可能同时命中）
const pendingImages = new WeakSet<HTMLImageElement>()

/** 上传正文图片，逐个提交；单张失败只提示并跳过，不阻断其余图片 */
export async function uploadMarkdownImages(files: File[]): Promise<string[]> {
  const urls: string[] = []
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
      urls.push(item.downloadUrl)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '图片上传失败')
    }
  }
  return urls
}

async function signedUrl(fileId: string): Promise<string> {
  const cached = signedUrlCache.get(fileId)
  if (cached && cached.expiresAt > Date.now()) return cached.url
  const { url, expiresIn } = await fetchFileAccessUrl(fileId)
  signedUrlCache.set(fileId, { url, expiresAt: Date.now() + expiresIn * 1000 - EXPIRY_MARGIN_MS })
  return url
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
 * 解析容器内 Markdown 渲染出的正文图片：把稳定下载路径换成 presigned 地址。
 * md-editor-v3 无同步图片地址转换钩子，故在渲染后按容器观察替换（文件管理详设 3.3）。
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
}
