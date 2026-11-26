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

function mountPopover(props: Partial<typeof baseProps & { to?: string | HTMLElement }> = {}) {
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
    expect(items()[0].getAttribute('aria-label')).toBe('AI 优化')
    expect(items()[0].querySelector('img')).not.toBeNull()
    expect(items()[1].textContent?.trim()).toBe('关联需求')
  })

  it('AI 未启用时仅展示关联需求', () => {
    mountPopover({ aiEntryVisible: false })
    expect(items()).toHaveLength(1)
    expect(items()[0].textContent?.trim()).toBe('关联需求')
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
    expect(popoverSource).toContain('class="case-node-popover__item case-node-popover__item--ai"')
    expect(popoverSource).toContain('aria-label="AI 优化"')
    expect(popoverSource).not.toContain('background: var(--el-bg-color)')
    expect(popoverSource).not.toContain('box-shadow: var(--el-box-shadow-light)')
  })

  it('两个入口渲染为 Element Plus 文字按钮', () => {
    mountPopover()
    expect(items().every((item) => item.classList.contains('el-button') && item.classList.contains('is-text'))).toBe(true)
  })

  it('两入口水平并排并采用差异化色彩与 AI 闪烁强调', () => {
    expect(popoverSource).toContain('flex-direction: row;')
    expect(popoverSource).toContain('--el-button-text-color: var(--color-success);')
    expect(popoverSource).toContain('animation: case-node-ai-pulse')
    expect(popoverSource).toContain('--el-button-text-color: var(--color-primary-500);')
  })

  it('AI 优化与关联需求渲染各自的样式类', () => {
    mountPopover()
    expect(items()[0].classList.contains('case-node-popover__item--ai')).toBe(true)
    expect(items()[1].classList.contains('case-node-popover__item--link')).toBe(true)
  })

  it('两个入口的悬浮提示均展示在下方', () => {
    expect(popoverSource.match(/placement="bottom"/g) ?? []).toHaveLength(2)
    expect(popoverSource).not.toContain('placement="right"')
  })

  it('默认挂载到 body，可经 to 改写为全屏容器', () => {
    expect(popoverSource).toContain('<teleport :to="to">')
    expect(popoverSource).toContain("{ to: 'body' }")

    const host = document.createElement('div')
    document.body.appendChild(host)
    mountPopover({ to: host })
    expect(host.querySelector('.case-node-popover')).not.toBeNull()
  })
})
