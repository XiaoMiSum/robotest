// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  onBeforeUnmountCb: null as (() => void) | null,
}))

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    onBeforeUnmount: (cb: () => void) => { mocks.onBeforeUnmountCb = cb },
  }
})

import { useCaseNodePopover } from './useCaseNodePopover'

const anchor = { x: 120, y: 240 }

describe('useCaseNodePopover', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.onBeforeUnmountCb = null
    document.body.innerHTML = ''
  })

  it('打开后按节点渲染框定位并记录目标节点', () => {
    const popover = useCaseNodePopover()

    popover.open('case-1', anchor)

    expect(popover.visible.value).toBe(true)
    expect(popover.anchor.value).toEqual(anchor)
    expect(popover.targetNodeId.value).toBe('case-1')
  })

  it('空节点 ID 不展开浮层', () => {
    const popover = useCaseNodePopover()

    popover.open('', anchor)

    expect(popover.visible.value).toBe(false)
    expect(popover.targetNodeId.value).toBe('')
  })

  it('窗外按下、Esc 与滚轮关闭，窗内按下不关闭', () => {
    const popover = useCaseNodePopover()
    popover.open('case-1', anchor)

    const inside = document.createElement('div')
    inside.className = 'case-node-popover'
    document.body.appendChild(inside)
    inside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(popover.visible.value).toBe(true)

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(popover.visible.value).toBe(false)

    popover.open('case-1', anchor)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(popover.visible.value).toBe(false)

    popover.open('case-1', anchor)
    document.dispatchEvent(new Event('wheel'))
    expect(popover.visible.value).toBe(false)
  })

  it('关闭后清空目标节点', () => {
    const popover = useCaseNodePopover()
    popover.open('case-1', anchor)

    popover.close()

    expect(popover.visible.value).toBe(false)
    expect(popover.targetNodeId.value).toBe('')
  })

  it('卸载时关闭浮层并摘除全局监听', () => {
    const popover = useCaseNodePopover()
    popover.open('case-1', anchor)

    mocks.onBeforeUnmountCb!()

    expect(popover.visible.value).toBe(false)
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(popover.visible.value).toBe(false)
  })
})
