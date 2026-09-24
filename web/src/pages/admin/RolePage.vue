<script setup lang="ts">
import { ref } from 'vue'
import RoleTreePanel from '@/components/admin/RoleTreePanel.vue'
import PermissionTable from '@/components/admin/PermissionTable.vue'
import RoleUsersTable from '@/components/admin/RoleUsersTable.vue'

interface SelectedRole {
  id: string
  isSystem: boolean
  type: string
  name: string
}

const selectedRole = ref<SelectedRole | null>(null)
const activeTab = ref('permissions')

function handleSelect(node: SelectedRole) {
  selectedRole.value = node
  // 分册 14 §6.3：单击树节点默认显示「权限点」Tab
  activeTab.value = 'permissions'
}

function handleCleared() {
  selectedRole.value = null
}
</script>

<template>
  <div class="role-page">
    <div class="role-page__head">
      <h1 class="role-page__title">角色管理</h1>
      <p class="role-page__desc">系统角色的权限点配置与关联用户</p>
    </div>

    <div class="role-layout">
      <RoleTreePanel @select="handleSelect" @cleared="handleCleared" />

      <!-- Tab 标签承担卡头，权限点/关联用户共用等高占满的卡体，滚动收进各 pane -->
      <section class="role-card role-detail">
        <el-tabs v-model="activeTab" class="role-detail__tabs">
          <el-tab-pane label="权限点" name="permissions">
            <PermissionTable
              v-if="selectedRole"
              :role-id="selectedRole.id"
              :is-system="selectedRole.isSystem"
              :role-type="selectedRole.type"
            />
            <div v-else class="role-detail__empty">
              <el-empty description="请选择左侧角色查看详情" />
            </div>
          </el-tab-pane>

          <el-tab-pane label="关联用户" name="users">
            <RoleUsersTable
              v-if="selectedRole"
              :role-id="selectedRole.id"
              :role-type="selectedRole.type"
            />
            <div v-else class="role-detail__empty">
              <el-empty description="请选择左侧角色查看详情" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 整页锁定在 AdminLayout 内容卡视口内：左右两栏等高占满，溢出收进各子区域 */
.role-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  height: 100%;
  min-height: 0;
}

.role-page__head {
  flex-shrink: 0;
}

.role-page__title {
  margin: 0 0 var(--space-xs);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-neutral-900);
}

.role-page__desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

/* 左角色列表 / 右详情：grid stretch 天然等高，flex:1 占满剩余可视区 */
.role-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
  align-items: stretch;
}

/* Tab 头固定、内容区接管剩余高度，滚动收进各 TabPane 内部 */
.role-detail__tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) {
    flex-shrink: 0;
    margin-bottom: 0;
    padding: 0 24px;
    border-bottom: 1px solid var(--color-neutral-100);
  }

  /* EP 默认整条下划线会与自绘的头部分隔线叠成双线，隐藏之 */
  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
  }

  :deep(.el-tab-pane) {
    height: 100%;
  }
}

.role-detail__empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 768px) {
  .role-page {
    height: auto;
  }

  .role-layout {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
/* 卡片壳由页面与子组件共用；scoped 样式无法命中子组件子树，故此全局定义（.role-card 命名空间限定作用域） */
.role-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.role-card__head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-neutral-100);
}

.role-card__title {
  margin: 0;
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  min-width: 0;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-neutral-900);
}

.role-card__subtitle {
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: var(--color-neutral-500);
}

.role-card__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  /* Firefox 对齐全局 6px webkit 细滚动条规范 */
  scrollbar-width: thin;
}
</style>
