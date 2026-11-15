// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ListTitleLink from './ListTitleLink.vue'

afterEach(() => { vi.useRealTimers(); document.body.innerHTML = '' })

describe('ListTitleLink', () => {
  it('保留完整的可访问标题，通过原生按钮向页面转发打开事件', async () => {
    const title = '超过可见列宽但不应丢失任何字符的完整评审标题'
    const wrapper = mount(ListTitleLink, { props: { title } })
    const button = wrapper.get('button')
    expect(button.text()).toBe(title)
    expect(button.attributes('type')).toBe('button')
    await button.trigger('click')
    expect(wrapper.emitted('open')).toHaveLength(1)
    wrapper.unmount()
  })

  it('聚焦后显示全文提示，Enter 由原生按钮处理而不切换 tooltip', async () => {
    vi.useFakeTimers()
    const wrapper = mount(ListTitleLink, { props: { title: '键盘可查看的完整标题' }, attachTo: document.body })
    await wrapper.get('button').trigger('focus')
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()
    expect(document.querySelector('[role="tooltip"]')?.textContent).toContain('键盘可查看的完整标题')
    await wrapper.get('button').trigger('keydown', { code: 'Enter' })
    expect(wrapper.emitted('open')).toBeUndefined()
    wrapper.unmount()
  })
})
