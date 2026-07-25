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
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <h2 class="dashboard__title">项目工作台</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="dashboard__stats">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/workspace/projects/cases')">
          <div class="stat-card__label">用例总数</div>
          <div class="stat-card__value">{{ data?.caseCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/workspace/projects/reviews')">
          <div class="stat-card__label">进行中评审</div>
          <div class="stat-card__value">{{ data?.activeReviewCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/workspace/projects/plans')">
          <div class="stat-card__label">进行中计划</div>
          <div class="stat-card__value">{{ data?.activePlanCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/workspace/projects/bugs')">
          <div class="stat-card__label">未关闭缺陷</div>
          <div class="stat-card__value stat-card__value--danger">{{ data?.openBugCount ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近活动 -->
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
                <el-tag :type="item.status === 'completed' ? 'success' : 'warning'" size="small">
                  {{ item.status === 'completed' ? '已完成' : '评审中' }}
                </el-tag>
                {{ formatDateTime(item.createdAt) }}
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
                <el-tag size="small">{{ item.status }}</el-tag>
                {{ formatDateTime(item.createdAt) }}
              </span>
            </li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.dashboard__title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 16px;
}

.dashboard__stats {
  margin-bottom: 16px;
}

.stat-card {
  cursor: pointer;
  transition: transform 0.15s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-card__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.stat-card__value {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.stat-card__value--danger {
  color: var(--el-color-danger);
}

.panel__title {
  font-weight: 600;
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
  padding: 10px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel__item:last-child {
  border-bottom: none;
}

.panel__item--link {
  cursor: pointer;
  border-radius: 4px;
}

.panel__item--link:hover {
  background-color: var(--el-fill-color-light);
}

.panel__item-name {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.panel__item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
