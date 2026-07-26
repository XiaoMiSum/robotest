<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchRoleList, fetchUsers, fetchWorkspaces } from '@/services/admin'
import type { AdminUser, AdminWorkspace } from '@/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const userTotal = ref(0)
const workspaceTotal = ref(0)
const systemRoleTotal = ref(0)
const disabledUserTotal = ref(0)
const recentUsers = ref<AdminUser[]>([])
const workspaceOverview = ref<AdminWorkspace[]>([])

async function loadDashboard() {
  loading.value = true
  try {
    const [userPage, disabledPage, workspacePage, roleTree] = await Promise.all([
      fetchUsers({ pageNo: 1, pageSize: 5 }),
      fetchUsers({ status: 'disabled', pageNo: 1, pageSize: 1 }),
      fetchWorkspaces({ pageNo: 1, pageSize: 5 }),
      fetchRoleList(),
    ])
    userTotal.value = userPage.total
    recentUsers.value = userPage.list
    disabledUserTotal.value = disabledPage.total
    workspaceTotal.value = workspacePage.total
    workspaceOverview.value = workspacePage.list
    const systemGroup = roleTree.find((node) => node.type === 'system')
    systemRoleTotal.value = systemGroup?.children?.length ?? 0
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载仪表盘数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)

const stats = [
  { key: 'users', label: '用户总数', icon: 'User', to: '/admin/users', value: userTotal, colorClass: 'stat-card--primary' },
  { key: 'workspaces', label: '工作空间', icon: 'OfficeBuilding', to: '/admin/workspaces', value: workspaceTotal, colorClass: 'stat-card--blue' },
  { key: 'roles', label: '系统角色', icon: 'Lock', to: '/admin/roles', value: systemRoleTotal, colorClass: 'stat-card--teal' },
  { key: 'disabled', label: '禁用用户', icon: 'WarningFilled', to: '/admin/users', value: disabledUserTotal, colorClass: 'stat-card--danger' },
]
</script>

<template>
  <div v-loading="loading" class="dashboard">

    <el-row :gutter="16" class="dashboard__stats">
      <el-col v-for="s in stats" :key="s.key" :xs="12" :sm="6">
        <div class="stat-card" :class="s.colorClass" @click="router.push(s.to)">
          <div class="stat-card__icon">
            <el-icon :size="22"><component :is="s.icon" /></el-icon>
          </div>
          <div class="stat-card__info">
            <div class="stat-card__label">{{ s.label }}</div>
            <div class="stat-card__value">{{ s.value }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="dashboard__panels">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel">
          <template #header>
            <span class="panel__title">最近新增用户</span>
          </template>
          <el-empty v-if="!recentUsers.length" description="暂无用户" :image-size="80" />
          <ul v-else class="panel__list">
            <li
              v-for="user in recentUsers"
              :key="user.id"
              class="panel__item panel__item--link"
              @click="router.push(`/admin/users/${user.id}`)"
            >
              <el-avatar :size="28" :src="user.avatarUrl || undefined">
                {{ user.username.charAt(0).toUpperCase() }}
              </el-avatar>
              <span class="panel__item-name">{{ user.username }}</span>
              <span class="panel__item-meta">{{ formatDateTime(user.createdAt) }}</span>
            </li>
          </ul>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel">
          <template #header>
            <span class="panel__title">工作空间概览</span>
          </template>
          <el-empty v-if="!workspaceOverview.length" description="暂无工作空间" :image-size="80" />
          <ul v-else class="panel__list">
            <li
              v-for="ws in workspaceOverview"
              :key="ws.id"
              class="panel__item panel__item--link"
              @click="router.push(`/admin/workspaces/${ws.id}`)"
            >
              <span class="panel__item-name">{{ ws.name }}</span>
              <span class="panel__item-meta">{{ ws.memberCount }} 人 · {{ ws.projectCount }} 项目</span>
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
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card--primary .stat-card__icon {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}
.stat-card--primary .stat-card__value { color: var(--color-primary-600); }

.stat-card--blue .stat-card__icon {
  background: #eff6ff;
  color: #2563eb;
}
.stat-card--blue .stat-card__value { color: #2563eb; }

.stat-card--teal .stat-card__icon {
  background: #f0fdfa;
  color: #0d9488;
}
.stat-card--teal .stat-card__value { color: #0d9488; }

.stat-card--danger .stat-card__icon {
  background: var(--color-danger-50);
  color: var(--color-danger-600);
}
.stat-card--danger .stat-card__value { color: var(--color-danger-600); }

.stat-card__label {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  font-weight: 500;
  margin-bottom: 2px;
}

.stat-card__value {
  font-size: var(--font-size-3xl);
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
  gap: var(--space-md);
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
  margin-left: auto;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}
</style>
