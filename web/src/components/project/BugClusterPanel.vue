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
import type { AiBugClusterSnapshot, AiTask, BugSeverity } from '@/types'
import { formatDateTime, formatShortId } from '@/utils/format'
import { buildModuleBars, buildSeveritySegments } from './bugClusterChart'

/**
 * 缺陷看板「AI 分析」聚类面板（US-AI-010，交互设计 4.1）：
 * 报告式主题卡片——汇总条 + 单列主题卡（首个默认展开、严重度内联分布点、
 * 明细行 = 严重度色点 + 缺陷标题 + 状态 + 短 ID，点击跳转详情）。
 * 快照 bugs 携带标题（详细设计 2.3），明细无需再查详情即渲染。
 * 结果只读洞察，不修改缺陷数据。
 */
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

// 抽屉显隐：无遮罩（modal=false）不阻断缺陷列表操作，ESC/X 关闭（交互设计 4.1）
const drawerVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const router = useRouter()
const aiStore = useAiStore()

const task = ref<AiTask | null>(null)
const starting = ref(false)
// 首个主题默认展开（交互设计 4.2），null 表示全部收起
const expandedCluster = ref<number | null>(0)
/** 根因全文展开：与缺陷清单独立，点击根因超长行展开全文 */
const expandedCause = ref<number | null>(null)

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

// 汇总条：共 N 条缺陷 · M 个聚类主题 · K 条未聚类
const summary = computed(() => ({
  bugs: snapshot.value?.bugCount ?? 0,
  clusters: snapshot.value?.clusters.length ?? 0,
  unclustered: snapshot.value?.unclustered.length ?? 0,
}))

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

// 单簇严重度内联分布点（●●●○ 形式，fatal/serious 高亮，用于「哪个主题最致命」一眼可辨）
const SEVERITY_DOT_MAX = 4
function severityDots(dist: Record<string, number>): { severity: BugSeverity; filled: boolean }[] {
  const order: BugSeverity[] = ['fatal', 'serious', 'general', 'minor']
  const dots: { severity: BugSeverity; filled: boolean }[] = []
  for (let i = 0; i < SEVERITY_DOT_MAX; i++) {
    dots.push({
      severity: order[i] ?? 'minor',
      filled: (dist[order[i] ?? 'minor'] ?? 0) > 0,
    })
  }
  return dots
}

// 缺陷状态中文标签（明细行状态列，与缺陷列表页文案一致）
const STATUS_LABEL: Record<string, string> = {
  active: '活跃',
  resolved: '已解决',
  rejected: '已拒绝',
  closed: '已关闭',
}

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
    expandedCluster.value = 0
    expandedCause.value = null
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
  <el-drawer
    v-model="drawerVisible"
    size="560px"
    :modal="false"
    class="bug-cluster"
  >
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
        </div>
      </div>
    </template>
    <el-card shadow="never" class="bug-cluster__card">

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
            <!-- 汇总条 -->
            <div class="bug-cluster__summary">
              共 {{ summary.bugs }} 条缺陷 · {{ summary.clusters }} 个聚类主题 · {{ summary.unclustered }} 条未聚类
            </div>

            <!-- 报告式主题卡片：首个默认展开，明细行 = 色点 + 标题 + 状态 + 短 ID -->
            <div v-if="snapshot.clusters.length" class="bug-cluster__cards">
              <div
                v-for="(cluster, index) in snapshot.clusters"
                :key="cluster.label"
                class="bug-cluster__card-item"
              >
                <div
                  class="bug-cluster__card-head"
                  @click="expandedCluster = expandedCluster === index ? null : index"
                >
                  <span class="bug-cluster__card-index">{{ index + 1 }}</span>
                  <div class="bug-cluster__card-main">
                    <div class="bug-cluster__card-label">
                      <el-tag
                        v-if="!cluster.labeled"
                        type="warning"
                        size="small"
                        effect="light"
                        class="bug-cluster__label-fallback"
                      >⚠ 标签生成失败</el-tag>
                      {{ cluster.label }}
                    </div>
                    <div class="bug-cluster__card-cause">
                      {{ cluster.labeled ? (cluster.rootCause ?? '未给出根因') : '未生成主题名与根因（LLM 归纳失败）' }}
                    </div>
                  </div>
                  <div class="bug-cluster__card-side">
                    <span class="bug-cluster__card-dots">
                      <span
                        v-for="(dot, di) in severityDots(cluster.severityDist)"
                        :key="di"
                        class="bug-cluster__dot"
                        :class="[`bug-cluster__dot--${dot.severity}`, { 'bug-cluster__dot--empty': !dot.filled }]"
                      />
                    </span>
                    <span class="bug-cluster__card-count">{{ cluster.bugs.length }}</span>
                    <span class="bug-cluster__card-toggle">{{ expandedCluster === index ? '▾' : '▸' }}</span>
                  </div>
                </div>
                <div v-if="expandedCluster === index" class="bug-cluster__card-body">
                  <!-- 根因全文（超长时可独立展开；标签生成失败时展开区同样明示） -->
                  <div
                    v-if="cluster.labeled"
                    class="bug-cluster__card-cause-full"
                    @click="expandedCause = expandedCause === index ? null : index"
                  >
                    {{ cluster.rootCause ?? '未给出根因' }}
                  </div>
                  <div v-else class="bug-cluster__card-cause-full">
                    未生成主题名与根因（LLM 归纳失败，请检查 AI 模型配置后刷新重试）
                  </div>
                  <div class="bug-cluster__card-list">
                    <div
                      v-for="bug in cluster.bugs"
                      :key="bug.id"
                      class="bug-cluster__card-bug"
                      @click="goDetail(bug.id)"
                    >
                      <span class="bug-cluster__card-bug-sev" :class="`bug-cluster__card-bug-sev--${bug.severity}`" />
                      <span class="bug-cluster__card-bug-title">{{ bug.title || formatShortId(bug.id) }}</span>
                      <span class="bug-cluster__card-bug-status">{{ STATUS_LABEL[bug.status] ?? bug.status }}</span>
                      <span class="bug-cluster__card-bug-id">{{ formatShortId(bug.id) }}</span>
                      <el-link type="primary" underline="never">查看</el-link>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="bug-cluster__none">暂无可归纳的聚类（缺陷未形成 ≥2 条的相似组）</div>

            <!-- 分布图（CSS 自绘，交互设计 4.2） -->
            <div v-if="moduleBars.length" class="bug-cluster__chart">
              <div class="bug-cluster__chart-title">按模块</div>
              <div v-for="bar in moduleBars" :key="bar.moduleName" class="bug-cluster__bar-row">
                <span class="bug-cluster__bar-name">{{ bar.moduleName }}</span>
                <div class="bug-cluster__bar-track">
                  <div class="bug-cluster__bar-fill" :style="{ width: `${bar.widthPercent}%` }" />
                </div>
                <span class="bug-cluster__bar-count">{{ bar.count }}</span>
                <span class="bug-cluster__bar-percent">{{ bar.widthPercent }}%</span>
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
  </el-drawer>
</template>

<style scoped lang="scss">
// 抽屉内不再需要内嵌时的顶部间距；卡片仅作内容容器
.bug-cluster__card {
  box-shadow: none;
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
  font-size: var(--font-size-base);
  color: var(--color-primary-600);
}

.bug-cluster__generated {
  font-size: var(--font-size-xs);
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
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

// 汇总条：辅助信息但保证可读，不低于 12px
.bug-cluster__summary {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  margin-bottom: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  background: var(--color-neutral-50);
  border-radius: var(--radius-md);
}

// 报告式单列主题卡片（替代 V1.1 密集网格）
.bug-cluster__cards {
  display: flex;
  flex-direction: column;
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
  align-items: flex-start;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-md);
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: var(--color-neutral-50);
  }
}

// 序号徽章：主题编号，一眼定位
.bug-cluster__card-index {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-primary-100);
  color: var(--color-primary-600);
  font-size: var(--font-size-xs);
  font-weight: 600;
  margin-top: 2px;
}

.bug-cluster__card-main {
  flex: 1;
  min-width: 0;
}

// 主题名：核心阅读对象，16px 加粗（V1.1 为 12px）
.bug-cluster__card-label {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-neutral-800);
  margin-bottom: 4px;
  line-height: 1.4;
}

// 标签生成失败标识（labeled=false）：warning el-tag 与占位主题名同行，区分正常/降级结果
.bug-cluster__label-fallback {
  margin-right: 6px;
  vertical-align: 2px;
}

// 根因正文：14px，最多 3 行省略（V1.1 为 10px）
.bug-cluster__card-cause {
  font-size: var(--font-size-base);
  color: var(--color-neutral-500);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bug-cluster__card-side {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
  padding-top: 2px;
}

// 严重度内联分布点：●●●○，fatal/serious 语义色，空位灰点
.bug-cluster__card-dots {
  display: inline-flex;
  gap: 3px;
}

.bug-cluster__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.bug-cluster__dot--fatal {
  background: var(--color-bug-fatal);
}

.bug-cluster__dot--serious {
  background: var(--color-bug-serious);
}

.bug-cluster__dot--general {
  background: var(--color-bug-general);
}

.bug-cluster__dot--minor {
  background: var(--color-bug-minor);
}

.bug-cluster__dot--empty {
  background: var(--color-neutral-200);
}

// 缺陷数徽章：加粗数字
.bug-cluster__card-count {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-neutral-700);
}

.bug-cluster__card-toggle {
  font-size: var(--font-size-base);
  color: var(--color-neutral-400);
}

// 展开区：根因全文 + 缺陷清单
.bug-cluster__card-body {
  border-top: 1px solid var(--color-neutral-100);
  padding: var(--space-md);
  background: var(--color-neutral-50);
}

.bug-cluster__card-cause-full {
  font-size: var(--font-size-base);
  color: var(--color-neutral-600);
  line-height: 1.6;
  cursor: pointer;
  margin-bottom: var(--space-sm);
}

.bug-cluster__card-list {
  display: flex;
  flex-direction: column;
}

// 明细行：色点 + 标题（14px）+ 状态 + 短 ID + 查看（V1.1 为 10px 纯 ID）
.bug-cluster__card-bug {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px var(--space-sm);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-base);
  color: var(--color-neutral-700);
  cursor: pointer;
  transition: background-color 0.15s;

  &:hover {
    background: var(--color-neutral-100);
    color: var(--color-primary-600);
  }
}

.bug-cluster__card-bug-sev {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.bug-cluster__card-bug-sev--fatal {
  background: var(--color-bug-fatal);
}

.bug-cluster__card-bug-sev--serious {
  background: var(--color-bug-serious);
}

.bug-cluster__card-bug-sev--general {
  background: var(--color-bug-general);
}

.bug-cluster__card-bug-sev--minor {
  background: var(--color-bug-minor);
}

.bug-cluster__card-bug-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-cluster__card-bug-status {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  background: var(--color-neutral-100);
  border-radius: var(--radius-full);
  padding: 1px 8px;
}

.bug-cluster__card-bug-id {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.bug-cluster__none {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-400);
  margin-bottom: var(--space-md);
}

.bug-cluster__chart {
  border-top: 1px solid var(--color-neutral-100);
  padding-top: var(--space-md);
}

.bug-cluster__chart-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
  margin-bottom: var(--space-sm);
}

.bug-cluster__bar-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: 8px;
}

.bug-cluster__bar-name {
  flex-shrink: 0;
  width: 96px;
  font-size: var(--font-size-xs);
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
  width: 24px;
  text-align: right;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-700);
}

// 百分比标签：V1.2 新增，条形图比例可读
.bug-cluster__bar-percent {
  flex-shrink: 0;
  width: 36px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
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
  font-size: var(--font-size-xs);
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
