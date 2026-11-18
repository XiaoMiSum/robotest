// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BugListToolbar from './BugListToolbar.vue'

function render() {
  return mount(BugListToolbar, {
    props: {
      quickFilterOptions: [{ value: '', label: '全部' }],
      advancedFilterCount: 0,
      quickFilter: '',
      viewMode: 'list',
      keyword: '',
      status: '',
      bugType: '',
      severity: '',
      priority: '',
      filtersExpanded: false,
      'onUpdate:quickFilter': () => {},
      'onUpdate:viewMode': () => {},
      'onUpdate:keyword': () => {},
      'onUpdate:status': () => {},
      'onUpdate:bugType': () => {},
      'onUpdate:severity': () => {},
      'onUpdate:priority': () => {},
      'onUpdate:filtersExpanded': () => {},
    },
    global: {
      stubs: {
        'el-radio-group': { template: '<div><slot /></div>' },
        'el-radio-button': { template: '<button><slot /></button>' },
        'el-input': { template: '<div><slot /></div>' },
        'el-popover': { template: '<div><slot name="reference" /><slot /></div>' },
        'el-badge': { template: '<div><slot /></div>' },
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': { template: '<span><slot /></span>' },
        Plus: { template: '<span />' },
        Search: { template: '<span />' },
        ArrowUp: { template: '<span />' },
        ArrowDown: { template: '<span />' },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-select': { template: '<div><slot /></div>' },
        'el-option': { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('BugListToolbar', () => {
  it('搜索框与更多筛选位于主工具栏行', () => {
    const wrapper = render()
    expect(wrapper.find('.bug-toolbar__row .bug-toolbar__search').exists()).toBe(true)
    expect(wrapper.find('.bug-toolbar__row .bug-toolbar__filter-actions').exists()).toBe(true)
    expect(wrapper.find('.bug-toolbar__search-row').exists()).toBe(false)
    wrapper.unmount()
  })
})
