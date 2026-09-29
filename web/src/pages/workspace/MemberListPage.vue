<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { Link, Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import MemberListPanel from '@/components/workspace/MemberListPanel.vue'
import InvitationListPanel from '@/components/workspace/InvitationListPanel.vue'
import MemberInviteDialog from '@/components/workspace/MemberInviteDialog.vue'
import InvitationCreateDialog from '@/components/workspace/InvitationCreateDialog.vue'
import InvitationLinkDialog from '@/components/workspace/InvitationLinkDialog.vue'
import { useMemberList } from '@/composables/workspace/useMemberList'
import { useInvitationList } from '@/composables/workspace/useInvitationList'
import { useMemberInvite } from '@/composables/workspace/useMemberInvite'
import { useInvitationCreate } from '@/composables/workspace/useInvitationCreate'

const authStore = useAuthStore()
const canManageMember = computed(() => authStore.hasPermission('ws-member:manage'))

const {
  currentUserId,
  roleOptions,
  members,
  membersLoading,
  membersLoadError,
  memberTotal,
  memberQuery,
  editingUserId,
  loadRoleOptions,
  loadMembers,
  handleMemberSearchInput,
  handleMemberSearchClear,
  handleRoleFilterChange,
  handleMemberPageChange,
  startEditRole,
  handleRoleVisibleChange,
  submitRoleChange,
  handleRemoveMember,
  dispose: disposeMemberList,
} = useMemberList()

const {
  canManageInvitation,
  invitations,
  invitationsLoading,
  invitationsLoadError,
  invitationTotal,
  invQuery,
  loadInvitations,
  handleInvitationPageChange,
  copyingInvitationId,
  copyInvitation,
  copyingLatestInvitation,
  handleCopyLatestInvitation,
  revokingInvitationId,
  handleExpireInvitation,
  dispose: disposeInvitationList,
} = useInvitationList()

const {
  addDialogVisible,
  addSubmitting,
  userSearchLoading,
  userOptions,
  selectedUserIds,
  openAddDialog,
  handleSelectedUsersChange,
  searchUsers,
  submitAddMembers,
  dispose: disposeMemberInvite,
} = useMemberInvite(() => {
  void loadMembers()
})

const {
  createDialogVisible,
  createForm,
  createSubmitting,
  createdInviteLink,
  linkDialogVisible,
  openCreateDialog,
  setExpiresAt,
  setMaxUses,
  submitCreateInvitation,
  handleCopyLink,
} = useInvitationCreate(() => {
  void loadInvitations()
})

onMounted(() => {
  void loadRoleOptions()
  void loadMembers()
  // 权限未就绪时由 composable 内部等待，避免刷新进入只拉到成员列表
  void loadInvitations()
})

onBeforeUnmount(() => {
  disposeMemberList()
  disposeInvitationList()
  disposeMemberInvite()
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
        <el-button v-if="canManageMember" type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon>邀请成员
        </el-button>
        <el-button v-if="canManageInvitation" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>生成链接
        </el-button>
        <el-button
          v-if="canManageInvitation"
          link
          :loading="invitationsLoading || copyingLatestInvitation"
          @click="handleCopyLatestInvitation"
        >
          <el-icon><Link /></el-icon>复制邀请链接
        </el-button>
      </div>
    </header>

    <div class="member-page__split" :class="{ 'member-page__split--solo': !canManageInvitation }">
      <MemberListPanel
        :members="members"
        :loading="membersLoading"
        :load-error="membersLoadError"
        :total="memberTotal"
        :keyword="memberQuery.keyword"
        :workspace-role="memberQuery.workspaceRole"
        :page-no="memberQuery.pageNo"
        :page-size="memberQuery.pageSize"
        :role-options="roleOptions"
        :can-manage-member="canManageMember"
        :current-user-id="currentUserId"
        :editing-user-id="editingUserId"
        @search-input="handleMemberSearchInput"
        @search-clear="handleMemberSearchClear"
        @role-filter-change="handleRoleFilterChange"
        @page-change="handleMemberPageChange"
        @start-edit-role="startEditRole"
        @role-change="submitRoleChange"
        @role-visible-change="handleRoleVisibleChange"
        @remove="handleRemoveMember"
        @retry="loadMembers"
      />

      <InvitationListPanel
        v-if="canManageInvitation"
        :invitations="invitations"
        :loading="invitationsLoading"
        :load-error="invitationsLoadError"
        :total="invitationTotal"
        :page-no="invQuery.pageNo"
        :page-size="invQuery.pageSize"
        :copying-id="copyingInvitationId"
        :revoking-id="revokingInvitationId"
        @page-change="handleInvitationPageChange"
        @copy="copyInvitation"
        @expire="handleExpireInvitation"
        @retry="loadInvitations"
      />
    </div>

    <MemberInviteDialog
      v-model="addDialogVisible"
      :selected-user-ids="selectedUserIds"
      :user-options="userOptions"
      :searching="userSearchLoading"
      :submitting="addSubmitting"
      @update:selected-user-ids="handleSelectedUsersChange"
      @search="searchUsers"
      @submit="submitAddMembers"
    />

    <InvitationCreateDialog
      v-model="createDialogVisible"
      :expires-at="createForm.expiresAt"
      :max-uses="createForm.maxUses"
      :submitting="createSubmitting"
      @update:expires-at="setExpiresAt"
      @update:max-uses="setMaxUses"
      @submit="submitCreateInvitation"
    />

    <InvitationLinkDialog
      v-model="linkDialogVisible"
      :link="createdInviteLink"
      @copy="handleCopyLink"
    />
  </div>
</template>

<style scoped lang="scss">
/* 高度链路：内容白卡 → 页面 → 双卡片 → 表体，滚动只发生在表体（UI-SC-01 / UI-SC-06） */
.member-page {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  height: 100%;
}

.member-page__head {
  display: flex;
  flex-shrink: 0;
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

/* 两张卡片左右并排：等高拉伸，表头与表尾对齐同一水平线 */
.member-page__split {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--block-gap);
}

/* 无邀请管理权限时只剩成员卡片，占满整行 */
.member-page__split--solo {
  grid-template-columns: minmax(0, 1fr);
}

.member-page__split > * {
  animation: member-card-in var(--transition-base) both;
}

@keyframes member-card-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .member-page__split > * {
    animation: none;
  }
}

/* 两卡共用的表格视觉基线由分栏容器统一下发，避免在两个组件里重复登记同一组覆盖（EX-DS-001） */
.member-page__split :deep(.el-table__header th.el-table__cell) {
  height: 44px;
  background: var(--color-neutral-50);
  color: var(--color-neutral-600);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

/* 表格与卡片容器贴合后，首列文字的 24px 左基线改由单元格留白承担（与卡片头、分页一致） */
.member-page__split :deep(.el-table__cell) {
  padding: 11px var(--space-md);
}

.member-page__split :deep(.el-pagination) {
  --el-pagination-button-bg-color: var(--color-neutral-0);
  --el-pagination-hover-color: var(--color-primary-600);
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .member-page__split :deep(.el-pagination) {
    align-self: flex-end;
  }
}

@media (max-width: 900px) {
  /* 堆叠后高度不再固定，恢复页面级滚动（UI-SC-08） */
  .member-page {
    height: auto;
  }

  .member-page__split {
    flex: none;
    grid-template-columns: minmax(0, 1fr);
  }

  .member-page__head {
    align-items: stretch;
    flex-direction: column;
  }

  .member-page__head-actions {
    justify-content: flex-start;
  }
}
</style>
