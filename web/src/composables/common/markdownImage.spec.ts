// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  uploadFile: vi.fn(),
  fetchFileAccessUrl: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('@/services/files', () => ({
  uploadFile: mocks.uploadFile,
  fetchFileAccessUrl: mocks.fetchFileAccessUrl,
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: mocks.warning, error: mocks.error },
}))

import {
  MAX_BODY_IMAGE_SIZE,
  clearSignedUrlCache,
  normalizeMarkdownImageUrls,
  resolveMarkdownImages,
  rewriteToSignedUrls,
  uploadMarkdownImages,
} from './markdownImage'

const FILE_ID = '7f3a1b2c-1111-4222-8333-444455556666'
const DOWNLOAD_SRC = `/api/files/${FILE_ID}/download`
const SIGNED_URL = 'http://localhost:9000/robotest/objects/x?X-Amz-Signature=abc'

function makeFile(name: string, type: string): File {
  return new File(['content'], name, { type })
}

function makeOversizedFile(name: string): File {
  const file = makeFile(name, 'image/png')
  Object.defineProperty(file, 'size', { value: MAX_BODY_IMAGE_SIZE + 1 })
  return file
}

function mountRoot(): { root: HTMLElement; img: HTMLImageElement } {
  const root = document.createElement('div')
  const img = document.createElement('img')
  img.setAttribute('src', DOWNLOAD_SRC)
  root.appendChild(img)
  document.body.appendChild(root)
  return { root, img }
}

/** 冲刷换签链路的微任务（伪时钟下不能依赖 setTimeout 轮询） */
async function settle(): Promise<void> {
  for (let i = 0; i < 10; i += 1) {
    await Promise.resolve()
  }
}

describe('正文贴图上传（缺陷详设 1.14 正文图片）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('逐个上传并返回稳定下载路径', async () => {
    mocks.uploadFile
      .mockResolvedValueOnce({ downloadUrl: '/api/files/a/download' })
      .mockResolvedValueOnce({ downloadUrl: '/api/files/b/download' })

    const images = await uploadMarkdownImages([
      makeFile('a.png', 'image/png'),
      makeFile('b.jpg', 'image/jpeg'),
    ])

    expect(mocks.uploadFile).toHaveBeenCalledTimes(2)
    expect(images).toEqual([
      { name: 'a.png', downloadUrl: '/api/files/a/download' },
      { name: 'b.jpg', downloadUrl: '/api/files/b/download' },
    ])
  })

  it('超过 10MB 的图片跳过并提示', async () => {
    const images = await uploadMarkdownImages([makeOversizedFile('big.png')])

    expect(mocks.uploadFile).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledWith(expect.stringContaining('超过 10MB'))
    expect(images).toEqual([])
  })

  it('非图片类型跳过并提示', async () => {
    const images = await uploadMarkdownImages([makeFile('doc.pdf', 'application/pdf')])

    expect(mocks.uploadFile).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledWith(expect.stringContaining('不是图片'))
    expect(images).toEqual([])
  })

  it('单张失败只提示，不影响其余图片', async () => {
    mocks.uploadFile.mockRejectedValueOnce(new Error('上传失败')).mockResolvedValueOnce({
      downloadUrl: '/api/files/b/download',
    })

    const images = await uploadMarkdownImages([
      makeFile('a.png', 'image/png'),
      makeFile('b.png', 'image/png'),
    ])

    expect(mocks.error).toHaveBeenCalledWith('上传失败')
    expect(images).toEqual([{ name: 'b.png', downloadUrl: '/api/files/b/download' }])
  })
})

describe('正文图片渲染换签（文件管理详设 3.3）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearSignedUrlCache()
    document.body.innerHTML = ''
    mocks.fetchFileAccessUrl.mockResolvedValue({ url: SIGNED_URL, expiresIn: 900 })
  })

  it('把稳定下载路径换成签名地址', async () => {
    const { root, img } = mountRoot()

    resolveMarkdownImages(root)

    await vi.waitFor(() => expect(img.getAttribute('src')).toBe(SIGNED_URL))
    expect(mocks.fetchFileAccessUrl).toHaveBeenCalledWith(FILE_ID)
  })

  it('外部图片地址不参与换签', async () => {
    const root = document.createElement('div')
    const img = document.createElement('img')
    img.setAttribute('src', 'https://example.com/a.png')
    root.appendChild(img)
    document.body.appendChild(root)

    resolveMarkdownImages(root)

    await Promise.resolve()
    expect(mocks.fetchFileAccessUrl).not.toHaveBeenCalled()
    expect(img.getAttribute('src')).toBe('https://example.com/a.png')
  })

  it('换签失败降级为文字占位', async () => {
    mocks.fetchFileAccessUrl.mockRejectedValue(new Error('1000018021'))
    const { root, img } = mountRoot()

    resolveMarkdownImages(root)

    await vi.waitFor(() => expect(img.alt).toBe('图片加载失败'))
    expect(img.getAttribute('src')).toBeNull()
    expect(img.title).toBe('图片加载失败')
  })

  it('同一资源复用签名缓存', async () => {
    const first = mountRoot()
    resolveMarkdownImages(first.root)
    await vi.waitFor(() => expect(first.img.getAttribute('src')).toBe(SIGNED_URL))

    const second = mountRoot()
    resolveMarkdownImages(second.root)
    await vi.waitFor(() => expect(second.img.getAttribute('src')).toBe(SIGNED_URL))

    expect(mocks.fetchFileAccessUrl).toHaveBeenCalledTimes(1)
  })

  it('签名过期后重新换签', async () => {
    vi.useFakeTimers()
    try {
      vi.setSystemTime(new Date('2026-10-10T10:00:00Z'))
      const first = mountRoot()
      resolveMarkdownImages(first.root)
      await settle()
      expect(first.img.getAttribute('src')).toBe(SIGNED_URL)

      // expiresIn 900s、缓存提前 60s 失效，仍在有效期内
      vi.setSystemTime(new Date('2026-10-10T10:13:59Z'))
      const second = mountRoot()
      resolveMarkdownImages(second.root)
      await settle()
      expect(mocks.fetchFileAccessUrl).toHaveBeenCalledTimes(1)
      expect(second.img.getAttribute('src')).toBe(SIGNED_URL)

      vi.setSystemTime(new Date('2026-10-10T10:14:01Z'))
      const third = mountRoot()
      resolveMarkdownImages(third.root)
      await settle()
      expect(mocks.fetchFileAccessUrl).toHaveBeenCalledTimes(2)
      expect(third.img.getAttribute('src')).toBe(SIGNED_URL)
    } finally {
      vi.useRealTimers()
    }
  })

  it('内容变化后由观察器解析新增图片', async () => {
    const { root } = mountRoot()
    resolveMarkdownImages(root)
    await vi.waitFor(() => expect(mocks.fetchFileAccessUrl).toHaveBeenCalledTimes(1))

    const added = document.createElement('img')
    added.setAttribute('src', '/api/files/8f3a1b2c-1111-4222-8333-444455556666/download')
    root.appendChild(added)

    await vi.waitFor(() => expect(added.getAttribute('src')).toBe(SIGNED_URL))
    expect(mocks.fetchFileAccessUrl).toHaveBeenCalledTimes(2)
  })

  it('停止观察后不再解析新增图片', async () => {
    const { root } = mountRoot()
    const stop = resolveMarkdownImages(root)
    await vi.waitFor(() => expect(mocks.fetchFileAccessUrl).toHaveBeenCalledTimes(1))
    stop()

    const added = document.createElement('img')
    added.setAttribute('src', '/api/files/9f3a1b2c-1111-4222-8333-444455556666/download')
    root.appendChild(added)

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(mocks.fetchFileAccessUrl).toHaveBeenCalledTimes(1)
    expect(added.getAttribute('src')).toBe(
      '/api/files/9f3a1b2c-1111-4222-8333-444455556666/download',
    )
  })
})

describe('presigned 不回写正文（文件管理详设 3.3）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearSignedUrlCache()
    document.body.innerHTML = ''
    mocks.fetchFileAccessUrl.mockResolvedValue({ url: SIGNED_URL, expiresIn: 900 })
  })

  it('编辑器里已换签的地址归一化为稳定下载路径', async () => {
    const { root } = mountRoot()
    resolveMarkdownImages(root)
    await vi.waitFor(() => expect(mocks.fetchFileAccessUrl).toHaveBeenCalledTimes(1))

    expect(normalizeMarkdownImageUrls(`截图：![a](${SIGNED_URL})`)).toBe(
      `截图：![a](${DOWNLOAD_SRC})`,
    )
  })

  it('未换签过的 presigned 地址按签名形态归一化（对象键带扩展名）', () => {
    const presigned =
      'https://oss.example.com/bucket/objects/11111111-2222-3333-4444-555555555555.png' +
      '?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc'

    expect(normalizeMarkdownImageUrls(presigned)).toBe(
      '/api/files/11111111-2222-3333-4444-555555555555/download',
    )
  })

  it('稳定路径与外部地址原样保留', () => {
    const markdown = `![](${DOWNLOAD_SRC}) 与 [外链](https://example.com/a.png?x=1)`
    expect(normalizeMarkdownImageUrls(markdown)).toBe(markdown)
  })

  it('渲染前只用已换签缓存替换，未缓存的保持稳定路径', async () => {
    const markdown = `![a](${DOWNLOAD_SRC}) ![b](/api/files/22222222-3333-4444-5555-666666666666/download)`
    expect(rewriteToSignedUrls(markdown)).toBe(markdown)

    const { root } = mountRoot()
    resolveMarkdownImages(root)
    await vi.waitFor(() => expect(mocks.fetchFileAccessUrl).toHaveBeenCalledTimes(1))

    expect(rewriteToSignedUrls(markdown)).toBe(markdown.replace(DOWNLOAD_SRC, SIGNED_URL))
  })
})
