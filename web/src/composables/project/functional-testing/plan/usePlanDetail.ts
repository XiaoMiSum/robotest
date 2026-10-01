import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  blockPlan,
  completePlan,
  getPlanDetail,
  getPlanModuleTree,
  getPlanPlannedCases,
  getPlanProgress,
  resumePlan,
  syncPlan,
  updatePlanCases,
} from '@/services/project'
import type {
  PlannedCases,
  SnapshotModule,
  TestPlanDetail,
  TestPlanProgress,
} from '@/types'

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

// ==================== Types ====================

export interface UsePlanDetailOptions {
  planId: string
}

// ==================== Composable ====================

// 以 expose 契约替代组件类型导入，避免组合式函数反向依赖 components
interface PlanMindMapRef {
  openBug: (bugId: string) => void
  reload: () => Promise<void>
}

export function usePlanDetail(options: UsePlanDetailOptions) {
  const { planId } = options

  const loading = ref(false)
  const detail = ref<TestPlanDetail | null>(null)
  const progress = ref<TestPlanProgress | null>(null)
  const mindMapRef = ref<PlanMindMapRef>()
  const moduleTree = ref<SnapshotModule[]>([])
  const selectedDocId = ref('')

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

  async function handleBlock() {
    try {
      await ElMessageBox.confirm(
        '确定阻塞该计划吗？阻塞期间不可标记执行结果、调整用例、同步与完成。',
        '阻塞计划',
        { type: 'warning' },
      )
    } catch {
      return
    }
    try {
      await blockPlan(planId)
      ElMessage.success('计划已阻塞')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '操作失败')
    }
  }

  async function handleResume() {
    try {
      await ElMessageBox.confirm('确定恢复该计划吗？恢复后回到执行中。', '恢复计划', {
        type: 'warning',
      })
    } catch {
      return
    }
    try {
      await resumePlan(planId)
      ElMessage.success('计划已恢复')
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
    selectorVisible,
    plannedCases,
    // Computed
    canAdjustCases,
    // Methods
    load,
    handleComplete,
    handleBlock,
    handleResume,
    handleSync,
    openCaseSelector,
    handleCasesConfirm,
    handleCasesRemoved,
    refreshProgress,
  }
}