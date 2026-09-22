import { ref, watch, onBeforeUnmount } from 'vue'

/** 空格唤醒定位所需的最小节点形状（kityminder 节点，'screen' 参照系坐标即 client 坐标） */
export interface ContextMenuAnchorNode {
  getRenderBox?: (rendererType?: unknown, refer?: unknown) => { x: number; y: number; height: number }
}

export interface UseContextMenuOptions {
  /** 仅选中节点时弹出（右键节点时 core 已先行选中；空白处右键不弹菜单） */
  hasSelection: () => boolean
  /** 空格唤醒时读取选中节点以定位菜单 */
  getSelectedNode: () => ContextMenuAnchorNode | null | undefined
}

/**
 * 右键菜单状态与关闭时机（三模式组件共用）：
 * 菜单展示期间挂全局监听，点击菜单外、Esc、滚轮均关闭。
 * 不用 mouseleave（划过即消失不友好，且空格唤醒时鼠标可能根本不在菜单上）。
 */
export function useContextMenu(options: UseContextMenuOptions) {
  const visible = ref(false)
  const pos = ref({ x: 0, y: 0 })

  function onContextMenu(e: MouseEvent): void {
    if (!options.hasSelection()) return
    pos.value = { x: e.clientX, y: e.clientY }
    visible.value = true
  }

  // 空格唤醒菜单：定位到选中节点下方（菜单为 fixed 定位）
  function openAtSelection(): void {
    const node = options.getSelectedNode()
    if (!node?.getRenderBox) return
    const box = node.getRenderBox(undefined, 'screen')
    pos.value = { x: Math.round(box.x), y: Math.round(box.y + box.height + 4) }
    visible.value = true
  }

  function closeMenuOnOutsidePress(e: MouseEvent): void {
    if ((e.target as HTMLElement | null)?.closest?.('.mindmap-context-menu')) return
    visible.value = false
  }

  function closeMenuOnEsc(e: KeyboardEvent): void {
    if (e.key !== 'Escape') return
    // 吞掉按键，避免同时被键盘接收器转发给 core 造成取消选中等副作用
    e.stopPropagation()
    visible.value = false
  }

  function close(): void {
    visible.value = false
  }

  function removeGlobalListeners(): void {
    document.removeEventListener('mousedown', closeMenuOnOutsidePress, true)
    document.removeEventListener('keydown', closeMenuOnEsc, true)
    document.removeEventListener('wheel', close, true)
  }

  watch(visible, (v) => {
    // capture 阶段监听，画布等内部元素 stopPropagation 也不影响菜单关闭
    if (v) {
      document.addEventListener('mousedown', closeMenuOnOutsidePress, true)
      document.addEventListener('keydown', closeMenuOnEsc, true)
      document.addEventListener('wheel', close, true)
    } else {
      removeGlobalListeners()
    }
  })

  // 菜单开着时卸载组件，watch 不再触发，需兜底摘除全局监听
  onBeforeUnmount(() => {
    visible.value = false
    removeGlobalListeners()
  })

  return { visible, pos, onContextMenu, openAtSelection, close }
}
