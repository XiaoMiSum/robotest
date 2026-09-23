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

function handleSelect(node: SelectedRole) {
  selectedRole.value = node
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

      <section v-if="!selectedRole" class="role-card role-page__placeholder">
        <header class="role-card__head">
          <h3 class="role-card__title">权限点</h3>
        </header>
        <div class="role-card__body role-page__placeholder-body">
          <el-empty description="请选择左侧角色查看详情" />
        </div>
      </section>
      <PermissionTable
        v-else
        :role-id="selectedRole.id"
        :is-system="selectedRole.isSystem"
        :role-type="selectedRole.type"
        :role-name="selectedRole.name"
      />
    </div>

    <RoleUsersTable
      v-if="selectedRole"
      class="role-page__users"
      :role-id="selectedRole.id"
      :role-type="selectedRole.type"
      :role-name="selectedRole.name"
    />
  </div>
</template>

<style scoped lang="scss">
/* 整页锁定在 AdminLayout 内容卡视口内：左右卡等高占满，溢出走卡内细滚动条 */
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

/* 左角色列表 / 右权限点：grid stretch 天然等高，flex:1 占满剩余可视区 */
.role-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
  align-items: stretch;
}

/* 关联用户卡随内容收缩、封顶 38% 高，保证三卡同屏不撑破视口 */
.role-page__users {
  flex: 0 1 auto;
  max-height: 38%;
}

.role-page__placeholder-body {
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

  .role-page__users {
    max-height: none;
  }
}
</style>

<style>
/* 卡片壳由页面与其三个子组件共用；scoped 样式无法命中子组件子树，故此全局定义（.role-card 命名空间限定作用域） */
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
