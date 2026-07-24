<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

/** Left sidebar menu items for admin mode */
const sidebarMenu = [
  { label: '仪表盘', path: '/admin/dashboard', icon: 'Odometer' },
  { label: '用户管理', path: '/admin/users', icon: 'User' },
  { label: '工作空间管理', path: '/admin/workspaces', icon: 'OfficeBuilding' },
  { label: '角色管理', path: '/admin/roles', icon: 'Lock' },
]

const activeSidebarPath = computed(() => route.path)

function handleSidebarSelect(index: string) {
  router.push(index)
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin-layout">
    <!-- 48px top nav bar (shared height with sidebar) -->
    <header class="admin-layout__topbar">
      <div class="admin-layout__topbar-left">
        <span class="admin-layout__logo">RoboTest</span>
        <el-tag type="danger" size="small" effect="dark" class="admin-layout__mode-tag"
          >系统管理</el-tag
        >
      </div>

      <div class="admin-layout__topbar-right">
        <el-dropdown
          trigger="click"
          @command="(cmd: string) => (cmd === 'logout' ? handleLogout() : null)"
        >
          <span class="admin-layout__user">
            <el-avatar :size="28" :src="authStore.avatarUrl || undefined">
              {{ authStore.username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <span class="admin-layout__username">{{ authStore.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- Body: sidebar + content -->
    <div class="admin-layout__body">
      <!-- 240px dark sidebar -->
      <aside class="admin-layout__sidebar">
        <el-menu
          :default-active="activeSidebarPath"
          background-color="#1d1e2c"
          text-color="rgba(255, 255, 255, 0.7)"
          active-text-color="#409eff"
          class="admin-layout__sidebar-menu"
          @select="handleSidebarSelect"
        >
          <el-menu-item v-for="item in sidebarMenu" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </aside>

      <!-- Main content -->
      <main class="admin-layout__content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.admin-layout__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #fff;
  padding: 0 16px;
  z-index: 100;
  flex-shrink: 0;
}

.admin-layout__topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-layout__logo {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.admin-layout__mode-tag {
  font-size: 11px;
}

.admin-layout__topbar-right {
  display: flex;
  align-items: center;
}

.admin-layout__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.admin-layout__user:hover {
  background-color: var(--el-fill-color-light);
}

.admin-layout__username {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.admin-layout__body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.admin-layout__sidebar {
  width: 240px;
  background-color: #1d1e2c;
  flex-shrink: 0;
  overflow-y: auto;
}

.admin-layout__sidebar-menu {
  border-right: none;
}

.admin-layout__content {
  flex: 1;
  overflow: auto;
  background-color: var(--el-fill-color-lighter);
  padding: 16px;
}
</style>
