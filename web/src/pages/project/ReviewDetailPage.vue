<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  completeReview,
  getReviewDetail,
  getReviewModuleTree,
  getReviewPlannedCases,
  getReviewProgress,
  syncReview,
  updateReviewCases,
} from '@/services/project'
import type { PlannedCases, SnapshotModule, TestReviewDetail, TestReviewProgress } from '@/types'
import ReviewMindMap from '@/components/project/ReviewMindMap.vue'
import SnapshotModuleTree from '@/components/project/SnapshotModuleTree.vue'
import CaseSelector from '@/components/project/CaseSelector.vue'
import ReviewAiSummary from '@/components/project/ReviewAiSummary.vue'
import ReviewAiCheckPanel from '@/components/project/ReviewAiCheckPanel.vue'
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

// AI 一键检查：仅评审发起人 + AI 启用可见；待评审/评审中可发起，已完成只读查看历史结果（交互设计 2.2）
const canShowCheck = computed(
  () => aiStore.aiEnabled && detail.value?.initiator.id === authStore.user?.id,
)
// 已完成评审不可再发起检查（后端 6012 兜底），面板仅以只读展示历史结果
const canRunCheck = computed(
  () => detail.value?.status === 'new' || detail.value?.status === 'in_progress',
)
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

const statusLabel: Record<string, string> = { new: '待评审', in_progress: '评审中', completed: '已完成' }
const statusType: Record<string, 'info' | 'warning' | 'success'> = { new: 'info', in_progress: 'warning', completed: 'success' }

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="review-detail">
    <el-page-header class="review-detail__page-header" @back="router.push('/workspace/projects/functional-testing?tab=reviews')">
      <template #content>
        <div class="review-detail__header">
          <span class="review-detail__title">{{ detail?.title ?? '评审详情' }}</span>
          <el-tag v-if="detail" :type="statusType[detail.status] ?? 'info'" size="small" effect="light" round>
            {{ statusLabel[detail.status] ?? detail.status }}
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
          <div v-if="detail && detail.status !== 'completed'" class="review-detail__actions">
            <el-button size="small" plain @click="openCaseSelector">
              <el-icon><EditPen /></el-icon>调整用例
            </el-button>
            <el-button size="small" plain @click="handleSync">
              <el-icon><Refresh /></el-icon>同步用例
            </el-button>
            <el-tooltip :disabled="canComplete" content="全部用例评审通过后才能完成评审" placement="bottom">
              <span>
                <el-button size="small" type="primary" :disabled="!canComplete" @click="handleComplete">
                  <el-icon><CircleCheck /></el-icon>完成评审
                </el-button>
              </span>
            </el-tooltip>
          </div>
          <!-- AI 一键检查：仅发起人可见；待评审/评审中可发起，已完成只读查看历史结果 -->
          <div v-if="canShowCheck" class="review-detail__actions">
            <el-button size="small" plain @click="openCheck">
              <el-icon><MagicStick /></el-icon>AI 一键检查
            </el-button>
          </div>
          <!-- AI 生成摘要：评审已完成后展示，与上方进行中操作组互斥（仅发起人可见） -->
          <div v-if="canShowSummary" class="review-detail__actions">
            <el-button size="small" type="primary" plain @click="summaryVisible = true">
              <el-icon><MagicStick /></el-icon>AI 生成摘要
            </el-button>
          </div>
        </div>
      </template>
    </el-page-header>

    <ReviewAiSummary v-if="summaryVisible" v-model="summaryVisible" :review-id="reviewId" />

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
          :removable="detail?.status !== 'completed'"
          @marked="refreshProgress"
          @removed="handleCasesRemoved"
        />
      </el-card>
    </div>

    <CaseSelector v-model="selectorVisible" :initial-selected="plannedCases" @confirm="handleCasesConfirm" />
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
  background: #fff;
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
