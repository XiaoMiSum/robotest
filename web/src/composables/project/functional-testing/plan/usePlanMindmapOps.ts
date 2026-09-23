import type { Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPlanSnapshotTree, getPlanPlannedCases, submitExecutionRecord, updatePlanCases } from '@/services/project'
import type { ExecutionResult, PlannedCases } from '@/types'
import { planNodeToKm } from '@/minder/adapter'
import { loadMinderEngine } from '@/minder/loader'
import type { useMinderInstance } from '@/minder/useMinderInstance'

export function usePlanMindmapOps({
  containerRef,
  loading,
  minder,
  selectedNodeId,
  selectedType,
  beginInit,
  isStale,
  getMinder,
  getSelectedNodeData,
  updateSelectedState,
  destroyMinder,
  execResult,
  getPlanId,
  getDocumentId,
  getRemovable,
  applyOrderBadges,
  onMarked,
  onRemoved,
}: Pick<
  ReturnType<typeof useMinderInstance>,
  | 'containerRef'
  | 'loading'
  | 'minder'
  | 'selectedNodeId'
  | 'selectedType'
  | 'beginInit'
  | 'isStale'
  | 'getMinder'
  | 'getSelectedNodeData'
  | 'updateSelectedState'
  | 'destroyMinder'
> & {
  execResult: Ref<string | null>
  getPlanId: () => string
  getDocumentId: () => string | undefined
  getRemovable: () => boolean | undefined
  applyOrderBadges: () => void
  onMarked: () => void
  onRemoved: () => void
}) {
  // ==================== 初始化 ====================
  async function initMinder() {
    if (!containerRef.value || !getPlanId()) return
    const token = beginInit()
    loading.value = true
    destroyMinder()
    try {
      // documentId 限定单文档快照；不传时后端返回多文档多根，仅取首个，页面应始终传入
      const tree = await getPlanSnapshotTree(getPlanId(), getDocumentId() || undefined)
      const root = tree.length ? planNodeToKm(tree[0]) : { data: { text: '空快照' }, children: [] }
      const kmData = { root, template: 'default', theme: 'fresh-purple' }

      const km = await loadMinderEngine()
      // 异步等待期间组件可能已卸载或已切换目标，过期结果直接丢弃
      if (isStale(token) || !containerRef.value) return

      // 快照只读展示，裸 minder 即可，无需编辑内核
      const instance: unknown = new km.Minder({ renderTo: containerRef.value })
      minder.value = instance
      const m = instance as Record<string, (...args: unknown[]) => unknown>
      m.importJson(kmData)

      // 禁用画布编辑以防止用户修改快照原始数据
      m.disable?.()

      // 推荐序号徽标可能早于脑图初始化就绪，导入后统一回填一次
      applyOrderBadges()

      m.on('selectionchange', updateSelectedState)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载脑图失败')
    } finally {
      loading.value = false
    }
  }

  // ==================== 执行标记操作 ====================
  async function markExecution(result: ExecutionResult) {
    if (!getPlanId() || !selectedNodeId.value) return
    if (selectedType.value !== 'case') {
      ElMessage.warning('仅关联用例节点可标记执行结果')
      return
    }
    try {
      await submitExecutionRecord(getPlanId(), { snapshotNodeId: selectedNodeId.value, result })
      execResult.value = result
      const data = getSelectedNodeData()
      if (data) { data.lastResult = result; data.executionStatus = { result } }
      getMinder()?.refresh?.()
      const labels: Record<string, string> = { pass: '通过', fail: '失败', block: '阻塞', untested: '待执行' }
      ElMessage.success(`已标记${labels[result]}`)
      onMarked()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '提交执行结果失败')
    }
  }

  // 移除选中用例：仅已关联 case 节点可移除，剔除后走全量覆盖接口（后端按新列表重刷快照关联）
  async function removeSelectedCase() {
    if (!getRemovable()) return
    if (selectedType.value !== 'case') {
      ElMessage.warning('仅关联用例节点可移除')
      return
    }
    const data = getSelectedNodeData()
    const originalNodeId = data?.originalNodeId as string | undefined
    // 快照含文档全部节点，仅关联节点才在规划列表中，未关联的 case 无关联可删
    if (!originalNodeId || data?.isAssociated !== true) {
      ElMessage.warning('该用例未关联，无需移除')
      return
    }
    try {
      await ElMessageBox.confirm(
        `确定从该计划中移除用例「${data.text as string}」吗？移除后该用例及其执行结果将不再展示，历史执行记录保留作审计。`,
        '移除用例',
        { type: 'warning' },
      )
    } catch { return }
    try {
      const planned = await getPlanPlannedCases(getPlanId())
      const next = planned
        .map((doc) => ({
          documentId: doc.documentId,
          // 剔除目标用例；该文档剩余用例为空时整文档移除（后端删文档快照并清理空目录）
          caseIds: doc.caseIds.filter((id) => id !== originalNodeId),
        }))
        .filter((doc) => doc.caseIds.length > 0) as PlannedCases[]
      await updatePlanCases(getPlanId(), next)
      ElMessage.success('已移除用例')
      onRemoved()
      await initMinder()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '移除用例失败')
    }
  }

  return {
    initMinder,
    markExecution,
    removeSelectedCase,
  }
}
