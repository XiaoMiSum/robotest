// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ListPagination from './ListPagination.vue'

/** 分页器桩：暴露 layout 等关键 props，并可手动触发页码/条数事件 */
const paginationStub = {
  props: {
    layout: { type: String, default: '' },
    total: { type: Number, default: 0 },
    pageSize: { type: Number, default: 20 },
    currentPage: { type: Number, default: 1 },
    pageSizes: { type: Array, default: () => [] },
    small: { type: Boolean, default: false },
  },
  emits: ['currentChange', 'sizeChange'],
  template:
    '<div class="pagination-stub" :class="{ small }" :data-layout="layout" @click="$emit(\'currentChange\', 3)" @size="$emit(\'sizeChange\', 50)" />',
}

function mountPager(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(ListPagination, {
    props,
    slots,
    global: { stubs: { 'el-pagination': paginationStub } },
  })
}

describe('ListPagination', () => {
  it('默认左侧显示 total 文案，layout 含 sizes 且不含 total', () => {
    const wrapper = mountPager({ total: 42, pageSize: 20 })
    expect(wrapper.find('.list-pager__total').text()).toBe('共 42 条 · 每页 20 条')
    expect(wrapper.find('.pagination-stub').attributes('data-layout')).toBe('sizes, prev, pager, next')
  })

  it('unit 覆盖文案单位', () => {
    const wrapper = mountPager({ total: 3, unit: '个计划' })
    expect(wrapper.find('.list-pager__total').text()).toBe('共 3 个计划 · 每页 20 条')
  })

  it('show-total-text=false 时左侧无文案且 total 并入右侧 layout', () => {
    const wrapper = mountPager({ total: 9, showTotalText: false })
    expect(wrapper.find('.list-pager__total').exists()).toBe(false)
    expect(wrapper.find('.pagination-stub').attributes('data-layout')).toBe(
      'total, sizes, prev, pager, next',
    )
  })

  it('pageSizes 为空时隐藏尺寸选择器（弹窗固定条数场景）', () => {
    const wrapper = mountPager({ total: 9, showTotalText: false, pageSizes: [] })
    expect(wrapper.find('.pagination-stub').attributes('data-layout')).toBe(
      'total, prev, pager, next',
    )
  })

  it('left 插槽覆盖默认文案（批量操作区场景）', () => {
    const wrapper = mountPager({ total: 2 }, { left: '<button class="batch">删除</button>' })
    expect(wrapper.find('.batch').exists()).toBe(true)
    expect(wrapper.find('.list-pager__total').exists()).toBe(false)
  })

  it('页码变化时上抛 update:page-no、page-change 与 change', async () => {
    const wrapper = mountPager({ total: 100, pageNo: 1, pageSize: 20 })
    await wrapper.find('.pagination-stub').trigger('click')

    expect(wrapper.emitted('update:page-no')?.[0]).toEqual([3])
    expect(wrapper.emitted('page-change')?.[0]).toEqual([{ pageNo: 3, pageSize: 20 }])
    expect(wrapper.emitted('change')?.[0]).toEqual([{ pageNo: 3, pageSize: 20 }])
    expect(wrapper.emitted('update:page-size')).toBeUndefined()
  })

  it('切换条数时先把页码置 1 再上抛 size-change 与 change', async () => {
    const wrapper = mountPager({ total: 500, pageNo: 4, pageSize: 20 })
    await wrapper.find('.pagination-stub').trigger('size')

    expect(wrapper.emitted('update:page-size')?.[0]).toEqual([50])
    expect(wrapper.emitted('update:page-no')?.[0]).toEqual([1])
    expect(wrapper.emitted('size-change')?.[0]).toEqual([{ pageNo: 1, pageSize: 50 }])
    expect(wrapper.emitted('change')?.[0]).toEqual([{ pageNo: 1, pageSize: 50 }])
    expect(wrapper.emitted('page-change')).toBeUndefined()
  })

  it('bare 与 small 修饰类透传', () => {
    const wrapper = mountPager({ bare: true, small: true })
    expect(wrapper.find('.list-pager--bare').exists()).toBe(true)
    expect(wrapper.find('.pagination-stub').attributes('class')).toContain('small')
  })
})
