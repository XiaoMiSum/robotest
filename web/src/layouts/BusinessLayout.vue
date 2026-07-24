<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

/** Determine which fixed menu is active based on route path */
const activeTopMenuIndex = computed(() => {
  const p = route.path
  if (p.startsWith('/admin')) return 'system'
  if (p.startsWith('/workspace')) return 'workspace'
  if (p.startsWith('/workspaces')) return 'workspaces'
  return ''
})

function handleTopMenuSelect(index: string) {
  switch (index) {
    case 'workspaces':
      router.push('/workspaces')
      break
    case 'workspace':
      router.push('/workspace/info')
      break
    case 'system':
      router.push('/admin/dashboard')
      break
  }
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

function goToProfile() {
  // TODO: implement user profile page
}
</script>

<template>
  <div class="business-layout">
    <!-- 48px fixed top nav bar -->
    <header class="top-nav">
      <!-- Left: Logo -->
      <div class="top-nav__left">
        <span class="top-nav__logo">RoboTest</span>
      </div>

      <!-- Center: Dynamic menu area -->
      <el-menu
        mode="horizontal"
        :default-active="activeTopMenuIndex"
        class="top-nav__menu"
        @select="handleTopMenuSelect"
      >
        <el-menu-item index="workspaces">
          <el-icon><FolderOpened /></el-icon>
          <span>我的空间</span>
        </el-menu-item>
        <el-menu-item index="workspace">
          <el-icon><Setting /></el-icon>
          <span>空间管理</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.hasSystemRole" index="system">
          <el-icon><Monitor /></el-icon>
          <span>系统管理</span>
        </el-menu-item>
      </el-menu>

      <!-- Right: User dropdown -->
      <div class="top-nav__right">
        <el-dropdown
          trigger="click"
          @command="(cmd: string) => (cmd === 'logout' ? handleLogout() : goToProfile())"
        >
          <span class="top-nav__user">
            <el-avatar :size="28" :src="authStore.avatarUrl || undefined">
              {{ authStore.username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <span class="top-nav__username">{{ authStore.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人资料</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- Content area -->
    <main class="business-layout__content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.business-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.top-nav {
  display: flex;
  align-items: center;
  height: 48px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #fff;
  padding: 0 16px;
  z-index: 100;
  flex-shrink: 0;
}

.top-nav__left {
  display: flex;
  align-items: center;
  margin-right: 24px;
}

.top-nav__logo {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-primary);
  white-space: nowrap;
}

.top-nav__menu {
  flex: 1;
  border-bottom: none !important;
}

.top-nav__right {
  display: flex;
  align-items: center;
  margin-left: 16px;
}

.top-nav__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.top-nav__user:hover {
  background-color: var(--el-fill-color-light);
}

.top-nav__username {
  font-size: 14px;
  color: var(--el-text-color-primary);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.business-layout__content {
  flex: 1;
  overflow: auto;
  background-color: var(--el-fill-color-lighter);
}
</style>
