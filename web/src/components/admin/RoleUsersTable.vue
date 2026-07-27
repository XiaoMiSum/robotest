<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addRoleUsers, fetchRoleWorkspaceUsers, fetchUsers, removeRoleUser, removeWorkspaceRoleUser } from '@/services/admin'
import type { AdminUser, RoleWorkspaceUser } from '@/types'
import { formatDateTime } from '@/utils/format'
import UserPickerDialog from '@/components/admin/UserPickerDialog.vue'

const props = defineProps<{
  roleId: string
  roleType: string
}>()

const loading = ref(false)
const users = ref<AdminUser[]>([])
const workspaceUsers = ref<RoleWorkspaceUser[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20 })
const pickerVisible = ref(false)

const isWorkspaceRole = () => props.roleType === 'workspace'

async function load() {
  if (!props.roleId) return
  loading.value = true
  try {
    if (isWorkspaceRole()) {
      workspaceUsers.value = await fetchRoleWorkspaceUsers(props.roleId)
    } else {
      const page = await fetchUsers({
        roleId: props.roleId,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })
      users.value = page.list
      total.value = page.total
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载关联用户失败')
  } finally {
    loading.value = false
  }
}

async function handleAddUsers(userIds: string[]) {
  try {
    await addRoleUsers(props.roleId, userIds)
    ElMessage.success('已添加用户')
    pickerVisible.value = false
    query.pageNo = 1
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '添加用户失败')
  }
}

async function handleRemove(user: AdminUser) {
  try {
    await ElMessageBox.confirm(`确定要移除用户「${user.name || user.username}」的该角色吗？`, '确认移除', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await removeRoleUser(props.roleId, user.id)
    ElMessage.success('已移除')
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '移除失败')
  }
}

async function handleRemoveWorkspace(user: RoleWorkspaceUser) {
  if (user.workspaces.length === 1) {
    // 只有一个空间，直接移除
    const ws = user.workspaces[0]
    try {
      await ElMessageBox.confirm(
        `确定要移除用户「${user.name || user.username}」在「${ws.workspaceName}」中的该角色吗？`,
        '确认移除',
        { type: 'warning' },
      )
    } catch {
      return
    }
    try {
      await removeWorkspaceRoleUser(props.roleId, user.userId, ws.workspaceId)
      ElMessage.success('已移除')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '移除失败')
    }
  } else {
    // 多个空间，弹窗选择
    const workspaceNames = user.workspaces.map((ws) => ws.workspaceName).join('、')
    try {
      await ElMessageBox.confirm(
        `用户「${user.name || user.username}」在以下空间中拥有该角色：${workspaceNames}\n\n注意：移除操作需要到对应空间中单独操作。\n如需移除，请到「空间管理」中操作。`,
        '归属多个空间',
        { type: 'info', showCancelButton: false },
      )
    } catch {
      // 用户关闭弹窗
    }
  }
}

watch(
  () => props.roleId,
  () => {
    query.pageNo = 1
    load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="role-users">
    <div class="role-users__toolbar">
      <el-button type="primary" size="small" @click="pickerVisible = true">
        <el-icon><Plus /></el-icon>添加用户
      </el-button>
    </div>

    <!-- 系统角色用户列表 -->
    <template v-if="!isWorkspaceRole()">
      <el-table v-loading="loading" :data="users" row-key="id">
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
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemove(row as AdminUser)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="role-users__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </template>

    <!-- 空间角色用户列表 -->
    <template v-else>
      <el-table v-loading="loading" :data="workspaceUsers" row-key="userId">
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
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemoveWorkspace(row as RoleWorkspaceUser)">
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <UserPickerDialog
      v-model="pickerVisible"
      title="添加关联用户"
      :exclude-ids="isWorkspaceRole() ? workspaceUsers.map((u) => u.userId) : users.map((u) => u.id)"
      @confirm="handleAddUsers"
    />
  </div>
</template>

<style scoped lang="scss">
.role-users__toolbar {
  margin-bottom: 12px;
}

.role-users__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.role-users__ws-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}
</style>
