<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const navStore = useNavStore()

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

const activeDynamicMenu = computed(() => {
  const p = route.path
  const items = navStore.dynamicMenuItems
  const match = items.find((item) => p.startsWith(item.path))
  return match?.path ?? ''
})

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
      <div class="top-nav__logo" @click="goHome">
        <span class="top-nav__logo-text">RoboTest</span>
      </div>

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

      <div class="top-nav__icons">
        <div class="top-nav__icon-btn" @click="goHome">
          <el-icon><FolderOpened /></el-icon>
          <span>我的空间</span>
        </div>

        <div v-if="showMyProject" class="top-nav__icon-btn top-nav__icon-btn--active" @click="goMyProjects">
          <el-icon><Folder /></el-icon>
          <span>我的项目</span>
        </div>

        <div v-if="showWorkspaceManage" class="top-nav__icon-btn" @click="goWorkspaceManage">
          <el-icon><Setting /></el-icon>
          <span>空间管理</span>
        </div>

        <div
          class="top-nav__icon-btn"
          :class="{ 'top-nav__icon-btn--active': isSystemActive }"
          @click="goSystemAdmin"
        >
          <el-icon><Monitor /></el-icon>
          <span>系统管理</span>
        </div>

        <div class="top-nav__icon-btn">
          <el-badge :is-dot="true">
            <el-icon><Bell /></el-icon>
          </el-badge>
          <span>消息中心</span>
        </div>

        <el-divider direction="vertical" />

        <el-dropdown trigger="click" @command="(cmd: string) => (cmd === 'logout' ? handleLogout() : null)">
          <div class="top-nav__user">
            <el-avatar :size="30" :src="authStore.avatarUrl || undefined">
              {{ authStore.username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <span class="top-nav__username">{{ authStore.username }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

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
  background: linear-gradient(90deg, var(--color-neutral-800) 0%, var(--color-neutral-900) 100%);
  padding: 0 20px;
  z-index: 100;
  flex-shrink: 0;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.top-nav__logo {
  cursor: pointer;
  margin-right: 12px;
  flex-shrink: 0;
}

.top-nav__logo-text {
  font-size: 17px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: -0.02em;
}

.top-nav__dynamic-menu {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.top-nav__menu-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
  user-select: none;

  &:hover {
    background: rgba(255, 255, 255, 0.08);
    color: #ffffff;
  }

  &--active {
    background: rgba(255, 255, 255, 0.1);
    color: #ffffff;
    font-weight: 600;
    position: relative;

    &::after {
      content: '';
      position: absolute;
      bottom: -10px;
      left: 50%;
      transform: translateX(-50%);
      width: 60%;
      height: 2px;
      background: var(--color-primary-400);
      border-radius: 1px;
    }
  }
}

.top-nav__menu-hint {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
}

.top-nav__icons {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.top-nav__icon-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border-radius: var(--radius-md);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
  user-select: none;

  &:hover {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.08);
  }

  &--active {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.1);
    font-weight: 500;
  }
}

.top-nav__user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast);

  &:hover {
    background: rgba(255, 255, 255, 0.08);
  }
}

.top-nav__username {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.business-layout__content {
  flex: 1;
  overflow: auto;
  background-color: var(--color-neutral-50);
  padding: var(--space-xl);
}
</style>
