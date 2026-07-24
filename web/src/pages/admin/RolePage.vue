<script setup lang="ts">
import { ref } from 'vue'
import RoleTreePanel from '@/components/admin/RoleTreePanel.vue'
import PermissionTable from '@/components/admin/PermissionTable.vue'
import RoleUsersTable from '@/components/admin/RoleUsersTable.vue'

const selectedRole = ref<{ id: string; isSystem: boolean } | null>(null)
const activeTab = ref<'permissions' | 'users'>('permissions')

function handleSelect(node: { id: string; isSystem: boolean }) {
  selectedRole.value = node
  activeTab.value = 'permissions'
}

function handleCleared() {
  selectedRole.value = null
}
</script>

<template>
  <div class="role-page">
    <h2 class="role-page__title">角色管理</h2>
    <div class="role-page__body">
      <el-card shadow="never" class="role-page__aside">
        <RoleTreePanel @select="handleSelect" @cleared="handleCleared" />
      </el-card>

      <el-card shadow="never" class="role-page__main">
        <el-empty v-if="!selectedRole" description="请选择左侧角色查看详情" />
        <el-tabs v-else v-model="activeTab">
          <el-tab-pane label="权限配置" name="permissions">
            <PermissionTable :role-id="selectedRole.id" :is-system="selectedRole.isSystem" />
          </el-tab-pane>
          <el-tab-pane label="关联用户" name="users">
            <RoleUsersTable :role-id="selectedRole.id" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.role-page__title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 16px;
}

.role-page__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.role-page__aside {
  width: 260px;
  flex-shrink: 0;
}

.role-page__main {
  flex: 1;
  min-width: 0;
}

/* 小屏下左右分栏切换为上下布局 */
@media (max-width: 768px) {
  .role-page__body {
    flex-direction: column;
  }

  .role-page__aside {
    width: 100%;
  }
}
</style>
