<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const navStore = useNavStore()

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

function goMyWorkspaces() {
  navStore.setMode('none')
  router.push('/workspaces')
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
        <el-tooltip content="我的空间" :show-after="1000">
          <div class="admin-layout__icon-btn" @click="goMyWorkspaces">
            <el-icon><FolderOpened /></el-icon>
            <span>我的空间</span>
          </div>
        </el-tooltip>

        <el-tooltip content="系统管理" :show-after="1000">
          <div class="admin-layout__icon-btn admin-layout__icon-btn--active">
            <el-icon><Monitor /></el-icon>
            <span>系统管理</span>
          </div>
        </el-tooltip>

        <el-tooltip content="消息通知" :show-after="1000">
          <div class="admin-layout__icon-btn">
            <el-badge :is-dot="true">
              <el-icon><Bell /></el-icon>
            </el-badge>
            <span>消息</span>
          </div>
        </el-tooltip>

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
          background-color="#1E293B"
          text-color="#CBD5E1"
          active-text-color="#3B82F6"
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

<style scoped lang="scss">
.admin-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.admin-layout__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  border-bottom: 1px solid var(--color-neutral-200);
  background: var(--color-neutral-0);
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
  gap: 8px;
}

.admin-layout__icon-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-neutral-600);
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover {
    color: var(--color-primary-500);
    background: var(--color-primary-50);
  }

  &--active {
    color: var(--color-primary-500);
    font-weight: 500;
    box-shadow: 0 0 6px rgba(59, 130, 246, 0.3);
  }
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
  width: var(--sidebar-width);
  background-color: var(--color-admin-sidebar-bg);
  flex-shrink: 0;
  overflow-y: auto;
}

.admin-layout__sidebar-menu {
  border-right: none;
}

.admin-layout__content {
  flex: 1;
  overflow: auto;
  background-color: var(--color-admin-bg);
  padding: 16px;
}
</style>
