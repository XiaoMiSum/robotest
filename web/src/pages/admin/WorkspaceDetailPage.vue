<script setup lang="ts">
import { useWorkspaceDetail } from '@/composables/admin/useWorkspaceDetail'
import type { WorkspaceMember } from '@/types'
import { formatDateTime } from '@/utils/format'

const {
  router,
  roleOptions,
  detail,
  infoLoading,
  infoSaving,
  infoFormRef,
  infoForm,
  infoRules,
  saveInfo,
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
  background: var(--color-primary-50);
  color: var(--color-primary-700);
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
  border: 1px solid var(--color-danger-border);
  background: var(--color-danger-light);
}

.ws-detail__danger-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ws-detail__danger-label {
  font-weight: 600;
  color: var(--color-danger-strong);
}

.ws-detail__danger-tip {
  margin-top: 4px;
  font-size: var(--font-size-xs);
  color: var(--color-danger);
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
