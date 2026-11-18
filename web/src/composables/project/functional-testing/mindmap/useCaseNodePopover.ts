import { onBeforeUnmount, ref } from 'vue'
import { fetchCaseRequirements } from '@/services/project'

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
  const requirementLinked = ref(false)
  const requirementLoading = ref(false)

  let requestSeq = 0
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
    requestSeq += 1
    visible.value = false
    targetNodeId.value = ''
    requirementLinked.value = false
    requirementLoading.value = false
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

  async function open(nodeId: string, nextAnchor: CaseNodePopoverAnchor): Promise<void> {
    if (!nodeId) return
    const seq = ++requestSeq
    targetNodeId.value = nodeId
    anchor.value = nextAnchor
    visible.value = true
    requirementLinked.value = false
    requirementLoading.value = true
    addGlobalListeners()

    try {
      const requirements = await fetchCaseRequirements(nodeId)
      if (seq !== requestSeq) return
      requirementLinked.value = requirements.length > 0
    } catch {
      // 查询失败按未关联处理，避免阻塞入口；实际关联状态由选取器操作反馈
      if (seq !== requestSeq) return
      requirementLinked.value = false
    } finally {
      if (seq === requestSeq) requirementLoading.value = false
    }
  }

  onBeforeUnmount(() => {
    close()
  })

  return {
    visible,
    anchor,
    targetNodeId,
    requirementLinked,
    requirementLoading,
    open,
    close,
  }
}
