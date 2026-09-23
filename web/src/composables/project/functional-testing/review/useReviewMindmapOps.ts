import type { Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getReviewSnapshotTree,
  getReviewPlannedCases,
  submitReviewRecord,
  getNodeReviewRecords,
  updateReviewCases,
} from '@/services/project'
import type { PlannedCases, ReviewMark, ReviewRecord } from '@/types'
import { reviewNodeToKm } from '@/minder/adapter'
import { loadMinderEngine } from '@/minder/loader'
import type { useMinderInstance } from '@/minder/useMinderInstance'

export function useReviewMindmapOps({
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
  reviewResult,
  getReviewId,
  getDocumentId,
  getRemovable,
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
  reviewResult: Ref<string | null>
  getReviewId: () => string
  getDocumentId: () => string | undefined
  getRemovable: () => boolean | undefined
  onMarked: () => void
  onRemoved: () => void
}) {
  // 评论抽屉
  const commentVisible = ref(false)
  const comments = ref<ReviewRecord[]>([])
  const newComment = ref('')

  // ==================== 初始化 ====================
  async function initMinder() {
    if (!containerRef.value || !getReviewId()) return
    const token = beginInit()
    loading.value = true
    destroyMinder()
    try {
      // documentId 限定单文档快照；不传时后端返回多文档多根，仅取首个，页面应始终传入
      const tree = await getReviewSnapshotTree(getReviewId(), getDocumentId() || undefined)
      const root = tree.length ? reviewNodeToKm(tree[0]) : { data: { text: '空快照' }, children: [] }
      const kmData = { root, template: 'default', theme: 'fresh-green' }

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

      m.on('selectionchange', updateSelectedState)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载脑图失败')
    } finally {
      loading.value = false
    }
  }

  // ==================== 评审操作 ====================
  async function markReview(mark: ReviewMark | null) {
    if (!getReviewId() || !selectedNodeId.value) return
    // 仅 case 节点可标记
    if (selectedType.value !== 'case' && mark !== null) {
      ElMessage.warning('仅用例节点可标记评审结果')
      return
    }
    try {
      await submitReviewRecord(getReviewId(), {
        snapshotNodeId: selectedNodeId.value,
        operationType: 'mark',
        // 后端以显式 pending 表示重置回待评审（落库 last_mark = null）
        mark: mark ?? 'pending',
      })
      reviewResult.value = mark
      const data = getSelectedNodeData()
      if (data) { data.lastMark = mark; data.reviewStatus = mark ? { result: mark } : null }
      getMinder()?.refresh?.()
      ElMessage.success(mark ? `已标记${mark === 'pass' ? '通过' : '不通过'}` : '已重置为待评审')
      onMarked()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '提交标记失败')
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
        `确定从该评审中移除用例「${data.text as string}」吗？移除后该用例及其评审标记将不再展示，历史标记记录保留作审计。`,
        '移除用例',
        { type: 'warning' },
      )
    } catch { return }
    try {
      const planned = await getReviewPlannedCases(getReviewId())
      const next = planned
        .map((doc) => ({
          documentId: doc.documentId,
          // 剔除目标用例；该文档剩余用例为空时整文档移除（后端删文档快照并清理空目录）
          caseIds: doc.caseIds.filter((id) => id !== originalNodeId),
        }))
        .filter((doc) => doc.caseIds.length > 0) as PlannedCases[]
      await updateReviewCases(getReviewId(), next)
      ElMessage.success('已移除用例')
      onRemoved()
      await initMinder()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '移除用例失败')
    }
  }

  async function openComments() {
    if (!getReviewId() || !selectedNodeId.value) {
      ElMessage.warning('请先选中一个节点')
      return
    }
    commentVisible.value = true
    try {
      comments.value = await getNodeReviewRecords(getReviewId(), selectedNodeId.value)
    } catch {
      comments.value = []
    }
  }

  async function addCommentFn() {
    if (!newComment.value.trim() || !getReviewId() || !selectedNodeId.value) return
    try {
      await submitReviewRecord(getReviewId(), {
        snapshotNodeId: selectedNodeId.value,
        operationType: 'comment',
        comment: newComment.value.trim(),
      })
      ElMessage.success('评论已发送')
      newComment.value = ''
      comments.value = await getNodeReviewRecords(getReviewId(), selectedNodeId.value)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '发送评论失败')
    }
  }

  return {
    commentVisible,
    comments,
    newComment,
    initMinder,
    markReview,
    removeSelectedCase,
    openComments,
    addCommentFn,
  }
}
