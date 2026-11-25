<script setup lang="ts">
import { Warning } from '@element-plus/icons-vue'
import { formatShortId } from '@/utils/format'
import type { BugDuplicateSuggestItem } from '@/types'

defineProps<{ items: BugDuplicateSuggestItem[] }>()

/** 短 ID 链接只负责跳转，重复判定仍由用户在详情页复核 */
function openBugDetail(bugId: string): void {
  window.open(`/workspace/projects/bugs/${bugId}`, '_blank', 'noopener')
}
</script>

<template>
  <div v-if="items.length > 0" class="dup-hint">
    <el-icon class="dup-hint__icon"><Warning /></el-icon>
    <span class="dup-hint__label">疑似重复：</span>
    <span class="dup-hint__links">
      <template v-for="(item, index) in items" :key="item.bugId">
        <span v-if="index > 0" class="dup-hint__separator">、</span>
        <el-tooltip :content="item.title" placement="top">
          <el-button link type="primary" class="dup-hint__link" @click="openBugDetail(item.bugId)">
            {{ formatShortId(item.bugId) }}
          </el-button>
        </el-tooltip>
      </template>
    </span>
  </div>
</template>

<style scoped lang="scss">
.dup-hint {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-xs);
  font-size: var(--font-size-sm);
  color: var(--color-warning-strong);
  line-height: 1.6;
}

.dup-hint__icon {
  flex-shrink: 0;
}

.dup-hint__label {
  white-space: nowrap;
}

.dup-hint__links {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.dup-hint__separator {
  color: var(--color-neutral-400);
}

.dup-hint__link {
  height: auto;
  padding: 0;
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
  vertical-align: baseline;
}
</style>
