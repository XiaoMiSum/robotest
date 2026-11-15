<script setup lang="ts">
withDefaults(defineProps<{ label: string; loading?: boolean }>(), { loading: false })
</script>

<template>
  <section class="list-workspace" :aria-label="label" :aria-busy="loading">
    <div v-if="$slots.toolbar" class="list-workspace__toolbar"><slot name="toolbar" /></div>
    <div v-if="$slots.feedback" class="list-workspace__feedback"><slot name="feedback" /></div>
    <div v-loading="loading" class="list-workspace__body"><slot /></div>
    <div v-if="$slots.pagination" class="list-workspace__pagination">
      <slot name="pagination" />
    </div>
  </section>
</template>

<style scoped>
.list-workspace {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.list-workspace__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md);
  flex-shrink: 0;
  padding: var(--space-lg);
  border-bottom: 1px solid var(--color-neutral-200);
}
.list-workspace__feedback,
.list-workspace__pagination {
  flex-shrink: 0;
}
.list-workspace__body {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}
</style>
