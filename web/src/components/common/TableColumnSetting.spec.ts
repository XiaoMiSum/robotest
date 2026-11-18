// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TableColumnSetting from './TableColumnSetting.vue'

function render(iconOnly = false) {
  return mount(TableColumnSetting, {
    props: {
      columns: [
        { key: 'title', label: '标题' },
        { key: 'status', label: '状态' },
        { key: 'actions', label: '操作', pinned: true },
      ],
      visibleKeys: ['title', 'actions'],
      iconOnly,
    },
    global: {
      stubs: {
        'el-popover': { template: '<div><slot name="reference" /><slot /></div>' },
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': { template: '<span><slot /></span>' },
        Setting: { template: '<span />' },
        'el-checkbox-group': { template: '<div><slot /></div>' },
        'el-checkbox': {
          props: ['value', 'disabled'],
          template: '<label><slot /></label>',
        },
      },
    },
  })
}

describe('TableColumnSetting', () => {
  it('提供列设置入口并列出全部列（含禁用态固定列）', () => {
    const wrapper = render()
    expect(wrapper.text()).toContain('列设置')
    expect(wrapper.text()).toContain('标题')
    expect(wrapper.text()).toContain('状态')
    expect(wrapper.text()).toContain('操作')
    wrapper.unmount()
  })

  it('提供全选与恢复默认操作', () => {
    const wrapper = render()
    expect(wrapper.text()).toContain('全选')
    expect(wrapper.text()).toContain('恢复默认')
    wrapper.unmount()
  })

  it('图标模式仅显示图标并保留可访问名称', () => {
    const wrapper = render(true)
    expect(wrapper.text()).not.toContain('列设置')
    expect(wrapper.get('button').attributes('aria-label')).toBe('列设置')
    expect(wrapper.text()).toContain('显示列')
    wrapper.unmount()
  })
})
