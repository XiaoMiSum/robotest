<script setup lang="ts">
import { nextTick, ref, type ComponentPublicInstance } from 'vue'
import { useWorkspaceDetail } from '@/composables/admin/useWorkspaceDetail'
import { WORKSPACE_ROLE, workspaceRoleLabel } from '@/services/admin'
import type { WorkspaceMember } from '@/types'
import { formatDateTime } from '@/utils/format'

const {
  roleOptions,
  detail,
  infoLoading,
  infoSaving,
  infoFormRef,
  infoForm,
  infoRules,
  saveInfo,
  resetInfo,
  members,
  membersLoading,
  memberTotal,
  memberQuery,
  loadMembers,
  handleRoleChange,
  handleRemoveMember,
  addDialogVisible,
  addSubmitting,
  userSearchLoading,
  userOptions,
  selectedUserIds,
  pendingRoles,
  openAddDialog,
  searchUsers,
  handleUserSelectChange,
  selectedUsers,
  submitAddMembers,
  handleDissolve,
} = useWorkspaceDetail()

function statusMeta(status?: string) {
  if (status === 'active') return { label: '活跃', dot: 'ws-detail__status--success' }
  if (status === 'dissolved') return { label: '已解散', dot: 'ws-detail__status--neutral' }
  return { label: status ?? '', dot: 'ws-detail__status--neutral' }
}

function roleTag(member: WorkspaceMember) {
  return {
    label:
      roleOptions.value.find((o) => o.value === member.workspaceRole)?.label ??
      workspaceRoleLabel(member.workspaceRole),
    isAdmin: member.workspaceRole === WORKSPACE_ROLE.ADMIN,
  }
}

const editingUserId = ref('')
const roleSelectRef = ref<ComponentPublicInstance>()

async function startEditRole(userId: string) {
  editingUserId.value = userId
  await nextTick()
  // el-select 聚焦配合 automatic-dropdown 可直接展开；聚焦不被接受时降级为手动点击一次
  const select = roleSelectRef.value as unknown as { focus?: () => void } | undefined
  select?.focus?.()
}

async function submitRoleChange(row: WorkspaceMember, next: string) {
  editingUserId.value = ''
  await handleRoleChange(row, next)
}

function handleRoleVisibleChange(visible: boolean) {
  if (!visible) editingUserId.value = ''
}
</script>

<template>
  <div class="ws-detail">
    <div class="ws-detail__breadcrumb">
      <router-link to="/admin/workspaces">空间管理</router-link>
      <el-icon :size="12"><ArrowRight /></el-icon>
      <span class="ws-detail__crumb-current">{{ detail?.name || '工作空间详情' }}</span>
    </div>

    <div class="ws-detail__head">
      <div class="ws-detail__head-main">
        <div class="ws-detail__title-row">
          <h1 class="ws-detail__title">{{ detail?.name || '工作空间详情' }}</h1>
          <span v-if="detail" class="ws-detail__status" :class="statusMeta(detail.status).dot">
            <span class="ws-detail__dot" />{{ statusMeta(detail.status).label }}
          </span>
        </div>
        <p class="ws-detail__desc">
          空间 ID <span class="ws-detail__mono">{{ detail?.id }}</span
          ><template v-if="detail?.createdAt"> · 创建于 {{ formatDateTime(detail.createdAt) }}</template>
        </p>
      </div>
      <el-button v-if="detail" type="danger" @click="handleDissolve">解散空间</el-button>
    </div>

    <section v-loading="infoLoading" class="ws-detail__card">
      <header class="ws-detail__card-head">
        <h3 class="ws-detail__card-title">空间信息</h3>
      </header>
      <div class="ws-detail__card-body">
        <el-form
          ref="infoFormRef"
          :model="infoForm"
          :rules="infoRules"
          label-position="top"
          class="ws-detail__form"
        >
          <div class="ws-detail__grid2">
            <el-form-item label="空间名称" prop="name">
              <el-input v-model="infoForm.name" maxlength="50" show-word-limit />
            </el-form-item>
            <el-form-item label="创建人">
              <el-input :model-value="detail?.createdByName || '—'" disabled />
            </el-form-item>
          </div>
          <el-form-item label="描述" prop="description">
            <el-input
              v-model="infoForm.description"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
        </el-form>

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

        <div class="ws-detail__form-actions">
          <el-button link @click="resetInfo">重置</el-button>
          <el-button type="primary" :loading="infoSaving" @click="saveInfo">保存修改</el-button>
        </div>
      </div>
    </section>

    <section class="ws-detail__card">
      <header class="ws-detail__card-head">
        <div class="ws-detail__card-head-left">
          <h3 class="ws-detail__card-title">成员列表</h3>
          <span class="ws-detail__card-subtitle">{{ memberTotal }} 人</span>
        </div>
        <el-button size="small" @click="openAddDialog">
          <el-icon><Plus /></el-icon>添加用户
        </el-button>
      </header>
      <el-table v-loading="membersLoading" :data="members" row-key="userId">
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">
            <div class="ws-detail__user">
              <el-avatar :size="28" :src="row.avatarUrl || undefined">
                {{ row.username.charAt(0).toUpperCase() }}
              </el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="空间角色" width="140">
          <template #default="{ row }">
            <el-select
              v-if="editingUserId === row.userId"
              ref="roleSelectRef"
              :model-value="row.workspaceRole"
              size="small"
              automatic-dropdown
              @change="(val: string) => submitRoleChange(row as WorkspaceMember, val)"
              @visible-change="handleRoleVisibleChange"
            >
              <el-option
                v-for="opt in roleOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <span
              v-else
              class="ws-detail__role-tag"
              :class="{ 'ws-detail__role-tag--info': roleTag(row as WorkspaceMember).isAdmin }"
              >{{ roleTag(row as WorkspaceMember).label }}</span
            >
          </template>
        </el-table-column>
        <el-table-column label="加入时间" width="170">
          <template #default="{ row }">
            <span class="ws-detail__num">{{ formatDateTime(row.joinedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="startEditRole(row.userId)">改角色</el-button>
            <el-button link type="danger" @click="handleRemoveMember(row as WorkspaceMember)"
              >移除</el-button
            >
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
    </section>

    <el-dialog v-model="addDialogVisible" title="添加用户" width="520px">
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
              :label="u.name"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="selectedUsers.length" class="ws-detail__pending">
        <div v-for="u in selectedUsers" :key="u.id" class="ws-detail__pending-item">
          <span class="ws-detail__pending-name">{{ u.name }}</span>
          <el-select v-model="pendingRoles[u.id]" size="small" style="width: 180px">
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
.ws-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-lg);
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.ws-detail__breadcrumb a {
  color: var(--color-neutral-500);
  text-decoration: none;
}

.ws-detail__breadcrumb a:hover {
  color: var(--color-primary-500);
}

.ws-detail__crumb-current {
  color: var(--color-neutral-900);
}

.ws-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--space-xl);
}

.ws-detail__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.ws-detail__title {
  margin: 0;
  font-size: var(--font-size-2xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-neutral-900);
}

.ws-detail__desc {
  margin: var(--space-xs) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

/* ID 用等宽字体便于逐字符比对，break-all 防止 UUID 撑破布局 */
.ws-detail__mono {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  word-break: break-all;
}

/* 状态点标对齐演示稿 status（圆点 + 文案），色义：活跃绿 / 已解散灰 */
.ws-detail__status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  white-space: nowrap;
}

.ws-detail__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-neutral-400);
}

.ws-detail__status--success .ws-detail__dot {
  background: var(--color-success);
}

/* 单卡片承载信息表单/成员表，对齐演示稿 workspace-detail.html 结构 */
.ws-detail__card {
  margin-bottom: var(--space-lg);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.ws-detail__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-neutral-100);
}

.ws-detail__card-head-left {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.ws-detail__card-title {
  margin: 0;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-neutral-900);
}

.ws-detail__card-subtitle {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.ws-detail__card-body {
  padding: 24px;
}

.ws-detail__form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.ws-detail__form :deep(.el-form-item__label) {
  font-size: var(--font-size-sm);
  font-weight: 500;
  line-height: 1.4;
  color: var(--color-neutral-700);
}

/* 左右双列：空间名称 + 创建人，对齐演示稿 form-grid */
.ws-detail__grid2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-xl);
}

.ws-detail__stats {
  display: flex;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
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
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.ws-detail__form-actions {
  display: flex;
  justify-content: flex-end;
}

.ws-detail__user {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

/* 空间角色标签对齐演示稿 tag--info / tag--outline，按预置角色 ID 区分管理员 */
.ws-detail__role-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border: 1px solid var(--color-neutral-300);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-neutral-600);
  font-size: var(--font-size-xs);
  font-weight: 500;
  white-space: nowrap;
}

.ws-detail__role-tag--info {
  border-color: var(--color-primary-100);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.ws-detail__num {
  font-variant-numeric: tabular-nums;
}

.ws-detail__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid var(--color-neutral-100);
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
