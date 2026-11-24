// @vitest-environment jsdom
/* eslint-disable vue/one-component-per-file -- 测试桩组件与被测组件同文件，便于就近喂行 */
import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import AiCaseGenerationPanel from './AiCaseGenerationPanel.vue'
import panelSource from './AiCaseGenerationPanel.vue?raw'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { AiProposalConfirmResult, TestCaseNode } from '@/types'

const selectorStub = defineComponent({
  props: {
    injectedNodes: { type: Array, default: () => [] },
    modelValue: { type: Boolean, default: false },
    inline: { type: Boolean, default: false },
    adoptedCaseIds: { type: Array, default: () => [] },
    showAdopted: { type: Boolean, default: false },
  },
  emits: ['selectionChange', 'update:showAdopted'],
  setup(props, { emit }) {
    return () =>
      h('div', { class: 'selector-stub' }, [
        ...(props.injectedNodes as TestCaseNode[]).map((node) =>
          h('span', { class: 'stub-key', 'data-key': node.id }, [
            node.title,
            ...node.children.map((child) => h('em', { class: 'stub-child' }, child.title)),
          ]),
        ),
        h(
          'button',
          {
            class: 'pick',
            onClick: () => emit('selectionChange', [(props.injectedNodes as TestCaseNode[])[0]?.id]),
          },
          '勾选第一条',
        ),
        h(
          'button',
          {
            class: 'pick-all',
            onClick: () =>
              emit('selectionChange', (props.injectedNodes as TestCaseNode[]).map((node) => node.id)),
          },
          '勾选全部',
        ),
        h(
          'button',
          {
            class: 'pick-adopted',
            onClick: () =>
              emit(
                'selectionChange',
                (props.adoptedCaseIds as string[]).filter((id) =>
                  (props.injectedNodes as TestCaseNode[]).some((node) => node.id === id),
                ),
              ),
          },
          '勾选已采纳',
        ),
        h(
          'button',
          {
            class: 'toggle-adopted',
            onClick: () => emit('update:showAdopted', !props.showAdopted),
          },
          '切换展示已采纳',
        ),
      ])
  },
})

// el-dialog 在 jsdom 中延迟渲染且 teleport 到 body，测试里改为就地渲染以稳定断言
const dialogStub = defineComponent({
  props: { modelValue: { type: Boolean, default: false } },
  setup(_props, { slots }) {
    return () => h('div', { class: 'dialog-stub' }, slots.default?.())
  },
})

function row(key: string, confirmStatus: AssistRow['confirmStatus'] = 'pending'): AssistRow {
  return {
    key,
    title: `${key} 用例`,
    confirmStatus,
    confirmLabel: '待确认',
    confirmTagType: 'info',
    suggestion: {
      priority: 'P0',
      precondition: ['已登录'],
      steps: ['点击提交'],
      expected: ['提交成功'],
      tags: ['登录'],
      sourceRefs: [],
    },
  }
}

function mountPanel(
  props: Record<string, unknown> = {},
): VueWrapper<InstanceType<typeof AiCaseGenerationPanel>> {
  return mount(AiCaseGenerationPanel, {
    props: {
      modelValue: true,
      requirement: { id: 'r1', code: 'REQ-001', title: '验证码需求' },
      rows: [row('case-1')],
      ...props,
    },
    global: {
      plugins: [ElementPlus],
      stubs: { CaseSelector: selectorStub, 'el-dialog': dialogStub },
    },
  })
}

function buttonByText(wrapper: VueWrapper<InstanceType<typeof AiCaseGenerationPanel>>, text: string) {
  return wrapper.findAll('button').find((item) => item.text().trim() === text)
}

describe('AiCaseGenerationPanel', () => {
  it('页脚采纳按钮右对齐且为 link 主色', async () => {
    const wrapper = mountPanel({ rows: [row('case-1')] })
    await flushPromises()

    const adopt = buttonByText(wrapper, '采纳')
    expect(adopt?.classes()).toContain('is-link')
    expect(adopt?.classes()).toContain('el-button--primary')
    expect(panelSource).toMatch(/\.ai-generation__actions\s*\{[^}]*justify-content:\s*flex-end;/)
  })

  it('把每条提案映射为可勾选的用例树，节点 id 沿用提案 key', async () => {
    const wrapper = mountPanel({ rows: [row('case-1'), row('case-2')] })
    await flushPromises()

    const keys = wrapper.findAll('.stub-key')
    expect(keys.map((item) => item.attributes('data-key'))).toEqual(['case-1', 'case-2'])
    expect(keys[0].find('.stub-child').text()).toBe('已登录')
    expect(wrapper.find('.ai-generation__title').text()).toContain('验证码需求')
    expect(wrapper.find('.ai-generation__selector').exists()).toBe(true)
  })

  it('勾选后按提案 key 透传采纳，未勾选时按钮禁用', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    expect(buttonByText(wrapper, '采纳')?.attributes('disabled')).toBeDefined()

    await wrapper.find('.pick').trigger('click')
    expect(buttonByText(wrapper, '采纳')?.attributes('disabled')).toBeUndefined()

    await buttonByText(wrapper, '采纳')!.trigger('click')
    expect(wrapper.emitted('adopt')).toEqual([[['case-1']]])
  })

  it('已完成任务默认隐藏已采纳用例，勾选后展示且只读', async () => {
    const wrapper = mountPanel({ rows: [row('case-old', 'adopted'), row('case-new')] })
    await flushPromises()

    expect(wrapper.findAll('.stub-key').map((item) => item.attributes('data-key'))).toEqual(['case-new'])

    await wrapper.find('.toggle-adopted').trigger('click')
    expect(wrapper.findAll('.stub-key').map((item) => item.attributes('data-key'))).toEqual([
      'case-old',
      'case-new',
    ])
  })

  it('勾选全为已采纳用例时采纳按钮置灰且不透传', async () => {
    const wrapper = mountPanel({ rows: [row('case-old', 'adopted'), row('case-new')] })
    await flushPromises()
    await wrapper.find('.toggle-adopted').trigger('click')
    await wrapper.find('.pick-adopted').trigger('click')

    expect(buttonByText(wrapper, '采纳')?.attributes('disabled')).toBeDefined()
    await buttonByText(wrapper, '采纳')!.trigger('click')
    expect(wrapper.emitted('adopt')).toBeUndefined()
  })

  it('已采纳与新用例同时勾选时只透传新用例 key', async () => {
    const wrapper = mountPanel({ rows: [row('case-old', 'adopted_edited'), row('case-new')] })
    await flushPromises()
    await wrapper.find('.toggle-adopted').trigger('click')
    await wrapper.find('.pick-all').trigger('click')

    await buttonByText(wrapper, '采纳')!.trigger('click')
    expect(wrapper.emitted('adopt')).toEqual([[['case-new']]])
  })

  it('进行中或失败态不展示勾选树也不允许采纳', async () => {
    const running = mountPanel({ running: true })
    await flushPromises()
    expect(running.find('.selector-stub').exists()).toBe(false)
    expect(running.emitted('adopt')).toBeUndefined()

    const failed = mountPanel({ failed: true, failedReason: '需求不存在' })
    await flushPromises()
    expect(failed.text()).toContain('需求不存在')
    expect(failed.find('.selector-stub').exists()).toBe(false)
  })

  it('展示插入回执并映射提案标题', async () => {
    const receipt: AiProposalConfirmResult[] = [
      {
        key: 'case-1',
        action: 'adopted',
        success: true,
        createdId: 'node-1',
        errorCode: null,
        errorMsg: null,
      },
    ]
    const wrapper = mountPanel({ receipt })
    await flushPromises()

    expect(wrapper.text()).toContain('确认完成')
  })

  it('回执失败项重试时按提案 key 透传给宿主', async () => {
    const receipt: AiProposalConfirmResult[] = [
      {
        key: 'case-1',
        action: 'adopted',
        success: false,
        createdId: null,
        errorCode: 1000018300,
        errorMsg: '确认失败',
      },
    ]
    const wrapper = mountPanel({ receipt })
    await flushPromises()

    await buttonByText(wrapper, '重试')!.trigger('click')
    expect(wrapper.emitted('retryItem')).toEqual([['case-1']])
  })

  it('关闭时透传 modelValue 变更交给宿主统一释放任务', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    await buttonByText(wrapper, '关闭')!.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })
})

describe('AiCaseGenerationPanel · 源码结构约束', () => {
  it('采纳动作只发事件，节点变更与采纳回执均由宿主执行', () => {
    expect(panelSource).toContain('@click="adopt"')
    expect(panelSource).toContain("emit('adopt', [...adoptableKeys.value])")
    expect(panelSource).not.toContain('submitItems')
    expect(panelSource).not.toContain('insertCases')
  })

  it('结果区不额外包裹容器，弹窗高度固定且仅卡片区内部滚动', () => {
    expect(panelSource).not.toContain('class="ai-generation__tree"')
    expect(panelSource).toContain('class="ai-generation__selector"')
    expect(panelSource).toContain('.ai-generation-dialog')
    expect(panelSource).toContain('height: 88vh')
    expect(panelSource).toContain('.el-dialog__body')
    expect(panelSource).toContain('min-height: 0')
    expect(panelSource).toContain('overflow: hidden')
  })
})
