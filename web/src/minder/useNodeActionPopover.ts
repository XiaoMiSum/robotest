import { onBeforeUnmount, ref } from 'vue'

/** 悬浮窗相对节点的屏幕定位点（节点渲染框下方） */
export interface NodeActionPopoverAnchor {
  x: number
  y: number
}

/** 点击命中节点：悬浮窗定位与用例判定所需的最小形状 */
export interface NodeActionAnchorNode {
  data: Record<string, unknown>
  getRenderBox?: (rendererType?: unknown, refer?: unknown) => { x: number; y: number; height: number }
}

function isPopoverElement(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && Boolean(target.closest('.node-action-popover'))
}

/**
 * 节点悬浮窗状态与关闭时机（评审/计划共用）：
 * 浮层展开期间挂全局监听，窗外按下、Esc、滚轮均关闭；
 * 不用 mouseleave，点击节点后鼠标可能根本不在浮层上。
 */
export function useNodeActionPopover() {
  const visible = ref(false)
  const anchor = ref<NodeActionPopoverAnchor>({ x: 0, y: 0 })
  const targetNodeId = ref('')

  let listenersAttached = false

  function removeGlobalListeners(): void {
    if (!listenersAttached) return
    document.removeEventListener('mousedown', closeOnOutsidePress, true)
    document.removeEventListener('keydown', closeOnEsc, true)
    document.removeEventListener('wheel', close, true)
    listenersAttached = false
  }

  function addGlobalListeners(): void {
    if (listenersAttached) return
    document.addEventListener('mousedown', closeOnOutsidePress, true)
    document.addEventListener('keydown', closeOnEsc, true)
    document.addEventListener('wheel', close, true)
    listenersAttached = true
  }

  function close(): void {
    visible.value = false
    targetNodeId.value = ''
    removeGlobalListeners()
  }

  function closeOnOutsidePress(event: MouseEvent): void {
    if (isPopoverElement(event.target)) return
    close()
  }

  function closeOnEsc(event: KeyboardEvent): void {
    if (event.key !== 'Escape') return
    // 吞掉按键，避免同时被键盘接收器转发给 core 造成取消选中等副作用
    event.stopPropagation()
    close()
  }

  function open(nodeId: string, nextAnchor: NodeActionPopoverAnchor): void {
    if (!nodeId) return
    targetNodeId.value = nodeId
    anchor.value = nextAnchor
    visible.value = true
    addGlobalListeners()
  }

  onBeforeUnmount(() => {
    close()
  })

  return {
    visible,
    anchor,
    targetNodeId,
    open,
    close,
  }
}
