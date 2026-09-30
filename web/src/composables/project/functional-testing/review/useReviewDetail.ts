import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  completeReview,
  getCaseDetail,
  getReviewDetail,
  getReviewModuleTree,
  getReviewPlannedCases,
  getReviewProgress,
  rejectReview,
  reopenReview,
  syncReview,
  updateReviewCases,
} from '@/services/project'
import type { PlannedCases, SnapshotModule, TestReviewDetail, TestReviewProgress } from '@/types'
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

// ==================== Types ====================

export interface UseReviewDetailOptions {
  reviewId: string
}

// 以 expose 契约替代组件类型导入，避免组合式函数反向依赖 components
interface ReviewMindMapRef {
  openBug: (bugId: string) => void
  reload: () => Promise<void>
  locateNode: (snapshotNodeId: string) => boolean
}

interface ReviewAiCheckPanelRef {
  start: () => Promise<void>
}

// ==================== Composable ====================

export function useReviewDetail(options: UseReviewDetailOptions) {
  const { reviewId } = options
  const router = useRouter()

  const authStore = useAuthStore()
  const aiStore = useAiStore()

  const loading = ref(false)
  const detail = ref<TestReviewDetail | null>(null)
  const progress = ref<TestReviewProgress | null>(null)
  const mindMapRef = ref<ReviewMindMapRef>()
  const moduleTree = ref<SnapshotModule[]>([])
  const selectedDocId = ref('')

  // ==================== Computed ====================

  // 全部用例评审通过（无待评审、无不通过）才允许完成评审
  const canComplete = computed(
    () => !!progress.value && progress.value.pending === 0 && progress.value.failed === 0,
  )

  // AI 生成摘要入口：仅评审发起人 + AI 已启用 + 评审已完成（后端强校验兜底，交互设计第 3 章）
  const canShowSummary = computed(
    () =>
      aiStore.aiEnabled &&
      detail.value?.status === 'completed' &&
      detail.value?.initiator.id === authStore.user?.id,
  )

  // AI 评审结论：随完成事件自动生成，此处可手动/重新生成（06 §5.2）；入口条件与摘要一致
  const canShowConclusion = computed(() => canShowSummary.value)

  // AI 一键检查：仅评审发起人 + AI 启用可见；活跃态可发起，终态只读查看历史结果（交互设计 2.2）
  const canShowCheck = computed(
    () => aiStore.aiEnabled && detail.value?.initiator.id === authStore.user?.id,
  )

  // 终态（已通过/已驳回）不可再发起检查（后端 6012 兜底），面板仅以只读展示历史结果
  // 活跃态口径与 reviewListPresentation.isActiveReview 一致，此处内联避免 composable 反向依赖 components 层
  const canRunCheck = computed(() =>
    detail.value?.status === 'new' || detail.value?.status === 'in_progress',
  )

  // ==================== AI 面板状态 ====================

  const summaryVisible = ref(false)
  const conclusionVisible = ref(false)
  const checkVisible = ref(false)
  const checkPanelRef = ref<ReviewAiCheckPanelRef>()

  // 入口点击：面板挂载后立即按最新任务状态发起或恢复轮询
  async function openCheck() {
    checkVisible.value = true
    await nextTick()
    checkPanelRef.value?.start()
  }

  // 建议定位：检查覆盖全部文档，脑图仅展示当前文档，未命中时提示切换左侧文档
  function handleCheckLocate(snapshotNodeId: string) {
    const located = mindMapRef.value?.locateNode(snapshotNodeId)
    if (!located) ElMessage.info('该建议指向的用例不在当前文档，请切换左侧文档后重试')
  }

  // ==================== Load ====================

  async function load() {
    loading.value = true
    try {
      const [d, p, tree] = await Promise.all([
        getReviewDetail(reviewId),
        getReviewProgress(reviewId),
        getReviewModuleTree(reviewId),
      ])
      detail.value = d
      progress.value = p
      moduleTree.value = tree
      // 同步后重载时若当前文档已被移除，回退到首个文档
      if (!selectedDocId.value || !findDoc(tree, selectedDocId.value)) {
        selectedDocId.value = firstDocument(tree)?.id ?? ''
      }
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载评审详情失败')
    } finally {
      loading.value = false
    }
  }

  // ==================== Actions ====================

  async function handleComplete() {
    // 按钮 disabled 已拦截，此处再兜底防止进度未加载时误触
    if (!canComplete.value) {
      ElMessage.warning('仍有待评审或不通过的用例，全部通过后才能完成评审')
      return
    }
    try {
      await ElMessageBox.confirm('确定完成该评审吗？完成后将不可再修改标记。', '完成评审', {
        type: 'warning',
      })
    } catch {
      return
    }
    try {
      await completeReview(reviewId)
      ElMessage.success('评审已完成')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '操作失败')
    }
  }

  // 驳回评审：仅发起人 + 活跃态（后端 1000011018 兜底），驳回后快照冻结、AI 检查任务联动终止
  async function handleReject() {
    try {
      await ElMessageBox.confirm(
        '确定驳回该评审吗？驳回后将不可再标记或调整用例，可通过「重新发起」恢复评审。',
        '驳回评审',
        { type: 'warning' },
      )
    } catch {
      return
    }
    try {
      await rejectReview(reviewId)
      ElMessage.success('评审已驳回')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '驳回评审失败')
    }
  }

  // 重新发起：仅发起人 + 已驳回（后端 1000011019 兜底），既有标记保留
  async function handleReopen() {
    try {
      await ElMessageBox.confirm('确定重新发起该评审吗？参与者可继续评审。', '重新发起评审', {
        type: 'warning',
      })
    } catch {
      return
    }
    try {
      await reopenReview(reviewId)
      ElMessage.success('评审已重新发起')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '重新发起失败')
    }
  }

  async function handleSync() {
    try {
      await ElMessageBox.confirm(
        '同步将更新快照节点属性，已有标记不受影响。确定同步？',
        '同步最新用例',
        { type: 'info' },
      )
    } catch {
      return
    }
    try {
      await syncReview(reviewId)
      ElMessage.success('已同步')
      load()
      // 同步会更新快照节点，脑图需一并重载
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
      plannedCases.value = await getReviewPlannedCases(reviewId)
      selectorVisible.value = true
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载规划用例失败')
    }
  }

  async function handleCasesConfirm(selectedNodes: PlannedCases[]) {
    try {
      await updateReviewCases(reviewId, selectedNodes)
      ElMessage.success('规划用例已更新')
      await load()
      mindMapRef.value?.reload()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '更新规划用例失败')
    }
  }

  // 脑图内移除用例成功后：脑图组件已自 reload，此处仅刷新进度/统计与左侧快照树
  async function handleCasesRemoved() {
    await load()
  }

  // ==================== AI Recommend ====================

  const recommendVisible = ref(false)
  const recommendExcludeIds = ref<string[]>([])

  // 打开弹窗前取当前已纳入用例节点 ID 集作为排除集（详细设计 4.5 步骤 2）
  async function openRecommend() {
    try {
      const existing = await getReviewPlannedCases(reviewId)
      recommendExcludeIds.value = existing.flatMap((s) => s.caseIds)
      recommendVisible.value = true
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载规划用例失败')
    }
  }

  // 带入评审：勾选 caseNodeId 解析所属文档，与既有规划用例合并去重后预选进 CaseSelector
  async function handleBringIn(caseNodeIds: string[]) {
    try {
      const [existing, details] = await Promise.all([
        getReviewPlannedCases(reviewId),
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

  // 标记后刷新进度与状态（首次标记会自动转入评审中），避免整页 load 触发脑图重载丢失选中态
  async function refreshProgress() {
    try {
      const [p, d] = await Promise.all([getReviewProgress(reviewId), getReviewDetail(reviewId)])
      progress.value = p
      detail.value = d
    } catch {
      // 标记本身已成功，进度刷新失败不打断操作，后续操作或刷新可恢复
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
    summaryVisible,
    conclusionVisible,
    checkVisible,
    checkPanelRef,
    selectorVisible,
    plannedCases,
    recommendVisible,
    recommendExcludeIds,
    // Computed
    canComplete,
    canShowSummary,
    canShowConclusion,
    canShowCheck,
    canRunCheck,
    // Methods
    load,
    handleComplete,
    handleReject,
    handleReopen,
    handleSync,
    openCaseSelector,
    handleCasesConfirm,
    handleCasesRemoved,
    openRecommend,
    handleBringIn,
    refreshProgress,
    openCheck,
    handleCheckLocate,
    // Router
    router,
    // Stores
    authStore,
    aiStore,
  }
}
