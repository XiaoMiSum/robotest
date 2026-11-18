import { onBeforeUnmount, ref } from 'vue'

export interface CaseNodePopoverAnchor {
  x: number
  y: number
}

function isMenuElement(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && Boolean(target.closest('.case-node-popover'))
}

export function useCaseNodePopover() {
  const visible = ref(false)
  const anchor = ref<CaseNodePopoverAnchor>({ x: 0, y: 0 })
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
    if (isMenuElement(event.target)) return
    close()
  }

  function closeOnEsc(event: KeyboardEvent): void {
    if (event.key !== 'Escape') return
    event.stopPropagation()
    close()
  }

  function open(nodeId: string, nextAnchor: CaseNodePopoverAnchor): void {
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
