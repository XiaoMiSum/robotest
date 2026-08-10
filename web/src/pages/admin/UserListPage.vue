<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchUpdateUserStatus,
  fetchRoleList,
  fetchUsers,
  resetUserPassword,
  updateUserStatus,
} from '@/services/admin'
import type { AdminUser, RoleSimple, UserStatus } from '@/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const users = ref<AdminUser[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])

const query = reactive({
  keyword: '',
  status: '' as UserStatus | '',
  roleId: '',
  pageNo: 1,
  pageSize: 20,
})

const roleOptions = ref<RoleSimple[]>([])

async function loadUsers() {
  loading.value = true
  try {
    const page = await fetchUsers({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      roleId: query.roleId || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    users.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

async function loadFilterOptions() {
  try {
    const list = await fetchRoleList('system')
    roleOptions.value = list.map((r) => ({
      id: r.id,
      name: r.name,
      type: 'system',
    }))
  } catch {
    // 筛选项加载失败不阻塞主列表
  }
}

function handleSearch() {
  query.pageNo = 1
  loadUsers()
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.roleId = ''
  query.pageNo = 1
  loadUsers()
}

function handleSelectionChange(rows: AdminUser[]) {
  selectedIds.value = rows.map((r) => r.id)
}

async function handleToggleStatus(user: AdminUser) {
  const next: UserStatus = user.status === 'active' ? 'disabled' : 'active'
  const actionText = next === 'disabled' ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${actionText}用户「${user.username}」吗？`, '确认操作', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await updateUserStatus(user.id, next)
    ElMessage.success(`已${actionText}`)
    loadUsers()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : `${actionText}失败`)
  }
}

async function handleBatchStatus(status: UserStatus) {
  if (!selectedIds.value.length) return
  const actionText = status === 'disabled' ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要批量${actionText}选中的 ${selectedIds.value.length} 个用户吗？`,
      '批量操作',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await batchUpdateUserStatus(selectedIds.value, status)
    ElMessage.success(`已批量${actionText}`)
    loadUsers()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '批量操作失败')
  }
}

const resetDialogVisible = ref(false)
const resetTarget = ref<AdminUser | null>(null)
const resetPassword = ref('')
const resetSubmitting = ref(false)

function openResetDialog(user: AdminUser) {
  resetTarget.value = user
  resetPassword.value = ''
  resetDialogVisible.value = true
}

async function submitResetPassword() {
  if (!resetTarget.value) return
  if (!resetPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  resetSubmitting.value = true
  try {
    await resetUserPassword(resetTarget.value.id, resetPassword.value)
    ElMessage.success('密码已重置')
    resetDialogVisible.value = false
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重置密码失败')
  } finally {
    resetSubmitting.value = false
  }
}

onMounted(() => {
  loadFilterOptions()
  loadUsers()
})
</script>

<template>
  <div class="user-list">
    <el-card shadow="never" class="user-list__filters">
      <el-form :inline="true" class="user-list__filter-form" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="query.keyword"
            placeholder="搜索用户名 / 邮箱"
            clearable
            :prefix-icon="'Search'"
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="query.roleId"
            placeholder="角色"
            clearable
            filterable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="user-list__filter-spacer" />
        <el-form-item>
          <el-button type="primary" @click="router.push('/admin/users/create')">
            <el-icon><Plus /></el-icon>新建用户
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="users"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="用户名" min-width="140">
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click="router.push(`/admin/users/${row.id}`)">
              {{ row.username }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <template v-if="row.roles.length">
              <el-tag v-for="role in row.roles" :key="role.id" size="small" class="user-list__tag">
                {{ role.name }}
              </el-tag>
            </template>
            <span v-else class="user-list__muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/admin/users/${row.id}`)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'active' ? 'warning' : 'success'"
              @click="handleToggleStatus(row as AdminUser)"
            >
              {{ row.status === 'active' ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="primary" @click="openResetDialog(row as AdminUser)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="user-list__footer">
        <div class="user-list__batch">
          <template v-if="selectedIds.length">
            <span class="user-list__muted">已选 {{ selectedIds.length }} 项</span>
            <el-button size="small" @click="handleBatchStatus('disabled')">批量禁用</el-button>
            <el-button size="small" @click="handleBatchStatus('active')">批量启用</el-button>
          </template>
        </div>
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadUsers"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <el-dialog v-model="resetDialogVisible" title="重置密码" width="420px">
      <p class="user-list__reset-tip">
        为用户「{{ resetTarget?.username }}」设置新密码（8-64
        字符，需包含大小写字母、数字、特殊字符中至少三种）。
      </p>
      <el-input v-model="resetPassword" type="password" placeholder="请输入新密码" show-password />
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="submitResetPassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.user-list__filters {
  margin-bottom: var(--space-lg);
}

.user-list__filters :deep(.el-form-item) {
  margin-bottom: 0;
}

.user-list__filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
}

.user-list__filter-spacer {
  flex: 1;
}

.user-list__tag {
  margin-right: 4px;
}

.user-list__muted {
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.user-list__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-neutral-100);
}

.user-list__batch {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.user-list__reset-tip {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  margin: 0 0 var(--space-md);
  line-height: 1.6;
}
</style>
