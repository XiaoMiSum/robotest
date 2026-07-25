<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchRoleTree, fetchUsers, fetchWorkspaces } from '@/services/admin'
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
      fetchRoleTree(),
    ])
    userTotal.value = userPage.total
    recentUsers.value = userPage.list
    disabledUserTotal.value = disabledPage.total
    workspaceTotal.value = workspacePage.total
    workspaceOverview.value = workspacePage.list
    // 角色树根节点为类型分组，系统角色数量取 system 分组的子节点数
    const systemGroup = roleTree.find((node) => node.type === 'system')
    systemRoleTotal.value = systemGroup?.children?.length ?? 0
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载仪表盘数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <h2 class="dashboard__title">仪表盘</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="dashboard__stats">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/admin/users')">
          <div class="stat-card__label">用户总数</div>
          <div class="stat-card__value">{{ userTotal }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/admin/workspaces')">
          <div class="stat-card__label">工作空间</div>
          <div class="stat-card__value">{{ workspaceTotal }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/admin/roles')">
          <div class="stat-card__label">系统角色</div>
          <div class="stat-card__value">{{ systemRoleTotal }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/admin/users')">
          <div class="stat-card__label">禁用用户</div>
          <div class="stat-card__value stat-card__value--danger">{{ disabledUserTotal }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 概览面板 -->
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
              <span class="panel__item-meta"
                >{{ ws.memberCount }} 人 · {{ ws.projectCount }} 项目</span
              >
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
  gap: 10px;
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
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
