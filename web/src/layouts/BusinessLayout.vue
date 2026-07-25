<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const navStore = useNavStore()

// 根据路由自动感知导航模式
watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/admin')) {
      navStore.setMode('admin')
    } else if (path.startsWith('/workspace/projects/')) {
      navStore.setMode('project')
    } else if (path.startsWith('/workspace')) {
      navStore.setMode('workspace')
    } else if (path === '/workspaces') {
      navStore.setMode('none')
    }
  },
  { immediate: true },
)

// 动态菜单激活项
const activeDynamicMenu = computed(() => {
  const p = route.path
  const items = navStore.dynamicMenuItems
  const match = items.find((item) => p.startsWith(item.path))
  return match?.path ?? ''
})

// 图标显隐逻辑（按全局导航设计文档 Section 3）
const showMyProject = computed(() => navStore.isProjectMode)
const showWorkspaceManage = computed(() => navStore.isWorkspaceMode || navStore.isProjectMode)
const isSystemActive = computed(() => navStore.isAdminMode)

function handleDynamicMenuClick(path: string) {
  router.push(path)
}

function goHome() {
  router.push('/workspaces')
  navStore.setMode('none')
}

function goMyWorkspaces() {
  router.push('/workspaces')
}

function goMyProjects() {
  router.push('/workspace/projects')
  navStore.setMode('workspace')
}

function goWorkspaceManage() {
  if (authStore.activeWorkspace?.id) {
    router.push(`/workspace/${authStore.activeWorkspace.id}`)
  }
  navStore.setMode('workspace')
}

function goSystemAdmin() {
  router.push('/admin/dashboard')
  navStore.setMode('admin')
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="business-layout">
    <header class="top-nav">
      <!-- Logo -->
      <div class="top-nav__logo" @click="goHome">
        <span class="top-nav__logo-text">RoboTest</span>
      </div>

      <!-- 动态菜单区 -->
      <nav class="top-nav__dynamic-menu">
        <template v-if="navStore.dynamicMenuItems.length">
          <div
            v-for="item in navStore.dynamicMenuItems"
            :key="item.path"
            class="top-nav__menu-item"
            :class="{ 'top-nav__menu-item--active': activeDynamicMenu === item.path }"
            @click="handleDynamicMenuClick(item.path)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </div>
        </template>
        <span v-else class="top-nav__menu-hint">请选择一个工作空间</span>
      </nav>

      <!-- 右侧图标组 -->
      <div class="top-nav__icons">
        <!-- 我的空间（始终显示） -->
        <el-tooltip content="我的空间" :show-after="1000">
          <div class="top-nav__icon-btn" @click="goMyWorkspaces">
            <el-icon><FolderOpened /></el-icon>
            <span>我的空间</span>
          </div>
        </el-tooltip>

        <!-- 我的项目（进入项目后显示） -->
        <el-tooltip v-if="showMyProject" content="我的项目" :show-after="1000">
          <div class="top-nav__icon-btn top-nav__icon-btn--active" @click="goMyProjects">
            <el-icon><Folder /></el-icon>
            <span>我的项目</span>
          </div>
        </el-tooltip>

        <!-- 空间管理（进入空间后显示） -->
        <el-tooltip v-if="showWorkspaceManage" content="空间管理" :show-after="1000">
          <div class="top-nav__icon-btn" @click="goWorkspaceManage">
            <el-icon><Setting /></el-icon>
            <span>空间管理</span>
          </div>
        </el-tooltip>

        <!-- 系统管理（始终显示） -->
        <el-tooltip content="系统管理" :show-after="1000">
          <div
            class="top-nav__icon-btn"
            :class="{ 'top-nav__icon-btn--active': isSystemActive }"
            @click="goSystemAdmin"
          >
            <el-icon><Monitor /></el-icon>
            <span>系统管理</span>
          </div>
        </el-tooltip>

        <!-- 消息通知（始终显示） -->
        <el-tooltip content="消息通知" :show-after="1000">
          <div class="top-nav__icon-btn">
            <el-badge :is-dot="true">
              <el-icon><Bell /></el-icon>
            </el-badge>
            <span>消息</span>
          </div>
        </el-tooltip>

        <!-- 用户头像 -->
        <el-dropdown trigger="click" @command="(cmd: string) => (cmd === 'logout' ? handleLogout() : null)">
          <div class="top-nav__user">
            <el-avatar :size="28" :src="authStore.avatarUrl || undefined">
              {{ authStore.username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <span class="top-nav__username">{{ authStore.username }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人资料</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 内容区 -->
    <main class="business-layout__content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped lang="scss">
.business-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.top-nav {
  display: flex;
  align-items: center;
  height: var(--header-height);
  border-bottom: 1px solid var(--color-neutral-200);
  background: var(--color-neutral-0);
  padding: 0 16px;
  z-index: 100;
  flex-shrink: 0;
  gap: 8px;
}

.top-nav__logo {
  cursor: pointer;
  margin-right: 16px;
  flex-shrink: 0;
}

.top-nav__logo-text {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-primary-500);
}

.top-nav__dynamic-menu {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.top-nav__menu-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 14px;
  color: var(--color-neutral-700);
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover {
    background: var(--color-primary-50);
    color: var(--color-primary-500);
  }

  &--active {
    background: var(--color-primary-50);
    color: var(--color-primary-500);
    font-weight: 500;
    position: relative;

    &::after {
      content: '';
      position: absolute;
      bottom: -7px;
      left: 50%;
      transform: translateX(-50%);
      width: 60%;
      height: 2px;
      background: var(--color-primary-500);
      border-radius: 1px;
    }
  }
}

.top-nav__menu-hint {
  font-size: 13px;
  color: var(--color-neutral-400);
}

.top-nav__icons {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.top-nav__icon-btn {
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

.top-nav__user {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background: var(--color-neutral-100);
  }
}

.top-nav__username {
  font-size: 13px;
  color: var(--color-neutral-700);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.business-layout__content {
  flex: 1;
  overflow: auto;
  background-color: var(--color-neutral-50);
}
</style>
