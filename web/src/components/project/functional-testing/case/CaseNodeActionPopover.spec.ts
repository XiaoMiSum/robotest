// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import CaseNodeActionPopover from './CaseNodeActionPopover.vue'
import popoverSource from './CaseNodeActionPopover.vue?raw'

const baseProps = {
  visible: true,
  x: 120,
  y: 240,
  aiEntryVisible: true,
  aiGateReason: '',
  canLaunchAi: true,
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

  it('按节点屏幕坐标渲染 AI 优化与关联需求两项入口', () => {
    mountPopover()
    const popover = document.body.querySelector('.case-node-popover') as HTMLElement
    expect(popover).not.toBeNull()
    expect(popover.style.left).toBe('120px')
    expect(popover.style.top).toBe('240px')
    expect(items().map((item) => item.textContent?.trim())).toEqual([
      'AI 优化',
      '关联需求',
    ])
  })

  it('AI 未启用时仅展示关联需求', () => {
    mountPopover({ aiEntryVisible: false })
    expect(items().map((item) => item.textContent?.trim())).toEqual(['关联需求'])
  })

  it('按门槛置灰 AI 入口，关联需求入口始终可用', () => {
    mountPopover({ canLaunchAi: false })
    expect(items().map((item) => item.disabled)).toEqual([true, false])
  })

  it('点击可用入口透传事件，禁用入口不触发', () => {
    const wrapper = mountPopover()
    items()[0].click()
    items()[1].click()
    expect(wrapper.emitted('ai-optimize')).toHaveLength(1)
    expect(wrapper.emitted('link-requirement')).toHaveLength(1)

    document.body.innerHTML = ''
    const disabled = mountPopover({ canLaunchAi: false })
    items().forEach((item) => item.click())
    expect(disabled.emitted('ai-optimize')).toBeUndefined()
    expect(disabled.emitted('link-requirement')).toHaveLength(1)
  })

  it('隐藏时不渲染浮层', () => {
    mountPopover({ visible: false })
    expect(document.body.querySelector('.case-node-popover')).toBeNull()
  })
})

describe('CaseNodeActionPopover · 透明背景按钮', () => {
  it('悬浮窗不渲染卡片底色与阴影，入口使用文字按钮', () => {
    expect(popoverSource).toContain('<el-button text class="case-node-popover__item"')
    expect(popoverSource).not.toContain('background: var(--el-bg-color)')
    expect(popoverSource).not.toContain('box-shadow: var(--el-box-shadow-light)')
  })

  it('两个入口渲染为 Element Plus 文字按钮', () => {
    mountPopover()
    expect(items().every((item) => item.classList.contains('el-button') && item.classList.contains('is-text'))).toBe(true)
  })
})
