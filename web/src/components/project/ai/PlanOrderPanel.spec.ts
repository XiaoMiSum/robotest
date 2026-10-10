// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import PlanOrderPanel from './PlanOrderPanel.vue'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { AiPlanOrderItem, AiPlanOrderSuggestion } from '@/types'

const DEFAULT_ITEMS: AiPlanOrderItem[] = [
  { nodeId: 'n1', caseTitle: '登录-验证码失效', suggestedRank: 1, reason: '阻塞面最广' },
  { nodeId: 'n2', caseTitle: '登录-图形验证码', suggestedRank: 2, reason: '核心链路' },
  { nodeId: 'n3', caseTitle: '冒烟用例集', suggestedRank: 3, reason: '' },
]

const DEFAULT_ORDER = DEFAULT_ITEMS.map((item) => item.caseTitle)

function planSuggestion(
  items: AiPlanOrderItem[] = DEFAULT_ITEMS,
  beforeOrder: string[] = DEFAULT_ORDER,
  afterOrder: string[] = DEFAULT_ORDER,
): AiPlanOrderSuggestion {
  return { planId: 'p1', items, beforeOrder, afterOrder }
}

function planRow(overrides: Partial<AssistRow> = {}): AssistRow {
  return {
    key: 'order-1',
    title: '执行顺序建议（3 项）',
    confirmStatus: 'pending',
    confirmLabel: '待确认',
    confirmTagType: 'info',
    suggestion: planSuggestion(),
    ...overrides,
  }
}

function mountPanel(props: Record<string, unknown> = {}): VueWrapper {
  return mount(PlanOrderPanel, {
    props: { rows: [planRow()], canAdopt: true, confirming: false, ...props },
    global: { plugins: [ElementPlus] },
  })
}

function titles(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.plan-order__title').map((item) => item.text())
}

function ranks(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.plan-order__rank').map((item) => item.text())
}

function actionButton(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!button) throw new Error(`未找到按钮：${text}`)
  return button
}

async function drag(wrapper: VueWrapper, from: number, to: number): Promise<void> {
  const rows = () => wrapper.findAll('.plan-order__row')
  await rows()[from].trigger('dragstart')
  await rows()[to].trigger('dragover')
  await rows()[to].trigger('drop')
}

describe('PlanOrderPanel', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('按建议名次初始化并渲染序号与理由', () => {
    const shuffled = planRow({
      suggestion: planSuggestion([
        { nodeId: 'n3', caseTitle: '冒烟用例集', suggestedRank: 3, reason: '' },
        { nodeId: 'n1', caseTitle: '登录-验证码失效', suggestedRank: 1, reason: '阻塞面最广' },
        { nodeId: 'n2', caseTitle: '登录-图形验证码', suggestedRank: 2, reason: '核心链路' },
      ]),
    })
    const wrapper = mountPanel({ rows: [shuffled] })

    expect(titles(wrapper)).toEqual(['登录-验证码失效', '登录-图形验证码', '冒烟用例集'])
    expect(ranks(wrapper)).toEqual(['1', '2', '3'])
    expect(wrapper.text()).toContain('阻塞面最广')
    expect(wrapper.text()).toContain('顺序与采纳前一致')
    expect(wrapper.text()).not.toContain('已人工调整')
  })

  it('拖拽调整后更新序号、前后对照与人工调整提示', async () => {
    const wrapper = mountPanel()

    await drag(wrapper, 0, 2)

    expect(titles(wrapper)).toEqual(['登录-图形验证码', '冒烟用例集', '登录-验证码失效'])
    expect(ranks(wrapper)).toEqual(['1', '2', '3'])
    expect(wrapper.text()).toContain('已人工调整')
    expect(wrapper.text()).toContain('3 项顺序变化')
    const afterColumn = wrapper.findAll('.plan-order__diff-cols ul')[1]
    expect(afterColumn?.text()).toContain('登录-验证码失效')
  })

  it('应用建议按当前顺序提交连续名次', async () => {
    const wrapper = mountPanel()
    await drag(wrapper, 2, 0)

    await actionButton(wrapper, '应用建议').trigger('click')

    expect(wrapper.emitted('adoptEdited')).toHaveLength(1)
    expect(wrapper.emitted('adoptEdited')?.[0]).toEqual([
      'order-1',
      {
        items: [
          { nodeId: 'n3', suggestedRank: 1 },
          { nodeId: 'n1', suggestedRank: 2 },
          { nodeId: 'n2', suggestedRank: 3 },
        ],
      },
    ])
  })

  it('放弃关闭面板且不产生变更', async () => {
    const wrapper = mountPanel()

    await actionButton(wrapper, '放弃').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('adoptEdited')).toBeUndefined()
  })

  it('无执行权限时置灰并提示原因', () => {
    const wrapper = mountPanel({ canAdopt: false })

    expect(actionButton(wrapper, '应用建议').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.plan-order__reason-text').text()).toBe('当前账号没有计划执行权限')
  })

  it('建议已采纳后提示失效并禁止重复应用', () => {
    const wrapper = mountPanel({
      rows: [
        planRow({ confirmStatus: 'adopted', confirmLabel: '已采纳', confirmTagType: 'success' }),
      ],
    })

    expect(actionButton(wrapper, '应用建议').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.plan-order__reason-text').text()).toBe('建议已采纳或已失效')
  })

  it('内容缺失时给出占位，加载中渲染骨架', () => {
    const empty = mountPanel({ rows: [planRow({ suggestion: null })] })
    expect(empty.text()).toContain('建议内容暂不可用')
    expect(actionButton(empty, '应用建议').attributes('disabled')).toBeDefined()

    const loading = mountPanel({ rows: [planRow({ suggestion: null })], loading: true })
    expect(loading.find('.el-skeleton').exists()).toBe(true)
  })

  it('无建议清单时禁止应用', () => {
    const wrapper = mountPanel({ rows: [planRow({ suggestion: planSuggestion([]) })] })

    expect(actionButton(wrapper, '应用建议').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.plan-order__reason-text').text()).toBe('建议清单为空')
  })

  it('未就绪时按钮保持禁用且不渲染置灰原因', async () => {
    const wrapper = mountPanel({ rows: [] })

    expect(wrapper.text()).toContain('建议内容暂不可用')
    expect(actionButton(wrapper, '应用建议').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.plan-order__reason-text').exists()).toBe(false)

    await nextTick()
    expect(wrapper.emitted('adoptEdited')).toBeUndefined()
  })
})
