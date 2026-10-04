import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchTraceGaps } from '@/services/project'
import { useTraceStore } from '@/stores/trace'
import type { TraceGap, TraceGapType } from '@/types'
import {
  TRACE_GAP_ROUTE,
  traceGapActionMeta,
  traceGapTypeMeta,
  type TraceGapActionMeta,
} from '@/composables/project/trace/tracePresentation'

export interface TraceGapView extends TraceGap {
  /** 引导动作的可执行性（generate 随批次二置灰） */
  actionMeta: TraceGapActionMeta
}

function gapView(item: TraceGap): TraceGapView {
  return { ...item, actionMeta: traceGapActionMeta(item.suggestedAction) }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 缺口清单（交互 04 §2.4）：按缺口类型分组切换（后端 type 必填单查），
 * 引导动作按可执行性分流——评审 / 计划接既有创建入口，生成随批次二置灰。
 */
export function useTraceGaps() {
  const router = useRouter()
  const traceStore = useTraceStore()

  const gaps = ref<TraceGapView[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const loadError = ref('')

  const gapType = computed<TraceGapType>(() => traceStore.gapType)
  const typeOptions = computed(() =>
    (['uncovered_requirement', 'orphan_case', 'unreviewed_case', 'unscheduled_case'] as TraceGapType[]).map(
      (type) => ({ value: type, label: traceGapTypeMeta(type).label, desc: traceGapTypeMeta(type).desc }),
    ),
  )

  let requestId = 0

  async function load() {
    const id = ++requestId
    loading.value = true
    loadError.value = ''
    try {
      const page = await fetchTraceGaps({
        type: gapType.value,
        pageNo: pageNo.value,
        pageSize: pageSize.value,
      })
      if (id !== requestId) return
      gaps.value = page.list.map(gapView)
      total.value = page.total
    } catch (err) {
      if (id !== requestId) return
      gaps.value = []
      loadError.value = errorMessage(err, '加载缺口清单失败')
    } finally {
      if (id === requestId) loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  function setType(type: TraceGapType): void {
    traceStore.setGapType(type)
    pageNo.value = 1
    void load()
  }

  function changePage(next: number): void {
    pageNo.value = next
    void load()
  }

  function changePageSize(next: number): void {
    pageSize.value = next
    pageNo.value = 1
    void load()
  }

  /** 行点击进入链路抽屉定位该条目（需求或用例为起点） */
  function openChain(gap: TraceGapView): void {
    const type = gap.targetType === 'requirement' ? 'requirement' : 'test_case'
    traceStore.openChain({ type, id: gap.targetId, title: gap.title })
  }

  function runAction(gap: TraceGapView): void {
    const meta = traceGapActionMeta(gap.suggestedAction)
    if (meta.disabled) {
      ElMessage.info(meta.disabledHint)
      return
    }
    const route = TRACE_GAP_ROUTE[gap.suggestedAction as 'review' | 'schedule']
    if (route) void router.push(route)
  }

  watch(gapType, () => {
    pageNo.value = 1
    void load()
  })

  return {
    gaps,
    total,
    pageNo,
    pageSize,
    loading,
    loadError,
    gapType,
    typeOptions,
    load,
    retry,
    setType,
    changePage,
    changePageSize,
    openChain,
    runAction,
  }
}
