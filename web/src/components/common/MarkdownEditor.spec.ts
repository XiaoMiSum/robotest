// @vitest-environment jsdom
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
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
    setHeight: vi.fn(),
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
  // 编辑器底部「完整显示」按钮：桩成原生 button，属性透传天然落到底层 DOM
  ElButton: { name: 'ElButton', template: '<button><slot /></button>' },
}))

interface CapturedOptions {
  mode?: string
  cdn?: string
  value?: string
  height?: string | number
  placeholder?: string
  toolbar?: unknown[]
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

  // ==================== 最大化预览（交互 06 §2.5.2） ====================

  interface MenuLike {
    name: string
    click?: (event: Event) => void
  }

  /** 取自定义「预览」按钮（工具栏末项） */
  function pickMaximizeItem(options: CapturedOptions): MenuLike {
    return options.toolbar?.[options.toolbar.length - 1] as MenuLike
  }

  it('maximizable 缺省不追加自定义按钮，开启时末项为预览按钮', async () => {
    const { options } = await mountEditor()
    expect(options.toolbar?.every((item) => typeof item === 'string')).toBe(true)

    const { options: maxOptions } = await mountEditor('', { maximizable: true })
    expect(pickMaximizeItem(maxOptions).name).toBe('preview-maximize')
  })

  it('点击预览按钮最大化展示遮罩，点遮罩退出并恢复背景滚动', async () => {
    const { options, wrapper } = await mountEditor('', { maximizable: true })

    pickMaximizeItem(options).click?.(new Event('click'))
    await nextTick()
    expect(wrapper.find('.markdown-editor-wrap--maximized').exists()).toBe(true)
    expect(wrapper.find('.markdown-editor').attributes('style')).toContain('height: 100%')
    expect(document.body.style.overflow).toBe('hidden')

    const mask = document.querySelector('.markdown-editor__mask')
    expect(mask).not.toBeNull()
    mask?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(wrapper.find('.markdown-editor-wrap--maximized').exists()).toBe(false)
    expect(wrapper.find('.markdown-editor').attributes('style')).toContain('height: 320px')
    expect(document.querySelector('.markdown-editor__mask')).toBeNull()
    expect(document.body.style.overflow).toBe('')
  })

  it('Esc 退出最大化；最大化态卸载时恢复背景滚动', async () => {
    const { options, wrapper } = await mountEditor('', { maximizable: true })
    const maximize = pickMaximizeItem(options)

    maximize.click?.(new Event('click'))
    await nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(wrapper.find('.markdown-editor-wrap--maximized').exists()).toBe(false)
    expect(document.body.style.overflow).toBe('')

    maximize.click?.(new Event('click'))
    await nextTick()
    wrapper.unmount()
    expect(document.body.style.overflow).toBe('')
    expect(document.querySelector('.markdown-editor__mask')).toBeNull()
  })

  // ==================== 完整显示（内容高度自适应） ====================

  /** jsdom 量不到布局，向挂载元素补一块带固定 scrollHeight/clientHeight 的正文区 */
  function attachArea(wrapper: VueWrapper, scrollHeight: number, clientHeight: number): HTMLElement {
    const container = wrapper.find('.markdown-editor').element as HTMLElement
    const area = document.createElement('div')
    area.className = 'vditor-wysiwyg'
    Object.defineProperty(area, 'scrollHeight', { value: scrollHeight, configurable: true })
    Object.defineProperty(area, 'clientHeight', { value: clientHeight, configurable: true })
    container.appendChild(area)
    return area
  }

  async function measureByResize(): Promise<void> {
    window.dispatchEvent(new Event('resize'))
    await nextTick()
  }

  function findExpandButton(wrapper: VueWrapper) {
    return wrapper.find('.markdown-editor-expand button')
  }

  it('内容未超出编辑区高度时不渲染完整显示按钮', async () => {
    const { options, wrapper } = await mountEditor()
    markReady(options)
    attachArea(wrapper, 300, 300)
    await measureByResize()
    expect(wrapper.find('.markdown-editor-expand').exists()).toBe(false)
  })

  it('内容显示不全时出现完整显示，点击撑高至内容高度，再点收起恢复默认高度', async () => {
    const { options, wrapper } = await mountEditor()
    markReady(options)
    attachArea(wrapper, 1000, 300)
    await measureByResize()

    const button = findExpandButton(wrapper)
    expect(button.exists()).toBe(true)
    expect(button.text()).toBe('完整显示')

    await button.trigger('click')
    // 展开高度 = 正文滚动高度(1000) + 工具栏高度(无 DOM 为 0) + 缓冲(2)
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith('1002px')
    expect(findExpandButton(wrapper).text()).toBe('收起')

    await findExpandButton(wrapper).trigger('click')
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith('320px')
    expect(findExpandButton(wrapper).text()).toBe('完整显示')
  })

  it('展开态下正文继续增长时按最新内容高度重新撑高', async () => {
    const { options, wrapper } = await mountEditor()
    markReady(options)
    const area = attachArea(wrapper, 1000, 300)
    await measureByResize()

    await findExpandButton(wrapper).trigger('click')
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith('1002px')

    Object.defineProperty(area, 'scrollHeight', { value: 1600 })
    await measureByResize()
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith('1602px')
  })

  it('禁用（只读）态不渲染完整显示按钮', async () => {
    const { options, wrapper } = await mountEditor('', { disabled: true })
    markReady(options)
    attachArea(wrapper, 1000, 300)
    await measureByResize()
    expect(wrapper.find('.markdown-editor-expand').exists()).toBe(false)
  })

  // ==================== 拖拽调高（底部手柄） ====================

  function findResizeHandle(wrapper: VueWrapper) {
    return wrapper.find('.markdown-editor-resize')
  }

  /** 模拟在手柄上按下并拖动指定像素（jsdom 量不到布局，起始高度走内部记录值） */
  async function dragHandle(wrapper: VueWrapper, deltaY: number, startClientY = 0): Promise<void> {
    await findResizeHandle(wrapper).trigger('mousedown', { clientY: startClientY })
    document.dispatchEvent(new MouseEvent('mousemove', { clientY: startClientY + deltaY }))
    document.dispatchEvent(new MouseEvent('mouseup'))
  }

  it('编辑态渲染拖拽手柄；只读、展开与最大化态不渲染', async () => {
    const { options, wrapper } = await mountEditor()
    markReady(options)
    expect(findResizeHandle(wrapper).exists()).toBe(true)

    attachArea(wrapper, 1000, 300)
    await measureByResize()
    await findExpandButton(wrapper).trigger('click')
    expect(findResizeHandle(wrapper).exists()).toBe(false)
    await findExpandButton(wrapper).trigger('click')
    expect(findResizeHandle(wrapper).exists()).toBe(true)

    const { options: disabledOptions, wrapper: disabledWrapper } = await mountEditor('', {
      disabled: true,
    })
    markReady(disabledOptions)
    expect(findResizeHandle(disabledWrapper).exists()).toBe(false)

    const { options: maxOptions, wrapper: maxWrapper } = await mountEditor('', {
      maximizable: true,
    })
    markReady(maxOptions)
    pickMaximizeItem(maxOptions).click?.(new Event('click'))
    await nextTick()
    expect(findResizeHandle(maxWrapper).exists()).toBe(false)
    maxWrapper.unmount()
  })

  it('上下拖动调整高度，向下钳制最小 200px、向上钳制视口高度', async () => {
    const { options, wrapper } = await mountEditor()
    markReady(options)
    fake.editor.setHeight.mockClear()

    await dragHandle(wrapper, -50)
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith('270px')

    await dragHandle(wrapper, -100000)
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith('200px')

    await dragHandle(wrapper, 100000)
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith(`${window.innerHeight}px`)
  })

  it('拖动后外部高度变化不再覆盖，收起完整显示恢复拖动后的值', async () => {
    const { options, wrapper } = await mountEditor()
    markReady(options)
    await dragHandle(wrapper, 80)
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith('400px')

    await wrapper.setProps({ height: '640px' })
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith('400px')

    attachArea(wrapper, 1000, 300)
    await measureByResize()
    await findExpandButton(wrapper).trigger('click')
    await findExpandButton(wrapper).trigger('click')
    expect(fake.editor.setHeight).toHaveBeenLastCalledWith('400px')
  })

  it('拖拽结束移除全局监听并还原光标与选中，卸载时中断拖拽同样清理', async () => {
    const { options, wrapper } = await mountEditor()
    markReady(options)

    await findResizeHandle(wrapper).trigger('mousedown', { clientY: 100 })
    expect(document.body.style.cursor).toBe('row-resize')
    document.dispatchEvent(new MouseEvent('mouseup'))
    expect(document.body.style.cursor).toBe('')
    expect(document.body.style.userSelect).toBe('')

    fake.editor.setHeight.mockClear()
    document.dispatchEvent(new MouseEvent('mousemove', { clientY: 500 }))
    expect(fake.editor.setHeight).not.toHaveBeenCalled()

    await findResizeHandle(wrapper).trigger('mousedown', { clientY: 100 })
    wrapper.unmount()
    expect(document.body.style.cursor).toBe('')
    expect(document.body.style.userSelect).toBe('')
  })
})
