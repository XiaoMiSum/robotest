<script setup lang="ts">
import { useFunctionalTesting } from '@/composables/project/functional-testing/useFunctionalTesting'
import TestCasePage from '@/pages/project/functional-testing/TestCasePage.vue'
import ReviewListPage from '@/pages/project/functional-testing/ReviewListPage.vue'
import PlanListPage from '@/pages/project/functional-testing/PlanListPage.vue'
import RequirementPoolPage from '@/pages/project/functional-testing/RequirementPoolPage.vue'

const { activeMenu, menuRef, testCaseRef, menuItems, handleMenuSelect } = useFunctionalTesting()
</script>

<template>
  <div class="func-testing">
    <aside class="func-testing__sidebar">
      <el-menu
        ref="menuRef"
        :default-active="activeMenu"
        class="func-testing__sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="func-testing__main">
      <TestCasePage v-if="activeMenu === 'cases'" ref="testCaseRef" />
      <ReviewListPage v-else-if="activeMenu === 'reviews'" />
      <PlanListPage v-else-if="activeMenu === 'plans'" />
      <RequirementPoolPage v-else-if="activeMenu === 'requirements'" />
    </main>
  </div>
</template>

<style scoped lang="scss">
.func-testing {
  display: flex;
  gap: var(--float-gap);
  height: 100%;
}

/* 模块侧栏 = 悬浮白卡，与主内容卡等高并排（视觉设计 6.1 双栏页） */
.func-testing__sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--shell-bg);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
  padding: 12px 8px;
  overflow-y: auto;
}

.func-testing__sidebar-menu {
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

    .el-icon {
      font-size: 16px;
    }
  }
}

.func-testing__main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  padding: var(--page-pad);
  background: var(--color-neutral-0);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
}
</style>
