<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'
import ChangePasswordDialog from '@/components/common/ChangePasswordDialog.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const navStore = useNavStore()

const sidebarMenu = computed(() => {
  const has = (code: string) => authStore.hasPermission(code)
  const items: Array<{ label: string; path: string; icon: string }> = []
  items.push({ label: '数据概览', path: '/admin/dashboard', icon: 'Odometer' })
  if (has('user:view')) items.push({ label: '用户管理', path: '/admin/users', icon: 'User' })
  if (has('workspace:view')) items.push({ label: '空间管理', path: '/admin/workspaces', icon: 'OfficeBuilding' })
  if (has('role:view')) items.push({ label: '角色管理', path: '/admin/roles', icon: 'Lock' })
  return items
})

const activeSidebarPath = computed(() => route.path)

function handleSidebarSelect(index: string) {
  router.push(index)
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

const pwdDialogVisible = ref(false)

function handleUserCommand(cmd: string) {
  if (cmd === 'logout') {
    handleLogout()
  } else if (cmd === 'change-password') {
    pwdDialogVisible.value = true
  }
}

function goMyWorkspaces() {
  navStore.setMode('none')
  router.push('/workspaces')
}
</script>

<template>
  <div class="admin-layout">
    <header class="admin-layout__topbar">
      <div class="admin-layout__topbar-left">
        <span class="admin-layout__logo">RoboTest</span>
        <el-tag type="danger" size="small" effect="dark" class="admin-layout__mode-tag">
          系统管理
        </el-tag>
      </div>

      <div class="admin-layout__topbar-right">
        <div v-if="authStore.hasWorkspace" class="admin-layout__icon-btn" @click="goMyWorkspaces">
          <el-icon><FolderOpened /></el-icon>
          <span>我的空间</span>
        </div>

        <div class="admin-layout__icon-btn admin-layout__icon-btn--active">
          <el-icon><Monitor /></el-icon>
          <span>系统管理</span>
        </div>

        <div class="admin-layout__icon-btn">
          <el-badge :is-dot="true">
            <el-icon><Bell /></el-icon>
          </el-badge>
          <span>消息中心</span>
        </div>

        <el-divider direction="vertical" />

        <el-dropdown trigger="click" @command="handleUserCommand">
          <span class="admin-layout__user">
            <el-avatar :size="30" :src="authStore.avatarUrl || undefined">
              {{ authStore.username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <span class="admin-layout__username">{{ authStore.username }}</span>
            <el-icon class="admin-layout__user-arrow"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="change-password">
                <el-icon><Lock /></el-icon>修改密码
              </el-dropdown-item>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <ChangePasswordDialog v-model="pwdDialogVisible" />
      </div>
    </header>

    <div class="admin-layout__body">
      <aside class="admin-layout__sidebar">
        <el-menu
          :default-active="activeSidebarPath"
          background-color="transparent"
          text-color="rgba(255,255,255,0.65)"
          active-text-color="#ffffff"
          class="admin-layout__sidebar-menu"
          @select="handleSidebarSelect"
        >
          <el-menu-item v-for="item in sidebarMenu" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </aside>

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
  background: linear-gradient(90deg, var(--color-neutral-800) 0%, var(--color-neutral-900) 100%);
  padding: 0 20px;
  z-index: 100;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.admin-layout__topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-layout__logo {
  font-size: 17px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: -0.02em;
}

.admin-layout__mode-tag {
  font-size: 10px;
  letter-spacing: 0.04em;
  padding: 2px 8px;
}

.admin-layout__topbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.admin-layout__icon-btn {
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

.admin-layout__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast);

  &:hover {
    background: rgba(255, 255, 255, 0.08);
  }
}

.admin-layout__username {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
}

.admin-layout__user-arrow {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}

.admin-layout__body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.admin-layout__sidebar {
  width: var(--sidebar-width);
  background: linear-gradient(180deg, var(--color-neutral-800) 0%, var(--color-neutral-900) 100%);
  flex-shrink: 0;
  overflow-y: auto;
}

.admin-layout__sidebar-menu {
  border-right: none;
  padding: 8px 0;

  :deep(.el-menu-item) {
    height: 42px;
    line-height: 42px;
    margin: 2px 8px;
    border-radius: var(--radius-md);
    font-size: 13px;
    transition: all var(--transition-fast);

    &:hover {
      background: rgba(255, 255, 255, 0.06) !important;
      color: #e2e8f0 !important;
    }

    &.is-active {
      background: rgba(59, 130, 246, 0.18) !important;
      color: #60a5fa !important;
      font-weight: 500;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 16px;
        background: var(--color-primary-400);
        border-radius: 0 2px 2px 0;
      }
    }

    .el-icon {
      font-size: 16px;
    }
  }
}

.admin-layout__content {
  flex: 1;
  overflow: auto;
  background-color: var(--color-admin-bg);
  padding: var(--space-xl);
}
</style>
