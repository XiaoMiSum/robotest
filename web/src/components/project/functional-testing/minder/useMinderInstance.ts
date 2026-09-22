import { ref, shallowRef } from 'vue'

/**
 * 脑图实例骨架（三模式组件共用）：容器/加载态/实例引用、竞态令牌、
 * 实例销毁与选中状态维护。initMinder 流程由各模式组件自行编排，
 * 因为取数来源、实例创建方式、事件接线均因模式而异。
 */
export interface UseMinderInstanceOptions {
  /** 选中状态刷新回调，模式组件借此同步各自的扩展字段（优先级/标记结果等） */
  onSelectionChange?: (data: Record<string, unknown> | null) => void
}

export function useMinderInstance(options: UseMinderInstanceOptions = {}) {
  const containerRef = ref<HTMLDivElement>()
  const loading = ref(false)
  // shallowRef：kity 实例重度依赖原型链与 this 身份，深度 reactive 代理会破坏内部调用
  const minder = shallowRef<unknown>(null)

  const selectedNodeId = ref('')
  const selectedType = ref('')

  // 竞态令牌：快速切换文档/页面时丢弃过期的异步初始化结果
  let initToken = 0

  /** 每轮初始化开头调用，取得本轮令牌 */
  function beginInit(): number {
    return ++initToken
  }

  /** 异步等待后校验：组件已卸载或已切换目标时丢弃过期结果 */
  function isStale(token: number): boolean {
    return token !== initToken
  }

  /** 卸载时递增令牌，令进行中的初始化全部过期 */
  function invalidate(): void {
    initToken++
  }

  function getMinder(): Record<string, (...args: unknown[]) => unknown> | null {
    return minder.value as Record<string, (...args: unknown[]) => unknown> | null
  }

  function getSelectedNodeData(): Record<string, unknown> | null {
    const m = getMinder()
    if (!m) return null
    // 必须以方法调用保留 this，kityminder 内部依赖 this.getSelectedNodes
    const node = m.getSelectedNode?.() as Record<string, unknown> | null | undefined
    return node ? ((node.data ?? {}) as Record<string, unknown>) : null
  }

  function updateSelectedState(): void {
    const data = getSelectedNodeData()
    selectedNodeId.value = data ? (data.id as string) || '' : ''
    selectedType.value = data ? (data.type as string) || '' : ''
    options.onSelectionChange?.(data)
  }

  function destroyMinder(destroyInstance?: () => void): void {
    const m = getMinder()
    minder.value = null
    // 画布 DOM 可能已被 Vue 卸载，destroy 内部的选区/布局清理会抛 parentNode 错误，
    // 必须吞掉，否则未捕获异常会阻断路由导航
    try {
      if (destroyInstance) destroyInstance()
      else m?.destroy?.()
    } catch {
      /* 画布已脱离 DOM，忽略清理异常 */
    }
    if (containerRef.value) containerRef.value.innerHTML = ''
  }

  return {
    containerRef,
    loading,
    minder,
    selectedNodeId,
    selectedType,
    beginInit,
    isStale,
    invalidate,
    getMinder,
    getSelectedNodeData,
    updateSelectedState,
    destroyMinder,
  }
}
