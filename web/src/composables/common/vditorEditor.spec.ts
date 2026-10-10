// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { createEditorOptions, VDITOR_CDN } from './vditorEditor'

const CALLBACKS = { onReady: vi.fn(), onInput: vi.fn(), onUpload: vi.fn() }

describe('Vditor 所见即所得参数（缺陷详设 1.15）', () => {
  it('固定用所见即所得模式与本地运行资源，并关闭缓存、保留内置过滤', () => {
    const options = createEditorOptions(CALLBACKS)

    expect(options.mode).toBe('wysiwyg')
    expect(options.cdn).toBe('/vditor')
    expect(options.cdn).toBe(VDITOR_CDN)
    // 带签名地址的正文不进 localStorage
    expect(options.cache?.enable).toBe(false)
    // 展示他人写入的正文，沿用内置 GFM 过滤
    expect(options.preview?.markdown?.sanitize).toBe(true)
    expect(options.lang).toBe('zh_CN')
  })

  it('工具栏只保留写作与结构按钮，不提供会被 Element Plus 弹层盖住的全屏', () => {
    const { toolbar } = createEditorOptions(CALLBACKS)

    expect(toolbar).toContain('headings')
    expect(toolbar).toContain('upload')
    expect(toolbar).toContain('edit-mode')
    expect(toolbar).not.toContain('fullscreen')
    expect(toolbar).not.toContain('record')
  })

  it('外观参数透传，默认高度与占位与旧编辑器一致', () => {
    const options = createEditorOptions({
      ...CALLBACKS,
      height: '260px',
      placeholder: '需求描述（支持 Markdown）',
      value: '# 标题',
    })

    expect(options.height).toBe('260px')
    expect(options.placeholder).toBe('需求描述（支持 Markdown）')
    expect(options.value).toBe('# 标题')
    expect(createEditorOptions(CALLBACKS).height).toBe('320px')
  })

  it('回写前把编辑器里的 presigned 地址归一化为稳定下载路径', () => {
    const onInput = vi.fn()
    const options = createEditorOptions({ ...CALLBACKS, onInput })
    const presigned =
      'https://oss.example.com/bucket/objects/11111111-2222-3333-4444-555555555555.png' +
      '?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc'

    options.input?.(`![截图](${presigned})`)

    expect(onInput).toHaveBeenCalledWith(
      '![截图](/api/files/11111111-2222-3333-4444-555555555555/download)',
    )
  })

  it('就绪回调挂到 after，输入不滞后保存（undoDelay 远小于默认 800ms）', () => {
    const onReady = vi.fn()
    const options = createEditorOptions({ ...CALLBACKS, onReady })

    options.after?.()
    expect(onReady).toHaveBeenCalledTimes(1)
    expect(options.undoDelay).toBeLessThanOrEqual(300)
  })

  it('自定义上传把文件交给调用方，成功返回 null（Vditor 不再校验与回插）', async () => {
    const onUpload = vi.fn().mockResolvedValue(undefined)
    const options = createEditorOptions({ ...CALLBACKS, onUpload })
    const files = [new File(['content'], 'a.png', { type: 'image/png' })]

    await expect(options.upload?.handler?.(files)).resolves.toBeNull()

    expect(onUpload).toHaveBeenCalledWith(files)
    expect(options.upload?.accept).toBe('image/*')
    // 不配置 url，Vditor 只会走 handler 分支（自带校验与默认上传都被绕过）
    expect(options.upload?.url).toBeUndefined()
  })
})
