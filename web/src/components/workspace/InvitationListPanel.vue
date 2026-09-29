<script setup lang="ts">
import type { InvitationListItem } from '@/types'
import { formatDateTime } from '@/utils/format'
import {
  canCopyInvitation,
  canExpireInvitation,
  invitationStatusMeta,
} from '@/utils/workspaceInvitation'

defineProps<{
  invitations: InvitationListItem[]
  loading: boolean
  loadError: boolean
  total: number
  pageNo: number
  pageSize: number
  copyingId: string
  revokingId: string
}>()

const emit = defineEmits<{
  'page-change': [pageNo: number]
  copy: [invitation: InvitationListItem]
  expire: [invitation: InvitationListItem]
  retry: []
}>()

function invitationUses(invitation: InvitationListItem): string {
  return `${invitation.useCount} / ${invitation.maxUses ?? '不限'}`
}

function handlePageChange(pageNo: number): void {
  emit('page-change', pageNo)
}
</script>

<template>
  <section class="invitation-list-card">
    <header class="invitation-list-card__head">
      <div class="invitation-list-card__title-group">
        <h2 class="invitation-list-card__title">邀请链接</h2>
        <span class="invitation-list-card__description">通过链接加入的成员按默认角色进入</span>
      </div>
    </header>

    <div class="invitation-list-card__body">
      <el-table
        v-loading="loading"
        class="invitation-list-card__table"
        :data="invitations"
        height="100%"
        row-key="id"
      >
        <el-table-column label="邀请链接" min-width="190">
          <template #default="{ row }">
            <span class="invitation-list-card__token">join/{{ row.tokenPreview || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="使用次数" min-width="100">
          <template #default="{ row }">
            <span class="invitation-list-card__num">{{
              invitationUses(row as InvitationListItem)
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" min-width="150">
          <template #default="{ row }">
            <span class="invitation-list-card__num">
              {{ row.expiresAt ? formatDateTime(row.expiresAt) : '永不过期' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <span
              class="invitation-list-card__status"
              :class="`invitation-list-card__status--${invitationStatusMeta(row.effectiveStatus).tone}`"
            >
              <span class="invitation-list-card__status-dot" />
              {{ invitationStatusMeta(row.effectiveStatus).label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150">
          <template #default="{ row }">
            <span class="invitation-list-card__num">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="invitation-list-card__row-actions">
              <el-button
                link
                type="primary"
                :disabled="!canCopyInvitation(row as InvitationListItem)"
                :loading="copyingId === row.id"
                @click="emit('copy', row as InvitationListItem)"
              >
                复制
              </el-button>
              <el-button
                v-if="canExpireInvitation(row as InvitationListItem)"
                link
                type="danger"
                :loading="revokingId === row.id"
                @click="emit('expire', row as InvitationListItem)"
              >
                失效
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="loadError" description="邀请链接加载失败">
            <el-button type="primary" @click="emit('retry')">重新加载</el-button>
          </el-empty>
          <el-empty v-else description="暂无邀请链接，点击右上角「生成链接」创建第一个邀请" />
        </template>
      </el-table>
    </div>

    <footer class="invitation-list-card__pager">
      <span>共 {{ total }} 条 · 每页 {{ pageSize }} 条</span>
      <el-pagination
        :current-page="pageNo"
        background
        layout="prev, pager, next"
        :page-size="pageSize"
        :pager-count="5"
        :total="total"
        @current-change="handlePageChange"
      />
    </footer>
  </section>
</template>

<style scoped lang="scss">
.invitation-list-card {
  display: flex;
  min-width: 0;
  /* 内容超出时由表体裁剪，卡片本身不参与页面滚动（UI-SC-02） */
  min-height: 0;
  overflow: hidden;
  flex-direction: column;
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  box-shadow: var(--shadow-card);
}

.invitation-list-card__head {
  display: flex;
  box-sizing: border-box;
  /* 与成员列表卡片统一表头高度，保证两卡表头落在同一水平线 */
  min-height: 65px;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding: var(--space-lg) var(--card-pad);
  border-bottom: 1px solid var(--color-neutral-100);
}

.invitation-list-card__title-group {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-sm);
}

.invitation-list-card__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-base);
  font-weight: 600;
}

.invitation-list-card__description {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.invitation-list-card__body {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex: 1;
}

/* 表格与卡片容器不留边距，首列文字由单元格留白对齐卡片头与分页 */
.invitation-list-card__table {
  width: 100%;
}

.invitation-list-card__num {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}

.invitation-list-card__row-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.invitation-list-card__pager {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  box-sizing: border-box;
  min-height: 60px;
  padding: var(--space-md) var(--card-pad);
  border-top: 1px solid var(--color-neutral-100);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.invitation-list-card__token {
  color: var(--color-neutral-700);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.invitation-list-card__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.invitation-list-card__status-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: var(--radius-full);
  background: var(--color-neutral-400);
}

.invitation-list-card__status--success .invitation-list-card__status-dot {
  background: var(--color-success);
}

.invitation-list-card__status--danger .invitation-list-card__status-dot {
  background: var(--color-danger);
}

@media (max-width: 900px) {
  .invitation-list-card__head {
    align-items: stretch;
    flex-direction: column;
  }

  /* 堆叠布局恢复页面级滚动，表体不再固定高度（UI-SC-08） */
  .invitation-list-card__body {
    overflow: visible;
    flex: none;
  }
}

@media (max-width: 640px) {
  .invitation-list-card__pager {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
