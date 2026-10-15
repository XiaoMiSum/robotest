<script setup lang="ts">
import { useRequirementShell } from '@/composables/project/requirement/useRequirementShell'
import RequirementListPage from '@/pages/project/requirement/RequirementListPage.vue'
import TraceMatrixPage from '@/pages/project/trace/TraceMatrixPage.vue'

const { visibleItems, activeTab, handleMenuSelect } = useRequirementShell()
</script>

<template>
  <div class="requirement-shell">
    <aside v-if="visibleItems.length" class="requirement-shell__sidebar">
      <el-menu
        :default-active="activeTab"
        class="requirement-shell__sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in visibleItems" :key="item.key" :index="item.key">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="requirement-shell__main">
      <RequirementListPage v-if="activeTab === 'list'" />
      <TraceMatrixPage v-else-if="activeTab === 'trace'" />
    </main>
  </div>
</template>

<style scoped lang="scss">
.requirement-shell {
  display: flex;
  gap: var(--float-gap);
  height: 100%;
}

/* 模块侧栏 = 悬浮白卡，与主内容卡等高并排（视觉设计 6.1 双栏页） */
.requirement-shell__sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--shell-bg);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
  padding: 12px 8px;
  overflow-y: auto;
}

.requirement-shell__sidebar-menu {
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

/* 子页自身依赖外层容器给内边距与滚动（同顶层路由形态），故此处置 page-pad 并自行滚动 */
.requirement-shell__main {
  flex: 1;
  min-width: 0;
  overflow: auto;
  padding: var(--page-pad);
  background: var(--color-neutral-0);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
}
</style>
