<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchDashboard } from '@/services/project'
import type { ProjectDashboard } from '@/types'
import { formatDateTime } from '@/utils/format'

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
  { key: 'cases', label: '用例总数', icon: 'Document', to: '/workspace/projects/cases', colorClass: 'stat-card--primary', valueKey: 'caseCount' as const },
  { key: 'reviews', label: '进行中评审', icon: 'Checked', to: '/workspace/projects/reviews', colorClass: 'stat-card--blue', valueKey: 'activeReviewCount' as const },
  { key: 'plans', label: '进行中计划', icon: 'Calendar', to: '/workspace/projects/plans', colorClass: 'stat-card--teal', valueKey: 'activePlanCount' as const },
  { key: 'bugs', label: '未关闭缺陷', icon: 'WarningFilled', to: '/workspace/projects/bugs', colorClass: 'stat-card--danger', valueKey: 'openBugCount' as const },
]
</script>

<template>
  <div v-loading="loading" class="dashboard">

    <el-row :gutter="16" class="dashboard__stats">
      <el-col v-for="s in statCards" :key="s.key" :xs="12" :sm="6">
        <div class="stat-card" :class="s.colorClass" @click="router.push(s.to)">
          <div class="stat-card__icon">
            <el-icon :size="20"><component :is="s.icon" /></el-icon>
          </div>
          <div class="stat-card__info">
            <div class="stat-card__label">{{ s.label }}</div>
            <div class="stat-card__value">{{ data?.[s.valueKey] ?? 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

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
                <el-tag :type="item.status === 'completed' ? 'success' : 'warning'" size="small" effect="light" round>
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
.dashboard__stats {
  margin-bottom: var(--space-xl);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  padding: var(--space-lg) var(--space-xl);
  background: var(--color-neutral-0);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-neutral-200);
  cursor: pointer;
  transition: all var(--transition-base);
  margin-bottom: var(--space-md);

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
  }
}

.stat-card__icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card--primary .stat-card__icon { background: var(--color-primary-50); color: var(--color-primary-600); }
.stat-card--primary .stat-card__value { color: var(--color-primary-600); }
.stat-card--blue .stat-card__icon { background: #eff6ff; color: #2563eb; }
.stat-card--blue .stat-card__value { color: #2563eb; }
.stat-card--teal .stat-card__icon { background: #f0fdfa; color: #0d9488; }
.stat-card--teal .stat-card__value { color: #0d9488; }
.stat-card--danger .stat-card__icon { background: var(--color-danger-50); color: var(--color-danger-600); }
.stat-card--danger .stat-card__value { color: var(--color-danger-600); }

.stat-card__label {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  font-weight: 500;
  margin-bottom: 2px;
}

.stat-card__value {
  font-size: var(--font-size-2xl);
  font-weight: 700;
  letter-spacing: -0.02em;
}

.panel__title {
  font-weight: 600;
  font-size: var(--font-size-sm);
}

.panel__list {
  list-style: none;
  margin: 0;
  padding: 0;
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

  &:hover {
    background-color: var(--color-neutral-50);
  }
}

.panel__item-name {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
  font-weight: 500;
}

.panel__item-meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.panel__item-date {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}
</style>
