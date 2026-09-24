<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  type ComponentPublicInstance,
} from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, Link, Plus, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  addMembers,
  createInvitation,
  fetchInvitationCopyLink,
  fetchInvitations,
  fetchMembers,
  removeMember,
  revokeInvitation,
  updateMemberRole,
} from '@/services/workspace'
import { fetchRoleList as fetchAdminRoleList, fetchSimpleUserList as fetchAdminUserList } from '@/services/admin'
import type { InvitationListItem, UserSimple, WorkspaceMember } from '@/types'
import { WORKSPACE_ROLE, workspaceRoleLabel } from '@/utils/workspaceRole'
import {
  canCopyInvitation,
  canExpireInvitation,
  invitationStatusMeta,
} from '@/utils/workspaceInvitation'
import { formatDateTime } from '@/utils/format'

const SEARCH_DEBOUNCE_MS = 300

const router = useRouter()
const authStore = useAuthStore()

const canManageMember = computed(() => authStore.hasPermission('ws-member:manage'))
const canManageInvitation = computed(() => authStore.hasPermission('ws-invitation:manage'))
const currentUserId = computed(() => authStore.user?.id ?? '')
const activeTab = ref('members')

const roleOptions = ref<{ value: string; label: string }[]>([])

async function loadRoleOptions() {
  try {
    const list = await fetchAdminRoleList('workspace')
    roleOptions.value = list
      .filter((role) => !role.isGroup)
      .map((role) => ({ value: role.id, label: role.name }))
  } catch {
    roleOptions.value = []
  }
}

function resolveWorkspaceRoleLabel(roleId: string): string {
  const roleName = roleOptions.value.find((role) => role.value === roleId)?.label
  return workspaceRoleLabel(roleId, roleName)
}

function isWorkspaceAdminRole(roleId: string): boolean {
  return roleId === WORKSPACE_ROLE.ADMIN
}

const members = ref<WorkspaceMember[]>([])
const membersLoading = ref(false)
const memberTotal = ref(0)
const memberQuery = reactive({ keyword: '', workspaceRole: '', pageNo: 1, pageSize: 20 })
let memberSearchTimer: ReturnType<typeof setTimeout> | null = null
let memberRequestId = 0

function cancelMemberSearch(): void {
  if (memberSearchTimer !== null) {
    clearTimeout(memberSearchTimer)
    memberSearchTimer = null
  }
}

async function loadMembers(): Promise<void> {
  const requestId = ++memberRequestId
  membersLoading.value = true
  try {
    const page = await fetchMembers({
      keyword: memberQuery.keyword.trim() || undefined,
      workspaceRole: memberQuery.workspaceRole || undefined,
      pageNo: memberQuery.pageNo,
      pageSize: memberQuery.pageSize,
    })
    if (requestId !== memberRequestId) return
    members.value = page.list
    memberTotal.value = page.total
  } catch (err) {
    if (requestId !== memberRequestId) return
    ElMessage.error(err instanceof Error ? err.message : '加载成员列表失败')
  } finally {
    if (requestId === memberRequestId) {
      membersLoading.value = false
    }
  }
}

function handleMemberSearchInput(): void {
  cancelMemberSearch()
  memberQuery.pageNo = 1
  memberSearchTimer = setTimeout(() => {
    memberSearchTimer = null
    void loadMembers()
  }, SEARCH_DEBOUNCE_MS)
}

function handleMemberSearchClear(): void {
  cancelMemberSearch()
  memberQuery.pageNo = 1
  void loadMembers()
}

function handleRoleFilterChange(): void {
  cancelMemberSearch()
  memberQuery.pageNo = 1
  void loadMembers()
}

const editingUserId = ref('')
const roleSelectRef = ref<ComponentPublicInstance>()

async function startEditRole(userId: string): Promise<void> {
  editingUserId.value = userId
  await nextTick()
  const select = roleSelectRef.value as unknown as { focus?: () => void } | undefined
  select?.focus?.()
}

async function handleRoleChange(member: WorkspaceMember, nextRoleId: string): Promise<void> {
  try {
    await updateMemberRole(member.userId, nextRoleId)
    member.workspaceRole = nextRoleId
    ElMessage.success('角色已更新')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '更新角色失败')
    void loadMembers()
  }
}

async function submitRoleChange(member: WorkspaceMember, nextRoleId: string): Promise<void> {
  editingUserId.value = ''
  await handleRoleChange(member, nextRoleId)
}

function handleRoleVisibleChange(visible: boolean): void {
  if (!visible) editingUserId.value = ''
}

async function handleRemoveMember(member: WorkspaceMember): Promise<void> {
  const isSelf = member.userId === currentUserId.value
  const message = isSelf
    ? '确定退出该工作空间？退出后将无法访问此空间的资源。'
    : `确定要移除成员「${member.name || member.username}」吗？`
  try {
    await ElMessageBox.confirm(message, isSelf ? '退出工作空间' : '确认移除', {
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await removeMember(member.userId)
    ElMessage.success(isSelf ? '已退出工作空间' : '已移除')
    if (isSelf) {
      authStore.setActiveWorkspace(null)
      await router.push('/workspaces')
    } else {
      void loadMembers()
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
let userSearchTimer: ReturnType<typeof setTimeout> | null = null
let userSearchRequestId = 0

function openAddDialog(): void {
  selectedUserIds.value = []
  userOptions.value = []
  addDialogVisible.value = true
}

function cancelUserSearch(): void {
  if (userSearchTimer !== null) {
    clearTimeout(userSearchTimer)
    userSearchTimer = null
  }
}

function searchUsers(keyword: string): void {
  cancelUserSearch()
  const requestId = ++userSearchRequestId
  const normalizedKeyword = keyword.trim()
  if (!normalizedKeyword) {
    userOptions.value = []
    userSearchLoading.value = false
    return
  }

  userSearchLoading.value = true
  userSearchTimer = setTimeout(async () => {
    userSearchTimer = null
    try {
      const users = await fetchAdminUserList(normalizedKeyword)
      if (requestId === userSearchRequestId) {
        userOptions.value = users
      }
    } catch {
      if (requestId === userSearchRequestId) {
        userOptions.value = []
      }
    } finally {
      if (requestId === userSearchRequestId) {
        userSearchLoading.value = false
      }
    }
  }, SEARCH_DEBOUNCE_MS)
}

async function submitAddMembers(): Promise<void> {
  if (!selectedUserIds.value.length) {
    ElMessage.warning('请至少选择一个用户')
    return
  }
  addSubmitting.value = true
  try {
    const result = await addMembers(
      selectedUserIds.value.map((id) => ({
        userId: id,
        workspaceRole: WORKSPACE_ROLE.MEMBER,
      })),
    )
    const message = result.skippedUserIds.length
      ? `成功添加 ${result.successCount} 人，${result.skippedUserIds.length} 人已在空间中跳过`
      : `成功添加 ${result.successCount} 人`
    ElMessage.success(message)
    addDialogVisible.value = false
    void loadMembers()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '邀请成员失败')
  } finally {
    addSubmitting.value = false
  }
}

const invitations = ref<InvitationListItem[]>([])
const invitationsLoading = ref(false)
const invitationsLoaded = ref(false)
const invitationTotal = ref(0)
const invQuery = reactive({ pageNo: 1, pageSize: 20 })
let invitationRequestId = 0

async function loadInvitations(): Promise<boolean> {
  if (!canManageInvitation.value) return false
  const requestId = ++invitationRequestId
  invitationsLoading.value = true
  try {
    const page = await fetchInvitations({ pageNo: invQuery.pageNo, pageSize: invQuery.pageSize })
    if (requestId !== invitationRequestId) return false
    invitations.value = page.list
    invitationTotal.value = page.total
    invitationsLoaded.value = true
    return true
  } catch (err) {
    if (requestId === invitationRequestId) {
      ElMessage.error(err instanceof Error ? err.message : '加载邀请链接失败')
    }
    return false
  } finally {
    if (requestId === invitationRequestId) {
      invitationsLoading.value = false
    }
  }
}

function invitationUses(invitation: InvitationListItem): string {
  return `${invitation.useCount} / ${invitation.maxUses ?? '不限'}`
}

function getInviteUrl(token: string): string {
  return `${window.location.origin}/join?token=${encodeURIComponent(token)}`
}

const copyingInvitationId = ref('')
const copyingLatestInvitation = ref(false)

async function copyInvitation(invitation: InvitationListItem): Promise<void> {
  if (!canCopyInvitation(invitation) || copyingInvitationId.value) return
  copyingInvitationId.value = invitation.id
  try {
    const result = await fetchInvitationCopyLink(invitation.id)
    if (!navigator.clipboard) throw new Error('当前浏览器不支持自动复制')
    await navigator.clipboard.writeText(getInviteUrl(result.token))
    ElMessage.success('邀请链接已复制')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '复制邀请链接失败')
  } finally {
    copyingInvitationId.value = ''
  }
}

async function handleCopyLatestInvitation(): Promise<void> {
  if (copyingLatestInvitation.value) return
  copyingLatestInvitation.value = true
  try {
    if (!invitationsLoaded.value && !(await loadInvitations())) return
    const latest = invitations.value.find((invitation) => invitation.effectiveStatus === 'active')
    if (!latest) {
      ElMessage.warning('暂无可复制的有效邀请链接')
      return
    }
    await copyInvitation(latest)
  } finally {
    copyingLatestInvitation.value = false
  }
}

const revokingInvitationId = ref('')

async function handleExpireInvitation(invitation: InvitationListItem): Promise<void> {
  if (!canExpireInvitation(invitation) || revokingInvitationId.value) return
  try {
    await ElMessageBox.confirm('确定要使该邀请链接失效吗？失效后将不可再使用。', '确认失效', {
      type: 'warning',
    })
  } catch {
    return
  }

  revokingInvitationId.value = invitation.id
  try {
    await revokeInvitation(invitation.id)
    ElMessage.success('邀请链接已失效')
    void loadInvitations()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '邀请链接失效失败')
  } finally {
    revokingInvitationId.value = ''
  }
}

const createDialogVisible = ref(false)
const createForm = reactive({ expiresAt: '' as string, maxUses: null as number | null })
const createSubmitting = ref(false)
const createdInviteLink = ref('')
const linkDialogVisible = ref(false)

function openCreateDialog(): void {
  createForm.expiresAt = ''
  createForm.maxUses = null
  createDialogVisible.value = true
}

async function submitCreateInvitation(): Promise<void> {
  createSubmitting.value = true
  try {
    const invitation = await createInvitation({
      expiresAt: createForm.expiresAt || null,
      maxUses: createForm.maxUses,
    })
    ElMessage.success('邀请链接已创建')
    createDialogVisible.value = false
    createdInviteLink.value = getInviteUrl(invitation.token)
    linkDialogVisible.value = true
    void loadInvitations()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建邀请链接失败')
  } finally {
    createSubmitting.value = false
  }
}

async function handleCopyLink(url: string): Promise<void> {
  try {
    if (!navigator.clipboard) throw new Error('当前浏览器不支持自动复制')
    await navigator.clipboard.writeText(url)
    ElMessage.success('已复制')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '复制失败，请手动复制')
  }
}

function handleTabChange(tab: string | number): void {
  if (tab === 'invitations' && canManageInvitation.value && !invitationsLoaded.value) {
    void loadInvitations()
  }
}

onMounted(() => {
  void loadRoleOptions()
  void loadMembers()
  if (canManageInvitation.value) {
    void loadInvitations()
  }
})

onBeforeUnmount(() => {
  cancelMemberSearch()
  cancelUserSearch()
  memberRequestId += 1
  userSearchRequestId += 1
  invitationRequestId += 1
})
</script>

<template>
  <div class="member-page">
    <header class="member-page__head">
      <div>
        <h1 class="member-page__title">成员管理</h1>
        <p class="member-page__subtitle">管理空间成员与角色，通过邀请链接扩招</p>
      </div>
      <div class="member-page__head-actions">
        <el-button
          v-if="canManageInvitation"
          :loading="invitationsLoading || copyingLatestInvitation"
          @click="handleCopyLatestInvitation"
        >
          <el-icon><Link /></el-icon>复制邀请链接
        </el-button>
        <el-button v-if="canManageMember" type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon>邀请成员
        </el-button>
      </div>
    </header>

    <el-card shadow="never" class="member-page__card">
      <el-tabs v-model="activeTab" class="member-page__tabs" @tab-change="handleTabChange">
        <el-tab-pane label="成员列表" name="members">
          <section class="member-page__panel">
            <header class="member-page__panel-head">
              <div class="member-page__title-group">
                <h2 class="member-page__panel-title">成员列表</h2>
                <span class="member-page__panel-count">{{ memberTotal }} 人</span>
              </div>
              <div class="member-page__filters">
                <el-input
                  v-model="memberQuery.keyword"
                  class="member-page__search"
                  placeholder="姓名 / 邮箱"
                  clearable
                  :prefix-icon="Search"
                  @input="handleMemberSearchInput"
                  @clear="handleMemberSearchClear"
                />
                <el-select
                  v-model="memberQuery.workspaceRole"
                  class="member-page__role-filter"
                  placeholder="全部角色"
                  clearable
                  @change="handleRoleFilterChange"
                >
                  <el-option
                    v-for="role in roleOptions"
                    :key="role.value"
                    :label="role.label"
                    :value="role.value"
                  />
                </el-select>
              </div>
            </header>

            <el-table
              v-loading="membersLoading"
              class="member-page__table"
              :data="members"
              row-key="userId"
              empty-text="暂无符合条件的成员"
            >
              <el-table-column label="用户" min-width="220">
                <template #default="{ row }">
                  <div class="member-page__user">
                    <el-avatar :size="32" :src="row.avatarUrl || undefined">
                      {{ (row.name || row.username).charAt(0).toUpperCase() }}
                    </el-avatar>
                    <div class="member-page__user-copy">
                      <div class="member-page__user-name">{{ row.name || row.username }}</div>
                      <div class="member-page__user-email">{{ row.email }}</div>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="角色" min-width="150">
                <template #default="{ row }">
                  <el-select
                    v-if="editingUserId === row.userId"
                    ref="roleSelectRef"
                    :model-value="row.workspaceRole"
                    size="small"
                    automatic-dropdown
                    @change="(value: string) => submitRoleChange(row as WorkspaceMember, value)"
                    @visible-change="handleRoleVisibleChange"
                  >
                    <el-option
                      v-for="role in roleOptions"
                      :key="role.value"
                      :label="role.label"
                      :value="role.value"
                    />
                  </el-select>
                  <span
                    v-else
                    class="member-page__role-tag"
                    :class="{ 'member-page__role-tag--admin': isWorkspaceAdminRole(row.workspaceRole) }"
                  >
                    {{ resolveWorkspaceRoleLabel(row.workspaceRole) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="加入时间" min-width="170">
                <template #default="{ row }">
                  <span class="member-page__num">{{ formatDateTime(row.joinedAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <div class="member-page__row-actions">
                    <el-button
                      v-if="canManageMember"
                      link
                      type="primary"
                      @click="startEditRole(row.userId)"
                    >
                      改角色
                    </el-button>
                    <el-button
                      v-if="canManageMember || row.userId === currentUserId"
                      link
                      type="danger"
                      @click="handleRemoveMember(row as WorkspaceMember)"
                    >
                      {{ row.userId === currentUserId ? '退出' : '移除' }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <footer class="member-page__pager">
              <span>共 {{ memberTotal }} 人 · 每页 {{ memberQuery.pageSize }} 条</span>
              <el-pagination
                v-model:current-page="memberQuery.pageNo"
                background
                layout="prev, pager, next"
                :page-size="memberQuery.pageSize"
                :pager-count="5"
                :total="memberTotal"
                @current-change="loadMembers"
              />
            </footer>
          </section>
        </el-tab-pane>

        <el-tab-pane v-if="canManageInvitation" label="邀请链接" name="invitations">
          <section class="member-page__panel">
            <header class="member-page__panel-head">
              <div class="member-page__title-group member-page__title-group--wrap">
                <h2 class="member-page__panel-title">邀请链接</h2>
                <span class="member-page__panel-description">通过链接加入的成员按默认角色进入</span>
              </div>
              <el-button type="primary" @click="openCreateDialog">
                <el-icon><Plus /></el-icon>生成链接
              </el-button>
            </header>

            <el-table
              v-loading="invitationsLoading"
              class="member-page__table"
              :data="invitations"
              row-key="id"
              empty-text="暂无邀请链接"
            >
              <el-table-column label="邀请链接" min-width="190">
                <template #default="{ row }">
                  <span class="member-page__token">join/{{ row.tokenPreview || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="使用次数" min-width="120">
                <template #default="{ row }">
                  <span class="member-page__num">{{ invitationUses(row as InvitationListItem) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="过期时间" min-width="170">
                <template #default="{ row }">
                  <span class="member-page__num">
                    {{ row.expiresAt ? formatDateTime(row.expiresAt) : '永不过期' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="状态" min-width="120">
                <template #default="{ row }">
                  <span
                    class="member-page__status"
                    :class="`member-page__status--${invitationStatusMeta(row.effectiveStatus).tone}`"
                  >
                    <span class="member-page__status-dot" />
                    {{ invitationStatusMeta(row.effectiveStatus).label }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" min-width="170">
                <template #default="{ row }">
                  <span class="member-page__num">{{ formatDateTime(row.createdAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="{ row }">
                  <div class="member-page__row-actions">
                    <el-button
                      link
                      type="primary"
                      :disabled="!canCopyInvitation(row as InvitationListItem)"
                      :loading="copyingInvitationId === row.id"
                      @click="copyInvitation(row as InvitationListItem)"
                    >
                      复制
                    </el-button>
                    <el-button
                      v-if="canExpireInvitation(row as InvitationListItem)"
                      link
                      type="danger"
                      :loading="revokingInvitationId === row.id"
                      @click="handleExpireInvitation(row as InvitationListItem)"
                    >
                      失效
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <footer class="member-page__pager">
              <span>共 {{ invitationTotal }} 条 · 每页 {{ invQuery.pageSize }} 条</span>
              <el-pagination
                v-model:current-page="invQuery.pageNo"
                background
                layout="prev, pager, next"
                :page-size="invQuery.pageSize"
                :pager-count="5"
                :total="invitationTotal"
                @current-change="loadInvitations"
              />
            </footer>
          </section>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="addDialogVisible" title="邀请成员" width="520px">
      <p class="member-page__dialog-tip">新成员将使用空间成员角色加入。</p>
      <el-select
        v-model="selectedUserIds"
        class="member-page__user-select"
        multiple
        filterable
        remote
        reserve-keyword
        placeholder="输入姓名、用户名或邮箱搜索"
        :remote-method="searchUsers"
        :loading="userSearchLoading"
      >
        <el-option v-for="user in userOptions" :key="user.id" :label="user.name" :value="user.id" />
      </el-select>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="submitAddMembers">
          确认邀请
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createDialogVisible" title="生成邀请链接" width="460px">
      <el-form label-position="top">
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="createForm.expiresAt"
            type="datetime"
            placeholder="留空表示永不过期"
            class="member-page__date-picker"
          />
        </el-form-item>
        <el-form-item label="最大使用次数">
          <el-input-number
            v-model="createForm.maxUses"
            :min="1"
            :max="10000"
            placeholder="留空表示不限"
            class="member-page__uses-input"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreateInvitation">
          生成
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="linkDialogVisible"
      title="邀请链接已创建"
      width="540px"
      :close-on-click-modal="false"
    >
      <p class="member-page__dialog-tip">请复制链接并发送给受邀成员：</p>
      <el-input :model-value="createdInviteLink" readonly>
        <template #append>
          <el-button :icon="CopyDocument" @click="handleCopyLink(createdInviteLink)">复制</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="linkDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.member-page {
  min-width: 0;
}

.member-page__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-xl);
  margin-bottom: var(--block-gap);
}

.member-page__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.member-page__subtitle {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.member-page__head-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-sm);
}

.member-page__card {
  overflow: hidden;
  border-radius: var(--radius-lg);
}

.member-page__card :deep(.el-card__body) {
  padding: 0;
}

.member-page__tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 var(--card-pad);
  border-bottom: 1px solid var(--color-neutral-200);
}

.member-page__tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.member-page__tabs :deep(.el-tabs__item) {
  height: 52px;
  padding: 0 var(--space-md);
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.member-page__tabs :deep(.el-tabs__item.is-active) {
  color: var(--color-primary-600);
  font-weight: 600;
}

.member-page__panel {
  min-width: 0;
}

.member-page__panel-head {
  display: flex;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding: var(--space-md) var(--card-pad);
}

.member-page__title-group {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.member-page__title-group--wrap {
  flex-wrap: wrap;
}

.member-page__panel-title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-base);
  font-weight: 600;
}

.member-page__panel-count,
.member-page__panel-description {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.member-page__filters {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.member-page__search {
  width: 220px;
}

.member-page__role-filter {
  width: 160px;
}

.member-page__table {
  width: 100%;
}

.member-page__table :deep(.el-table__header th.el-table__cell) {
  height: 44px;
  background: var(--color-neutral-50);
  color: var(--color-neutral-600);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.member-page__table :deep(.el-table__cell) {
  padding: 11px 0;
}

.member-page__user {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.member-page__user-copy {
  min-width: 0;
}

.member-page__user-name {
  overflow: hidden;
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-page__user-email {
  overflow: hidden;
  margin-top: 2px;
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-page__role-tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 1px var(--space-sm);
  border: 1px solid var(--color-neutral-300);
  border-radius: var(--radius-sm);
  background: var(--color-neutral-0);
  color: var(--color-neutral-600);
  font-size: var(--font-size-xs);
  line-height: 1.4;
}

.member-page__role-tag--admin {
  border-color: var(--color-primary-200);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.member-page__num {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}

.member-page__row-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.member-page__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  min-height: 60px;
  padding: var(--space-md) var(--card-pad);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.member-page__pager :deep(.el-pagination) {
  --el-pagination-button-bg-color: var(--color-neutral-0);
  --el-pagination-hover-color: var(--color-primary-600);
  flex-shrink: 0;
}

.member-page__token {
  color: var(--color-neutral-700);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.member-page__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.member-page__status-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: var(--radius-full);
  background: var(--color-neutral-400);
}

.member-page__status--success .member-page__status-dot {
  background: var(--color-success);
}

.member-page__status--danger .member-page__status-dot {
  background: var(--color-danger);
}

.member-page__dialog-tip {
  margin: 0 0 var(--space-md);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}

.member-page__user-select,
.member-page__date-picker,
.member-page__uses-input {
  width: 100%;
}

@media (max-width: 900px) {
  .member-page__head,
  .member-page__panel-head {
    align-items: stretch;
    flex-direction: column;
  }

  .member-page__head-actions {
    justify-content: flex-start;
  }

  .member-page__filters {
    width: 100%;
  }

  .member-page__search,
  .member-page__role-filter {
    width: auto;
    flex: 1 1 180px;
  }
}

@media (max-width: 640px) {
  .member-page__pager {
    align-items: flex-start;
    flex-direction: column;
  }

  .member-page__pager :deep(.el-pagination) {
    align-self: flex-end;
  }
}

</style>
