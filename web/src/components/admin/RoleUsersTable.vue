<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addRoleUsers, fetchRoleUsers, removeRoleUser } from '@/services/admin'
import type { RoleUser } from '@/types'
import { formatDateTime } from '@/utils/format'
import UserPickerDialog from '@/components/admin/UserPickerDialog.vue'

const props = defineProps<{
  roleId: string
}>()

const loading = ref(false)
const users = ref<RoleUser[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20 })
const pickerVisible = ref(false)

async function load() {
  if (!props.roleId) return
  loading.value = true
  try {
    const page = await fetchRoleUsers(props.roleId, {
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    users.value = page.list
    total.value = page.total
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

async function handleRemove(user: RoleUser) {
  try {
    await ElMessageBox.confirm(`确定要移除用户「${user.username}」的该角色吗？`, '确认移除', {
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

    <el-table v-loading="loading" :data="users" row-key="id">
      <el-table-column prop="username" label="用户名" min-width="140" />
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
          <el-button link type="danger" @click="handleRemove(row as RoleUser)">移除</el-button>
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

    <UserPickerDialog
      v-model="pickerVisible"
      title="添加关联用户"
      :exclude-ids="users.map((u) => u.id)"
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
</style>
