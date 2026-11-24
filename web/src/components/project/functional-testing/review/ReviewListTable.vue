<script setup lang="ts">
import { computed } from 'vue'
import type { TestReviewListItem } from '@/types'
import { formatDateTime } from '@/utils/format'
import ListTitleLink from '@/components/common/ListTitleLink.vue'
import {
  isActiveReview,
  reviewAvatars,
  reviewListAction,
  reviewNameSub,
  reviewPassRate,
  reviewProgressStatus,
  reviewProgressText,
  reviewStatusMeta,
} from './reviewListPresentation'

const props = defineProps<{
  reviews: TestReviewListItem[]
  currentUserId: string
  visibleKeys: string[]
}>()
const emit = defineEmits<{
  open: [id: string]
  edit: [review: TestReviewListItem]
  reopen: [review: TestReviewListItem]
  delete: [review: TestReviewListItem]
}>()
const rows = computed(() =>
  props.reviews.map((review) => ({
    id: review.id,
    review,
    canEdit: isActiveReview(review.status) && review.initiator.id === props.currentUserId,
    meta: reviewStatusMeta(review.status),
    action: reviewListAction(review.status),
    passRate: reviewPassRate(review.status, review.passRate),
    progressText: reviewProgressText(review.reviewed, review.totalAssociated),
    progressStatus: reviewProgressStatus(review.status),
    avatars: reviewAvatars(review),
    nameSub: reviewNameSub(review.totalAssociated, review.requirementCount),
  })),
)
const brandAvatarStyle = {
  '--el-avatar-background-color': 'var(--color-primary-500)',
  '--el-avatar-color': 'var(--color-neutral-0)',
}
</script>

<template>
  <el-table class="review-table" :data="rows" row-key="id" height="100%">
    <template #empty><slot name="empty" /></template>
    <el-table-column v-if="visibleKeys.includes('title')" label="标题" min-width="300">
      <template #default="{ row }">
        <div class="review-table__cell">
          <ListTitleLink :title="row.review.title" @open="emit('open', row.review.id)" />
          <span class="review-table__secondary">{{ row.nameSub }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('initiator')" label="发起人" width="100" show-overflow-tooltip>
      <template #default="{ row }">{{ row.review.initiator.name }}</template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('status')" label="状态" width="96">
      <template #default="{ row }">
        <span class="review-table__status" :class="`review-table__status--${row.meta.modifier}`">
          <span class="review-table__dot" aria-hidden="true" />{{ row.meta.label }}
        </span>
      </template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('progress')" label="进度" min-width="170">
      <template #default="{ row }">
        <el-progress
          :percentage="row.review.progressPercent"
          :stroke-width="6"
          :status="row.progressStatus"
          >{{ row.progressText }}</el-progress
        >
      </template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('passRate')" label="通过率" width="92" align="right">
      <template #default="{ row }"
        ><span :class="`review-table__rate--${row.passRate.tone}`">{{
          row.passRate.text
        }}</span></template
      >
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('participants')" label="参与者" width="124">
      <template #default="{ row }">
        <span v-if="!row.avatars.visible.length" class="review-table__secondary">—</span>
        <span v-else class="review-table__avatars">
          <span
            v-for="avatar in row.avatars.visible"
            :key="avatar.key"
            class="review-table__avatar"
          >
            <el-avatar
              :size="24"
              :src="avatar.avatarUrl ?? undefined"
              :style="avatar.brand ? brandAvatarStyle : undefined"
              >{{ avatar.label }}</el-avatar
            >
          </span>
          <span v-if="row.avatars.overflow" class="review-table__secondary"
            >+{{ row.avatars.overflow }}</span
          >
        </span>
      </template>
    </el-table-column>
    <el-table-column v-if="visibleKeys.includes('createdAt')" label="创建时间" width="160">
      <template #default="{ row }"
        ><span class="review-table__time">{{
          formatDateTime(row.review.createdAt)
        }}</span></template
      >
    </el-table-column>
    <el-table-column label="操作" width="260" fixed="right">
      <template #header>
        <div class="review-table__action-header">
          <span>操作</span>
          <slot name="column-setting" />
        </div>
      </template>
      <template #default="{ row }">
        <el-space :size="8">
          <el-button link type="primary" size="small" @click="emit('open', row.review.id)">{{
            row.action === 'enter' ? '进入' : '查看'
          }}</el-button>
          <el-button v-if="row.canEdit" link type="primary" size="small" @click="emit('edit', row.review)">编辑</el-button>
          <el-button
            v-if="row.review.status === 'rejected'"
            link
            type="primary"
            size="small"
            @click="emit('reopen', row.review)"
            >重新执行</el-button>
          <el-button link type="danger" size="small" @click="emit('delete', row.review)">删除</el-button>
        </el-space>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped lang="scss">
.review-table__cell {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  min-width: 0;
}
.review-table__secondary,
.review-table__time {
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}
.review-table__time {
  font-variant-numeric: tabular-nums;
}
.review-table__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-neutral-600);
  white-space: nowrap;
}
.review-table__dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: var(--radius-full);
  background: currentColor;
}
.review-table__status--running {
  color: var(--color-warning);
  .review-table__dot {
    box-shadow: 0 0 0 3px var(--el-color-warning-light-8);
  }
}
.review-table__status--success,
.review-table__rate--success {
  color: var(--color-success);
}
.review-table__rate--success {
  font-weight: 600;
}
.review-table__status--danger,
.review-table__rate--danger {
  color: var(--color-danger);
}
.review-table__rate--muted {
  color: var(--color-neutral-600);
}
.review-table__avatars {
  display: inline-flex;
  align-items: center;
}
.review-table__avatar {
  display: inline-flex;
  border-radius: var(--radius-full);
  box-shadow: 0 0 0 2px var(--color-neutral-0);
}
.review-table__avatar + .review-table__avatar {
  margin-left: -6px;
}
.review-table__avatars > .review-table__secondary {
  margin-left: var(--space-xs);
}
.review-table__action-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
  width: 100%;
}
</style>
