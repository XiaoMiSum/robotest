import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  completePlan,
  getCaseDetail,
  getPlanDetail,
  getPlanModuleTree,
  getPlanPlannedCases,
  getPlanProgress,
  syncPlan,
  updatePlanCases,
} from '@/services/project'
import type {
  AiPlanOrderRecommendItem,
  PlannedCases,
  SnapshotModule,
  TestPlanDetail,
  TestPlanProgress,
} from '@/types'
import PlanMindMap from '@/components/project/functional-testing/plan/PlanMindMap.vue'
import PlanOrderRecommend from '@/components/project/functional-testing/plan/PlanOrderRecommend.vue'
import { useAuthStore } from '@/stores/auth'
import { useAiStore } from '@/stores/ai'

// ==================== Helpers ====================

function firstDocument(nodes: SnapshotModule[]): SnapshotModule | null {
  for (const node of nodes) {
    if (node.type === 'document') return node
    const found = firstDocument(node.children ?? [])
    if (found) return found
  }
  return null
}

function findDoc(nodes: SnapshotModule[], id: string): boolean {
  return nodes.some((n) => n.id === id || findDoc(n.children ?? [], id))
}

// ==================== Constants ====================

const statusLabel: Record<string, string> = {
  new: '待开始',
  in_progress: '进行中',
  completed: '已完成',
  closed: '已关闭',
}

// ==================== Types ====================

export interface UsePlanDetailOptions {
  planId: string
}

// ==================== Composable ====================

export function usePlanDetail(options: UsePlanDetailOptions) {
  const { planId } = options
  const router = useRouter()

  const authStore = useAuthStore()
  const aiStore = useAiStore()

  const loading = ref(false)
  const detail = ref<TestPlanDetail | null>(null)
  const progress = ref<TestPlanProgress | null>(null)
  const mindMapRef = ref<InstanceType<typeof PlanMindMap>>()
  const moduleTree = ref<SnapshotModule[]>([])
  const selectedDocId = ref('')

  const activeTab = ref<'records' | 'order'>('records')
  const orderPanelRef = ref<InstanceType<typeof PlanOrderRecommend>>()

  const canShowOrder = computed(
    () => aiStore.aiEnabled && detail.value?.executor?.id === authStore.user?.id,
  )

  const canAdjustCases = computed(
    () => detail.value?.status === 'new' || detail.value?.status === 'in_progress',
  )

  // ==================== Load ====================

  async function load() {
    loading.value = true
    try {
      const [d, p, tree] = await Promise.all([
        getPlanDetail(planId),
        getPlanProgress(planId),
        getPlanModuleTree(planId),
      ])
      detail.value = d
      progress.value = p
      moduleTree.value = tree
      if (!selectedDocId.value || !findDoc(tree, selectedDocId.value)) {
        selectedDocId.value = firstDocument(tree)?.id ?? ''
      }
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载计划详情失败')
    } finally {
      loading.value = false
    }
  }

  // ==================== Actions ====================

  async function handleComplete() {
    try {
      await ElMessageBox.confirm('确定完成该计划吗？完成后将不可再标记执行结果。', '完成执行', {
        type: 'warning',
      })
    } catch {
      return
    }
    try {
      await completePlan(planId)
      ElMessage.success('计划已完成')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '操作失败')
    }
  }

  async function handleSync() {
    try {
      await ElMessageBox.confirm(
        '同步将更新快照节点属性，已有执行记录不受影响。确定同步？',
        '同步最新用例',
        { type: 'info' },
      )
    } catch {
      return
    }
    try {
      await syncPlan(planId)
      ElMessage.success('已同步')
      load()
      mindMapRef.value?.reload()
      orderPanelRef.value?.load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '同步失败')
    }
  }

  // ==================== Case Selector ====================

  const selectorVisible = ref(false)
  const plannedCases = ref<PlannedCases[]>([])

  async function openCaseSelector() {
    try {
      plannedCases.value = await getPlanPlannedCases(planId)
      selectorVisible.value = true
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载规划用例失败')
    }
  }

  async function handleCasesConfirm(selectedNodes: PlannedCases[]) {
    try {
      await updatePlanCases(planId, selectedNodes)
      ElMessage.success('规划用例已更新')
      await load()
      mindMapRef.value?.reload()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '更新规划用例失败')
    }
  }

  async function handleCasesRemoved() {
    await load()
    orderPanelRef.value?.load()
  }

  // ==================== AI Recommend ====================

  const recommendVisible = ref(false)
  const recommendExcludeIds = ref<string[]>([])

  async function openRecommend() {
    try {
      const existing = await getPlanPlannedCases(planId)
      recommendExcludeIds.value = existing.flatMap((s) => s.caseIds)
      recommendVisible.value = true
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载规划用例失败')
    }
  }

  async function handleBringIn(caseNodeIds: string[]) {
    try {
      const [existing, details] = await Promise.all([
        getPlanPlannedCases(planId),
        Promise.all(caseNodeIds.map((id) => getCaseDetail(id))),
      ])
      const merged = new Map<string, Set<string>>()
      existing.forEach((s) => merged.set(s.documentId, new Set(s.caseIds)))
      details.forEach((d) => {
        if (!d.documentId) return
        const set = merged.get(d.documentId) ?? new Set<string>()
        set.add(d.id)
        merged.set(d.documentId, set)
      })
      plannedCases.value = [...merged.entries()].map(([documentId, caseIds]) => ({
        documentId,
        caseIds: [...caseIds],
      }))
      selectorVisible.value = true
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载推荐用例失败')
    }
  }

  // ==================== Progress ====================

  async function refreshProgress() {
    try {
      const [p, d] = await Promise.all([getPlanProgress(planId), getPlanDetail(planId)])
      progress.value = p
      detail.value = d
    } catch {
      // 标记本身已成功，进度刷新失败不打断操作
    }
  }

  // ==================== Order Recommend ====================

  async function handleOrderLocate(snapshotNodeId: string) {
    activeTab.value = 'records'
    await nextTick()
    const located = mindMapRef.value?.locateNode(snapshotNodeId)
    if (!located) ElMessage.info('该建议指向的用例不在当前文档，请切换左侧文档后重试')
  }

  function handleOrderResult(items: AiPlanOrderRecommendItem[]) {
    mindMapRef.value?.setOrderBadges(items)
  }

  async function handleOrderSelect(order: number) {
    activeTab.value = 'order'
    await nextTick()
    orderPanelRef.value?.scrollToOrder(order)
  }

  // ==================== Lifecycle ====================

  onMounted(load)

  return {
    // State
    loading,
    detail,
    progress,
    mindMapRef,
    moduleTree,
    selectedDocId,
    activeTab,
    orderPanelRef,
    selectorVisible,
    plannedCases,
    recommendVisible,
    recommendExcludeIds,
    // Computed
    canShowOrder,
    canAdjustCases,
    // Methods
    load,
    handleComplete,
    handleSync,
    openCaseSelector,
    handleCasesConfirm,
    handleCasesRemoved,
    openRecommend,
    handleBringIn,
    refreshProgress,
    handleOrderLocate,
    handleOrderResult,
    handleOrderSelect,
    // Constants
    statusLabel,
    // Router
    router,
    // Stores
    aiStore,
  }
}