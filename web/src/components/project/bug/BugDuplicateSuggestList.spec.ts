// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BugDuplicateSuggestList from './BugDuplicateSuggestList.vue'
import type { BugDuplicateSuggestItem } from '@/types'

const items: BugDuplicateSuggestItem[] = [
  {
    bugId: 'aaaaaaaa-bbbb-cccc-dddd-000000000001',
    title: '登录按钮无反应',
    status: 'active',
    similarity: 0.87,
    basis: '命中原文分块',
  },
  {
    bugId: 'aaaaaaaa-bbbb-cccc-dddd-000000000002',
    title: '登录后跳转空白页',
    status: 'resolved',
    similarity: 0.72,
    basis: '命中原文分块',
  },
]

function render(list: BugDuplicateSuggestItem[]) {
  return mount(BugDuplicateSuggestList, {
    props: { items: list },
    global: {
      stubs: {
        'el-icon': { template: '<span><slot /></span>' },
        'el-tooltip': { template: '<span><slot /></span>' },
        'el-button': { template: '<button class="dup-hint__link"><slot /></button>' },
        Warning: { template: '<span />' },
      },
    },
  })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('BugDuplicateSuggestList（标题下疑似重复提示）', () => {
  it('一行按相似度顺序渲染短 ID 链接，以顿号分隔', () => {
    const wrapper = render(items)
    expect(wrapper.find('.dup-hint__label').text()).toBe('疑似重复：')
    const links = wrapper.findAll('.dup-hint__link')
    expect(links).toHaveLength(2)
    expect(links[0].text()).toBe('aaaa...0001')
    expect(links[1].text()).toBe('aaaa...0002')
    expect(wrapper.findAll('.dup-hint__separator')).toHaveLength(1)
    wrapper.unmount()
  })

  it('点击链接在新标签页打开缺陷详情', async () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    const wrapper = render(items)
    await wrapper.findAll('.dup-hint__link')[0].trigger('click')
    expect(open).toHaveBeenCalledWith(
      '/workspace/projects/bugs/aaaaaaaa-bbbb-cccc-dddd-000000000001',
      '_blank',
      'noopener',
    )
    wrapper.unmount()
  })

  it('无命中时不渲染提示', () => {
    const wrapper = render([])
    expect(wrapper.find('.dup-hint').exists()).toBe(false)
    wrapper.unmount()
  })
})
