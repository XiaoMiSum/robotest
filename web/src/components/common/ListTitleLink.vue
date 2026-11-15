<script setup lang="ts">
defineProps<{ title: string }>()
const emit = defineEmits<{ open: [] }>()
</script>

<template>
  <el-tooltip
    :content="title"
    placement="top-start"
    :show-after="300"
    :trigger="['hover', 'focus']"
    :trigger-keys="[]"
  >
    <button type="button" class="list-title-link" @click="emit('open')">{{ title }}</button>
  </el-tooltip>
</template>

<style scoped>
.list-title-link {
  display: block;
  width: 100%;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-primary-600);
  font: inherit;
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}
.list-title-link:hover {
  text-decoration: underline;
}
.list-title-link:focus-visible {
  outline: 2px solid var(--color-primary-500);
  outline-offset: -2px;
}
</style>
