<script setup lang="ts">
import { MagicStick } from '@element-plus/icons-vue'
import { useBugCluster } from '@/composables/useBugCluster'
import { formatDateTime, formatShortId } from '@/utils/format'
import BugClusterChart from './BugClusterChart.vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const {
  drawerVisible,
  task,
  starting,
  expandedCluster,
  expandedCause,
  snapshot,
  running,
  semanticDegraded,
  summary,
  moduleBars,
  severitySegments,
  severityDots,
  start,
  cancel,
  retry,
  goDetail,
  STATUS_LABEL,
} = useBugCluster({ modelValue: props.modelValue, emit })
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    size="560px"
    :modal="false"
    modal-penetrable
    :lock-scroll="false"
    class="bug-cluster"
  >
    <template #header>
      <div class="bug-cluster__header">
        <span class="bug-cluster__title"><el-icon><MagicStick /></el-icon> AI 分析</span>
        <span v-if="snapshot" class="bug-cluster__generated">
          生成时间：{{ formatDateTime(snapshot.generatedAt) }}
        </span>
        <div class="bug-cluster__header-actions">
          <el-button link type="primary" :disabled="running || starting" @click="start">
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

        <div v-if="running" class="bug-cluster__running">
          <el-progress :percentage="task?.progress ?? 0" :stroke-width="10" />
          <div class="bug-cluster__progress-text">聚类分析中…</div>
          <el-button size="small" @click="cancel">取消任务</el-button>
        </div>

        <el-empty
          v-else-if="!task"
          description="暂无聚类结果，点击下方按钮开始分析"
          :image-size="72"
        >
          <el-button type="primary" :loading="starting" @click="start">开始分析</el-button>
        </el-empty>

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
            <div class="bug-cluster__summary">
              共 {{ summary.bugs }} 条缺陷 · {{ summary.clusters }} 个聚类主题 · {{ summary.unclustered }} 条未聚类
            </div>

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

            <BugClusterChart v-if="moduleBars.length" :module-bars="moduleBars" :severity-segments="severitySegments" />
          </template>
        </template>
      </el-card>
  </el-drawer>
</template>

<style scoped lang="scss">
.bug-cluster__card { box-shadow: none; }

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
  margin-right: var(--space-lg);
  display: flex;
  gap: var(--space-sm);
}

.bug-cluster__degraded { margin-bottom: var(--space-md); }

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

.bug-cluster__summary {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  margin-bottom: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  background: var(--color-neutral-50);
  border-radius: var(--radius-md);
}

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
  padding: var(--space-md);
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover { background-color: var(--color-neutral-50); }
}

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

.bug-cluster__card-main { flex: 1; min-width: 0; }

.bug-cluster__card-label {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-neutral-800);
  margin-bottom: 4px;
  line-height: 1.4;
}

.bug-cluster__label-fallback {
  margin-right: 6px;
  vertical-align: 2px;
}

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

.bug-cluster__card-dots { display: inline-flex; gap: 3px; }

.bug-cluster__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.bug-cluster__dot--fatal { background: var(--color-bug-fatal); }
.bug-cluster__dot--serious { background: var(--color-bug-serious); }
.bug-cluster__dot--general { background: var(--color-bug-general); }
.bug-cluster__dot--minor { background: var(--color-bug-minor); }
.bug-cluster__dot--empty { background: var(--color-neutral-200); }

.bug-cluster__card-count {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-neutral-700);
}

.bug-cluster__card-toggle {
  font-size: var(--font-size-base);
  color: var(--color-neutral-400);
}

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

.bug-cluster__card-list { display: flex; flex-direction: column; }

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

.bug-cluster__card-bug-sev--fatal { background: var(--color-bug-fatal); }
.bug-cluster__card-bug-sev--serious { background: var(--color-bug-serious); }
.bug-cluster__card-bug-sev--general { background: var(--color-bug-general); }
.bug-cluster__card-bug-sev--minor { background: var(--color-bug-minor); }

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
</style>
