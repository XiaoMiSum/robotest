// @vitest-environment jsdom
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MarkdownEditor from './MarkdownEditor.vue'

// Vditor 内核依赖浏览器完整 DOM，jsdom 跑不起来；此处只桩接组件依赖的构造参数与实例方法
const fake = vi.hoisted(() => {
  const editor = {
    value: '',
    getValue: vi.fn(() => editor.value),
    setValue: vi.fn(),
    insertMD: vi.fn(),
    disabled: vi.fn(),
    enable: vi.fn(),
    destroy: vi.fn(),
  }
  return {
    editor,
    options: undefined as Record<string, unknown> | undefined,
    uploadFile: vi.fn(),
    warning: vi.fn(),
  }
})

vi.mock('vditor', () => ({
  default: class {
    constructor(_element: HTMLElement, options: Record<string, unknown>) {
      fake.options = options
      Object.assign(this, fake.editor)
    }
  },
}))

vi.mock('@/services/files', () => ({
  uploadFile: fake.uploadFile,
  fetchFileAccessUrl: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: fake.warning, error: vi.fn() },
}))

interface CapturedOptions {
  mode?: string
  cdn?: string
  value?: string
  height?: string | number
  placeholder?: string
  after?: () => void
  input?: (markdown: string) => void
  upload?: { handler?: (files: File[]) => Promise<string | null> }
}

interface MountResult {
  wrapper: VueWrapper
  options: CapturedOptions
}

async function mountEditor(modelValue = '', props: Record<string, unknown> = {}): Promise<MountResult> {
  const wrapper = mount(MarkdownEditor, { props: { modelValue, ...props } })
  // 等待内核分包加载完成（构造后还要异步取 i18n 资源）
  await flushPromises()
  return { wrapper, options: fake.options as unknown as CapturedOptions }
}

/** 模拟内核就绪：就绪前实例方法不可用 */
function markReady(options: CapturedOptions): void {
  options.after?.()
}

describe('MarkdownEditor（缺陷详设 1.15 重现步骤编辑）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fake.editor.value = ''
    fake.options = undefined
    fake.uploadFile.mockReset()
    fake.warning.mockReset()
  })

  it('以所见即所得模式初始化，并透传外观与初始值', async () => {
    const { options } = await mountEditor('# 标题', {
      height: '260px',
      placeholder: '重现步骤（支持 Markdown）',
    })

    expect(options.mode).toBe('wysiwyg')
    expect(options.cdn).toBe('/vditor')
    expect(options.height).toBe('260px')
    expect(options.placeholder).toBe('重现步骤（支持 Markdown）')
    expect(options.value).toBe('# 标题')
  })

  it('就绪后才允许实例操作，并同步 disabled', async () => {
    const { options } = await mountEditor('', { disabled: true })
    expect(fake.editor.disabled).not.toHaveBeenCalled()

    markReady(options)
    expect(fake.editor.disabled).toHaveBeenCalledTimes(1)

    const { wrapper } = await mountEditor()
    const readyOptions = fake.options as unknown as CapturedOptions
    markReady(readyOptions)
    await wrapper.setProps({ disabled: true })
    expect(fake.editor.enable).not.toHaveBeenCalled()
    await wrapper.setProps({ disabled: false })
    expect(fake.editor.enable).toHaveBeenCalledTimes(1)
  })

  it('就绪时以最新值回填，外部值变化时才 setValue', async () => {
    fake.editor.value = ''
    const { options, wrapper } = await mountEditor('# 标题')
    markReady(options)
    expect(fake.editor.setValue).toHaveBeenCalledWith('# 标题')

    fake.editor.value = '# 标题'
    await wrapper.setProps({ modelValue: '# 标题' })
    expect(fake.editor.setValue).toHaveBeenCalledTimes(1)

    await wrapper.setProps({ modelValue: '# 新标题' })
    expect(fake.editor.setValue).toHaveBeenLastCalledWith('# 新标题')
  })

  it('回写 v-model 前归一化 presigned 地址，且不产生回填环路', async () => {
    const { options, wrapper } = await mountEditor()
    fake.editor.value = ''
    markReady(options)
    fake.editor.setValue.mockClear()
    const presigned =
      'https://oss.example.com/bucket/objects/11111111-2222-3333-4444-555555555555.png' +
      '?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc'

    // 编辑器 DOM 内图片已被换签，getValue 带回的是 presigned 地址
    fake.editor.value = `![截图](${presigned})`
    options.input?.(`![截图](${presigned})`)

    const emitted = wrapper.emitted('update:modelValue') ?? []
    const next = emitted[emitted.length - 1]?.[0] as string
    expect(next).toBe('![截图](/api/files/11111111-2222-3333-4444-555555555555/download)')

    // 父组件把归一化后的值推回来时，与编辑器当前内容等价，不应打断输入
    await wrapper.setProps({ modelValue: next })
    expect(fake.editor.setValue).not.toHaveBeenCalled()
  })

  it('上传经前置校验后按原顺序插入图片引用，handler 返回 null', async () => {
    fake.uploadFile.mockResolvedValue({
      downloadUrl: '/api/files/11111111-2222-3333-4444-555555555555/download',
    })
    const { options } = await mountEditor()
    fake.editor.value = ''
    markReady(options)
    const file = new File(['content'], '截图.png', { type: 'image/png' })

    const result = await options.upload?.handler?.([file])

    expect(result).toBeNull()
    expect(fake.uploadFile).toHaveBeenCalledWith(file)
    expect(fake.editor.insertMD).toHaveBeenCalledWith(
      '![截图.png](/api/files/11111111-2222-3333-4444-555555555555/download)',
    )
  })

  it('就绪时销毁实例；未就绪就卸载时由就绪回调自毁', async () => {
    const { options, wrapper } = await mountEditor()
    fake.editor.value = ''
    markReady(options)
    wrapper.unmount()
    expect(fake.editor.destroy).toHaveBeenCalledTimes(1)

    const pending = await mountEditor()
    const pendingOptions = fake.options as unknown as CapturedOptions
    pending.wrapper.unmount()
    pendingOptions.after?.()
    expect(fake.editor.destroy).toHaveBeenCalledTimes(2)
  })
})
