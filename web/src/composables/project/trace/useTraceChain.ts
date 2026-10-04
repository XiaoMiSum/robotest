import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createTraceEdge, fetchTraceChain, patchTraceEdge } from '@/services/project'
import { useAuthStore } from '@/stores/auth'
import { useTraceStore } from '@/stores/trace'
import type { TraceChain, TraceEdgeCreatePayload, TraceEdgePatchPayload } from '@/types'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 链路抽屉逻辑（交互 04 §2.2）：抽屉开关与起点由 trace store 承载（跨页保活），
 * 每次写操作成功后 revision 自增，页面据此重载矩阵计数。
 */
export function useTraceChain() {
  const authStore = useAuthStore()
  const traceStore = useTraceStore()

  const chain = ref<TraceChain | null>(null)
  const loading = ref(false)
  const loadError = ref('')
  const busy = ref(false)
  /** 数据写操作成功计数，监听方据此刷新矩阵 */
  const revision = ref(0)

  const visible = computed({
    get: () => traceStore.chainOrigin !== null,
    set: (open: boolean) => {
      if (!open) traceStore.closeChain()
    },
  })
  const origin = computed(() => traceStore.chainOrigin)

  async function load() {
    const current = traceStore.chainOrigin
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

  watch(
    () => traceStore.chainOrigin,
    (next) => {
      if (next) void load()
    },
  )

  onMounted(() => {
    // 跨页进入（需求详情「查看追溯」）时抽屉已由 store 恢复
    if (traceStore.chainOrigin) void load()
  })

  return {
    visible,
    origin,
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
