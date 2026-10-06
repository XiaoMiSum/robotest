import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchProjectModuleTree, fetchTraceGaps, getRequirement } from '@/services/project'
import { useTraceStore } from '@/stores/trace'
import type { AiGenerationScopeItem, ProjectModule, TraceGap, TraceGapType } from '@/types'
import {
  TRACE_GAP_ROUTE,
  traceGapActionMeta,
  traceGapTypeMeta,
  type TraceGapActionMeta,
} from '@/composables/project/trace/tracePresentation'

export interface TraceGapView extends TraceGap {
  /** 引导动作的可执行性（未知动作兜底置灰） */
  actionMeta: TraceGapActionMeta
}

function gapView(item: TraceGap): TraceGapView {
  return { ...item, actionMeta: traceGapActionMeta(item.suggestedAction) }
}

/** 需求挂在模块目录上，落位选择树只留目录（文档不承载需求） */
function stripDocuments(nodes: ProjectModule[]): ProjectModule[] {
  return nodes
    .filter((node) => node.type === 'directory')
    .map((node) => ({ ...node, children: stripDocuments(node.children) }))
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 缺口清单（交互 04 §2.4）：按缺口类型分组切换（后端 type 必填单查），
 * 引导动作按类型分流——评审 / 计划接既有入口跳转，生成就地打开生成配置对话框。
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
    if (gap.suggestedAction === 'generate') {
      void openGeneration(gap)
      return
    }
    const route = TRACE_GAP_ROUTE[gap.suggestedAction as 'review' | 'schedule']
    if (route) void router.push(route)
  }

  // ==================== 缺口发起生成（交互 04 §2.4） ====================
  const generationDialogVisible = ref(false)
  const generationScope = ref<AiGenerationScopeItem[]>([])
  const generationModuleTree = ref<ProjectModule[]>([])

  /** 以缺口需求为范围打开生成配置对话框；详情取 code / status 供范围回显与置灰 */
  async function openGeneration(gap: TraceGapView): Promise<void> {
    try {
      const detail = await getRequirement(gap.targetId)
      generationScope.value = [
        { id: detail.id, code: detail.code, title: detail.title, status: detail.status },
      ]
      if (generationModuleTree.value.length === 0) {
        generationModuleTree.value = stripDocuments(await fetchProjectModuleTree())
      }
      generationDialogVisible.value = true
    } catch (err) {
      ElMessage.error(errorMessage(err, '读取需求失败，无法发起生成'))
    }
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
    generationDialogVisible,
    generationScope,
    generationModuleTree,
  }
}
