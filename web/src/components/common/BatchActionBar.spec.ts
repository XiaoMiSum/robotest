// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BatchActionBar from './BatchActionBar.vue'

describe('BatchActionBar', () => {
  it('count=0 时不渲染浮动条根节点', () => {
    const wrapper = mount(BatchActionBar, { props: { count: 0 } })
    expect(wrapper.find('.batch-action-bar').exists()).toBe(false)
  })

  it('count=2 时渲染浮动条并展示已选计数', () => {
    const wrapper = mount(BatchActionBar, { props: { count: 2 } })
    const bar = wrapper.find('.batch-action-bar')
    expect(bar.exists()).toBe(true)
    expect(bar.attributes('aria-label')).toBe('批量操作')
    expect(wrapper.text()).toContain('已选')
    expect(wrapper.text()).toContain('2')
  })

  it('点击[清空]触发 clear 事件', async () => {
    const wrapper = mount(BatchActionBar, { props: { count: 3 } })
    await wrapper.find('.batch-action-bar__clear').trigger('click')
    expect(wrapper.emitted('clear')).toHaveLength(1)
  })

  it('默认插槽内容正常渲染在计数与清空之间', () => {
    const wrapper = mount(BatchActionBar, {
      props: { count: 1 },
      slots: { default: '<button class="batch-action-btn">批量删除</button>' },
    })
    expect(wrapper.find('.batch-action-btn').text()).toBe('批量删除')
    expect(wrapper.find('.batch-action-bar__count').exists()).toBe(true)
    expect(wrapper.find('.batch-action-bar__clear').exists()).toBe(true)
  })
})
