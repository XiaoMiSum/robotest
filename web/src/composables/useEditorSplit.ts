import { ref, watch, onUnmounted, type ComponentPublicInstance } from 'vue'

/**
 * 左右可拖拽分割条（从 SceneEditorPage 提取）。
 * 每个 pane 独立实例，避免容器 ref 互抢。
 */
export function useEditorSplit(initial = 0.42) {
  const ratio = ref(initial)
  const dragging = ref(false)
  let container: HTMLElement | null = null
  const register = (el: Element | ComponentPublicInstance | null): void => {
    container = (el as HTMLElement | null)
  }
  const start = () => { dragging.value = true }
  const move = (e: MouseEvent) => {
    if (!dragging.value || !container) return
    const rect = container.getBoundingClientRect()
    ratio.value = Math.min(0.6, Math.max(0.28, (e.clientX - rect.left) / rect.width))
  }
  const end = () => { dragging.value = false }
  watch(dragging, (val) => {
    document.body.style.cursor = val ? 'col-resize' : ''
    document.body.style.userSelect = val ? 'none' : ''
    if (val) {
      window.addEventListener('mousemove', move)
      window.addEventListener('mouseup', end)
    } else {
      window.removeEventListener('mousemove', move)
      window.removeEventListener('mouseup', end)
    }
  })
  onUnmounted(() => {
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', end)
  })
  return { ratio, dragging, register, start }
}
