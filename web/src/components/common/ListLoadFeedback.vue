<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  mode: 'loading' | 'error' | 'refresh-error' | 'empty' | 'filtered'
  title: string
  description?: string
  createLabel?: string
  createIcon?: Component
  retrying?: boolean
}>()
const emit = defineEmits<{ retry: []; reset: []; create: [] }>()
</script>

<template>
  <div v-if="mode === 'loading'" class="list-feedback__loading" role="status">{{ title }}</div>
  <div v-else-if="mode === 'error' || mode === 'refresh-error'" class="list-feedback__error">
    <el-alert :title="title" :description="description" type="error" show-icon :closable="false" />
    <el-button :loading="retrying" size="small" @click="emit('retry')">刷新</el-button>
  </div>
  <el-empty v-else :description="title" :image-size="72">
    <el-button v-if="mode === 'filtered'" @click="emit('reset')">重置筛选</el-button>
    <el-button v-else-if="createLabel" type="primary" @click="emit('create')">
      <el-icon v-if="createIcon"><component :is="createIcon" /></el-icon>{{ createLabel }}
    </el-button>
  </el-empty>
</template>

<style scoped>
.list-feedback__loading {
  padding: var(--space-lg);
}
.list-feedback__error {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  line-height: normal;
  text-align: left;
}
.list-feedback__error > :first-child {
  flex: 1 1 16rem;
  min-width: 0;
}
</style>
