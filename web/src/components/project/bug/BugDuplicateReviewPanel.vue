<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { AiTaskProposalSummary } from '@/types'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import { formatShortId } from '@/utils/format'
import {
  useBugDuplicateReview,
  type BugDuplicateRow,
} from '@/composables/project/bug/useBugDuplicateReview'

const props = defineProps<{
  taskId: string
  proposals: AiTaskProposalSummary[]
}>()

const emit = defineEmits<{ confirmed: []; leave: [] }>()

const router = useRouter()

const review = useBugDuplicateReview(
  props.taskId,
  () => props.proposals,
  () => true,
  () => {
    review.syncRows()
    emit('confirmed')
  },
)
review.init()

const groupVisible = ref(false)
const groupAction = ref<'adopted' | 'rejected'>('adopted')
const groupNote = ref('')
const groupingRow = ref<BugDuplicateRow | null>(null)

function openGroup(row: BugDuplicateRow, action: 'adopted' | 'rejected'): void {
  groupNote.value = ''
  groupAction.value = action
  groupingRow.value = row
  groupVisible.value = true
}

async function submitGroup(): Promise<void> {
  if (!groupingRow.value) return
  await review.handleGroupConfirm(groupingRow.value, groupAction.value, groupNote.value)
  groupVisible.value = false
  groupingRow.value = null
}

function openBug(bugId: string): void {
  void router.push(`/workspace/projects/bugs/${bugId}`)
}

function similarityText(value: number): string {
  return `${Math.round(value * 100)}%`
}
</script>

<template>
  <section class="bug-duplicate-review">
    <header class="bug-duplicate-review__head">
      <div class="bug-duplicate-review__head-main">
        <h2 class="bug-duplicate-review__title">存量重复分组审核</h2>
        <span class="bug-duplicate-review__muted">
          已处理 {{ review.processed.value.processed }} / 共 {{ review.processed.value.total }}
        </span>
      </div>
      <span class="bug-duplicate-review__muted">
        确认疑似重复留痕或排除误报，均不修改任何缺陷，后续按既有「重复缺陷」流程处理
      </span>
    </header>

    <ul
      v-for="row in review.duplicateRows.value"
      :key="row.key"
      class="bug-duplicate-review__groups"
    >
      <li v-loading="!row.loaded" class="bug-duplicate-review__group">
        <div class="bug-duplicate-review__group-head">
          <span class="bug-duplicate-review__group-title">
            疑似重复组 · 主缺陷
            <el-link
              v-if="row.group?.canonicalBugId"
              type="primary"
              :underline="false"
              @click="openBug(row.group.canonicalBugId)"
            >
              {{ formatShortId(row.group.canonicalBugId) }}
            </el-link>
            <span v-else class="bug-duplicate-review__muted">未指定</span>
          </span>
          <el-tag
            v-if="row.confirmStatus !== 'pending'"
            size="small"
            effect="plain"
            :type="row.confirmStatus === 'rejected' ? 'info' : 'success'"
          >
            {{ row.confirmStatus === 'rejected' ? '已排除' : '已确认' }}
          </el-tag>
        </div>
        <ul class="bug-duplicate-review__dup-items">
          <li
            v-for="item in row.group?.items ?? []"
            :key="item.bugId"
            class="bug-duplicate-review__dup-item"
          >
            <el-link type="primary" :underline="false" @click="openBug(item.bugId)">
              {{ formatShortId(item.bugId) }}
            </el-link>
            <span class="bug-duplicate-review__dup-title">{{ item.reason }}</span>
            <span class="bug-duplicate-review__muted">相似度 {{ similarityText(item.similarity) }}</span>
          </li>
        </ul>
        <div v-if="row.confirmStatus === 'pending'" class="bug-duplicate-review__group-actions">
          <el-button
            size="small"
            type="primary"
            :loading="review.confirming.value"
            @click="openGroup(row, 'adopted')"
          >
            确认为重复组
          </el-button>
          <el-button
            size="small"
            :loading="review.confirming.value"
            @click="openGroup(row, 'rejected')"
          >
            排除误报
          </el-button>
        </div>
      </li>
    </ul>

    <AiConfirmReceipt
      v-if="review.receipt.value.length > 0"
      :receipt="review.receipt.value"
      :confirming="review.confirming.value"
      @retry="review.retryResult"
      @clear="review.clearReceipt"
    />

    <!-- 确认仅记录结论留痕，不合并或修改任何缺陷（详设 3.9） -->
    <el-dialog
      v-model="groupVisible"
      :title="groupAction === 'adopted' ? '确认疑似重复组' : '排除误报'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-alert
        :type="groupAction === 'adopted' ? 'warning' : 'info'"
        :title="groupAction === 'adopted'
          ? '确认仅记录结论留痕，不会合并或修改任何缺陷；请在缺陷中按既有「重复缺陷」流程逐条处理'
          : '排除仅记录结论留痕，不会修改任何缺陷'"
        show-icon
        :closable="false"
        class="bug-duplicate-review__dialog-alert"
      />
      <el-input
        v-model="groupNote"
        type="textarea"
        :rows="3"
        :maxlength="500"
        show-word-limit
        placeholder="结论说明可选（不超过 500 字）"
      />
      <template #footer>
        <el-button @click="groupVisible = false">取消</el-button>
        <el-button
          :type="groupAction === 'adopted' ? 'primary' : 'default'"
          :loading="review.confirming.value"
          @click="submitGroup"
        >
          {{ groupAction === 'adopted' ? '确认' : '排除' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.bug-duplicate-review {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.bug-duplicate-review__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.bug-duplicate-review__head-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.bug-duplicate-review__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.bug-duplicate-review__muted {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.bug-duplicate-review__groups {
  margin: 0;
  padding: 0;
  list-style: none;
}

.bug-duplicate-review__group-actions {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-xs);
}

.bug-duplicate-review__group {
  margin-top: var(--space-sm);
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.bug-duplicate-review__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.bug-duplicate-review__group-title {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.bug-duplicate-review__dup-items {
  margin: var(--space-xs) 0 0;
  padding: 0;
  list-style: none;
}

.bug-duplicate-review__dup-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xxs, 2px) 0;
}

.bug-duplicate-review__dup-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
}

.bug-duplicate-review__dialog-alert {
  margin-bottom: var(--space-md);
}
</style>
