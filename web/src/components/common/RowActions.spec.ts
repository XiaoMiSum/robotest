// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import RowActions from './RowActions.vue'

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('RowActions', () => {
  it('最多内联三个动作，其余进入更多下拉', async () => {
    const wrapper = mount(RowActions, {
      props: {
        actions: [
          { key: 'open', label: '查看' },
          { key: 'edit', label: '编辑' },
          { key: 'complete', label: '完成', form: 'DLsm' },
          { key: 'resume', label: '恢复' },
          { key: 'delete', label: '删除', form: 'DGsm' },
        ],
      },
    })

    const inline = wrapper.findAll('.row-actions > .el-button')
    expect(inline.map((button) => button.text())).toEqual(['查看', '编辑', '完成'])
    expect(wrapper.find('.row-actions__more').exists()).toBe(true)

    await wrapper.get('.row-actions__more').trigger('click')
    vi.runAllTimers()
    await nextTick()
    const texts = wrapper.findAll('.el-dropdown-menu button').map((button) => button.text())
    expect(texts).toContain('恢复')
    expect(texts).toContain('删除')
  })

  it('点击空白处收起更多下拉，恢复默认内联按钮', async () => {
    const wrapper = mount(RowActions, {
      props: {
        actions: [
          { key: 'open', label: '查看' },
          { key: 'edit', label: '编辑' },
          { key: 'complete', label: '完成' },
          { key: 'resume', label: '恢复' },
        ],
      },
    })

    await wrapper.get('.row-actions__more').trigger('click')
    vi.runAllTimers()
    await nextTick()
    expect(wrapper.get('.more-dropdown__trigger').attributes('aria-expanded')).toBe('true')

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    vi.runAllTimers()
    await nextTick()

    expect(wrapper.get('.more-dropdown__trigger').attributes('aria-expanded')).toBe('false')
    expect(wrapper.findAll('.row-actions > .el-button')).toHaveLength(3)
  })

  it('隐藏动作不渲染且点击转发动作键', async () => {
    const wrapper = mount(RowActions, {
      props: {
        actions: [
          { key: 'edit', label: '编辑', visible: true },
          { key: 'delete', label: '删除', form: 'DGsm', visible: false },
        ],
      },
    })

    expect(wrapper.findAll('.row-actions > .el-button')).toHaveLength(1)
    expect(wrapper.find('.row-actions__more').exists()).toBe(false)
    await wrapper.get('.row-actions > .el-button').trigger('click')
    expect(wrapper.emitted('action')?.[0]).toEqual(['edit'])
  })
})
