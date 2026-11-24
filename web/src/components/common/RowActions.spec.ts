// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RowActions from './RowActions.vue'

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
    const texts = wrapper.findAll('.el-dropdown-menu button').map((button) => button.text())
    expect(texts).toContain('恢复')
    expect(texts).toContain('删除')
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
