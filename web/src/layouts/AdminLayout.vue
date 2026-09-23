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
  if (has('ai:view')) items.push({ label: 'AI 配置', path: '/admin/ai-config', icon: 'MagicStick' })
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
        <span class="admin-layout__logo">
          <span class="admin-layout__logo-mark"><el-icon><Lightning /></el-icon></span>
          RoboTest
        </span>
        <span class="admin-layout__mode-tag">系统管理</span>
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
          text-color="var(--shell-text)"
          active-text-color="var(--color-primary-500)"
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
  background: var(--shell-bg);
  padding: 0 20px;
  z-index: 100;
  flex-shrink: 0;
  border-bottom: 1px solid var(--shell-border);
}

.admin-layout__topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-layout__logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--shell-text-strong);
  letter-spacing: -0.01em;
}

.admin-layout__logo-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: 14px;
  border-radius: 5px;
  background: var(--color-primary-500);
  color: #fff;
  flex-shrink: 0;
}

.admin-layout__mode-tag {
  font-size: 10px;
  letter-spacing: 0.04em;
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
  background: var(--color-neutral-900);
  color: var(--color-neutral-0);
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
  color: var(--shell-text);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
  user-select: none;

  &:hover {
    color: var(--color-neutral-900);
    background: var(--shell-item-hover);
  }

  &--active {
    color: var(--color-primary-700);
    background: var(--shell-item-active);
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
    background: var(--shell-item-hover);
  }
}

.admin-layout__username {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-neutral-900);
}

.admin-layout__user-arrow {
  font-size: 12px;
  color: var(--color-neutral-400);
}

.admin-layout__topbar :deep(.el-avatar) {
  background: var(--color-primary-500);
  color: #fff;
}

.admin-layout__body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.admin-layout__sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  /* 顶边与内容卡对齐（视觉设计 6.1 与验收基准演示稿一致）；right 保持 0，与内容卡间距由内容区 margin-left 构成 */
  margin: var(--float-gap) 0 var(--float-gap) var(--float-gap);
  background: var(--shell-bg);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
  overflow-y: auto;
}

.admin-layout__sidebar-menu {
  border-right: none;
  padding: 12px 8px;

  :deep(.el-menu-item) {
    height: 38px;
    line-height: 38px;
    margin: 3px 0;
    padding: 0 12px;
    border-radius: var(--radius-lg);
    font-size: 13px;
    color: var(--shell-text);
    transition: all var(--transition-fast);

    &:hover {
      background: var(--shell-item-hover) !important;
      color: var(--shell-text-strong) !important;
    }

    &.is-active {
      background: var(--shell-item-active) !important;
      color: var(--color-primary-500) !important;
      font-weight: 600;
    }

    .el-icon {
      font-size: 16px;
    }
  }
}

/* 方案B：主体整体一张悬浮白卡，左缘 = 侧栏(16+180) + 间距 16 = 212px（视觉设计 6.1） */
.admin-layout__content {
  flex: 1;
  overflow: auto;
  margin: var(--float-gap);
  padding: var(--page-pad);
  background: var(--color-neutral-0);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
}
</style>
