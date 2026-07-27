<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addRoleUsers,
  addWorkspaceRoleUsers,
  fetchRoleWorkspaceUsers,
  fetchUsers,
  removeRoleUser,
  removeWorkspaceRoleUser,
} from '@/services/admin'
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

// 多空间移除弹窗
const wsRemoveVisible = ref(false)
const wsRemoveTarget = ref<RoleWorkspaceUser | null>(null)
const wsRemoveSelected = ref<string[]>([])

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

async function handleAddUsers(userIds: string[], workspaceIds?: string[]) {
  try {
    if (isWorkspaceRole()) {
      if (!workspaceIds?.length) {
        ElMessage.warning('请至少选择一个空间')
        return
      }
      await addWorkspaceRoleUsers(props.roleId, userIds, workspaceIds)
    } else {
      await addRoleUsers(props.roleId, userIds)
    }
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
    // 只有一个空间，确认后直接移除
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
    // 多个空间，弹窗选择要移除的空间
    wsRemoveTarget.value = user
    wsRemoveSelected.value = []
    wsRemoveVisible.value = true
  }
}

async function handleWsRemoveConfirm() {
  if (!wsRemoveSelected.value.length) {
    ElMessage.warning('请至少选择一个空间')
    return
  }
  const user = wsRemoveTarget.value
  if (!user) return
  try {
    await Promise.all(
      wsRemoveSelected.value.map((wsId) => removeWorkspaceRoleUser(props.roleId, user.userId, wsId)),
    )
    ElMessage.success('已移除')
    wsRemoveVisible.value = false
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '移除失败')
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

.role-users__ws-remove-tip {
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.role-users__ws-checkbox {
  padding: 6px 0;
}
</style>
