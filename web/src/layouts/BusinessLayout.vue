<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'
import { useAiStore } from '@/stores/ai'
import ChangePasswordDialog from '@/components/common/ChangePasswordDialog.vue'
// 全局智能助手悬浮入口（详细设计 5.1）：仅业务布局挂载，管理端布局不挂载
import AssistantFab from '@/components/assistant/AssistantFab.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const navStore = useNavStore()
const aiStore = useAiStore()

// AI 状态为全局能力（GET /workspace/ai/status 不依赖 X-Active-Workspace 头，见 AI 基础设施 3.2.1）：
// 进入业务布局加载一次，切换工作空间后强制刷新；无工作空间（如 /workspaces 列表页）时不重置，
// 保持全局开关状态以维持悬浮入口可见（交互设计 1.1）
watch(
  () => authStore.activeWorkspace?.id,
  (workspaceId) => {
    void aiStore.load(Boolean(workspaceId))
  },
  { immediate: true },
)

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
const showWorkspaceManage = computed(() => (navStore.isWorkspaceMode || navStore.isProjectMode) && authStore.hasWorkspaceAccess)
const showSystemAdmin = computed(() => authStore.hasSystemPermission)
const isSystemActive = computed(() => navStore.isAdminMode)

// 项目 tag 仅 project 模式显示：workspace 模式（项目列表页）下 activeProjectName 可能是上次进入的残留
const showProjectTag = computed(() => navStore.isProjectMode && Boolean(authStore.activeProjectName))

// 侧边栏布局页面需内容区 padding 归零，侧边栏才能贴边通高（功能测试 / 接口测试）
const isFullHeightPage = computed(
  () =>
    route.path.startsWith('/workspace/projects/functional-testing') ||
    route.path.startsWith('/workspace/projects/api-testing'),
)

function handleDynamicMenuClick(path: string) {
  router.push(path)
}

function goHome() {
  if (navStore.isAdminMode) {
    router.push('/admin/dashboard')
  } else if (navStore.isProjectMode) {
    router.push('/workspace/projects/dashboard')
  } else {
    router.push('/workspaces')
    navStore.setMode('none')
  }
}

function goMyProjects() {
  router.push('/workspace/projects')
  navStore.setMode('workspace')
}

// 我的空间固定回空间列表；goHome 是 logo 的"回当前上下文首页"语义，两者不可复用
function goMyWorkspaces() {
  router.push('/workspaces')
  navStore.setMode('none')
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

const pwdDialogVisible = ref(false)

function handleUserCommand(cmd: string) {
  if (cmd === 'logout') {
    handleLogout()
  } else if (cmd === 'change-password') {
    pwdDialogVisible.value = true
  }
}
</script>

<template>
  <div class="business-layout">
    <header class="top-nav">
      <div class="top-nav__logo" @click="goHome">
        <span class="top-nav__logo-text">RoboTest</span>
      </div>

      <!-- 当前上下文名称（样式参考管理模式顶栏的系统管理标签）：空间名 + 项目名 -->
      <div class="top-nav__context-tags">
        <el-tag
          v-if="authStore.activeWorkspace?.name"
          class="top-nav__context-tag"
          type="primary"
          size="small"
          effect="dark"
        >
          {{ authStore.activeWorkspace.name }}
        </el-tag>
        <el-tag v-if="showProjectTag" class="top-nav__context-tag" type="info" size="small" effect="dark">
          {{ authStore.activeProjectName }}
        </el-tag>
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
        <div v-if="authStore.hasWorkspace" class="top-nav__icon-btn" @click="goMyWorkspaces">
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
          v-if="showSystemAdmin"
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

        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="top-nav__user">
            <el-avatar :size="30" :src="authStore.avatarUrl || undefined">
              {{ authStore.username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <span class="top-nav__username">{{ authStore.username }}</span>
          </div>
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

    <main
      class="business-layout__content"
      :class="{ 'business-layout__content--full': isFullHeightPage }"
    >
      <RouterView />
    </main>

    <!-- 智能助手悬浮入口（交互设计 1.1：随全局 aiEnabled 显隐；无工作空间时仍显示，面板内引导选择空间） -->
    <AssistantFab v-if="aiStore.aiEnabled" />
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

.top-nav__context-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-right: 8px;
  flex-shrink: 0;
}

.top-nav__context-tag {
  font-size: 10px;
  letter-spacing: 0.04em;
  padding: 2px 8px;
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

  &--full {
    padding: 0;
  }
}
</style>
