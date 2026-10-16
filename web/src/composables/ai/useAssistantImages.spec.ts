// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  uploadFile: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('@/services/files', () => ({
  uploadFile: mocks.uploadFile,
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: mocks.warning, error: mocks.error },
}))

import { useAiAssistantStore } from '@/stores/aiAssistant'
import { useAssistantImages } from './useAssistantImages'

function imageFile(name: string, size = 1024): File {
  const file = new File(['x'], name, { type: 'image/png' })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('composables/ai/useAssistantImages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('选择即上传，完成后回写 fileId', async () => {
    mocks.uploadFile.mockResolvedValue({ id: 'f1' })
    const store = useAiAssistantStore()
    const images = useAssistantImages()

    images.addImages([imageFile('a.png')])
    await flushPromises()

    expect(mocks.uploadFile).toHaveBeenCalledTimes(1)
    expect(store.imagesForCurrent[0]).toMatchObject({ status: 'done', fileId: 'f1' })
  })

  it('超过 10MB 或非白名单在选中时提示，不发起上传', async () => {
    const store = useAiAssistantStore()
    const images = useAssistantImages()

    images.addImages([
      imageFile('big.png', 11 * 1024 * 1024),
      new File(['x'], 'doc.pdf', { type: 'application/pdf' }),
    ])
    await flushPromises()

    expect(mocks.uploadFile).not.toHaveBeenCalled()
    expect(store.imagesForCurrent).toHaveLength(0)
    expect(mocks.warning).toHaveBeenCalledWith(expect.stringContaining('超过 10MB'))
    expect(mocks.warning).toHaveBeenCalledWith(expect.stringContaining('不是支持的图片格式'))
  })

  it('张数上限 3：超出部分提示且不登记', async () => {
    mocks.uploadFile.mockResolvedValue({ id: 'f1' })
    const store = useAiAssistantStore()
    const images = useAssistantImages()

    images.addImages([imageFile('1.png'), imageFile('2.png'), imageFile('3.png'), imageFile('4.png')])
    await flushPromises()

    expect(store.imagesForCurrent).toHaveLength(3)
    expect(mocks.warning).toHaveBeenCalledWith(expect.stringContaining('最多携带 3 张'))
  })

  it('上传失败保留失败态，重试成功后转完成', async () => {
    mocks.uploadFile.mockRejectedValueOnce(new Error('网络错误')).mockResolvedValueOnce({ id: 'f1' })
    const store = useAiAssistantStore()
    const images = useAssistantImages()

    images.addImages([imageFile('a.png')])
    await flushPromises()

    expect(store.imagesForCurrent[0].status).toBe('failed')
    expect(mocks.error).toHaveBeenCalledWith(expect.stringContaining('上传失败'))

    images.retryImage(store.imagesForCurrent[0].key)
    await flushPromises()

    expect(store.imagesForCurrent[0]).toMatchObject({ status: 'done', fileId: 'f1' })
  })

  it('移除图片后本会话待发附图清空', async () => {
    mocks.uploadFile.mockResolvedValue({ id: 'f1' })
    const store = useAiAssistantStore()
    const images = useAssistantImages()

    images.addImages([imageFile('a.png')])
    await flushPromises()
    images.removeImage(store.imagesForCurrent[0].key)

    expect(store.imagesForCurrent).toHaveLength(0)
  })
})
