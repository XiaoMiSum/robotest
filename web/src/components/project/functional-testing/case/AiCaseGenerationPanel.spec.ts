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
  },
  emits: ['selectionChange'],
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

function row(key: string): AssistRow {
  return {
    key,
    title: `${key} 用例`,
    confirmStatus: 'pending',
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
  return wrapper.findAll('button').find((item) => item.text().includes(text))
}

describe('AiCaseGenerationPanel', () => {
  it('把每条提案映射为可勾选的用例树，节点 id 沿用提案 key', async () => {
    const wrapper = mountPanel({ rows: [row('case-1'), row('case-2')] })
    await flushPromises()

    const keys = wrapper.findAll('.stub-key')
    expect(keys.map((item) => item.attributes('data-key'))).toEqual(['case-1', 'case-2'])
    expect(keys[0].find('.stub-child').text()).toBe('已登录')
    expect(wrapper.find('.ai-generation__title').text()).toContain('验证码需求')
  })

  it('勾选后插入按提案 key 透传，未勾选时按钮禁用', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    expect(buttonByText(wrapper, '插入选中用例')?.attributes('disabled')).toBeDefined()

    await wrapper.find('.pick').trigger('click')
    expect(buttonByText(wrapper, '插入选中用例')?.attributes('disabled')).toBeUndefined()

    await buttonByText(wrapper, '插入选中用例')!.trigger('click')
    expect(wrapper.emitted('insert')).toEqual([[['case-1']]])
  })

  it('进行中或失败态不展示勾选树也不允许插入', async () => {
    const running = mountPanel({ running: true })
    await flushPromises()
    expect(running.find('.selector-stub').exists()).toBe(false)
    expect(running.emitted('insert')).toBeUndefined()

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
  it('插入动作只发事件，节点变更与采纳回执均由宿主执行', () => {
    expect(panelSource).toContain('@click="insert"')
    expect(panelSource).toContain("emit('insert', [...pendingKeys.value])")
    expect(panelSource).not.toContain('submitItems')
    expect(panelSource).not.toContain('adopt(')
  })
})
