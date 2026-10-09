<script setup lang="ts">
import type { BugAssigneeCandidate } from '@/types'

defineProps<{
  candidates: BugAssigneeCandidate[]
  /** 当前已选指派人为候选之一时高亮 */
  selectedId: string
}>()

const emit = defineEmits<{ adopt: [userId: string] }>()
</script>

<template>
  <!-- 指派候选：依据同模块历史缺陷处理人，悬浮展示理由；点击采纳填入指派人 -->
  <div v-if="candidates.length > 0" class="assignee-chips">
    <span class="assignee-chips__label">AI 指派候选</span>
    <el-tooltip
      v-for="candidate in candidates"
      :key="candidate.userId"
      :content="candidate.reason || '同模块历史缺陷处理人'"
      placement="top"
    >
      <el-check-tag
        :checked="selectedId === candidate.userId"
        class="assignee-chips__chip"
        @change="emit('adopt', candidate.userId)"
      >
        {{ candidate.name }}
      </el-check-tag>
    </el-tooltip>
  </div>
</template>

<style scoped lang="scss">
.assignee-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-xs);
}

.assignee-chips__label {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.assignee-chips__chip {
  cursor: pointer;
}
</style>
