<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
import {
  isActiveReview,
  reviewStatusMeta,
} from '@/components/project/functional-testing/review/reviewListPresentation'
import ReviewMindMap from '@/components/project/functional-testing/review/ReviewMindMap.vue'
import SnapshotModuleTree from '@/components/project/functional-testing/review/SnapshotModuleTree.vue'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import CasePlanRecommendDialog from '@/components/project/functional-testing/review/CasePlanRecommendDialog.vue'
import ReviewAiSummary from '@/components/project/functional-testing/review/ReviewAiSummary.vue'
import ReviewAiCheckPanel from '@/components/project/functional-testing/review/ReviewAiCheckPanel.vue'
import ReviewAiConclusionPanel from '@/components/project/functional-testing/review/ReviewAiConclusionPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useAiStore } from '@/stores/ai'

const route = useRoute()
const router = useRouter()
const reviewId = route.params.reviewId as string

const authStore = useAuthStore()
const aiStore = useAiStore()

const loading = ref(false)
const detail = ref<TestReviewDetail | null>(null)
const progress = ref<TestReviewProgress | null>(null)
const mindMapRef = ref<InstanceType<typeof ReviewMindMap>>()
const moduleTree = ref<SnapshotModule[]>([])
const selectedDocId = ref('')

// 多文档评审需逐文档切换脑图，默认选中快照树中首个文档
function firstDocument(nodes: SnapshotModule[]): SnapshotModule | null {
  for (const node of nodes) {
    if (node.type === 'document') return node
    const found = firstDocument(node.children ?? [])
    if (found) return found
  }
  return null
}

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
const summaryVisible = ref(false)
const conclusionVisible = ref(false)

// AI 评审结论：随完成事件自动生成，此处可手动/重新生成（06 §5.2）；入口条件与摘要一致
const canShowConclusion = computed(() => canShowSummary.value)

// AI 一键检查：仅评审发起人 + AI 启用可见；活跃态可发起，终态只读查看历史结果（交互设计 2.2）
const canShowCheck = computed(
  () => aiStore.aiEnabled && detail.value?.initiator.id === authStore.user?.id,
)
// 终态（已通过/已驳回）不可再发起检查（后端 6012 兜底），面板仅以只读展示历史结果
const canRunCheck = computed(() => isActiveReview(detail.value?.status ?? ''))
const checkVisible = ref(false)
const checkPanelRef = ref<InstanceType<typeof ReviewAiCheckPanel>>()

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

function findDoc(nodes: SnapshotModule[], id: string): boolean {
  return nodes.some((n) => n.id === id || findDoc(n.children ?? [], id))
}

async function handleComplete() {
  // 按钮 disabled 已拦截，此处再兑底防止进度未加载时误触
  if (!canComplete.value) {
    ElMessage.warning('仍有待评审或不通过的用例，全部通过后才能完成评审')
    return
  }
  try {
    await ElMessageBox.confirm('确定完成该评审吗？完成后将不可再修改标记。', '完成评审', { type: 'warning' })
  } catch { return }
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
  } catch { return }
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
    await ElMessageBox.confirm('确定重新发起该评审吗？参与者可继续评审。', '重新发起评审', { type: 'warning' })
  } catch { return }
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
    await ElMessageBox.confirm('同步将更新快照节点属性，已有标记不受影响。确定同步？', '同步最新用例', { type: 'info' })
  } catch { return }
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

// 调整规划用例：弹窗回显当前选择，确认后提交差量并整页重载
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

// AI 用例规划推荐（US-AI-018，交互设计第 6 章）：勾选结果带入既有 CaseSelector 关联流程
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

// 状态展示口径与列表页共用（交互设计 07 §1.1）
const statusMeta = computed(() =>
  reviewStatusMeta(detail.value?.status ?? ''),
)

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="review-detail">
    <el-page-header class="review-detail__page-header" @back="router.push('/workspace/projects/functional-testing?tab=reviews')">
      <template #content>
        <div class="review-detail__header">
          <span class="review-detail__title">{{ detail?.title ?? '评审详情' }}</span>
          <el-tag v-if="detail" :type="statusMeta.tagType" size="small" effect="light" round>
            {{ statusMeta.label }}
          </el-tag>
        </div>
      </template>
      <template #extra>
        <div class="review-detail__extra">
          <div v-if="progress" class="review-detail__progress-row">
            <el-progress class="review-detail__progress" :percentage="progress.progressPercent" :stroke-width="8" />
            <div class="review-detail__stats">
              <span class="review-detail__stat review-detail__stat--pass">通过 {{ progress.passed }}</span>
              <span class="review-detail__stat review-detail__stat--fail">不通过 {{ progress.failed }}</span>
              <span class="review-detail__stat review-detail__stat--pending">待评审 {{ progress.pending }}</span>
              <span class="review-detail__stat">共 {{ progress.totalAssociated }}</span>
            </div>
          </div>
          <!-- 活跃态操作组：调整/同步/驳回/完成，终态收起（交互设计 07 §1.2） -->
          <div v-if="detail && isActiveReview(detail.status)" class="review-detail__actions">
            <el-button v-if="aiStore.aiEnabled" size="small" plain @click="openRecommend">
              <el-icon><MagicStick /></el-icon>AI 推荐用例
            </el-button>
            <el-button size="small" plain @click="openCaseSelector">
              <el-icon><EditPen /></el-icon>调整用例
            </el-button>
            <el-button size="small" plain @click="handleSync">
              <el-icon><Refresh /></el-icon>同步用例
            </el-button>
            <el-button size="small" type="danger" plain @click="handleReject">
              <el-icon><CircleClose /></el-icon>驳回
            </el-button>
            <el-tooltip :disabled="canComplete" content="全部用例评审通过后才能完成评审" placement="bottom">
              <span>
                <el-button size="small" type="primary" :disabled="!canComplete" @click="handleComplete">
                  <el-icon><CircleCheck /></el-icon>完成评审
                </el-button>
              </span>
            </el-tooltip>
          </div>
          <!-- 已驳回：仅发起人可见，重新发起后回到进行中 -->
          <div
            v-else-if="detail && detail.status === 'rejected' && detail.initiator.id === authStore.user?.id"
            class="review-detail__actions"
          >
            <el-button size="small" type="primary" @click="handleReopen">
              <el-icon><RefreshLeft /></el-icon>重新发起
            </el-button>
          </div>
          <!-- AI 一键检查：仅发起人可见；活跃态可发起，终态只读查看历史结果 -->
          <div v-if="canShowCheck" class="review-detail__actions">
            <el-button size="small" plain @click="openCheck">
              <el-icon><MagicStick /></el-icon>AI 一键检查
            </el-button>
          </div>
          <!-- AI 生成摘要：评审已通过（completed）后展示，与活跃态操作组互斥（仅发起人可见） -->
          <div v-if="canShowSummary" class="review-detail__actions">
            <el-button size="small" type="primary" plain @click="summaryVisible = true">
              <el-icon><MagicStick /></el-icon>AI 生成摘要
            </el-button>
          </div>
          <!-- AI 评审结论：评审已通过后展示（自动结论随完成事件落库，此处可手动触发/重新生成） -->
          <div v-if="canShowConclusion" class="review-detail__actions">
            <el-button size="small" plain @click="conclusionVisible = true">
              <el-icon><MagicStick /></el-icon>AI 评审结论
            </el-button>
          </div>
        </div>
      </template>
    </el-page-header>

    <ReviewAiSummary v-if="summaryVisible" v-model="summaryVisible" :review-id="reviewId" />

    <ReviewAiConclusionPanel
      v-if="conclusionVisible"
      v-model="conclusionVisible"
      :review-id="reviewId"
    />

    <ReviewAiCheckPanel
      v-if="checkVisible"
      ref="checkPanelRef"
      v-model="checkVisible"
      :review-id="reviewId"
      :can-run="canRunCheck"
      @locate="handleCheckLocate"
    />

    <div class="review-detail__workspace">
      <el-card shadow="never" class="review-detail__tree-card">
        <SnapshotModuleTree
          :data="moduleTree"
          :current-doc-id="selectedDocId"
          @select-document="(id: string) => (selectedDocId = id)"
        />
      </el-card>
      <el-card shadow="never" class="review-detail__body">
        <div v-if="!selectedDocId" class="review-detail__placeholder">
          <el-empty description="请在左侧选择一个文档" />
        </div>
        <ReviewMindMap
          v-else
          ref="mindMapRef"
          :review-id="reviewId"
          :document-id="selectedDocId"
          :removable="isActiveReview(detail?.status ?? '')"
          @marked="refreshProgress"
          @removed="handleCasesRemoved"
        />
      </el-card>
    </div>

    <CaseSelector v-model="selectorVisible" :initial-selected="plannedCases" @confirm="handleCasesConfirm" />
    <CasePlanRecommendDialog
      v-model="recommendVisible"
      :exclude-case-node-ids="recommendExcludeIds"
      target="review"
      @bring-in="handleBringIn"
    />
  </div>
</template>

<style scoped lang="scss">
// 页面整高 flex 布局：头部固定、脑图卡片撑满剩余空间，消除底部留白
.review-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

// 标题行用白底卡片化横条承载，与下方脑图卡片视觉统一
.review-detail__page-header {
  flex-shrink: 0;
  padding: var(--space-sm) var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);

  // 左侧标题/元信息区可伸缩，窗口变窄时优先裁剪标题而非挤压右侧操作区
  :deep(.el-page-header__left) {
    flex: 1;
    min-width: 0;
    margin-right: var(--space-lg);
  }

  :deep(.el-page-header__content) {
    flex: 1;
    min-width: 0;
    overflow: hidden;
  }
}

.review-detail__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.review-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

// 标题行右侧：进度/统计 + 操作按钮同行排布
.review-detail__extra {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.review-detail__actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  // 与左侧进度统计区用竖线分隔，避免同行内容粘连
  padding-left: var(--space-lg);
  border-left: 1px solid var(--color-neutral-200);

  // 间距由 gap 统一控制，去除 el-button 相邻默认 margin（tooltip 包裹导致间距不均）
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  :deep(.el-button .el-icon) {
    margin-right: 4px;
  }
}

.review-detail__progress-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.review-detail__progress {
  width: 140px;
}

.review-detail__stats {
  flex-shrink: 0;
  display: flex;
  gap: var(--space-md);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.review-detail__stat--pass {
  color: var(--color-success);
}

.review-detail__stat--fail {
  color: var(--color-danger);
}

.review-detail__stat--pending {
  color: var(--color-warning);
}

.review-detail__workspace {
  margin-top: var(--space-lg);
  flex: 1;
  min-height: 0;
  display: flex;
  gap: var(--space-lg);
}

.review-detail__tree-card {
  width: 240px;
  flex-shrink: 0;

  :deep(.el-card__body) {
    padding: 0;
    overflow: auto;
    height: 100%;
  }
}

.review-detail__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.review-detail__body {
  flex: 1;
  min-width: 0;

  :deep(.el-card__body) {
    height: 100%;
    padding: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
}
</style>
