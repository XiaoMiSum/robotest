import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchPlanOrderRecommend, planOrderReason, planOrderRecommend } from '@/services/ai'
import { getPlanSnapshotTree } from '@/services/project'
import type { AiPlanOrderQueryResp, AiPlanOrderRecommendItem } from '@/types'
import {
  collectPlanCaseMeta,
  type PlanCaseMeta,
} from '@/components/project/functional-testing/plan/planOrderRecommend'

export function usePlanOrderRecommend({
  getPlanId,
  onResult,
  getListRef,
  onLocate,
}: {
  getPlanId: () => string
  onResult: (items: AiPlanOrderRecommendItem[]) => void
  getListRef: () => HTMLElement | undefined
  onLocate: (snapshotNodeId: string) => void
}) {
  const query = ref<AiPlanOrderQueryResp | null>(null)
  const computing = ref(false)
  const loaded = ref(false)
  const expanded = ref<Set<string>>(new Set())
  const reasoning = ref<Set<string>>(new Set())
  const caseMeta = ref<Map<string, PlanCaseMeta>>(new Map())

  let controller: AbortController | null = null

  const result = computed(() => query.value?.result ?? null)
  const items = computed(() => result.value?.items ?? [])
  const stale = computed(() => query.value?.stale ?? false)
  const hasResult = computed(() => items.value.length > 0)

  // 优先级渲染仅取标签色，与脑图 priorityBadge 配色一致（P0 红 / P1 橙 / P2 蓝 / P3 灰）
  const PRIORITY_TAG_TYPE: Record<string, 'danger' | 'warning' | 'primary' | 'info'> = {
    P0: 'danger',
    P1: 'warning',
    P2: 'primary',
    P3: 'info',
  }

  function metaOf(snapshotNodeId: string): PlanCaseMeta | undefined {
    return caseMeta.value.get(snapshotNodeId)
  }

  function priorityTagType(priority: string | null | undefined): 'danger' | 'warning' | 'primary' | 'info' {
    return (priority && PRIORITY_TAG_TYPE[priority]) || 'info'
  }

  async function load(): Promise<void> {
    try {
      // 快照树跨全部文档取 case 标题/优先级；推荐结果覆盖全计划，不受当前选中文档影响
      const [resp, tree] = await Promise.all([
        fetchPlanOrderRecommend(getPlanId()),
        getPlanSnapshotTree(getPlanId()),
      ])
      query.value = resp
      caseMeta.value = collectPlanCaseMeta(tree)
      onResult(resp.result?.items ?? [])
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载推荐结果失败')
    } finally {
      loaded.value = true
    }
  }

  async function compute(): Promise<void> {
    if (computing.value) return
    computing.value = true
    const { controller: c, promise } = planOrderRecommend(getPlanId())
    controller = c
    try {
      const resp = await promise
      query.value = { stale: false, result: resp.result }
      onResult(resp.result.items)
    } catch (err) {
      // 用户主动取消不提示（同步调用无部分结果）
      if (controller?.signal.aborted) return
      ElMessage.error(err instanceof Error ? err.message : '计算执行顺序失败')
    } finally {
      computing.value = false
      controller = null
    }
  }

  function cancelCompute(): void {
    controller?.abort()
    controller = null
    computing.value = false
  }

  function toggleExpand(snapshotNodeId: string): void {
    const next = new Set(expanded.value)
    if (next.has(snapshotNodeId)) next.delete(snapshotNodeId)
    else next.add(snapshotNodeId)
    expanded.value = next
  }

  async function generateReason(item: AiPlanOrderRecommendItem): Promise<void> {
    // 已生成（后端缓存回填）或生成中则跳过，避免重复请求
    if (item.reason || reasoning.value.has(item.snapshotNodeId)) return
    reasoning.value.add(item.snapshotNodeId)
    try {
      const resp = await planOrderReason(getPlanId(), item.snapshotNodeId).promise
      item.reason = resp.reason
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '生成理由失败')
    } finally {
      reasoning.value.delete(item.snapshotNodeId)
    }
  }

  function rowClick(snapshotNodeId: string): void {
    onLocate(snapshotNodeId)
  }

  // 脑图 #序号 徽标反向滚动列表至对应行（双向联动），由父组件切换标签页后调用
  function scrollToOrder(order: number): void {
    getListRef()?.querySelector(`[data-order="${order}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  onMounted(load)
  onBeforeUnmount(() => controller?.abort())

  return {
    query,
    computing,
    loaded,
    expanded,
    reasoning,
    caseMeta,
    result,
    items,
    stale,
    hasResult,
    PRIORITY_TAG_TYPE,
    metaOf,
    priorityTagType,
    load,
    compute,
    cancelCompute,
    toggleExpand,
    generateReason,
    rowClick,
    scrollToOrder,
  }
}
