// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import ElementPlus, { ElButton } from 'element-plus'
import NodeActionPopover from './NodeActionPopover.vue'
import popoverSource from './NodeActionPopover.vue?raw'

const baseProps = { visible: true, x: 120, y: 240 }

function mountPopover(
  props: Partial<typeof baseProps & { to?: string | HTMLElement }> = {},
  labels: string[] = ['通过', '不通过'],
) {
  return mount(NodeActionPopover, {
    props: { ...baseProps, ...props },
    slots: {
      default: () => labels.map((label) => h(ElButton, { link: true }, () => label)),
    },
    global: { plugins: [ElementPlus] },
  })
}

function entries(): HTMLButtonElement[] {
  return Array.from(document.body.querySelectorAll<HTMLButtonElement>('.node-action-popover button'))
}

describe('NodeActionPopover', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('按节点屏幕坐标渲染 slot 入口', () => {
    mountPopover()
    const popover = document.body.querySelector('.node-action-popover') as HTMLElement
    expect(popover).not.toBeNull()
    expect(popover.style.left).toBe('120px')
    expect(popover.style.top).toBe('240px')
    expect(entries().map((item) => item.textContent?.trim())).toEqual(['通过', '不通过'])
  })

  it('隐藏时不渲染浮层', () => {
    mountPopover({ visible: false })
    expect(document.body.querySelector('.node-action-popover')).toBeNull()
  })

  it('默认挂载到 body，可经 to 改写为全屏容器', () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    mountPopover({ to: host })
    expect(host.querySelector('.node-action-popover')).not.toBeNull()
    expect(document.body.querySelector('.mindmap-container .node-action-popover')).toBeNull()
  })

  it('浮层内点击不冒泡到画布', () => {
    const wrapper = mountPopover()
    entries()[0].click()
    expect(wrapper.emitted()).toEqual({})
    expect(document.body.querySelector('.node-action-popover')).not.toBeNull()
  })
})

describe('NodeActionPopover · 透明背景 link 按钮水平排列', () => {
  it('入口渲染为 Element Plus link 按钮', () => {
    mountPopover()
    expect(entries().every((item) => item.classList.contains('el-button') && item.classList.contains('is-link'))).toBe(true)
  })

  it('浮层无卡片底色、边框与阴影，背景透明', () => {
    expect(popoverSource).toContain('background: transparent;')
    expect(popoverSource).not.toContain('background: var(--el-bg-color)')
    expect(popoverSource).not.toContain('box-shadow')
    expect(popoverSource).not.toContain('border: 1px')
  })

  it('入口水平并排', () => {
    expect(popoverSource).toContain('flex-direction: row;')
    expect(popoverSource).toContain('align-items: center;')
    expect(popoverSource).not.toContain('flex-direction: column;')
  })

  it('浮层拦截右键与点击冒泡', () => {
    expect(popoverSource).toContain('@click.stop')
    expect(popoverSource).toContain('@contextmenu.prevent')
  })
})
