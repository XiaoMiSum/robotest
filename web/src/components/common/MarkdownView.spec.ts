// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MarkdownView from './MarkdownView.vue'

// Vditor 内核依赖浏览器完整 DOM，jsdom 跑不起来；只读渲染退化为静态 preview 调用断言
const fake = vi.hoisted(() => ({
  preview: vi.fn(async () => undefined),
  rewriteToSignedUrls: vi.fn((html: string) => html),
}))

vi.mock('vditor', () => ({
  default: { preview: fake.preview },
}))

vi.mock('@/composables/common/markdownImage', () => ({
  resolveMarkdownImages: vi.fn(() => () => undefined),
  rewriteToSignedUrls: fake.rewriteToSignedUrls,
}))

interface PreviewCall {
  element: HTMLElement
  markdown: string
  options: { mode?: string; cdn?: string; markdown?: { sanitize?: boolean }; transform?: unknown }
}

function lastCall(): PreviewCall {
  const [element, markdown, options] = fake.preview.mock.calls[
    fake.preview.mock.calls.length - 1
  ] as unknown as [HTMLElement, string, PreviewCall['options']]
  return { element, markdown, options }
}

/** 防抖窗口（200ms）加余量，等待排队渲染完成 */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 300))
}

describe('MarkdownView 只读渲染（缺陷详设 1.15 关闭态渲染）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('挂载即渲染，并使用本地运行资源与内置过滤', async () => {
    const wrapper = mount(MarkdownView, { props: { content: '# 标题' } })
    await flushPromises()

    expect(fake.preview).toHaveBeenCalledTimes(1)
    const call = lastCall()
    expect(call.element).toBe(wrapper.element)
    expect(call.markdown).toBe('# 标题')
    expect(call.options.mode).toBe('light')
    expect(call.options.cdn).toBe('/vditor')
    expect(call.options.markdown).toEqual({ sanitize: true })
    // 渲染前优先用换签缓存替换稳定路径，未命中的交给 DOM 观察器
    expect(call.options.transform).toBe(fake.rewriteToSignedUrls)
  })

  it('流式高频更新合并为一次渲染，并以最终内容收口', async () => {
    const wrapper = mount(MarkdownView, { props: { content: '第 1 段' } })
    await flushPromises()
    expect(fake.preview).toHaveBeenCalledTimes(1)

    await wrapper.setProps({ content: '第 2 段' })
    await wrapper.setProps({ content: '第 3 段' })
    expect(fake.preview).toHaveBeenCalledTimes(1)

    await settle()
    expect(fake.preview).toHaveBeenCalledTimes(2)
    expect(lastCall().markdown).toBe('第 3 段')
  })

  it('卸载后丢弃排队中的渲染', async () => {
    const wrapper = mount(MarkdownView, { props: { content: '正文' } })
    await flushPromises()
    await wrapper.setProps({ content: '更新后的正文' })

    wrapper.unmount()
    await settle()

    expect(fake.preview).toHaveBeenCalledTimes(1)
    expect(lastCall().markdown).toBe('正文')
  })
})
