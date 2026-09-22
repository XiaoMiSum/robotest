<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAiTask,
  fetchAiTask,
  fetchReviewCheckResult,
  retryAiTask,
  startReviewCheck,
} from '@/services/ai'
import type { AiReviewCheckDimension, AiReviewCheckItem, AiReviewCheckResult, AiTask } from '@/types'

/**
 * AI 一键检查侧面板（US-AI-005，交互设计第 2 章）：
 * 打开先读最近一次检查任务，进行中则 2s 轮询恢复进度；取消/失败保留已产出部分结果。
 * 发起权限由父组件入口控制，后端强校验兜底（2001/6012/6005）。
 * canRun=false（评审已完成）时仅只读展示历史结果，隐藏发起/重试入口。
 */
const props = defineProps<{ reviewId: string; canRun: boolean }>()
const visible = defineModel<boolean>({ required: true })

// 建议定位通知父组件交给脑图高亮；检查覆盖全部文档，当前文档未命中时由父组件提示切换文档
const emit = defineEmits<{ locate: [snapshotNodeId: string] }>()

const DIMENSION_ORDER: AiReviewCheckDimension[] = [
  'missing_precondition',
  'vague_step',
  'missing_expected',
  'priority_conflict',
]
const DIMENSION_LABELS: Record<AiReviewCheckDimension, string> = {
  missing_precondition: '缺少前置条件',
  vague_step: '步骤描述过于笼统',
  missing_expected: '缺少预期结果',
  priority_conflict: '优先级冲突',
}

const task = ref<AiTask | null>(null)
const starting = ref(false)
const filter = ref<'all' | AiReviewCheckDimension>('all')

let pollTimer: ReturnType<typeof setInterval> | null = null
let initialLoad: Promise<void> | null = null

const result = computed<AiReviewCheckResult | null>(() =>
  task.value?.result ? (task.value.result as unknown as AiReviewCheckResult) : null,
)
const running = computed(
  () => task.value?.status === 'pending' || task.value?.status === 'running',
)

// 按维度过滤并保持固定维度序，便于按问题类别浏览
const filteredItems = computed<AiReviewCheckItem[]>(() =>
  (result.value?.items ?? []).filter(
    (item) => filter.value === 'all' || item.dimension === filter.value,
  ),
)
const groupedItems = computed(() =>
  DIMENSION_ORDER.map((dimension) => ({
    dimension,
    items: filteredItems.value.filter((item) => item.dimension === dimension),
  })).filter((group) => group.items.length > 0),
)

function stopPolling(): void {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
}

async function loadLatest(): Promise<void> {
  try {
    const latest = await fetchReviewCheckResult(props.reviewId)
    task.value = latest
    if (latest && (latest.status === 'pending' || latest.status === 'running')) {
      startPolling(latest.id)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载检查结果失败')
  }
}

function startPolling(taskId: string): void {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const latest = await fetchAiTask(taskId)
      task.value = latest
      if (latest.status === 'success' || latest.status === 'failed' || latest.status === 'cancelled') {
        stopPolling()
      }
    } catch {
      // 单次轮询失败不重试，保留当前进度，用户重开面板可恢复
      stopPolling()
    }
  }, 2000)
}

// 发起/重新发起检查：已有进行中任务时仅恢复轮询（同一评审仅允许一个任务，后端 6005 兜底）
async function startCheck(): Promise<void> {
  // 评审已完成时仅允许查看历史结果，禁止发起/重试（后端 6012 兜底）
  if (!props.canRun) return
  if (task.value && (task.value.status === 'pending' || task.value.status === 'running')) {
    ElMessage.info('已有检查任务在执行')
    startPolling(task.value.id)
    return
  }
  starting.value = true
  try {
    const { taskId } = await startReviewCheck(props.reviewId)
    task.value = {
      id: taskId,
      type: 'review_check',
      targetId: props.reviewId,
      status: 'pending',
      progress: 0,
      result: null,
      errorMessage: null,
      createdBy: '',
      createdAt: '',
      updatedAt: '',
    }
    startPolling(taskId)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '发起检查失败')
  } finally {
    starting.value = false
  }
}

// 入口点击触发：等待初始读取完成后，按最新任务状态决定恢复轮询或发起新任务
async function start(): Promise<void> {
  await (initialLoad ?? loadLatest())
  if (props.canRun) await startCheck()
}

async function cancel(): Promise<void> {
  if (!task.value) return
  try {
    await ElMessageBox.confirm('取消后已产出的部分结果仍可查看，确定取消？', '取消检查', { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelAiTask(task.value.id)
    // 轮询继续，下一轮将读到 cancelled 状态与部分结果
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '取消失败')
  }
}

async function retry(): Promise<void> {
  if (!task.value) return
  try {
    await retryAiTask(task.value.id)
    startPolling(task.value.id)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重试失败')
  }
}

function locate(snapshotNodeId: string): void {
  emit('locate', snapshotNodeId)
}

function footerAction(): void {
  if (task.value?.status === 'failed') retry()
  else startCheck()
}

onMounted(() => {
  initialLoad = loadLatest()
})

onBeforeUnmount(stopPolling)

defineExpose({ start })
</script>

<template>
  <el-drawer v-model="visible" size="640px" :close-on-click-modal="true" modal-class="ai-check-drawer-modal">
    <template #header>
      <div class="ai-check-header">
        <span class="ai-check-title"><el-icon><MagicStick /></el-icon> AI 检查</span>
      </div>
    </template>

    <div class="ai-check">
      <!-- 进行中：批次进度 + 取消任务 -->
      <template v-if="running">
        <el-progress :percentage="task?.progress ?? 0" :stroke-width="4" />
        <div class="ai-check-progress-text">
          <span v-if="result">已检查 {{ result.checkedCaseCount }} / {{ result.totalCaseCount }} 条用例</span>
          <span>检查进行中…</span>
        </div>
        <el-button size="small" @click="cancel">取消任务</el-button>
      </template>

      <!-- 无任务：空态 -->
      <el-empty
        v-else-if="!task"
        :description="canRun ? '暂无检查记录，点击下方按钮发起检查' : '该评审已完成，无历史检查结果'"
        :image-size="72"
      />

      <!-- 终态：结果列表 -->
      <template v-else>
        <el-alert
          v-if="task.status === 'failed'"
          type="error"
          :closable="false"
          :title="task.errorMessage || '检查失败'"
          show-icon
        />
        <el-alert
          v-if="task.status === 'cancelled'"
          type="warning"
          :closable="false"
          title="任务已取消，以下为已产出的部分结果"
          show-icon
        />
        <el-alert
          v-if="result && result.skippedBatches > 0"
          type="info"
          :closable="false"
          :title="`部分用例未完成检查（${result.skippedBatches} 批）`"
          show-icon
        />

        <div class="ai-check-filter">
          <span class="ai-check-filter__label">维度过滤：</span>
          <el-select v-model="filter" size="small">
            <el-option label="全部" value="all" />
            <el-option v-for="d in DIMENSION_ORDER" :key="d" :label="DIMENSION_LABELS[d]" :value="d" />
          </el-select>
        </div>

        <div v-if="groupedItems.length" class="ai-check-list">
          <div v-for="group in groupedItems" :key="group.dimension" class="ai-check-group">
            <div class="ai-check-group__title">⚠ {{ DIMENSION_LABELS[group.dimension] }}</div>
            <div
              v-for="item in group.items"
              :key="`${item.snapshotNodeId}:${item.dimension}`"
              class="ai-check-item"
              @click="locate(item.snapshotNodeId)"
            >
              <div class="ai-check-item__text">{{ item.suggestion }}</div>
              <el-tag size="small" effect="plain">点击定位</el-tag>
            </div>
          </div>
        </div>
        <el-empty v-else-if="task.status === 'success'" description="未发现检查问题" :image-size="72" />
      </template>

      <!-- 底部操作：空态发起 / 失败重试 / 终态重新检查；已完成只读不渲染 -->
      <div v-if="!running && canRun" class="ai-check-actions">
        <el-button type="primary" :loading="starting" @click="footerAction">
          {{ !task ? '发起检查' : task.status === 'failed' ? '重试' : '重新检查' }}
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.ai-check-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.ai-check-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.ai-check {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-check-progress-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-check-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.ai-check-filter__label {
  flex-shrink: 0;
}

.ai-check-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-check-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-check-group__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ai-check-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--el-color-primary);
  }
}

.ai-check-item__text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  word-break: break-word;
}

.ai-check-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

/* 透明遮罩：点击抽屉外空白处自动关闭，同时不压暗画布（交互设计 2.1，统一规格 2.9） */
:deep(.ai-check-drawer-modal) {
  background: transparent;
}
</style>
