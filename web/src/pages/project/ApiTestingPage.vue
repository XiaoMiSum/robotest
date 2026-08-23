<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuInstance } from 'element-plus'
import ProjectSettingsPage from './ProjectSettingsPage.vue'
import EnvironmentPage from './EnvironmentPage.vue'
import DebugPage from './DebugPage.vue'

const route = useRoute()
const router = useRouter()

const menuItems = [
  { key: 'debug', label: '快速调试', icon: 'Promotion' },
  { key: 'interfaces', label: '接口管理', icon: 'Link' },
  { key: 'mocks', label: 'Mock 服务', icon: 'Cpu' },
  { key: 'scenes', label: '测试场景', icon: 'Operation' },
  { key: 'reports', label: '接口测试报告', icon: 'DataAnalysis' },
  { key: 'schedules', label: '定时任务', icon: 'Timer' },
]

// 项目设置分组为平台级框架（交互设计 3.5）；仅安全策略与应用设置已实装，
// 其余项后端未就绪，保持禁用占位避免跳转到空白页
const settingsItems = [
  { key: 'environments', label: '环境管理', icon: 'Compass', enabled: true },
  { key: 'assets', label: '全局资产', icon: 'Box', enabled: false },
  { key: 'gitlab-repos', label: 'GitLab 仓库配置', icon: 'Platform', enabled: false },
  { key: 'security', label: '安全策略与应用设置', icon: 'Lock', enabled: true },
]

// 刷新与详情页返回时通过 ?tab= 恢复激活子模块（子页切换不走路由，仅初始化读取）
const selectableKeys = [
  ...menuItems.map((item) => item.key),
  ...settingsItems.filter((item) => item.enabled).map((item) => item.key),
]
const initialTab = String(route.query.tab ?? '')
const activeMenu = ref(selectableKeys.includes(initialTab) ? initialTab : 'debug')
const menuRef = ref<MenuInstance>()

const allItems = [...menuItems, ...settingsItems]
const activeLabel = computed(() => allItems.find((item) => item.key === activeMenu.value)?.label ?? '')

// replace 避免子页切换污染浏览器历史
function handleMenuSelect(key: string) {
  if (key === activeMenu.value) return
  activeMenu.value = key
  router.replace({ query: { ...route.query, tab: key } })
}
</script>

<template>
  <div class="api-testing">
    <aside class="api-testing__sidebar">
      <el-menu
        ref="menuRef"
        :default-active="activeMenu"
        background-color="transparent"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#ffffff"
        class="api-testing__sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
        <el-menu-item-group class="api-testing__settings-group" title="项目设置">
          <el-menu-item v-for="item in settingsItems" :key="item.key" :index="item.key" :disabled="!item.enabled">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </aside>

    <main class="api-testing__main">
      <ProjectSettingsPage v-if="activeMenu === 'security'" />
      <EnvironmentPage v-else-if="activeMenu === 'environments'" />
      <DebugPage v-else-if="activeMenu === 'debug'" />
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
  height: 100%;
}

.api-testing__sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: linear-gradient(180deg, var(--color-neutral-800) 0%, var(--color-neutral-900) 100%);
  overflow-y: auto;
}

.api-testing__sidebar-menu {
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

    &.is-disabled {
      opacity: 0.4;
      cursor: not-allowed;

      &:hover {
        background: transparent !important;
        color: rgba(255, 255, 255, 0.65) !important;
      }
    }

    .el-icon {
      font-size: 16px;
    }
  }

  :deep(.el-menu-item-group__title) {
    padding: 12px 16px 4px;
    margin: 0 8px;
    border-top: 1px solid rgba(255, 255, 255, 0.08);
    margin-top: 8px;
    font-size: 11px;
    letter-spacing: 0.05em;
    color: rgba(255, 255, 255, 0.35);
  }
}

.api-testing__main {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xl);
  display: flex;
  flex-direction: column;
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
