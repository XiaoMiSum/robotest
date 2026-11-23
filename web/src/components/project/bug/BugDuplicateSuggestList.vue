<script setup lang="ts">
import { useRouter } from 'vue-router'
import { formatShortId } from '@/utils/format'
import { BUG_STATUS_LABEL } from '@/composables/project/bug/bugStatus'
import type { BugDuplicateSuggestItem, BugStatus } from '@/types'

defineProps<{ items: BugDuplicateSuggestItem[] }>()

const router = useRouter()

/** 相似度百分比仅作排序信号，阈值不对外承诺（详设 3.4） */
function similarityText(value: number): string {
  return `${Math.round(value * 100)}%`
}

function statusLabel(status: BugStatus): string {
  return BUG_STATUS_LABEL[status] ?? status
}

function openBug(bugId: string): void {
  void router.push(`/workspace/projects/bugs/${bugId}`)
}
</script>

<template>
  <div v-if="items.length > 0" class="dup-suggest">
    <div class="dup-suggest__heading">疑似重复缺陷</div>
    <ul class="dup-suggest__list">
      <li v-for="item in items" :key="item.bugId" class="dup-suggest__item">
        <div class="dup-suggest__row">
          <el-link type="primary" :underline="false" @click="openBug(item.bugId)">
            {{ formatShortId(item.bugId) }}
          </el-link>
          <span class="dup-suggest__bug-title">{{ item.title }}</span>
          <el-tag size="small" effect="plain">{{ statusLabel(item.status) }}</el-tag>
          <el-tooltip content="相似度仅作排序信号，不对外承诺阈值" placement="top">
            <span
              class="dup-suggest__similarity"
              :class="{ 'dup-suggest__similarity--high': item.similarity >= 0.8 }"
            >
              <el-icon v-if="item.similarity >= 0.8"><Warning /></el-icon>
              相似度 {{ similarityText(item.similarity) }}
            </span>
          </el-tooltip>
        </div>
        <p class="dup-suggest__basis">{{ item.basis }}</p>
      </li>
    </ul>
    <p class="dup-suggest__hint">仅作建议，请确认后再提交；确认为重复时走既有「重复缺陷」解决方案</p>
  </div>
</template>

<style scoped lang="scss">
.dup-suggest__heading {
  margin-bottom: var(--space-xs);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-600);
}

.dup-suggest__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.dup-suggest__item {
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.dup-suggest__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.dup-suggest__bug-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-neutral-700);
}

.dup-suggest__similarity {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  flex-shrink: 0;
}

.dup-suggest__similarity--high {
  color: var(--color-warning-strong);
}

.dup-suggest__basis {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dup-suggest__hint {
  margin: var(--space-sm) 0 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}
</style>
