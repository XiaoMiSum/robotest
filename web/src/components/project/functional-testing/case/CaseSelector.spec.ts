// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import CaseSelector from './CaseSelector.vue'
import type { TestCaseNode } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchProjectModuleTree: vi.fn(async () => []),
  fetchDocumentNodes: vi.fn(async () => ({ node: null })),
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
  fetchDocumentNodes: mocks.fetchDocumentNodes,
}))

function injectedNodes(): TestCaseNode[] {
  return [
    {
      id: 'case-1',
      parentId: null,
      type: 'case',
      title: '获取验证码',
      priority: 'P0',
      sortOrder: 0,
      version: 1,
      aiGenerated: true,
      children: [
        { id: 'step-1', parentId: 'case-1', type: 'step', title: '输入手机号', priority: null, sortOrder: 0, version: 1, children: [] },
      ],
    },
    {
      id: 'case-2',
      parentId: null,
      type: 'case',
      title: '登录成功',
      priority: 'P1',
      sortOrder: 0,
      version: 1,
      aiGenerated: true,
      children: [],
    },
  ]
}

describe('CaseSelector · 注入树模式', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('不加载后端模块与文档树，直接渲染提案节点及属性子节点', async () => {
    const wrapper = mount(CaseSelector, {
      props: { modelValue: true, inline: true, injectedNodes: injectedNodes() },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.vm.$nextTick()

    expect(mocks.fetchProjectModuleTree).not.toHaveBeenCalled()
    expect(mocks.fetchDocumentNodes).not.toHaveBeenCalled()
    expect(wrapper.find('.case-selector__tree').exists()).toBe(false)
    expect(wrapper.text()).toContain('获取验证码')
    expect(wrapper.text()).toContain('输入手机号')
    expect(wrapper.text()).toContain('登录成功')
  })

  it('勾选用例节点回传其提案 key，子节点不单独计入勾选', async () => {
    const wrapper = mount(CaseSelector, {
      props: { modelValue: true, inline: true, injectedNodes: injectedNodes() },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.vm.$nextTick()

    const chips = wrapper.findAll('.cst-chip')
    const caseChip = chips.find((chip) => chip.text().includes('获取验证码'))
    expect(caseChip).toBeDefined()
    caseChip!.trigger('click')
    await wrapper.vm.$nextTick()

    const emissions = wrapper.emitted('selectionChange') ?? []
    expect(emissions).toContainEqual([['case-1']])
    expect(emissions.some((payload) => payload.some((key) => key === 'step-1'))).toBe(false)
  })

  it('全选一次选中全部提案用例，再次点击清空', async () => {
    const wrapper = mount(CaseSelector, {
      props: { modelValue: true, inline: true, injectedNodes: injectedNodes() },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.vm.$nextTick()

    const selectAll = wrapper.find('.case-selector__toolbar input')
    expect(selectAll.exists()).toBe(true)
    await selectAll.setValue(true)
    await wrapper.vm.$nextTick()

    const emissions = wrapper.emitted('selectionChange') ?? []
    expect(emissions[emissions.length - 1]).toEqual([['case-1', 'case-2']])

    await wrapper.find('.case-selector__toolbar input').setValue(false)
    await wrapper.vm.$nextTick()

    const cleared = wrapper.emitted('selectionChange') ?? []
    expect(cleared[cleared.length - 1]).toEqual([[]])
  })

  it('部分勾选时全选呈半选态', async () => {
    const wrapper = mount(CaseSelector, {
      props: { modelValue: true, inline: true, injectedNodes: injectedNodes() },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.vm.$nextTick()

    const caseChip = wrapper.findAll('.cst-chip').find((chip) => chip.text().includes('获取验证码'))
    caseChip!.trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.case-selector__toolbar .el-checkbox__input.is-indeterminate').exists()).toBe(true)
  })

  it('已采纳用例默认勾选且不可取消', async () => {
    const wrapper = mount(CaseSelector, {
      props: {
        modelValue: true,
        inline: true,
        injectedNodes: injectedNodes(),
        adoptedCaseIds: ['case-1'],
      },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.vm.$nextTick()

    const emissions = wrapper.emitted('selectionChange') ?? []
    expect(emissions[emissions.length - 1]).toEqual([['case-1']])

    const lockedChip = wrapper.findAll('.cst-chip').find((chip) => chip.text().includes('获取验证码'))!
    expect(lockedChip.classes()).toContain('cst-chip--locked')
    const checkbox = lockedChip.find('input')
    expect(checkbox.attributes('disabled')).toBeDefined()

    await lockedChip.trigger('click')
    await wrapper.vm.$nextTick()
    const afterClick = wrapper.emitted('selectionChange') ?? []
    expect(afterClick[afterClick.length - 1]).toEqual([['case-1']])
  })

  it('全选跳过已采纳用例，只切换可采纳项', async () => {
    const wrapper = mount(CaseSelector, {
      props: {
        modelValue: true,
        inline: true,
        injectedNodes: injectedNodes(),
        adoptedCaseIds: ['case-1'],
      },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.vm.$nextTick()

    const selectAll = wrapper.find('.case-selector__toolbar input')
    await selectAll.setValue(true)
    await wrapper.vm.$nextTick()

    let emissions = wrapper.emitted('selectionChange') ?? []
    expect(emissions[emissions.length - 1]).toEqual([['case-1', 'case-2']])

    await wrapper.find('.case-selector__toolbar input').setValue(false)
    await wrapper.vm.$nextTick()
    emissions = wrapper.emitted('selectionChange') ?? []
    expect(emissions[emissions.length - 1]).toEqual([['case-1']])
  })

  it('展示状态切换时锁定项随可见性勾选或移除', async () => {
    const wrapper = mount(CaseSelector, {
      props: {
        modelValue: true,
        inline: true,
        injectedNodes: [injectedNodes()[1]],
        adoptedCaseIds: ['case-1'],
      },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.vm.$nextTick()

    let emissions = wrapper.emitted('selectionChange') ?? []
    expect(emissions[emissions.length - 1]).toEqual([[]])

    await wrapper.setProps({ injectedNodes: injectedNodes() })
    await wrapper.vm.$nextTick()
    emissions = wrapper.emitted('selectionChange') ?? []
    expect(emissions[emissions.length - 1]).toEqual([['case-1']])
  })

  it('重新注入提案集合时清空旧勾选', async () => {
    const wrapper = mount(CaseSelector, {
      props: { modelValue: true, inline: true, injectedNodes: injectedNodes() },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.vm.$nextTick()
    const caseChip = wrapper.findAll('.cst-chip').find((chip) => chip.text().includes('登录成功'))
    caseChip!.trigger('click')
    await wrapper.vm.$nextTick()

    await wrapper.setProps({ injectedNodes: [injectedNodes()[0]] })
    await wrapper.vm.$nextTick()

    const emissions = wrapper.emitted('selectionChange') ?? []
    expect(emissions[emissions.length - 1]).toEqual([[]])
  })

  it('常规弹窗模式仍加载模块树', async () => {
    mount(CaseSelector, {
      props: { modelValue: true },
      global: { plugins: [ElementPlus] },
    })
    await Promise.resolve()

    expect(mocks.fetchProjectModuleTree).toHaveBeenCalledTimes(1)
  })
})
