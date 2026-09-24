<script setup lang="ts">
import { useRoleUsers } from '@/composables/admin/useRoleUsers'
import type { AdminUser, RoleWorkspaceUser } from '@/types'
import { formatDateTime } from '@/utils/format'
import UserPickerDialog from '@/components/admin/UserPickerDialog.vue'

const props = defineProps<{
  roleId: string
  roleType: string
}>()

const {
  loading,
  users,
  workspaceUsers,
  total,
  query,
  pickerVisible,
  wsRemoveVisible,
  wsRemoveTarget,
  wsRemoveSelected,
  isWorkspaceRole,
  handleAddUsers,
  handleRemove,
  handleRemoveWorkspace,
  handleWsRemoveConfirm,
  handlePageChange,
  handlePageSizeChange,
} = useRoleUsers(
  () => props.roleId,
  () => props.roleType,
)
</script>

<template>
  <div class="role-users">
    <div class="role-users__body">
      <div class="role-users__scroll">
        <!-- 系统角色用户列表 -->
        <el-table
          v-if="!isWorkspaceRole()"
          v-loading="loading"
          :data="users"
          row-key="id"
          border
          height="100%"
          class="role-users__table"
        >
          <el-table-column prop="username" label="用户名" min-width="140" />
          <el-table-column prop="name" label="姓名" min-width="120" />
          <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                {{ row.status === 'active' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="授权时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.grantedAt) }}</template>
          </el-table-column>
          <el-table-column width="130" fixed="right">
            <template #header>
              <el-button type="primary" @click="pickerVisible = true">
                <el-icon><Plus /></el-icon>添加用户
              </el-button>
            </template>
            <template #default="{ row }">
              <el-button link type="danger" @click="handleRemove(row as AdminUser)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 空间角色用户列表 -->
        <el-table
          v-else
          v-loading="loading"
          :data="workspaceUsers"
          row-key="userId"
          border
          height="100%"
          class="role-users__table"
        >
          <el-table-column prop="username" label="用户名" min-width="140" />
          <el-table-column prop="name" label="姓名" min-width="120" />
          <el-table-column label="归属空间" min-width="200">
            <template #default="{ row }">
              <el-tag
                v-for="ws in row.workspaces"
                :key="ws.workspaceId"
                size="small"
                class="role-users__ws-tag"
              >
                {{ ws.workspaceName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="授权时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.grantedAt) }}</template>
          </el-table-column>
          <el-table-column width="130" fixed="right">
            <template #header>
              <el-button type="primary" @click="pickerVisible = true">
                <el-icon><Plus /></el-icon>添加用户
              </el-button>
            </template>
            <template #default="{ row }">
              <el-button link type="danger" @click="handleRemoveWorkspace(row as RoleWorkspaceUser)">
                移除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页钉在 pane 底，滚动只作用于表体，避免长列表把分页顶出可视区 -->
      <div class="role-users__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </div>

    <!-- 用户选择弹窗 -->
    <UserPickerDialog
      v-model="pickerVisible"
      :title="isWorkspaceRole() ? '添加关联用户与空间' : '添加关联用户'"
      :show-workspace="isWorkspaceRole()"
      :exclude-ids="isWorkspaceRole() ? workspaceUsers.map((u) => u.userId) : users.map((u) => u.id)"
      @confirm="handleAddUsers"
    />

    <!-- 多空间移除弹窗 -->
    <el-dialog
      v-model="wsRemoveVisible"
      title="选择要移除的空间"
      width="400px"
    >
      <p v-if="wsRemoveTarget" class="role-users__ws-remove-tip">
        用户「{{ wsRemoveTarget.name || wsRemoveTarget.username }}」在以下空间中拥有该角色，请选择要移除的空间：
      </p>
      <el-checkbox-group v-model="wsRemoveSelected">
        <div v-for="ws in wsRemoveTarget?.workspaces" :key="ws.workspaceId" class="role-users__ws-checkbox">
          <el-checkbox :value="ws.workspaceId">{{ ws.workspaceName }}</el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="wsRemoveVisible = false">取消</el-button>
        <el-button type="danger" @click="handleWsRemoveConfirm">移除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
/* pane 整体不滚动，拆成「表体滚动 + 分页钉底」两段，高度由页面与 Tab 统一约束 */
.role-users {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 12px 24px 16px;
}

.role-users__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.role-users__scroll {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.role-users__pager {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 0;
  border-top: 1px solid var(--color-neutral-100);
}

/* 与权限点表格保持一致：13px 字号 + 单元格垂直居中（配合 border 属性） */
.role-users__table {
  font-size: 13px;

  :deep(.el-scrollbar__bar.is-vertical) {
    display: none;
  }

  :deep(td.el-table__cell) {
    vertical-align: middle;
  }
}

.role-users__ws-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.role-users__ws-remove-tip {
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.role-users__ws-checkbox {
  padding: 6px 0;
}
</style>
