<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchDashboard } from '@/services/project'
import type { ProjectActivity, ProjectDashboard } from '@/types'
import { formatDate, formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const data = ref<ProjectDashboard | null>(null)

async function load() {
  loading.value = true
  try {
    data.value = await fetchDashboard()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载工作台数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

const statCards = [
  {
    key: 'cases',
    label: '用例总数',
    icon: 'Document',
    to: '/workspace/projects/functional-testing',
    colorClass: 'stat-card--primary',
    valueKey: 'caseCount' as const,
  },
  {
    key: 'reviews',
    label: '进行中评审',
    icon: 'Checked',
    to: '/workspace/projects/functional-testing?tab=reviews',
    colorClass: 'stat-card--warning',
    valueKey: 'activeReviewCount' as const,
  },
  {
    key: 'plans',
    label: '进行中计划',
    icon: 'Calendar',
    to: '/workspace/projects/functional-testing?tab=plans',
    colorClass: 'stat-card--warning',
    valueKey: 'activePlanCount' as const,
  },
  {
    key: 'bugs',
    label: '未关闭缺陷',
    icon: 'WarningFilled',
    to: '/workspace/projects/bugs',
    colorClass: 'stat-card--danger',
    valueKey: 'openBugCount' as const,
  },
]

const quickEntries = [
  {
    key: 'cases',
    label: '编写测试用例',
    description: '管理项目测试用例',
    icon: 'EditPen',
    to: '/workspace/projects/functional-testing',
  },
  {
    key: 'plans',
    label: '新建测试计划',
    description: '安排测试执行任务',
    icon: 'Calendar',
    to: '/workspace/projects/functional-testing?tab=plans',
  },
  {
    key: 'bugs',
    label: '提交缺陷',
    description: '记录和跟踪问题',
    icon: 'CirclePlus',
    to: '/workspace/projects/bugs/create',
  },
  {
    key: 'api',
    label: '接口调试',
    description: '快速验证接口请求',
    icon: 'Connection',
    to: '/workspace/projects/api-testing',
  },
]

const projectStatusLabel = computed(() =>
  data.value?.projectStatus === 'archived' ? '已归档' : '活跃',
)
const projectPeriod = computed(() => {
  const startTime = formatDate(data.value?.startTime)
  const endTime = formatDate(data.value?.endTime)
  if (startTime === '-' && endTime === '-') return '未设置起止时间'
  return `${startTime} ~ ${endTime}`
})

function activityTarget(activity: ProjectActivity): string | null {
  switch (activity.resourceType) {
    case 'TEST_REVIEW':
      return `/workspace/projects/reviews/${activity.resourceId}`
    case 'TEST_PLAN':
      return `/workspace/projects/plans/${activity.resourceId}`
    case 'BUG':
      return `/workspace/projects/bugs/${activity.resourceId}`
    case 'TEST_CASE_DOCUMENT':
    case 'TEST_CASE_NODE':
      return '/workspace/projects/functional-testing'
    default:
      return null
  }
}

function openActivity(activity: ProjectActivity) {
  const target = activityTarget(activity)
  if (target) {
    router.push(target)
  }
}
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <header class="dashboard__header">
      <div>
        <h1 class="dashboard__title">项目工作台</h1>
        <div class="dashboard__meta">
          <span class="dashboard__project">
            <el-icon><FolderOpened /></el-icon>
            {{ data?.projectName || '当前项目' }}
          </span>
          <el-tag
            :type="data?.projectStatus === 'archived' ? 'info' : 'success'"
            size="small"
            effect="light"
          >
            {{ projectStatusLabel }}
          </el-tag>
          <span class="dashboard__period">{{ projectPeriod }}</span>
        </div>
      </div>
    </header>

    <el-row :gutter="16" class="dashboard__overview">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="overview-card quick-overview">
          <template #header>
            <div class="panel__header">
              <span id="quick-entry-title" class="panel__title">快捷入口</span>
              <span class="panel__hint">快速进入常用功能</span>
            </div>
          </template>
          <div class="quick-grid">
            <button
              v-for="entry in quickEntries"
              :key="entry.key"
              type="button"
              class="quick-card"
              @click="router.push(entry.to)"
            >
              <span class="quick-card__icon">
                <el-icon :size="18"><component :is="entry.icon" /></el-icon>
              </span>
              <span class="quick-card__content">
                <span class="quick-card__title">{{ entry.label }}</span>
                <span class="quick-card__description">{{ entry.description }}</span>
              </span>
              <el-icon class="quick-card__arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="overview-card stats-overview">
          <template #header>
            <div class="panel__header">
              <span class="panel__title">项目统计</span>
              <span class="panel__hint">当前项目数据概览</span>
            </div>
          </template>
          <div class="stats-grid">
            <div
              v-for="s in statCards"
              :key="s.key"
              class="stat-card"
              :class="s.colorClass"
              @click="router.push(s.to)"
            >
              <div class="stat-card__icon">
                <el-icon :size="20"><component :is="s.icon" /></el-icon>
              </div>
              <div class="stat-card__info">
                <div class="stat-card__label">{{ s.label }}</div>
                <div class="stat-card__value">{{ data?.[s.valueKey] ?? 0 }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card
      shadow="never"
      class="panel dashboard__activity"
      aria-labelledby="recent-activity-title"
    >
      <template #header>
        <div class="panel__header">
          <span id="recent-activity-title" class="panel__title">最近动态</span>
          <span class="panel__hint">记录项目关键操作</span>
        </div>
      </template>
      <el-empty
        v-if="!data?.recentActivities?.length"
        description="暂无项目动态"
        :image-size="60"
      />
      <el-timeline v-else class="activity-timeline">
        <el-timeline-item
          v-for="activity in data.recentActivities"
          :key="activity.id"
          :timestamp="formatDateTime(activity.occurredAt)"
          placement="top"
        >
          <div class="activity-item">
            <div class="activity-item__content">
              <div class="activity-item__summary">{{ activity.summary }}</div>
              <div class="activity-item__meta">
                <span>{{ activity.actorName }}</span>
                <span class="activity-item__separator">·</span>
                <span>{{ activity.resourceName }}</span>
              </div>
            </div>
            <el-button
              v-if="activityTarget(activity)"
              link
              type="primary"
              size="small"
              @click="openActivity(activity)"
            >
              查看
            </el-button>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-row :gutter="16" class="dashboard__panels">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel">
          <template #header><span class="panel__title">最近评审</span></template>
          <el-empty v-if="!data?.recentReviews?.length" description="暂无评审" :image-size="60" />
          <ul v-else class="panel__list">
            <li
              v-for="item in data.recentReviews"
              :key="item.id"
              class="panel__item panel__item--link"
              @click="router.push(`/workspace/projects/reviews/${item.id}`)"
            >
              <span class="panel__item-name">{{ item.title }}</span>
              <span class="panel__item-meta">
                <el-tag
                  :type="item.status === 'completed' ? 'success' : 'warning'"
                  size="small"
                  effect="light"
                  round
                >
                  {{ item.status === 'completed' ? '已完成' : '评审中' }}
                </el-tag>
                <span class="panel__item-date">{{ formatDateTime(item.createdAt) }}</span>
              </span>
            </li>
          </ul>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel">
          <template #header><span class="panel__title">最近计划</span></template>
          <el-empty v-if="!data?.recentPlans?.length" description="暂无计划" :image-size="60" />
          <ul v-else class="panel__list">
            <li
              v-for="item in data.recentPlans"
              :key="item.id"
              class="panel__item panel__item--link"
              @click="router.push(`/workspace/projects/plans/${item.id}`)"
            >
              <span class="panel__item-name">{{ item.title }}</span>
              <span class="panel__item-meta">
                <el-tag size="small" effect="light" round>{{ item.status }}</el-tag>
                <span class="panel__item-date">{{ formatDateTime(item.createdAt) }}</span>
              </span>
            </li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.dashboard__header {
  margin-bottom: var(--space-xl);
}

.dashboard__title {
  margin: 0 0 var(--space-sm);
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 700;
}

.dashboard__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.dashboard__project,
.dashboard__period {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
}

.dashboard__project {
  color: var(--color-neutral-700);
  font-weight: 600;
}

.dashboard__overview {
  align-items: stretch;
  margin-bottom: var(--space-xl);
}

.dashboard__overview :deep(.el-col) {
  display: flex;
}

.overview-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

.overview-card :deep(.el-card__body) {
  flex: 1;
  padding: var(--space-lg);
}

.panel__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-md);
}

.panel__title {
  margin: 0;
  color: var(--color-neutral-800);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.panel__hint {
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.quick-grid,
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-md);
  height: 100%;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  min-width: 0;
  min-height: 92px;
  height: 100%;
  padding: var(--space-md) var(--space-lg);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  color: var(--color-neutral-800);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    transform var(--transition-fast);
}

.quick-card:hover {
  border-color: var(--color-primary-300);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.quick-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.quick-card__content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.quick-card__title {
  overflow: hidden;
  font-size: var(--font-size-sm);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-card__description {
  overflow: hidden;
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-card__arrow {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--color-neutral-400);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  min-height: 92px;
  height: 100%;
  padding: var(--space-md) var(--space-lg);
  background: var(--color-neutral-0);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-neutral-200);
  cursor: pointer;
  transition: all var(--transition-base);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: var(--radius-lg);
}

.stat-card--primary .stat-card__icon {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}
.stat-card--primary .stat-card__value {
  color: var(--color-primary-600);
}
.stat-card--warning .stat-card__icon {
  background: var(--color-warning-light);
  color: var(--color-warning-strong);
}
.stat-card--warning .stat-card__value {
  color: var(--color-warning-strong);
}
.stat-card--danger .stat-card__icon {
  background: var(--color-danger-light);
  color: var(--color-danger-strong);
}
.stat-card--danger .stat-card__value {
  color: var(--color-danger-strong);
}

.stat-card__info {
  min-width: 0;
}

.stat-card__label {
  margin-bottom: 2px;
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.stat-card__value {
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 700;
  letter-spacing: -0.02em;
}

.dashboard__activity {
  margin-bottom: var(--space-xl);
}

.activity-timeline {
  padding: var(--space-sm) 0 0;
}

.activity-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.activity-item__content {
  min-width: 0;
}

.activity-item__summary {
  overflow: hidden;
  color: var(--color-neutral-800);
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-item__meta {
  display: flex;
  gap: var(--space-xs);
  margin-top: var(--space-xs);
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.activity-item__separator {
  color: var(--color-neutral-300);
}

.dashboard__panels {
  margin-bottom: var(--space-xl);
}

.panel__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.panel__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast);
}

.panel__item:last-child {
  border-bottom: none;
}

.panel__item--link {
  cursor: pointer;
}

.panel__item--link:hover {
  background-color: var(--color-neutral-50);
}

.panel__item-name {
  overflow: hidden;
  color: var(--color-neutral-800);
  font-size: var(--font-size-sm);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel__item-meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.panel__item-date {
  color: var(--color-neutral-400);
  font-size: var(--font-size-2xs);
}

@media (max-width: 960px) {
  .quick-grid,
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .quick-grid,
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .dashboard__period {
    width: 100%;
  }
}
</style>
