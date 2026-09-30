import { ref } from 'vue'

const DEFAULT_WIDTH = 640
const MIN_WIDTH = 480
const CSS_VAR = '--ai-dw'

/** 页面内两抽屉共用同一宽度记忆（交互设计 45 §2.9 左缘拖拽调宽 480px–90vw） */
let sharedWidth = DEFAULT_WIDTH

function clamp(value: number): number {
  const max = typeof window === 'undefined' ? DEFAULT_WIDTH : window.innerWidth * 0.9
  return Math.min(Math.max(value, MIN_WIDTH), Math.max(max, MIN_WIDTH))
}

function applyWidth(width: number): void {
  if (typeof document === 'undefined') return
  document.documentElement.style.setProperty(CSS_VAR, `${width}px`)
}

/**
 * 抽屉左缘拖拽调宽：pointerdown 起在文档级监听 move/up，宽度写入 CSS 变量；
 * 页面内记忆，两个抽屉实例共享。
 */
export function useDrawerResize() {
  const width = ref(clamp(sharedWidth))
  applyWidth(width.value)

  function onResizeStart(event: PointerEvent): void {
    event.preventDefault()
    const startX = event.clientX
    const startWidth = width.value

    function move(ev: PointerEvent): void {
      // 向左拖加宽、向右拖收窄（把手在抽屉左缘）
      width.value = clamp(startWidth + (startX - ev.clientX))
      sharedWidth = width.value
      applyWidth(width.value)
    }

    function up(): void {
      document.removeEventListener('pointermove', move)
      document.removeEventListener('pointerup', up)
    }

    document.addEventListener('pointermove', move)
    document.addEventListener('pointerup', up)
  }

  return { width, onResizeStart }
}
