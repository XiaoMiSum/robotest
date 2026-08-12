<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
import type { AiPlanOrderRecommendItem, PlannedCases, SnapshotModule, TestPlanDetail, TestPlanProgress } from '@/types'
import PlanMindMap from '@/components/project/PlanMindMap.vue'
import SnapshotModuleTree from '@/components/project/SnapshotModuleTree.vue'
import CaseSelector from '@/components/project/CaseSelector.vue'
import CasePlanRecommendDialog from '@/components/project/CasePlanRecommendDialog.vue'
import PlanOrderRecommend from '@/components/project/PlanOrderRecommend.vue'
import { useAuthStore } from '@/stores/auth'
import { useAiStore } from '@/stores/ai'

const route = useRoute()
const router = useRouter()
const planId = route.params.planId as string

const authStore = useAuthStore()
const aiStore = useAiStore()

const loading = ref(false)
const detail = ref<TestPlanDetail | null>(null)
const progress = ref<TestPlanProgress | null>(null)
const mindMapRef = ref<InstanceType<typeof PlanMindMap>>()
const moduleTree = ref<SnapshotModule[]>([])
const selectedDocId = ref('')

// 详情页顶部标签：执行记录（默认）与执行顺序推荐（交互设计 5.1）
const activeTab = ref<'records' | 'order'>('records')
const orderPanelRef = ref<InstanceType<typeof PlanOrderRecommend>>()

// 执行顺序推荐入口仅计划负责人/执行人可见（交互设计 5 入口约定）
const canShowOrder = computed(
  () => aiStore.aiEnabled && detail.value?.executor?.id === authStore.user?.id,
)

// 多文档计划需逐文档切换脑图，默认选中快照树中首个文档
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

const statusLabel: Record<string, string> = { new: '待开始', in_progress: '进行中', completed: '已完成', closed: '已关闭' }

// 未结束（待开始/进行中）才允许调整规划用例
const canAdjustCases = computed(
  () => detail.value?.status === 'new' || detail.value?.status === 'in_progress',
)

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
    // 同步后重载时若当前文档已被移除，回退到首个文档
    if (!selectedDocId.value || !findDoc(tree, selectedDocId.value)) {
      selectedDocId.value = firstDocument(tree)?.id ?? ''
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载计划详情失败')
  } finally {
    loading.value = false
  }
}

async function handleComplete() {
  try {
    await ElMessageBox.confirm('确定完成该计划吗？完成后将不可再标记执行结果。', '完成执行', { type: 'warning' })
  } catch { return }
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
    await ElMessageBox.confirm('同步将更新快照节点属性，已有执行记录不受影响。确定同步？', '同步最新用例', { type: 'info' })
  } catch { return }
  try {
    await syncPlan(planId)
    ElMessage.success('已同步')
    load()
    // 同步会更新快照节点，脑图需一并重载
    mindMapRef.value?.reload()
    // 快照同步会改变 snapshot_synced_at，推荐结果随之失效，刷新面板重新判定 stale（3.4.2）
    orderPanelRef.value?.load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '同步失败')
  }
}

// 调整规划用例：弹窗回显当前选择，确认后提交差量并整页重载
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

// 脑图内移除用例成功后：脑图组件已自 reload，此处仅刷新进度/统计与左侧快照树
async function handleCasesRemoved() {
  await load()
  // 移除会改变快照结构，执行顺序推荐结果随之失效，刷新面板重新判定 stale（3.4.2）
  orderPanelRef.value?.load()
}

// AI 用例规划推荐（US-AI-018，交互设计第 6 章）：勾选结果带入既有 CaseSelector 关联流程
const recommendVisible = ref(false)
const recommendExcludeIds = ref<string[]>([])

// 打开弹窗前取当前已纳入用例节点 ID 集作为排除集（详细设计 4.5 步骤 2）
async function openRecommend() {
  try {
    const existing = await getPlanPlannedCases(planId)
    recommendExcludeIds.value = existing.flatMap((s) => s.caseIds)
    recommendVisible.value = true
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载规划用例失败')
  }
}

// 带入计划关联：勾选 caseNodeId 解析所属文档，与既有规划用例合并去重后预选进 CaseSelector
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

// 标记后刷新进度与状态（首次标记会自动转入进行中），避免整页 load 触发脑图重载丢失选中态
async function refreshProgress() {
  try {
    const [p, d] = await Promise.all([getPlanProgress(planId), getPlanDetail(planId)])
    progress.value = p
    detail.value = d
  } catch {
    // 标记本身已成功，进度刷新失败不打断操作，后续操作或刷新可恢复
  }
}

// ==================== 执行顺序推荐联动（US-AI-017） ====================

// 推荐列表行点击 → 切回执行记录标签并定位脑图；跨文档节点未命中时提示切换文档
async function handleOrderLocate(snapshotNodeId: string) {
  activeTab.value = 'records'
  await nextTick()
  const located = mindMapRef.value?.locateNode(snapshotNodeId)
  if (!located) ElMessage.info('该建议指向的用例不在当前文档，请切换左侧文档后重试')
}

// 推荐结果就绪 → 注入脑图 #序号 徽标（双向联动数据源）
function handleOrderResult(items: AiPlanOrderRecommendItem[]) {
  mindMapRef.value?.setOrderBadges(items)
}

// 脑图 #序号 徽标点击 → 切到推荐标签并滚动列表至对应行（双向联动）
async function handleOrderSelect(order: number) {
  activeTab.value = 'order'
  await nextTick()
  orderPanelRef.value?.scrollToOrder(order)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="plan-detail">
    <el-page-header class="plan-detail__page-header" @back="router.push('/workspace/projects/functional-testing?tab=plans')">
      <template #content>
        <div class="plan-detail__header">
          <span class="plan-detail__title">{{ detail?.name ?? '计划详情' }}</span>
          <el-tag v-if="detail" size="small" effect="light" round>{{ statusLabel[detail.status] }}</el-tag>
        </div>
      </template>
      <template #extra>
        <div class="plan-detail__extra">
          <div v-if="progress" class="plan-detail__progress-row">
            <el-progress class="plan-detail__progress" :percentage="progress.progressPercent" :stroke-width="8" />
            <div class="plan-detail__stats">
              <span class="plan-detail__stat plan-detail__stat--pass">通过 {{ progress.passed }}</span>
              <span class="plan-detail__stat plan-detail__stat--fail">失败 {{ progress.failed }}</span>
              <span class="plan-detail__stat plan-detail__stat--blocked">阻塞 {{ progress.blocked }}</span>
              <span class="plan-detail__stat">待执行 {{ progress.untested }}</span>
              <span class="plan-detail__stat">共 {{ progress.totalAssociated }}</span>
            </div>
          </div>
          <div v-if="detail" class="plan-detail__actions">
            <el-button v-if="aiStore.aiEnabled && canAdjustCases" size="small" plain @click="openRecommend">
              <el-icon><MagicStick /></el-icon>AI 推荐用例
            </el-button>
            <el-button v-if="canAdjustCases" size="small" plain @click="openCaseSelector">
              <el-icon><EditPen /></el-icon>调整用例
            </el-button>
            <el-button v-if="canAdjustCases" size="small" plain @click="handleSync">
              <el-icon><Refresh /></el-icon>同步用例
            </el-button>
            <el-button v-if="detail.status === 'new' || detail.status === 'in_progress'" size="small" type="primary" @click="handleComplete">
              <el-icon><CircleCheck /></el-icon>完成执行
            </el-button>
          </div>
        </div>
      </template>
    </el-page-header>

    <!-- 计划详情标签：执行记录（默认）与执行顺序推荐（交互设计 5.1）；推荐标签仅计划负责人/执行人可见 -->
    <el-tabs v-model="activeTab" class="plan-detail__tabs">
      <template #extra>
        <el-button
          v-if="activeTab === 'order'"
          size="small"
          :loading="orderPanelRef?.computing"
          @click="orderPanelRef?.compute()"
        >
          {{ orderPanelRef?.hasResult ? '重新计算' : '开始计算' }}
        </el-button>
      </template>
      <el-tab-pane label="执行记录" name="records">
        <div class="plan-detail__workspace">
          <el-card shadow="never" class="plan-detail__tree-card">
            <SnapshotModuleTree
              :data="moduleTree"
              :current-doc-id="selectedDocId"
              @select-document="(id: string) => (selectedDocId = id)"
            />
          </el-card>
          <el-card shadow="never" class="plan-detail__body">
            <div v-if="!selectedDocId" class="plan-detail__placeholder">
              <el-empty description="请在左侧选择一个文档" />
            </div>
            <PlanMindMap
              v-else
              ref="mindMapRef"
              :plan-id="planId"
              :document-id="selectedDocId"
              :removable="canAdjustCases"
              @marked="refreshProgress"
              @order-select="handleOrderSelect"
              @removed="handleCasesRemoved"
            />
          </el-card>
        </div>
      </el-tab-pane>
      <el-tab-pane v-if="canShowOrder" label="执行顺序推荐✨" name="order">
        <PlanOrderRecommend
          ref="orderPanelRef"
          :plan-id="planId"
          @locate="handleOrderLocate"
          @result="handleOrderResult"
        />
      </el-tab-pane>
    </el-tabs>

    <CaseSelector v-model="selectorVisible" :initial-selected="plannedCases" @confirm="handleCasesConfirm" />
    <CasePlanRecommendDialog
      v-model="recommendVisible"
      :exclude-case-node-ids="recommendExcludeIds"
      target="plan"
      @bring-in="handleBringIn"
    />
  </div>
</template>

<style scoped lang="scss">
// 页面整高 flex 布局：头部固定、脑图卡片撑满剩余空间，消除底部留白（与评审详情页保持一致）
.plan-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

// 标题行用白底卡片化横条承载，与下方脑图卡片视觉统一
.plan-detail__page-header {
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

.plan-detail__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.plan-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

// 标题行右侧：进度/统计 + 操作按钮同行排布
.plan-detail__extra {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.plan-detail__actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  // 与左侧进度统计区用竖线分隔，避免同行内容粘连
  padding-left: var(--space-lg);
  border-left: 1px solid var(--color-neutral-200);

  // 间距由 gap 统一控制，去除 el-button 相邻默认 margin
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  :deep(.el-button .el-icon) {
    margin-right: 4px;
  }
}

.plan-detail__progress-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.plan-detail__progress {
  width: 140px;
}

.plan-detail__stats {
  flex-shrink: 0;
  display: flex;
  gap: var(--space-md);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.plan-detail__stat--pass {
  color: var(--color-success);
}

.plan-detail__stat--fail {
  color: var(--color-danger);
}

.plan-detail__stat--blocked {
  color: var(--color-warning);
}

// 标签容器撑满剩余高度：header 固定、内容区弹性占满，脑图/推荐面板在其中整高布局
.plan-detail__tabs {
  margin-top: var(--space-lg);
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) {
    flex-shrink: 0;
    margin-bottom: 0;
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
  }

  :deep(.el-tab-pane) {
    height: 100%;
  }
}

.plan-detail__workspace {
  height: 100%;
  display: flex;
  gap: var(--space-lg);
}

.plan-detail__tree-card {
  width: 240px;
  flex-shrink: 0;

  :deep(.el-card__body) {
    padding: 0;
    overflow: auto;
    height: 100%;
  }
}

.plan-detail__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.plan-detail__body {
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
