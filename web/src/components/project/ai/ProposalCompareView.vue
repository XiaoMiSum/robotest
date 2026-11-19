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
      link
      type="info"
      class="compare-view__fold"
      @click="showUnchanged = !showUnchanged"
    >
      {{ showUnchanged ? '收起无变化项' : `展开其余 ${unchangedItems.length} 项无变化内容` }}
    </el-button>
  </div>
</template>

<style scoped lang="scss">
.compare-view {
  margin-bottom: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.compare-view__head,
.compare-view__row {
  display: grid;
  grid-template-columns: minmax(72px, 1fr) minmax(0, 2fr) minmax(0, 2fr);
}

.compare-view__head {
  background: var(--color-neutral-100);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.compare-view__head-cell {
  padding: var(--space-xs) var(--space-sm);
  color: var(--color-neutral-500);

  &--new {
    color: var(--color-primary-500);
  }
}

.compare-view__row {
  border-top: 1px solid var(--color-neutral-200);
  font-size: var(--font-size-sm);

  &--changed {
    background: var(--color-success-light);
  }
}

.compare-view__label,
.compare-view__existing,
.compare-view__suggested {
  padding: var(--space-xs) var(--space-sm);
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
