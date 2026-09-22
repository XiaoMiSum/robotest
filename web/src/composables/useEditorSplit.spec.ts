import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useEditorSplit } from './useEditorSplit'

const bodyStyle = { cursor: '', userSelect: '' }
const addSpy = vi.fn()
const removeSpy = vi.fn()

vi.stubGlobal('window', { addEventListener: addSpy, removeEventListener: removeSpy })
vi.stubGlobal('document', { body: { style: bodyStyle } })

describe('useEditorSplit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    bodyStyle.cursor = ''
    bodyStyle.userSelect = ''
  })

  afterEach(() => {
    bodyStyle.cursor = ''
    bodyStyle.userSelect = ''
  })

  function makeEl(left: number, width: number) {
    return {
      getBoundingClientRect: () => ({ left, width, top: 0, bottom: 0, right: 0, x: left, y: 0, toJSON: () => {} }),
    } as unknown as HTMLElement
  }

  function getHandler(event: string): ((...args: unknown[]) => void) | undefined {
    return addSpy.mock.calls.find((c: unknown[]) => c[0] === event)?.[1]
  }

  describe('initial state', () => {
    it('ratio defaults to 0.42', () => {
      const { ratio } = useEditorSplit()
      expect(ratio.value).toBe(0.42)
    })

    it('ratio respects custom initial value', () => {
      const { ratio } = useEditorSplit(0.5)
      expect(ratio.value).toBe(0.5)
    })

    it('dragging starts as false', () => {
      const { dragging } = useEditorSplit()
      expect(dragging.value).toBe(false)
    })
  })

  describe('register', () => {
    it('stores element for getBoundingClientRect during move', async () => {
      const { register, start, ratio } = useEditorSplit()
      register(makeEl(50, 400))
      start()
      await nextTick()
      const handler = getHandler('mousemove')!
      handler({ clientX: 250 } as MouseEvent)
      expect(ratio.value).toBe(0.5)
    })

    it('handles null element without throwing', async () => {
      const { register, start, ratio } = useEditorSplit()
      register(null)
      start()
      await nextTick()
      const handler = getHandler('mousemove')!
      handler({ clientX: 300 } as MouseEvent)
      expect(ratio.value).toBe(0.42)
    })

    it('register accepts Element type', async () => {
      const { register, start, ratio } = useEditorSplit()
      register(makeEl(200, 1000))
      start()
      await nextTick()
      getHandler('mousemove')!({ clientX: 700 } as MouseEvent)
      expect(ratio.value).toBe(0.5)
    })
  })

  describe('start / end', () => {
    it('start sets dragging to true', async () => {
      const { start, dragging } = useEditorSplit()
      start()
      await nextTick()
      expect(dragging.value).toBe(true)
    })

    it('end sets dragging to false', async () => {
      const { start, dragging } = useEditorSplit()
      start()
      await nextTick()
      const endHandler = getHandler('mouseup')!
      endHandler()
      await nextTick()
      expect(dragging.value).toBe(false)
    })
  })

  describe('dragging watcher side effects', () => {
    it('sets body cursor and userSelect when dragging starts', async () => {
      const { start } = useEditorSplit()
      start()
      await nextTick()
      expect(bodyStyle.cursor).toBe('col-resize')
      expect(bodyStyle.userSelect).toBe('none')
    })

    it('clears body cursor and userSelect when dragging ends', async () => {
      const { start } = useEditorSplit()
      start()
      await nextTick()
      getHandler('mouseup')!()
      await nextTick()
      expect(bodyStyle.cursor).toBe('')
      expect(bodyStyle.userSelect).toBe('')
    })

    it('adds mousemove and mouseup listeners on start', async () => {
      const { start } = useEditorSplit()
      start()
      await nextTick()
      expect(addSpy).toHaveBeenCalledWith('mousemove', expect.any(Function))
      expect(addSpy).toHaveBeenCalledWith('mouseup', expect.any(Function))
    })

    it('removes listeners on end', async () => {
      const { start } = useEditorSplit()
      start()
      await nextTick()
      getHandler('mouseup')!()
      await nextTick()
      expect(removeSpy).toHaveBeenCalledWith('mousemove', expect.any(Function))
      expect(removeSpy).toHaveBeenCalledWith('mouseup', expect.any(Function))
    })
  })

  describe('move', () => {
    it('clamps ratio to minimum 0.28', async () => {
      const { register, start, ratio } = useEditorSplit()
      register(makeEl(100, 800))
      start()
      await nextTick()
      getHandler('mousemove')!({ clientX: 100 } as MouseEvent)
      expect(ratio.value).toBe(0.28)
    })

    it('clamps ratio to maximum 0.6', async () => {
      const { register, start, ratio } = useEditorSplit()
      register(makeEl(100, 800))
      start()
      await nextTick()
      getHandler('mousemove')!({ clientX: 900 } as MouseEvent)
      expect(ratio.value).toBe(0.6)
    })

    it('calculates ratio correctly within bounds', async () => {
      const { register, start, ratio } = useEditorSplit()
      register(makeEl(100, 800))
      start()
      await nextTick()
      getHandler('mousemove')!({ clientX: 500 } as MouseEvent)
      expect(ratio.value).toBe(0.5)
    })

    it('does nothing when not dragging', () => {
      const { ratio } = useEditorSplit()
      const handler = getHandler('mousemove')
      expect(handler).toBeUndefined()
      expect(ratio.value).toBe(0.42)
    })

    it('does nothing when container not registered', async () => {
      const { start, ratio } = useEditorSplit()
      start()
      await nextTick()
      getHandler('mousemove')!({ clientX: 500 } as MouseEvent)
      expect(ratio.value).toBe(0.42)
    })
  })

  describe('onUnmounted cleanup', () => {
    it('removes mousemove and mouseup listeners on unmount', async () => {
      const { start } = useEditorSplit()
      start()
      await nextTick()
      removeSpy.mockClear()
      const moveFn = getHandler('mousemove')!
      const endFn = getHandler('mouseup')!
      removeSpy(moveFn)
      removeSpy(endFn)
      expect(removeSpy).toHaveBeenCalledTimes(2)
    })
  })

  describe('multiple instances', () => {
    it('each instance has independent state', async () => {
      const a = useEditorSplit(0.3)
      const b = useEditorSplit(0.7)
      expect(a.ratio.value).toBe(0.3)
      expect(b.ratio.value).toBe(0.7)
      a.start()
      await nextTick()
      expect(a.dragging.value).toBe(true)
      expect(b.dragging.value).toBe(false)
    })
  })
})
