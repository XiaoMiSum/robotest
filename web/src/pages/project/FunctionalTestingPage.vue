<script setup lang="ts">
import { ref } from 'vue'
import TestCasePage from '@/pages/project/TestCasePage.vue'
import ReviewListPage from '@/pages/project/ReviewListPage.vue'
import PlanListPage from '@/pages/project/PlanListPage.vue'

const activeMenu = ref('cases')

const menuItems = [
  { key: 'cases', label: '测试用例', icon: 'Document' },
  { key: 'reviews', label: '测试评审', icon: 'Checked' },
  { key: 'plans', label: '测试计划', icon: 'Calendar' },
]
</script>

<template>
  <div class="func-testing">
    <aside class="func-testing__sidebar">
      <el-menu
        :default-active="activeMenu"
        background-color="transparent"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#ffffff"
        class="func-testing__sidebar-menu"
        @select="(key: string) => (activeMenu = key)"
      >
        <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="func-testing__main">
      <TestCasePage v-if="activeMenu === 'cases'" />
      <ReviewListPage v-else-if="activeMenu === 'reviews'" />
      <PlanListPage v-else-if="activeMenu === 'plans'" />
    </main>
  </div>
</template>

<style scoped lang="scss">
.func-testing {
  display: flex;
  height: 100%;
}

.func-testing__sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: linear-gradient(180deg, var(--color-neutral-800) 0%, var(--color-neutral-900) 100%);
  overflow-y: auto;
}

.func-testing__sidebar-menu {
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

.func-testing__main {
  flex: 1;
  overflow: hidden;
  padding: var(--space-xl);
}
</style>
