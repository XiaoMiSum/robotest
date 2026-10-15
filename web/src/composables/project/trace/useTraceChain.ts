import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createTraceEdge, fetchTraceChain, patchTraceEdge } from '@/services/project'
import { useAuthStore } from '@/stores/auth'
import type { TraceChain, TraceEdgeCreatePayload, TraceEdgePatchPayload, TraceNodeType } from '@/types'

/** 链路起点：由链路明细页从路由参数解析后传入，不入 store */
export interface TraceChainSource {
  type: TraceNodeType
  id: string
  /** 矩阵单元格点击进入时的定位类型，链路内对该类型节点高亮 */
  focusType?: TraceNodeType
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 链路明细逻辑（交互 04 §2.2）：起点由路由参数驱动，
 * 每次写操作成功后 revision 自增，页面据此联动刷新覆盖与影响数据。
 */
export function useTraceChain(source: () => TraceChainSource | null) {
  const authStore = useAuthStore()

  const chain = ref<TraceChain | null>(null)
  const loading = ref(false)
  const loadError = ref('')
  const busy = ref(false)
  /** 数据写操作成功计数，监听方据此刷新覆盖与影响数据 */
  const revision = ref(0)

  async function load() {
    const current = source()
    if (!current) return
    loading.value = true
    loadError.value = ''
    try {
      chain.value = await fetchTraceChain({
        sourceType: current.type,
        sourceId: current.id,
        direction: 'down',
      })
    } catch (err) {
      chain.value = null
      loadError.value = errorMessage(err, '加载链路失败')
    } finally {
      loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  async function runPatch(
    edgeId: string,
    payload: TraceEdgePatchPayload,
    successText: string,
  ): Promise<boolean> {
    busy.value = true
    try {
      await patchTraceEdge(edgeId, payload)
      ElMessage.success(successText)
      revision.value += 1
      await load()
      return true
    } catch (err) {
      ElMessage.error(errorMessage(err, '边修正失败'))
      return false
    } finally {
      busy.value = false
    }
  }

  function confirmEdge(edgeId: string): Promise<boolean> {
    return runPatch(edgeId, { action: 'confirm' }, '边已确认')
  }

  function reattachEdge(edgeId: string, target: { type: string; id: string }): Promise<boolean> {
    return runPatch(
      edgeId,
      { action: 'reattach', targetType: target.type as TraceEdgePatchPayload['targetType'], targetId: target.id },
      '边已改挂',
    )
  }

  function detachEdge(edgeId: string, reason: string): Promise<boolean> {
    return runPatch(edgeId, { action: 'detach', reason }, '边已断开')
  }

  function restoreEdge(edgeId: string): Promise<boolean> {
    return runPatch(edgeId, { action: 'restore' }, '边已恢复')
  }

  async function addEdge(payload: TraceEdgeCreatePayload): Promise<boolean> {
    busy.value = true
    try {
      await createTraceEdge(payload)
      ElMessage.success('边已建立')
      revision.value += 1
      await load()
      return true
    } catch (err) {
      ElMessage.error(errorMessage(err, '建边失败'))
      return false
    } finally {
      busy.value = false
    }
  }

  const canEdit = computed(() => authStore.hasPermission('trace:edit'))

  // 同页切换起点（点击下游节点）只变路由参数、组件复用，须按键值重新取链
  watch(
    () => {
      const current = source()
      return current ? `${current.type}:${current.id}` : ''
    },
    (key) => {
      if (key) void load()
    },
  )

  onMounted(() => {
    if (source()) void load()
  })

  return {
    chain,
    loading,
    loadError,
    busy,
    revision,
    canEdit,
    load,
    retry,
    confirmEdge,
    reattachEdge,
    detachEdge,
    restoreEdge,
    addEdge,
  }
}
