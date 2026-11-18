<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuInstance } from 'element-plus'
import EnvironmentPage from './environment/EnvironmentPage.vue'
import GitLabSettingsPage from './gitlab/GitLabSettingsPage.vue'
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
  { key: 'debug', label: '快速调试', icon: 'Promotion' },
  { key: 'interfaces', label: '接口管理', icon: 'Link' },
  { key: 'mocks', label: 'Mock 服务', icon: 'Cpu' },
  { key: 'scenes', label: '测试场景', icon: 'Operation' },
  { key: 'reports', label: '测试报告', icon: 'DataAnalysis' },
  { key: 'schedules', label: '定时任务', icon: 'Timer' },
]

interface SettingsItem {
  key: string
  label: string
  icon: string
  enabled: boolean
}

const settingsItems: SettingsItem[] = [
  { key: 'environments', label: '环境管理', icon: 'Compass', enabled: true },
  { key: 'gitlab', label: 'GitLab 集成', icon: 'Connection', enabled: true },
  { key: 'functions', label: '函数管理', icon: 'SetUp', enabled: true },
  { key: 'assets', label: '公共组件', icon: 'Box', enabled: true },
]

// 刷新与详情页返回时通过 ?tab= 恢复激活子模块（子页切换不走路由，仅初始化读取）。
const allItems = [...menuItems, ...settingsItems]
const allKeys = allItems.map((item) => item.key)
const initialTab = String(route.query.tab ?? '')
const activeMenu = ref(allKeys.includes(initialTab) ? initialTab : allKeys[0] ?? '')
const menuRef = ref<MenuInstance>()

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
    if (key && key !== activeMenu.value && allKeys.includes(key)) {
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
        class="api-testing__sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
        <el-menu-item-group class="api-testing__settings-group" title="项目设置">
          <el-menu-item
            v-for="item in settingsItems"
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
      <GitLabSettingsPage v-else-if="activeMenu === 'gitlab'" />
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
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--shell-text);
  --el-menu-hover-bg-color: var(--shell-item-hover);
  --el-menu-hover-text-color: var(--shell-text-strong);
  --el-menu-active-color: var(--color-primary-500);
  border-right: none;
  padding: 0;

  :deep(.el-menu-item) {
    height: 38px;
    line-height: 38px;
    margin: 3px 0;
    padding: 0 12px;
    border-radius: var(--radius-lg);
    font-size: 13px;
    transition: all var(--transition-fast);

    &.is-active {
      background: var(--shell-item-active);
      font-weight: 600;
    }

    &.is-disabled {
      opacity: 0.4;
      cursor: not-allowed;

      &:hover {
        color: var(--shell-text);
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
