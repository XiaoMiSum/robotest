<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAiTask,
  fetchAiTask,
  fetchLatestBugClustering,
  startBugClustering,
  toBugClusterSnapshot,
  retryAiTask,
} from '@/services/ai'
import { useAiStore } from '@/stores/ai'
import type { AiBugClusterSnapshot, AiTask } from '@/types'
import { formatDateTime, formatShortId } from '@/utils/format'
import { buildModuleBars, buildSeveritySegments } from './bugClusterChart'

/**
 * 缺陷看板「AI 分析」聚类面板（US-AI-010，交互设计 4.1）：
 * 打开先读最近一次任务，进行中 2s 轮询恢复；开始/刷新创建异步任务；
 * 聚类卡片展开缺陷清单（快照仅含 bugIds，短 ID 展示 + 跳转详情）。
 * 结果只读洞察，不修改缺陷数据。
 */
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const aiStore = useAiStore()

const task = ref<AiTask | null>(null)
const starting = ref(false)
const expandedCluster = ref<number | null>(null)

let pollTimer: ReturnType<typeof setInterval> | null = null
let initialLoad: Promise<void> | null = null

const snapshot = computed<AiBugClusterSnapshot | null>(() =>
  toBugClusterSnapshot(task.value?.result ?? null),
)
const running = computed(
  () => task.value?.status === 'pending' || task.value?.status === 'running',
)
// 无向量时聚类降级为关键词归纳，顶部提示条（基础设施 4.10）
const semanticDegraded = computed(() => aiStore.semanticDegraded)

const moduleBars = computed(() => buildModuleBars(snapshot.value?.clusters.flatMap((c) => c.moduleDist) ?? []))
const severitySegments = computed(() =>
  buildSeveritySegments(
    snapshot.value?.clusters.reduce<Record<string, number>>((acc, c) => {
      for (const [key, count] of Object.entries(c.severityDist)) {
        acc[key] = (acc[key] ?? 0) + count
      }
      return acc
    }, {}) ?? { fatal: 0, serious: 0, general: 0, minor: 0 },
  ),
)

function stopPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function loadLatest(): Promise<void> {
  try {
    const latest = await fetchLatestBugClustering()
    task.value = latest
    if (latest && (latest.status === 'pending' || latest.status === 'running')) {
      startPolling(latest.id)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载聚类结果失败')
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
      // 单次轮询失败保留当前进度，用户重开面板可恢复
      stopPolling()
    }
  }, 2000)
}

// 开始/刷新：已有进行中任务时仅恢复轮询（同项目同时仅一个任务，后端 6005 兜底）
async function start(): Promise<void> {
  if (task.value && running.value) {
    ElMessage.info('已有聚类任务在执行')
    startPolling(task.value.id)
    return
  }
  starting.value = true
  try {
    const { taskId } = await startBugClustering()
    task.value = {
      id: taskId,
      type: 'bug_clustering',
      targetId: null,
      status: 'pending',
      progress: 0,
      result: null,
      errorMessage: null,
      createdBy: '',
      createdAt: '',
      updatedAt: '',
    }
    expandedCluster.value = null
    startPolling(taskId)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '发起聚类失败')
  } finally {
    starting.value = false
  }
}

async function open(): Promise<void> {
  initialLoad = loadLatest()
  await initialLoad
  if (running.value) return
  // 打开无结果且无任务时展示空态，由 [开始分析] 发起
}

async function cancel(): Promise<void> {
  if (!task.value) return
  try {
    await ElMessageBox.confirm('取消后已产出的部分结果仍可查看，确定取消？', '取消任务', { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelAiTask(task.value.id)
    // 轮询继续，下一轮读到 cancelled 状态与部分快照
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

function goDetail(bugId: string): void {
  emit('update:modelValue', false)
  router.push(`/workspace/projects/bugs/${bugId}`)
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      void open()
    } else {
      stopPolling()
    }
  },
)

onBeforeUnmount(stopPolling)
</script>

<template>
  <div v-if="props.modelValue" class="bug-cluster">
    <el-card shadow="never" class="bug-cluster__card">
      <template #header>
        <div class="bug-cluster__header">
          <span class="bug-cluster__title"><el-icon><MagicStick /></el-icon> AI 分析</span>
          <span v-if="snapshot" class="bug-cluster__generated">
            生成时间：{{ formatDateTime(snapshot.generatedAt) }}
          </span>
          <div class="bug-cluster__header-actions">
            <el-button size="small" :disabled="running || starting" @click="start">
              {{ starting ? '分析中…' : '刷新' }}
            </el-button>
            <el-button size="small" @click="emit('update:modelValue', false)">收起</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="semanticDegraded"
        type="warning"
        :closable="false"
        show-icon
        title="语义检索暂不可用，聚类基于关键词与降级模型"
        class="bug-cluster__degraded"
      />

      <!-- 进行中：进度条 + 取消 -->
      <div v-if="running" class="bug-cluster__running">
        <el-progress :percentage="task?.progress ?? 0" :stroke-width="10" />
        <div class="bug-cluster__progress-text">聚类分析中…</div>
        <el-button size="small" @click="cancel">取消任务</el-button>
      </div>

      <!-- 空态：无结果无任务 -->
      <el-empty
        v-else-if="!task"
        description="暂无聚类结果，点击下方按钮开始分析"
        :image-size="72"
      >
        <el-button type="primary" :loading="starting" @click="start">开始分析</el-button>
      </el-empty>

      <!-- 终态：结果快照 -->
      <template v-else>
        <el-alert
          v-if="task.status === 'failed'"
          type="error"
          :closable="false"
          :title="task.errorMessage || '聚类失败'"
          show-icon
        >
          <template #default>
            <el-button size="small" type="primary" plain @click="retry">重试</el-button>
          </template>
        </el-alert>
        <el-alert
          v-if="task.status === 'cancelled'"
          type="warning"
          :closable="false"
          title="任务已取消，以下为已产出的部分结果"
          show-icon
        />

        <template v-if="snapshot">
          <div class="bug-cluster__summary">共 {{ snapshot.bugCount }} 条缺陷</div>

          <!-- 聚类卡片 -->
          <div v-if="snapshot.clusters.length" class="bug-cluster__cards">
            <div
              v-for="(cluster, index) in snapshot.clusters"
              :key="cluster.label"
              class="bug-cluster__card-item"
            >
              <div class="bug-cluster__card-head" @click="expandedCluster = expandedCluster === index ? null : index">
                <div class="bug-cluster__card-main">
                  <div class="bug-cluster__card-label">{{ cluster.label }}</div>
                  <div class="bug-cluster__card-cause">{{ cluster.rootCause ?? '未给出根因' }}</div>
                </div>
                <div class="bug-cluster__card-side">
                  <span class="bug-cluster__card-count">缺陷数：{{ cluster.bugIds.length }}</span>
                  <span class="bug-cluster__card-toggle">{{ expandedCluster === index ? '▾' : '▸' }}</span>
                </div>
              </div>
              <div v-if="expandedCluster === index" class="bug-cluster__card-list">
                <div
                  v-for="bugId in cluster.bugIds"
                  :key="bugId"
                  class="bug-cluster__card-bug"
                  @click="goDetail(bugId)"
                >
                  <span>{{ formatShortId(bugId) }}</span>
                  <el-link type="primary" :underline="false">查看</el-link>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="bug-cluster__none">暂无可归纳的聚类（缺陷未形成 ≥2 条的相似组）</div>

          <div v-if="snapshot.unclustered.length" class="bug-cluster__unclustered">
            未聚类缺陷：{{ snapshot.unclustered.length }} 条
          </div>

          <!-- 分布图（CSS 自绘，交互设计 4.2） -->
          <div v-if="moduleBars.length" class="bug-cluster__chart">
            <div class="bug-cluster__chart-title">按模块</div>
            <div v-for="bar in moduleBars" :key="bar.moduleName" class="bug-cluster__bar-row">
              <span class="bug-cluster__bar-name">{{ bar.moduleName }}</span>
              <div class="bug-cluster__bar-track">
                <div class="bug-cluster__bar-fill" :style="{ width: `${bar.widthPercent}%` }" />
              </div>
              <span class="bug-cluster__bar-count">{{ bar.count }}</span>
            </div>

            <div class="bug-cluster__chart-title">按等级</div>
            <div class="bug-cluster__seg-track">
              <div
                v-for="seg in severitySegments"
                :key="seg.severity"
                class="bug-cluster__seg-fill"
                :class="`bug-cluster__seg-fill--${seg.severity}`"
                :style="{ width: `${seg.widthPercent}%` }"
              />
            </div>
            <div class="bug-cluster__seg-legend">
              <span
                v-for="seg in severitySegments"
                :key="seg.severity"
                class="bug-cluster__seg-legend-item"
              >
                <span class="bug-cluster__seg-dot" :class="`bug-cluster__seg-dot--${seg.severity}`" />
                {{ seg.severity === 'fatal' ? '致命' : seg.severity === 'serious' ? '严重' : seg.severity === 'general' ? '一般' : '轻微' }}
                {{ seg.count }}
              </span>
            </div>
          </div>
        </template>
      </template>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.bug-cluster__card {
  margin-top: var(--space-lg);
}

.bug-cluster__header {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.bug-cluster__title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--color-primary-600);
}

.bug-cluster__generated {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}

.bug-cluster__header-actions {
  margin-left: auto;
  display: flex;
  gap: var(--space-sm);
}

.bug-cluster__degraded {
  margin-bottom: var(--space-md);
}

.bug-cluster__running {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md) 0;
}

.bug-cluster__progress-text {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.bug-cluster__summary {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-500);
  margin-bottom: var(--space-sm);
}

.bug-cluster__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--space-md);
  margin-bottom: var(--space-md);
}

.bug-cluster__card-item {
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.bug-cluster__card-head {
  display: flex;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: var(--color-neutral-50);
  }
}

.bug-cluster__card-main {
  min-width: 0;
}

.bug-cluster__card-label {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-800);
  margin-bottom: 2px;
}

.bug-cluster__card-cause {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-500);
  line-height: 1.5;
}

.bug-cluster__card-side {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.bug-cluster__card-count {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}

.bug-cluster__card-toggle {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.bug-cluster__card-list {
  border-top: 1px solid var(--color-neutral-100);
  padding: var(--space-xs) var(--space-md);
}

.bug-cluster__card-bug {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-600);
  cursor: pointer;

  &:hover {
    color: var(--color-primary-600);
  }
}

.bug-cluster__none {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
  margin-bottom: var(--space-md);
}

.bug-cluster__unclustered {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
  margin-bottom: var(--space-md);
}

.bug-cluster__chart {
  border-top: 1px solid var(--color-neutral-100);
  padding-top: var(--space-md);
}

.bug-cluster__chart-title {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-700);
  margin-bottom: var(--space-sm);
}

.bug-cluster__bar-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: 6px;
}

.bug-cluster__bar-name {
  flex-shrink: 0;
  width: 96px;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-600);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-cluster__bar-track {
  flex: 1;
  height: 10px;
  background: var(--color-neutral-100);
  border-radius: var(--radius-full);
  overflow: hidden;
}

.bug-cluster__bar-fill {
  height: 100%;
  background: var(--color-primary-500);
  border-radius: var(--radius-full);
}

.bug-cluster__bar-count {
  flex-shrink: 0;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-500);
}

.bug-cluster__seg-track {
  display: flex;
  height: 14px;
  border-radius: var(--radius-full);
  overflow: hidden;
  margin-bottom: var(--space-sm);
}

.bug-cluster__seg-fill--fatal {
  background: var(--color-bug-fatal);
}

.bug-cluster__seg-fill--serious {
  background: var(--color-bug-serious);
}

.bug-cluster__seg-fill--general {
  background: var(--color-bug-general);
}

.bug-cluster__seg-fill--minor {
  background: var(--color-bug-minor);
}

.bug-cluster__seg-legend {
  display: flex;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.bug-cluster__seg-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-500);
}

.bug-cluster__seg-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.bug-cluster__seg-dot--fatal {
  background: var(--color-bug-fatal);
}

.bug-cluster__seg-dot--serious {
  background: var(--color-bug-serious);
}

.bug-cluster__seg-dot--general {
  background: var(--color-bug-general);
}

.bug-cluster__seg-dot--minor {
  background: var(--color-bug-minor);
}
</style>
