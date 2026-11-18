// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import CaseNodeActionPopover from './CaseNodeActionPopover.vue'

const baseProps = {
  visible: true,
  x: 120,
  y: 240,
  aiEntryVisible: true,
  aiGateReason: '',
  canLaunchAi: true,
  requirementDisabled: false,
  requirementLoading: false,
  requirementTitle: '为当前用例关联需求',
}

function mountPopover(props: Partial<typeof baseProps> = {}) {
  return mount(CaseNodeActionPopover, {
    props: { ...baseProps, ...props },
    global: { plugins: [ElementPlus] },
  })
}

function items(): HTMLButtonElement[] {
  return Array.from(document.body.querySelectorAll('.case-node-popover button'))
}

describe('CaseNodeActionPopover', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('按节点屏幕坐标渲染三项入口', () => {
    mountPopover()
    const popover = document.body.querySelector('.case-node-popover') as HTMLElement
    expect(popover).not.toBeNull()
    expect(popover.style.left).toBe('120px')
    expect(popover.style.top).toBe('240px')
    expect(items().map((item) => item.textContent?.trim())).toEqual([
      'AI 补全',
      '级别推荐',
      '关联需求',
    ])
  })

  it('AI 未启用时仅展示关联需求', () => {
    mountPopover({ aiEntryVisible: false })
    expect(items().map((item) => item.textContent?.trim())).toEqual(['关联需求'])
  })

  it('按门槛与关联状态置灰入口', () => {
    mountPopover({ canLaunchAi: false, requirementDisabled: true })
    expect(items().every((item) => item.disabled)).toBe(true)
  })

  it('点击可用入口透传事件，禁用入口不触发', () => {
    const wrapper = mountPopover()
    items()[0].click()
    items()[2].click()
    expect(wrapper.emitted('ai-complete')).toHaveLength(1)
    expect(wrapper.emitted('link-requirement')).toHaveLength(1)

    document.body.innerHTML = ''
    const disabled = mountPopover({ canLaunchAi: false, requirementDisabled: true })
    items().forEach((item) => item.click())
    expect(disabled.emitted('ai-complete')).toBeUndefined()
    expect(disabled.emitted('ai-priority')).toBeUndefined()
    expect(disabled.emitted('link-requirement')).toBeUndefined()
  })

  it('隐藏时不渲染浮层', () => {
    mountPopover({ visible: false })
    expect(document.body.querySelector('.case-node-popover')).toBeNull()
  })
})
