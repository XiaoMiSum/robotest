<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuInstance } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import EnvironmentPage from './environment/EnvironmentPage.vue'
import FunctionPage from './function/FunctionPage.vue'
import DebugPage from './debug/DebugPage.vue'
import MocksPage from './mock/MocksPage.vue'
import ComponentPage from './component/ComponentPage.vue'
import SceneWorkspace from './scene/SceneWorkspace.vue'
import InterfaceWorkspace from './interface/InterfaceWorkspace.vue'
import ReportsPage from './report/ReportsPage.vue'
import ReportDetailPage from './report/ReportDetailPage.vue'
import SchedulesPage from './schedule/SchedulesPage.vue'

const route = useRoute()
const router = useRouter()

const menuItems = [
  { key: 'debug', label: '快速调试', icon: 'Promotion', permission: 'api-debug:view' },
  { key: 'interfaces', label: '接口管理', icon: 'Link', permission: 'api-interface:view' },
  { key: 'mocks', label: 'Mock 服务', icon: 'Cpu', permission: 'api-mock:view' },
  { key: 'scenes', label: '测试场景', icon: 'Operation', permission: 'api-scene:view' },
  { key: 'reports', label: '测试报告', icon: 'DataAnalysis', permission: 'api-report:view' },
  { key: 'schedules', label: '定时任务', icon: 'Timer', permission: 'api-timer:view' },
]

const settingsItems = [
  { key: 'environments', label: '环境管理', icon: 'Compass', enabled: true, permission: 'api-env:view' },
  { key: 'functions', label: '函数管理', icon: 'SetUp', enabled: true, permission: 'api-func:view' },
  { key: 'assets', label: '公共组件', icon: 'Box', enabled: true, permission: 'api-component:view' },
]

const authStore = useAuthStore()
const has = (code: string) => authStore.hasPermission(code)
const visibleMenuItems = computed(() => menuItems.filter((item) => has(item.permission)))
const visibleSettingsItems = computed(() => settingsItems.filter((item) => item.enabled && has(item.permission)))
const visibleKeys = computed(() => [
  ...visibleMenuItems.value.map((item) => item.key),
  ...visibleSettingsItems.value.map((item) => item.key),
])

// 刷新与详情页返回时通过 ?tab= 恢复激活子模块（子页切换不走路由，仅初始化读取）。

const initialTab = String(route.query.tab ?? '')
// 权限点异步加载完成后可见菜单会变化，激活项须始终落在可见列表内；?tab= 仅在可见时还原
const activeMenu = ref('')
watch(
  visibleKeys,
  (keys) => {
    if (!keys.length) {
      activeMenu.value = ''
      return
    }
    if (!keys.includes(activeMenu.value)) {
      activeMenu.value = keys.includes(initialTab) ? initialTab : keys[0]
    }
  },
  { immediate: true },
)
const menuRef = ref<MenuInstance>()

const allItems = [...menuItems, ...settingsItems]
const activeLabel = computed(
  () => allItems.find((item) => item.key === activeMenu.value)?.label ?? '',
)

// 接口管理多 Tab 编辑器：刷新/直链经 query 恢复由 InterfaceWorkspace 内部处理；
// pendingInterfaceId 供「快速调试 → 查看接口」跨子页跳转打开对应编辑 Tab
const pendingInterfaceId = ref<string | null>(null)

// 报告详情状态
const reportDetailId = ref<string | null>(null)

function handleViewInterface(id: string) {
  activeMenu.value = 'interfaces'
  pendingInterfaceId.value = id
}

function handleViewHandled() {
  pendingInterfaceId.value = null
}

function handleReportBack() {
  reportDetailId.value = null
}

function handleReportView(reportId: string) {
  reportDetailId.value = reportId
}

const isReportDetail = computed(
  () => activeMenu.value === 'reports' && reportDetailId.value,
)

// replace 避免子页切换污染浏览器历史
function handleMenuSelect(key: string) {
  if (key === activeMenu.value) return
  activeMenu.value = key
  pendingInterfaceId.value = null
  reportDetailId.value = null
  router.replace({ query: { tab: key } })
}

// 子页跨模块跳转（如接口管理列表「调试」→ 快速调试）只更新 URL tab，不复用菜单点击，
// 需同步切换 activeMenu 才会卸载旧页并渲染目标子页（DebugPage 在 onMounted 消费 pendingDebugRequest）
watch(
  () => route.query.tab,
  (tab) => {
    const key = String(tab ?? '')
    if (key && key !== activeMenu.value && visibleKeys.value.includes(key)) {
      activeMenu.value = key
    }
  },
)
</script>

<template>
  <div class="api-testing">
    <aside class="api-testing__sidebar">
      <el-menu
        ref="menuRef"
        :default-active="activeMenu"
        background-color="transparent"
        text-color="var(--shell-text)"
        active-text-color="var(--color-primary-500)"
        class="api-testing__sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in visibleMenuItems" :key="item.key" :index="item.key">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
        <el-menu-item-group v-if="visibleSettingsItems.length" class="api-testing__settings-group" title="项目设置">
          <el-menu-item
            v-for="item in visibleSettingsItems"
            :key="item.key"
            :index="item.key"
            :disabled="!item.enabled"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </aside>

    <main class="api-testing__main">
      <EnvironmentPage v-if="activeMenu === 'environments'" />
      <FunctionPage v-else-if="activeMenu === 'functions'" />
      <DebugPage v-else-if="activeMenu === 'debug'" @view-interface="handleViewInterface" />
      <InterfaceWorkspace
        v-else-if="activeMenu === 'interfaces'"
        :pending-interface-id="pendingInterfaceId"
        @view-handled="handleViewHandled"
      />
      <MocksPage v-else-if="activeMenu === 'mocks'" />
      <ComponentPage v-else-if="activeMenu === 'assets'" />
      <ReportDetailPage
        v-else-if="isReportDetail"
        :report-id="reportDetailId!"
        @back="handleReportBack"
      />
      <ReportsPage
        v-else-if="activeMenu === 'reports'"
        @view="handleReportView"
      />
      <SceneWorkspace v-else-if="activeMenu === 'scenes'" />
      <SchedulesPage v-else-if="activeMenu === 'schedules'" />
      <div v-else-if="!activeMenu" class="api-testing__norights">
        <div class="api-testing__norights-title">暂无可用功能模块</div>
        <p class="api-testing__norights-desc">当前角色未被分配接口测试相关权限，请联系空间管理员</p>
      </div>
      <div v-else class="api-testing__placeholder">
        <div class="api-testing__placeholder-icon">
          <el-icon :size="48"><Connection /></el-icon>
        </div>
        <div class="api-testing__placeholder-title">{{ activeLabel }}</div>
        <p class="api-testing__placeholder-desc">功能建设中，敬请期待...</p>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.api-testing {
  display: flex;
  gap: var(--float-gap);
  height: 100%;
}

/* 模块侧栏 = 悬浮白卡，与主内容卡等高并排（视觉设计 6.1 双栏页） */
.api-testing__sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--shell-bg);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
  padding: 12px 8px;
  overflow-y: auto;
}

.api-testing__sidebar-menu {
  border-right: none;
  padding: 0;

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

    &.is-disabled {
      opacity: 0.4;
      cursor: not-allowed;

      &:hover {
        background: transparent !important;
        color: var(--shell-text) !important;
      }
    }

    .el-icon {
      font-size: 16px;
    }
  }

  :deep(.el-menu-item-group__title) {
    padding: 14px 12px 6px;
    margin: 0;
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--color-neutral-400);
  }
}

.api-testing__main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: var(--page-pad);
  display: flex;
  flex-direction: column;
  background: var(--color-neutral-0);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
}

.api-testing__norights {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.api-testing__norights-title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-700);
  margin-bottom: var(--space-sm);
}

.api-testing__norights-desc {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-400);
  margin: 0;
}

.api-testing__placeholder {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.api-testing__placeholder-icon {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-xl);
  background: var(--color-neutral-100);
  color: var(--color-neutral-400);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-lg);
}

.api-testing__placeholder-title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-700);
  margin: 0 0 var(--space-sm);
}

.api-testing__placeholder-desc {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-400);
  margin: 0;
}
</style>
