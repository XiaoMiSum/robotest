// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ReceiptCard from './ReceiptCard.vue'
import type { AiAssistantExecution, AiAssistantIntent } from '@/types'

function makeIntent(overrides: Partial<AiAssistantIntent> = {}): AiAssistantIntent {
  return {
    kind: 'create_plan',
    targetType: 'test_plan',
    targetTitle: '回归计划',
    createCount: 1,
    changes: [],
    scope: { workspaceId: 'w1', projectId: 'p1' },
    expiresAt: '2026-10-08T09:10:00',
    ...overrides,
  }
}

function makeExecution(overrides: Partial<AiAssistantExecution> = {}): AiAssistantExecution {
  return {
    status: 'executed',
    executedBy: 'u1',
    executedAt: '2026-10-08T01:00:00Z',
    ...overrides,
  }
}

interface CardProps {
  execution?: AiAssistantExecution
  intent?: AiAssistantIntent | null
  executing?: boolean
  readonly?: boolean
}

let activeWrapper: VueWrapper | null = null

function mountCard(props: CardProps = {}): VueWrapper {
  const wrapper = mount(ReceiptCard, {
    props: {
      execution: makeExecution(),
      intent: makeIntent(),
      executing: false,
      readonly: false,
      ...props,
    },
    global: { plugins: [ElementPlus] },
  })
  activeWrapper = wrapper
  return wrapper
}

function findButton(wrapper: VueWrapper, text: string) {
  const found = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!found) throw new Error(`未找到按钮：${text}`)
  return found
}

describe('components/ai/assistant/ReceiptCard', () => {
  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = null
  })

  it('逐项标注成败与失败原因', () => {
    const wrapper = mountCard({
      execution: makeExecution({
        results: [
          { action: 'create_plan', success: true, createdId: 'p1' },
          {
            action: 'create_case',
            success: false,
            errorCode: 1000018257,
            errorMsg: '无权限执行该操作',
          },
        ],
      }),
    })

    expect(wrapper.get('.ai-receipt').attributes('aria-label')).toBe('执行回执')
    expect(wrapper.text()).toContain('创建计划')
    expect(wrapper.text()).toContain('已执行')
    expect(wrapper.get('.ai-receipt__mark[aria-label="成功"]').text()).toBe('✓')
    expect(wrapper.get('.ai-receipt__mark[aria-label="失败"]').text()).toBe('✗')
    expect(wrapper.text()).toContain('创建用例')
    expect(wrapper.text()).toContain('无权限执行该操作（1000018257）')
    expect(wrapper.text()).toContain('执行时间：')
  })

  it('失败项点击重试携带结果数组下标', async () => {
    const wrapper = mountCard({
      execution: makeExecution({
        results: [
          { action: 'create_plan', success: true },
          { action: 'create_case', success: false, errorMsg: '失败一' },
          { action: 'create_case', success: false, errorMsg: '失败二' },
        ],
      }),
    })

    const retryButtons = wrapper
      .findAll('button')
      .filter((item) => item.text().includes('重试'))
    expect(retryButtons).toHaveLength(2)

    await retryButtons[0].trigger('click')
    expect(wrapper.emitted('retry')?.[0]).toEqual([1])
    await retryButtons[1].trigger('click')
    expect(wrapper.emitted('retry')?.[1]).toEqual([2])
  })

  it('执行中或归档只读禁用重试', async () => {
    const wrapper = mountCard({
      execution: makeExecution({
        results: [{ action: 'create_case', success: false, errorMsg: '失败' }],
      }),
      executing: true,
      readonly: true,
    })

    expect(findButton(wrapper, '重试').attributes('disabled')).toBeDefined()
    await findButton(wrapper, '重试').trigger('click')
    expect(wrapper.emitted('retry')).toBeUndefined()
  })

  it('已取消状态只读展示且无重试入口', () => {
    const wrapper = mountCard({
      execution: makeExecution({
        status: 'rejected',
        executedAt: null,
        rejectedBy: 'u1',
        rejectedAt: '2026-10-08T02:00:00Z',
      }),
    })

    expect(wrapper.text()).toContain('已取消')
    expect(wrapper.text()).toContain('已取消本次变更，未产生任何数据')
    expect(wrapper.text()).not.toContain('重试')
  })

  it('单项链接与整体链接均发出跳转事件', async () => {
    const wrapper = mountCard({
      execution: makeExecution({
        results: [
          { action: 'create_plan', success: true, link: '/workspace/projects/plans/p1' },
          { action: 'create_case', success: true },
        ],
        link: '/workspace/projects/plans/p1',
      }),
    })

    await findButton(wrapper, '查看').trigger('click')
    expect(wrapper.emitted('jump')?.[0]).toEqual(['/workspace/projects/plans/p1'])

    await findButton(wrapper, '查看结果').trigger('click')
    expect(wrapper.emitted('jump')?.[1]).toEqual(['/workspace/projects/plans/p1'])
  })

  it('进度查看类无回执项时展示空态并提供整体链接', async () => {
    const wrapper = mountCard({
      execution: makeExecution({ results: [], link: '/workspace/projects/plans/p1' }),
    })

    expect(wrapper.text()).toContain('执行完成，无回执项')
    expect(wrapper.text()).not.toContain('重试')

    await findButton(wrapper, '查看结果').trigger('click')
    expect(wrapper.emitted('jump')?.[0]).toEqual(['/workspace/projects/plans/p1'])
  })
})
