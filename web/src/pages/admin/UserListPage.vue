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

/** 状态三态文案与点标样式（交互设计 4.2.2；点标色义对应原 success/info/danger 标签） */
const STATUS_META: Record<UserStatus, { label: string; dot: string }> = {
  active: { label: '启用', dot: 'user-list__status--success' },
  disabled: { label: '禁用', dot: 'user-list__status--neutral' },
  locked: { label: '锁定', dot: 'user-list__status--danger' },
}

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

/** 状态流转（启用/禁用/锁定三态互转），均需二次确认（交互设计 4.2.3） */
async function handleChangeStatus(user: AdminUser, next: UserStatus) {
  const actionText = STATUS_META[next].label
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
  const actionText = STATUS_META[status].label
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
    <div class="user-list__head">
      <div>
        <h1 class="user-list__title">用户管理</h1>
        <p class="user-list__desc">平台账号、角色与启用状态</p>
      </div>
      <el-button type="primary" @click="router.push('/admin/users/create')">
        <el-icon><Plus /></el-icon>新建用户
      </el-button>
    </div>

    <section class="user-list__card">
      <div class="user-list__toolbar">
        <div class="user-list__filters">
          <el-input
            v-model="query.keyword"
            placeholder="搜索用户名 / 邮箱"
            clearable
            :prefix-icon="'Search'"
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
          <el-select
            v-model="query.status"
            placeholder="状态"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="disabled" />
            <el-option label="锁定" value="locked" />
          </el-select>
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
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <span class="user-list__count">共 {{ total }} 个账号</span>
      </div>

      <el-table
        v-loading="loading"
        :data="users"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="36" />
        <el-table-column label="用户" min-width="210">
          <template #default="{ row }">
            <div class="user-list__user">
              <span class="user-list__avatar">{{ row.name.charAt(0) || row.username.charAt(0) }}</span>
              <div class="user-list__lines">
                <el-link
                  type="primary"
                  underline="never"
                  @click="router.push(`/admin/users/${row.id}`)"
                >
                  {{ row.username }}
                </el-link>
                <span class="user-list__sub">{{ row.name }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="210">
          <template #default="{ row }">
            <span class="user-list__email">{{ row.email }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="user-list__status" :class="STATUS_META[row.status as UserStatus].dot">
              <span class="user-list__dot" />{{ STATUS_META[row.status as UserStatus].label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="系统角色" min-width="170">
          <template #default="{ row }">
            <template v-if="row.roles.length">
              <span v-for="role in row.roles" :key="role.id" class="user-list__role">
                {{ role.name }}
              </span>
            </template>
            <span v-else class="user-list__muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="user-list__num">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/admin/users/${row.id}`)">
              编辑
            </el-button>
            <el-button
              v-if="row.status !== 'active'"
              link
              type="primary"
              @click="handleChangeStatus(row as AdminUser, 'active')"
            >
              启用
            </el-button>
            <el-button
              v-if="row.status !== 'disabled'"
              link
              type="danger"
              @click="handleChangeStatus(row as AdminUser, 'disabled')"
            >
              禁用
            </el-button>
            <el-button
              v-if="row.status !== 'locked'"
              link
              type="danger"
              @click="handleChangeStatus(row as AdminUser, 'locked')"
            >
              锁定
            </el-button>
            <el-button link type="primary" @click="openResetDialog(row as AdminUser)">
              重置密码
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <footer class="user-list__footer">
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
      </footer>
    </section>

    <el-dialog v-model="resetDialogVisible" title="重置密码" width="420px">
      <p class="user-list__reset-tip">
        为用户「{{ resetTarget?.username }}」设置新密码（8-64
        字符，需包含大小写字母、数字、特殊字符中至少三种）。
      </p>
      <el-input v-model="resetPassword" type="password" placeholder="请输入新密码" show-password />
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="submitResetPassword">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.user-list__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--space-xl);
}

.user-list__title {
  margin: 0 0 var(--space-xs);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-neutral-900);
}

.user-list__desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

/* 单卡片承载 toolbar/表格/分页，对齐演示稿 users.html 结构（表格与分页的基础观感由全局变量层提供） */
.user-list__card {
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.user-list__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-neutral-100);
  flex-wrap: wrap;
}

.user-list__filters {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.user-list__count {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.user-list__user {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.user-list__avatar {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--color-neutral-700);
  color: var(--color-neutral-0);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-2xs);
  font-weight: 600;
}

.user-list__lines {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.user-list__lines .el-link {
  align-self: flex-start;
  height: auto;
  font-size: var(--font-size-base);
  line-height: inherit;
}

.user-list__sub {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.user-list__email {
  color: var(--color-neutral-500);
}

/* 状态点标对齐演示稿 status（圆点 + 文案），色义沿用 4.2.2 的三态映射 */
.user-list__status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  white-space: nowrap;
}

.user-list__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-neutral-400);
}

.user-list__status--success .user-list__dot {
  background: var(--color-success);
}

.user-list__status--danger .user-list__dot {
  background: var(--color-danger);
}

/* 描边标签对齐演示稿 tag--outline，避免填充色与状态点标语义冲突 */
.user-list__role {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  margin: 0 4px 4px 0;
  border: 1px solid var(--color-neutral-300);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-neutral-600);
  font-size: var(--font-size-xs);
  font-weight: 500;
  white-space: nowrap;
}

.user-list__num {
  font-variant-numeric: tabular-nums;
}

.user-list__muted {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.user-list__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 14px 20px;
  border-top: 1px solid var(--color-neutral-100);
  flex-wrap: wrap;
}

/* 空态占位保持高度稳定，避免批量操作区出现/消失时分页条跳动 */
.user-list__batch {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: 32px;
}

.user-list__reset-tip {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  margin: 0 0 var(--space-md);
  line-height: 1.6;
}
</style>
