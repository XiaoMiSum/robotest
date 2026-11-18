// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetch: vi.fn(),
  onBeforeUnmountCb: null as (() => void) | null,
}))

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    onBeforeUnmount: (cb: () => void) => { mocks.onBeforeUnmountCb = cb },
  }
})

vi.mock('@/services/project', () => ({
  fetchCaseRequirements: mocks.fetch,
}))

import { useCaseNodePopover } from './useCaseNodePopover'

const anchor = { x: 120, y: 240 }

describe('useCaseNodePopover', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.onBeforeUnmountCb = null
    document.body.innerHTML = ''
  })

  it('打开后按节点渲染框定位，并查询关联状态', async () => {
    mocks.fetch.mockResolvedValue([{ id: 'r1', code: 'REQ-1', title: '需求' }])
    const popover = useCaseNodePopover()

    const pending = popover.open('case-1', anchor)
    expect(popover.visible.value).toBe(true)
    expect(popover.anchor.value).toEqual(anchor)
    expect(popover.requirementLoading.value).toBe(true)
    await pending

    expect(mocks.fetch).toHaveBeenCalledWith('case-1')
    expect(popover.requirementLinked.value).toBe(true)
    expect(popover.requirementLoading.value).toBe(false)
  })

  it('无关联需求时关联入口保持可用', async () => {
    mocks.fetch.mockResolvedValue([])
    const popover = useCaseNodePopover()
    await popover.open('case-1', anchor)
    expect(popover.requirementLinked.value).toBe(false)
  })

  it('查询失败按未关联处理，不阻塞入口', async () => {
    mocks.fetch.mockRejectedValue(new Error('网络错误'))
    const popover = useCaseNodePopover()
    await popover.open('case-1', anchor)
    expect(popover.requirementLinked.value).toBe(false)
    expect(popover.requirementLoading.value).toBe(false)
  })

  it('快速切换节点时丢弃过期的关联状态响应', async () => {
    let resolveFirst!: (value: unknown[]) => void
    mocks.fetch
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve }))
      .mockResolvedValueOnce([])
    const popover = useCaseNodePopover()

    const first = popover.open('case-1', anchor)
    const second = popover.open('case-2', anchor)
    await second
    resolveFirst([{ id: 'r1', code: 'REQ-1', title: '需求' }])
    await first

    expect(popover.targetNodeId.value).toBe('case-2')
    expect(popover.requirementLinked.value).toBe(false)
    expect(popover.requirementLoading.value).toBe(false)
  })

  it('窗外按下、Esc 与滚轮关闭，窗内按下不关闭', async () => {
    mocks.fetch.mockResolvedValue([])
    const popover = useCaseNodePopover()
    await popover.open('case-1', anchor)

    const inside = document.createElement('div')
    inside.className = 'case-node-popover'
    document.body.appendChild(inside)
    inside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(popover.visible.value).toBe(true)

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(popover.visible.value).toBe(false)

    await popover.open('case-1', anchor)
    const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(escape)
    expect(popover.visible.value).toBe(false)

    await popover.open('case-1', anchor)
    document.dispatchEvent(new Event('wheel'))
    expect(popover.visible.value).toBe(false)
  })

  it('关闭后清空目标与关联态', async () => {
    mocks.fetch.mockResolvedValue([{ id: 'r1', code: 'REQ-1', title: '需求' }])
    const popover = useCaseNodePopover()
    await popover.open('case-1', anchor)
    popover.close()
    expect(popover.visible.value).toBe(false)
    expect(popover.targetNodeId.value).toBe('')
    expect(popover.requirementLinked.value).toBe(false)
  })

  it('卸载时关闭浮层并摘除全局监听', async () => {
    mocks.fetch.mockResolvedValue([])
    const popover = useCaseNodePopover()
    await popover.open('case-1', anchor)
    mocks.onBeforeUnmountCb!()
    expect(popover.visible.value).toBe(false)
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(popover.visible.value).toBe(false)
  })
})
