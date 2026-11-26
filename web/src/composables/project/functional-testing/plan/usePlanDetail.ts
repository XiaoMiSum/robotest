import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  blockPlan,
  completePlan,
  getPlanDetail,
  getPlanModuleTree,
  getPlanPlannedCases,
  getPlanProgress,
  getPlanSnapshotTree,
  resumePlan,
  syncPlan,
  updatePlanCases,
} from '@/services/project'
import type {
  ExecutionResult,
  PlannedCases,
  SnapshotModule,
  TestPlanDetail,
  TestPlanProgress,
  TestPlanSnapshotNode,
} from '@/types'
import { buildPlanExecutionOrder, planExecutionOrderMap, type PlanExecutionCase } from '@/minder/adapter'

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

function allDocuments(nodes: SnapshotModule[]): string[] {
  const ids: string[] = []
  for (const node of nodes) {
    if (node.type === 'document') ids.push(node.id)
    ids.push(...allDocuments(node.children ?? []))
  }
  return ids
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
  focusCase: (snapshotNodeId: string) => void
}

export function usePlanDetail(options: UsePlanDetailOptions) {
  const { planId } = options

  const loading = ref(false)
  const detail = ref<TestPlanDetail | null>(null)
  const progress = ref<TestPlanProgress | null>(null)
  const mindMapRef = ref<PlanMindMapRef>()
  const moduleTree = ref<SnapshotModule[]>([])
  const selectedDocId = ref('')

  // 快照节点 id 到快照文档 id 的映射：规划用例接口返回的是原始文档 id，不能用于快照查询
  const docIdByCase = new Map<string, string>()
  const loadedDocIds = new Set<string>()

  const canAdjustCases = computed(
    () => detail.value?.status === 'new' || detail.value?.status === 'in_progress',
  )

  // 与后端状态机一致：活跃态允许执行与阻塞，已阻塞仅允许恢复（详设 3.2）
  const canOperateActive = computed(() => canAdjustCases.value)
  const canBlock = computed(() => canAdjustCases.value)
  const canResume = computed(() => detail.value?.status === 'blocked')

  // ==================== 执行顺序引导 ====================

  const executionCases = ref<PlanExecutionCase[]>([])
  const executionOrder = computed(() => planExecutionOrderMap(executionCases.value))
  const nextPendingCase = computed(() =>
    executionCases.value.find((item) => item.lastResult == null || item.lastResult === 'untested') ?? null,
  )

  async function loadExecutionOrder(): Promise<void> {
    // 调整或同步用例后文档集合会变，映射缓存随全量快照一起重建
    docIdByCase.clear()
    loadedDocIds.clear()
    try {
      executionCases.value = buildPlanExecutionOrder(await getPlanSnapshotTree(planId))
    } catch {
      // 引导依赖额外读取，失败时保留已有序号，不阻断详情页加载
    }
  }

  async function ensureCaseDocument(snapshotNodeId: string): Promise<string | undefined> {
    for (const docId of allDocuments(moduleTree.value)) {
      if (loadedDocIds.has(docId)) continue
      try {
        const tree = await getPlanSnapshotTree(planId, docId)
        const collect = (nodes: TestPlanSnapshotNode[]): void => {
          for (const node of nodes) {
            docIdByCase.set(node.id, docId)
            collect(node.children ?? [])
          }
        }
        collect(tree)
        loadedDocIds.add(docId)
        const found = docIdByCase.get(snapshotNodeId)
        if (found) return found
      } catch {
        // 单个文档读取失败时继续找其余文档，避免一次失败让引导整体不可用
      }
    }
    return docIdByCase.get(snapshotNodeId)
  }

  async function focusNextCase(): Promise<void> {
    const next = nextPendingCase.value
    if (!next) return
    let docId = docIdByCase.get(next.snapshotNodeId)
    if (!docId) docId = await ensureCaseDocument(next.snapshotNodeId)
    if (docId && docId !== selectedDocId.value) {
      selectedDocId.value = docId
    }
    // 当前画布已有目标节点时直接定位；跨文档时由 PlanMindMap 暂存 id，重载后再定位
    mindMapRef.value?.focusCase(next.snapshotNodeId)
  }

  async function handleMarked(payload: { snapshotNodeId: string; result: ExecutionResult }): Promise<void> {
    const target = executionCases.value.find((item) => item.snapshotNodeId === payload.snapshotNodeId)
    if (target) target.lastResult = payload.result
    void refreshProgress()
    await focusNextCase()
  }

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
      await loadExecutionOrder()
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
    executionOrder,
    nextPendingCase,
    // Computed
    canAdjustCases,
    canOperateActive,
    canBlock,
    canResume,
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
    focusNextCase,
    handleMarked,
  }
}
