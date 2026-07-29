<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  addMembers,
  createInvitation,
  fetchInvitations,
  fetchMembers,
  removeMember,
  revokeInvitation,
  updateMemberRole,
} from '@/services/workspace'
import { fetchRoleList, fetchSimpleUserList } from '@/services/admin'
import type { Invitation, UserSimple, WorkspaceMember } from '@/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const authStore = useAuthStore()

const canManageMember = computed(() => authStore.hasPermission('ws-member:manage'))
const canViewInvitation = computed(() => authStore.hasPermission('ws-invitation:view'))
const canManageInvitation = computed(() => authStore.hasPermission('ws-invitation:manage'))
const currentUserId = computed(() => authStore.user?.id ?? '')
const activeTab = ref('members')

const roleOptions = ref<{ value: string; label: string }[]>([])
const defaultRoleId = ref('')

async function loadRoleOptions() {
  try {
    const list = await fetchRoleList('workspace')
    roleOptions.value = list.map((r) => ({ value: r.id, label: r.name }))
    if (list.length > 0) {
      defaultRoleId.value = list[0].id
    }
  } catch {
    // 角色选项加载失败不阻塞页面
  }
}

function workspaceRoleLabel(roleId: string): string {
  const role = roleOptions.value.find((r) => r.value === roleId)
  return role?.label ?? '未知'
}

const members = ref<WorkspaceMember[]>([])
const membersLoading = ref(false)
const memberTotal = ref(0)
const memberQuery = reactive({ keyword: '', pageNo: 1, pageSize: 20 })

async function loadMembers() {
  membersLoading.value = true
  try {
    const page = await fetchMembers({ keyword: memberQuery.keyword || undefined, pageNo: memberQuery.pageNo, pageSize: memberQuery.pageSize })
    members.value = page.list
    memberTotal.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载成员列表失败')
  } finally {
    membersLoading.value = false
  }
}

function handleResetMembers() {
  memberQuery.keyword = ''
  memberQuery.pageNo = 1
  loadMembers()
}

async function handleRoleChange(member: WorkspaceMember, next: string) {
  try {
    await updateMemberRole(member.userId, next)
    member.workspaceRole = next
    ElMessage.success('角色已更新')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '更新角色失败')
    loadMembers()
  }
}

async function handleRemoveMember(member: WorkspaceMember) {
  const isSelf = member.userId === currentUserId.value
  const msg = isSelf ? '确定退出该工作空间？退出后将无法访问此空间的资源。' : `确定要移除成员「${member.username}」吗？`
  try {
    await ElMessageBox.confirm(msg, isSelf ? '退出工作空间' : '确认移除', { type: 'warning' })
  } catch {
    return
  }
  try {
    await removeMember(member.userId)
    ElMessage.success(isSelf ? '已退出工作空间' : '已移除')
    if (isSelf) {
      authStore.setActiveWorkspace(null)
      router.push('/workspaces')
    } else {
      loadMembers()
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '移除失败')
  }
}

const addDialogVisible = ref(false)
const addSubmitting = ref(false)
const userSearchLoading = ref(false)
const userOptions = ref<UserSimple[]>([])
const selectedUserIds = ref<string[]>([])

function openAddDialog() {
  selectedUserIds.value = []
  userOptions.value = []
  addDialogVisible.value = true
}

async function searchUsers(keyword: string) {
  if (!keyword) { userOptions.value = []; return }
  userSearchLoading.value = true
  try {
    userOptions.value = await fetchSimpleUserList(keyword)
  } catch {
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

async function submitAddMembers() {
  if (!selectedUserIds.value.length) {
    ElMessage.warning('请至少选择一个用户')
    return
  }
  addSubmitting.value = true
  try {
    const result = await addMembers(
      selectedUserIds.value.map((id) => ({ userId: id, workspaceRole: defaultRoleId.value })),
    )
    const msg = result.skippedUserIds.length
      ? `成功添加 ${result.successCount} 人，${result.skippedUserIds.length} 人已在空间中跳过`
      : `成功添加 ${result.successCount} 人`
    ElMessage.success(msg)
    addDialogVisible.value = false
    loadMembers()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '添加成员失败')
  } finally {
    addSubmitting.value = false
  }
}

const invitations = ref<Invitation[]>([])
const invitationsLoading = ref(false)
const invitationTotal = ref(0)
const invQuery = reactive({ pageNo: 1, pageSize: 20 })

async function loadInvitations() {
  invitationsLoading.value = true
  try {
    const page = await fetchInvitations({ pageNo: invQuery.pageNo, pageSize: invQuery.pageSize })
    invitations.value = page.list
    invitationTotal.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载邀请链接失败')
  } finally {
    invitationsLoading.value = false
  }
}

const createDialogVisible = ref(false)
const createForm = reactive({ expiresAt: '' as string, maxUses: null as number | null })
const createSubmitting = ref(false)

function openCreateDialog() {
  createForm.expiresAt = ''
  createForm.maxUses = null
  createDialogVisible.value = true
}

async function submitCreateInvitation() {
  createSubmitting.value = true
  try {
    await createInvitation({
      expiresAt: createForm.expiresAt || null,
      maxUses: createForm.maxUses,
    })
    ElMessage.success('邀请链接已创建')
    createDialogVisible.value = false
    loadInvitations()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  } finally {
    createSubmitting.value = false
  }
}

function getInviteUrl(token: string): string {
  return `${window.location.origin}/join?token=${token}`
}

async function handleCopyLink(token: string) {
  try {
    await navigator.clipboard.writeText(getInviteUrl(token))
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

async function handleRevoke(inv: Invitation) {
  try {
    await ElMessageBox.confirm('确定要撤销该邀请链接吗？撤销后将不可再使用。', '确认撤销', { type: 'warning' })
  } catch {
    return
  }
  try {
    await revokeInvitation(inv.id)
    ElMessage.success('已撤销')
    loadInvitations()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '撤销失败')
  }
}

function handleTabChange(tab: string | number) {
  if (tab === 'invitations' && !invitations.value.length) {
    loadInvitations()
  }
}

onMounted(() => {
  loadRoleOptions()
  loadMembers()
})
</script>

<template>
  <div class="member-page">

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="成员列表" name="members">
          <div class="member-page__toolbar">
            <el-input
              v-model="memberQuery.keyword"
              placeholder="搜索成员"
              clearable
              style="width: 200px"
              @keyup.enter="() => { memberQuery.pageNo = 1; loadMembers() }"
              @clear="() => { memberQuery.pageNo = 1; loadMembers() }"
            />
            <el-button type="primary" @click="() => { memberQuery.pageNo = 1; loadMembers() }">
              <el-icon><Search /></el-icon>搜索
            </el-button>
            <el-button @click="handleResetMembers">重置</el-button>
            <div class="member-page__toolbar-spacer" />
            <el-button v-if="canManageMember" type="primary" size="small" @click="openAddDialog">
              <el-icon><Plus /></el-icon>添加成员
            </el-button>
          </div>
          <el-table v-loading="membersLoading" :data="members" row-key="userId">
            <el-table-column label="用户" min-width="180">
              <template #default="{ row }">
                <div class="member-page__user">
                  <el-avatar :size="30" :src="row.avatarUrl || undefined">
                    {{ row.username.charAt(0).toUpperCase() }}
                  </el-avatar>
                  <div>
                    <div class="member-page__username">{{ row.username }}</div>
                    <div class="member-page__email">{{ row.email }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="140">
              <template #default="{ row }">
                <el-select
                  v-if="canManageMember"
                  :model-value="row.workspaceRole"
                  size="small"
                  @change="(val: string) => handleRoleChange(row as WorkspaceMember, val)"
                >
                  <el-option v-for="opt in roleOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-tag v-else size="small" effect="light" round>{{ workspaceRoleLabel(row.workspaceRole) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="加入时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.joinedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canManageMember || row.userId === currentUserId"
                  link
                  type="danger"
                  @click="handleRemoveMember(row as WorkspaceMember)"
                >
                  {{ row.userId === currentUserId ? '退出' : '移除' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="member-page__pager">
            <el-pagination
              v-model:current-page="memberQuery.pageNo"
              :total="memberTotal"
              :page-size="memberQuery.pageSize"
              layout="total, prev, pager, next"
              @current-change="loadMembers"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canViewInvitation" label="邀请链接" name="invitations">
          <div class="member-page__toolbar">
            <el-button v-if="canManageInvitation" type="primary" size="small" @click="openCreateDialog">
              <el-icon><Link /></el-icon>创建邀请链接
            </el-button>
          </div>
          <el-table v-loading="invitationsLoading" :data="invitations" row-key="id">
            <el-table-column label="链接" min-width="200">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleCopyLink(row.token)">复制链接</el-button>
              </template>
            </el-table-column>
            <el-table-column label="使用次数" width="120">
              <template #default="{ row }">
                {{ row.useCount }}{{ row.maxUses != null ? ` / ${row.maxUses}` : ' / 不限' }}
              </template>
            </el-table-column>
            <el-table-column label="过期时间" width="170">
              <template #default="{ row }">
                {{ row.expiresAt ? formatDateTime(row.expiresAt) : '永不过期' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
                  {{ row.status === 'active' ? '有效' : '已撤销' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'active'"
                  link
                  type="danger"
                  @click="handleRevoke(row as Invitation)"
                >
                  撤销
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="member-page__pager">
            <el-pagination
              v-model:current-page="invQuery.pageNo"
              :total="invitationTotal"
              :page-size="invQuery.pageSize"
              layout="total, prev, pager, next"
              @current-change="loadInvitations"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="addDialogVisible" title="添加成员" width="480px">
      <el-select
        v-model="selectedUserIds"
        multiple
        filterable
        remote
        reserve-keyword
        placeholder="输入姓名/用户名/邮箱搜索"
        :remote-method="searchUsers"
        :loading="userSearchLoading"
        style="width: 100%"
      >
        <el-option
          v-for="u in userOptions"
          :key="u.id"
          :label="u.name"
          :value="u.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="submitAddMembers">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createDialogVisible" title="创建邀请链接" width="440px">
      <el-form label-width="100px">
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="createForm.expiresAt"
            type="datetime"
            placeholder="留空表示永不过期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="最大使用次数">
          <el-input-number v-model="createForm.maxUses" :min="1" :max="10000" placeholder="留空表示不限" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreateInvitation">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.member-page__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-md);
}

.member-page__toolbar-spacer {
  flex: 1;
}

.member-page__user {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.member-page__username {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-800);
}

.member-page__email {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}

.member-page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-lg);
}
</style>
