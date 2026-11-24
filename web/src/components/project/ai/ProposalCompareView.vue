<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ProposalCompareRow } from '@/composables/project/ai/reviewProfiles'

const props = defineProps<{
  items: ProposalCompareRow[]
}>()

const showUnchanged = ref(false)

const changedItems = computed(() => props.items.filter((item) => item.changed))
const unchangedItems = computed(() => props.items.filter((item) => !item.changed))
const visibleItems = computed(() => (showUnchanged.value ? props.items : changedItems.value))
</script>

<template>
  <div v-if="items.length > 0" class="compare-view">
    <div class="compare-view__head">
      <span class="compare-view__head-cell">项目</span>
      <span class="compare-view__head-cell">现有内容</span>
      <span class="compare-view__head-cell compare-view__head-cell--new">AI 建议</span>
    </div>
    <div
      v-for="(item, index) in visibleItems"
      :key="`${item.label}-${index}`"
      class="compare-view__row"
      :class="{ 'compare-view__row--changed': item.changed }"
    >
      <span class="compare-view__label">{{ item.label }}</span>
      <span class="compare-view__existing">{{ item.existing || '—' }}</span>
      <span class="compare-view__suggested">{{ item.suggested || '—' }}</span>
    </div>
    <el-button
      v-if="unchangedItems.length > 0"
      class="compare-view__fold"
      link
      size="small"
      @click="showUnchanged = !showUnchanged"
    >
      {{ showUnchanged ? '收起无变化项' : `展开其余 ${unchangedItems.length} 项无变化内容` }}
    </el-button>
  </div>
</template>

<style scoped lang="scss">
.compare-view {
  margin: var(--space-xs) 0 var(--space-sm);
}

.compare-view__head,
.compare-view__row {
  display: grid;
  grid-template-columns: minmax(72px, 1fr) minmax(0, 2fr) minmax(0, 2fr);
  column-gap: var(--space-sm);
}

.compare-view__head {
  font-size: var(--font-size-xs);
}

.compare-view__head-cell {
  padding-bottom: var(--space-xs);
  color: var(--color-neutral-500);

  &--new {
    color: var(--color-primary-500);
  }
}

.compare-view__row {
  padding: var(--space-xs) 0;
  border-bottom: 1px solid var(--color-neutral-200);
  font-size: var(--font-size-sm);

  &--changed {
    padding-left: var(--space-sm);
    background: var(--color-success-light);
    box-shadow: inset 3px 0 0 var(--color-success);
  }
}

.compare-view__label,
.compare-view__existing,
.compare-view__suggested {
  line-height: 1.5;
  word-break: break-word;
}

.compare-view__label {
  color: var(--color-neutral-500);
  font-weight: 600;
}

.compare-view__existing {
  color: var(--color-neutral-500);
}

.compare-view__suggested {
  color: var(--color-neutral-900);
}

.compare-view__fold {
  padding: var(--space-xs) var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}
</style>
