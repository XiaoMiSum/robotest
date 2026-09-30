// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { useDrawerResize } from './useDrawerResize'

function fire(type: string, clientX: number): void {
  const event = new Event(type) as PointerEvent
  Object.defineProperty(event, 'clientX', { value: clientX })
  document.dispatchEvent(event)
}

let drawer: ReturnType<typeof useDrawerResize>

beforeEach(() => {
  drawer = useDrawerResize()
})

function startAt(clientX: number): void {
  const event = new Event('pointerdown') as PointerEvent
  Object.defineProperty(event, 'clientX', { value: clientX })
  Object.defineProperty(event, 'preventDefault', { value: () => {} })
  drawer.onResizeStart(event)
}

describe('useDrawerResize 抽屉宽度', () => {
  it('初始化写入默认宽度到 CSS 变量', () => {
    expect(drawer.width.value).toBeGreaterThanOrEqual(480)
    expect(document.documentElement.style.getPropertyValue('--ai-dw')).toBe(
      `${drawer.width.value}px`,
    )
  })

  it('左缘向左拖加宽、pointerup 后停止跟随', () => {
    const before = drawer.width.value
    startAt(300)
    fire('pointermove', 200)
    expect(drawer.width.value).toBe(before + 100)
    expect(document.documentElement.style.getPropertyValue('--ai-dw')).toBe(
      `${drawer.width.value}px`,
    )

    fire('pointerup', 200)
    fire('pointermove', 100)
    expect(drawer.width.value).toBe(before + 100)
  })

  it('收窄不低于 480px 下限', () => {
    startAt(0)
    fire('pointermove', 100000)
    expect(drawer.width.value).toBe(480)
    fire('pointerup', 0)
  })

  it('页面内两抽屉共享同一宽度记忆', () => {
    startAt(300)
    fire('pointermove', 250)
    fire('pointerup', 250)
    const second = useDrawerResize()
    expect(second.width.value).toBe(drawer.width.value)
  })
})
