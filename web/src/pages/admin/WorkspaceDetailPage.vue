<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  addWorkspaceMembers,
  dissolveWorkspace,
  fetchRoleList,
  fetchUsers,
  fetchWorkspaceDetail,
  fetchWorkspaceMembers,
  removeWorkspaceMember,
  updateWorkspace,
  updateWorkspaceMemberRole,
} from '@/services/admin'
import type { AdminWorkspace, WorkspaceMember } from '@/types'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const workspaceId = route.params.id as string

const roleOptions = ref<{ value: string; label: string }[]>([])
const defaultRoleId = ref('')

async function loadRoleOptions() {
  try {
    const list = await fetchRoleList('workspace')
    roleOptions.value = list.map((r) => ({ value: r.id, label: r.name }))
    // 默认选中第一个角色
    if (list.length > 0) {
      defaultRoleId.value = list[0].id
    }
  } catch {
    // 角色选项加载失败不阻塞页面
  }
}

const detail = ref<AdminWorkspace | null>(null)
const infoLoading = ref(false)
const infoSaving = ref(false)
const infoFormRef = ref<FormInstance>()
const infoForm = reactive({ name: '', description: '' })
const infoRules: FormRules = {
  name: [
    { required: true, message: '请输入工作空间名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度需在 2-50 字符之间', trigger: 'blur' },
  ],
}

async function loadDetail() {
  infoLoading.value = true
  try {
    const data = await fetchWorkspaceDetail(workspaceId)
    detail.value = data
    infoForm.name = data.name
    infoForm.description = data.description ?? ''
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载工作空间详情失败')
  } finally {
    infoLoading.value = false
  }
}

async function saveInfo() {
  if (!infoFormRef.value) return
  try {
    await infoFormRef.value.validate()
  } catch {
    return
  }
  infoSaving.value = true
  try {
    const updated = await updateWorkspace(workspaceId, {
      name: infoForm.name.trim(),
      description: infoForm.description.trim(),
    })
    detail.value = updated
    ElMessage.success('已保存')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    infoSaving.value = false
  }
}

const members = ref<WorkspaceMember[]>([])
const membersLoading = ref(false)
const memberTotal = ref(0)
const memberQuery = reactive({ pageNo: 1, pageSize: 20 })

async function loadMembers() {
  membersLoading.value = true
  try {
    const page = await fetchWorkspaceMembers(workspaceId, {
      pageNo: memberQuery.pageNo,
      pageSize: memberQuery.pageSize,
    })
    members.value = page.list
    memberTotal.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载成员列表失败')
  } finally {
    membersLoading.value = false
  }
}

async function handleRoleChange(member: WorkspaceMember, next: string) {
  try {
    await updateWorkspaceMemberRole(workspaceId, member.userId, next)
    member.workspaceRole = next
    ElMessage.success('角色已更新')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '更新角色失败')
    loadMembers()
  }
}

async function handleRemoveMember(member: WorkspaceMember) {
  try {
    await ElMessageBox.confirm(`确定要移除成员「${member.username}」吗？`, '确认移除', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await removeWorkspaceMember(workspaceId, member.userId)
    ElMessage.success('已移除')
    loadMembers()
    loadDetail()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '移除失败')
  }
}

const addDialogVisible = ref(false)
const addSubmitting = ref(false)
const userSearchLoading = ref(false)
const userOptions = ref<{ id: string; username: string; email: string }[]>([])
const selectedUserIds = ref<string[]>([])
const pendingRoles = reactive<Record<string, string>>({})

function openAddDialog() {
  selectedUserIds.value = []
  userOptions.value = []
  Object.keys(pendingRoles).forEach((k) => delete pendingRoles[k])
  addDialogVisible.value = true
}

async function searchUsers(keyword: string) {
  if (!keyword) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const page = await fetchUsers({ keyword, status: 'active', pageNo: 1, pageSize: 20 })
    userOptions.value = page.list
      .filter((u) => !u.roles.some((r) => r.type === 'system'))
      .map((u) => ({ id: u.id, username: u.username, email: u.email }))
  } catch {
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

function handleUserSelectChange(ids: string[]) {
  ids.forEach((id) => {
    if (!pendingRoles[id]) pendingRoles[id] = defaultRoleId.value
  })
  Object.keys(pendingRoles).forEach((id) => {
    if (!ids.includes(id)) delete pendingRoles[id]
  })
}

const selectedUsers = computed(() =>
  selectedUserIds.value.map((id) => {
    const opt = userOptions.value.find((u) => u.id === id)
    return { id, username: opt?.username ?? id, email: opt?.email ?? '' }
  }),
)

async function submitAddMembers() {
  if (!selectedUserIds.value.length) {
    ElMessage.warning('请至少选择一个用户')
    return
  }
  addSubmitting.value = true
  try {
    await addWorkspaceMembers(
      workspaceId,
      selectedUserIds.value.map((id) => ({
        userId: id,
        workspaceRole: pendingRoles[id] ?? defaultRoleId.value,
      })),
    )
    ElMessage.success('成员已添加')
    addDialogVisible.value = false
    loadMembers()
    loadDetail()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '添加成员失败')
  } finally {
    addSubmitting.value = false
  }
}

async function handleDissolve() {
  if (!detail.value) return
  if (detail.value.projectCount > 0) {
    ElMessage.warning('该工作空间下仍有项目，无法解散')
    return
  }
  try {
    await ElMessageBox.prompt(
      `解散后数据不可恢复。请输入工作空间名称「${detail.value.name}」以确认解散。`,
      '解散工作空间',
      {
        type: 'warning',
        inputPlaceholder: '请输入工作空间名称',
        inputValidator: (val: string) => val === detail.value?.name || '名称不匹配',
      },
    )
  } catch {
    return
  }
  try {
    await dissolveWorkspace(workspaceId)
    ElMessage.success('工作空间已解散')
    router.push('/admin/workspaces')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '解散失败')
  }
}

onMounted(() => {
  loadRoleOptions()
  loadDetail()
  loadMembers()
})
</script>

<template>
  <div class="ws-detail">
    <div class="ws-detail__header">
      <el-page-header @back="router.push('/admin/workspaces')">
        <template #content>
          <span class="ws-detail__title">{{ detail?.name || '工作空间详情' }}</span>
        </template>
      </el-page-header>
    </div>

    <el-card v-loading="infoLoading" shadow="never" class="ws-detail__section">
      <template #header><span class="ws-detail__section-title">基本信息</span></template>
      <el-form
        ref="infoFormRef"
        :model="infoForm"
        :rules="infoRules"
        label-width="80px"
        class="ws-detail__form"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="infoForm.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="infoForm.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="统计">
          <div class="ws-detail__stats">
            <div class="ws-detail__stat-badge ws-detail__stat-badge--primary">
              <el-icon><User /></el-icon>
              成员 {{ detail?.memberCount ?? 0 }}
            </div>
            <div class="ws-detail__stat-badge ws-detail__stat-badge--blue">
              <el-icon><Folder /></el-icon>
              项目 {{ detail?.projectCount ?? 0 }}
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="infoSaving" @click="saveInfo">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="ws-detail__section">
      <template #header>
        <div class="ws-detail__section-header">
          <span class="ws-detail__section-title">成员管理</span>
          <el-button type="primary" size="small" @click="openAddDialog">
            <el-icon><Plus /></el-icon>添加成员
          </el-button>
        </div>
      </template>
      <el-table v-loading="membersLoading" :data="members" row-key="userId">
        <el-table-column label="用户名" min-width="140">
          <template #default="{ row }">
            <div class="ws-detail__user">
              <el-avatar :size="28" :src="row.avatarUrl || undefined">
                {{ row.username.charAt(0).toUpperCase() }}
              </el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-select
              :model-value="row.workspaceRole"
              size="small"
              @change="(val: string) => handleRoleChange(row as WorkspaceMember, val)"
            >
              <el-option
                v-for="opt in roleOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.joinedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemoveMember(row as WorkspaceMember)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="ws-detail__pager">
        <el-pagination
          v-model:current-page="memberQuery.pageNo"
          v-model:page-size="memberQuery.pageSize"
          :total="memberTotal"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadMembers"
          @size-change="loadMembers"
        />
      </div>
    </el-card>

    <el-card shadow="never" class="ws-detail__section ws-detail__danger">
      <template #header><span class="ws-detail__section-title">危险操作</span></template>
      <div class="ws-detail__danger-body">
        <div>
          <div class="ws-detail__danger-label">解散工作空间</div>
          <div class="ws-detail__danger-tip">解散后数据不可恢复，且需先清空所有项目。</div>
        </div>
        <el-button type="danger" @click="handleDissolve">解散工作空间</el-button>
      </div>
    </el-card>

    <el-dialog v-model="addDialogVisible" title="添加成员" width="520px">
      <el-form label-width="80px">
        <el-form-item label="选择用户">
          <el-select
            v-model="selectedUserIds"
            multiple
            filterable
            remote
            reserve-keyword
            placeholder="输入用户名 / 邮箱搜索"
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
            @change="handleUserSelectChange"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="`${u.username} (${u.email})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="selectedUsers.length" class="ws-detail__pending">
        <div v-for="u in selectedUsers" :key="u.id" class="ws-detail__pending-item">
          <span class="ws-detail__pending-name">{{ u.username }}</span>
          <el-select v-model="pendingRoles[u.id]" size="small" style="width: 120px">
            <el-option
              v-for="opt in roleOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="submitAddMembers">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.ws-detail__header {
  margin-bottom: var(--space-xl);
}

.ws-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.ws-detail__section {
  margin-bottom: var(--space-lg);
}

.ws-detail__section-title {
  font-weight: 600;
  font-size: var(--font-size-sm);
}

.ws-detail__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ws-detail__form {
  max-width: 560px;
}

.ws-detail__stats {
  display: flex;
  gap: var(--space-md);
}

.ws-detail__stat-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-xs) var(--space-md);
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.ws-detail__stat-badge--primary {
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.ws-detail__stat-badge--blue {
  background: #eff6ff;
  color: #1d4ed8;
}

.ws-detail__user {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.ws-detail__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-lg);
}

.ws-detail__danger {
  border: 1px solid var(--color-danger-200);
  background: var(--color-danger-50);
}

.ws-detail__danger-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ws-detail__danger-label {
  font-weight: 600;
  color: var(--color-danger-700);
}

.ws-detail__danger-tip {
  margin-top: 4px;
  font-size: var(--font-size-xs);
  color: var(--color-danger-500);
}

.ws-detail__pending {
  border-top: 1px solid var(--color-neutral-200);
  padding-top: var(--space-md);
}

.ws-detail__pending-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) 0;
}

.ws-detail__pending-name {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
}
</style>
