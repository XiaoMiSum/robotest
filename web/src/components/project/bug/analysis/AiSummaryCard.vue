<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { BugSourceRef } from '@/types'

const props = defineProps<{
  loading: boolean
  running: boolean
  text: string
  citations: BugSourceRef[]
  error: string
  /** 范围变化后摘要基于旧快照，提示重新生成 */
  stale: boolean
}>()

const emit = defineEmits<{ generate: [] }>()

const router = useRouter()

const hasResult = computed(() => props.text.length > 0)

function openCitation(ref_: BugSourceRef): void {
  if (ref_.type === 'bug' && ref_.id) {
    void router.push(`/workspace/projects/bugs/${ref_.id}`)
  }
}
</script>

<template>
  <el-card shadow="never" class="summary-card">
    <template #header>
      <div class="summary-card__head">
        <span class="summary-card__title">AI 摘要</span>
        <!-- 只读建议，不写入任何数据（详设 4.1） -->
        <el-button size="small" type="primary" plain :disabled="running" @click="emit('generate')">
          {{ hasResult || error ? '重新生成' : '生成' }}
        </el-button>
      </div>
    </template>

    <div v-if="running" class="summary-card__loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>摘要生成中…</span>
    </div>

    <el-alert
      v-else-if="error"
      type="error"
      :title="error"
      show-icon
      :closable="false"
      class="summary-card__error"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="emit('generate')">重试</el-button>
      </template>
    </el-alert>

    <template v-else-if="hasResult">
      <p v-if="stale" class="summary-card__stale">范围已变化，当前摘要基于上一次计算结果</p>
      <p class="summary-card__text">{{ text }}</p>
      <div v-if="citations.length > 0" class="summary-card__citations">
        <span class="summary-card__citations-label">引用来源</span>
        <el-link
          v-for="ref_ in citations"
          :key="ref_.id"
          type="primary"
          :underline="false"
          class="summary-card__citation"
          @click="openCitation(ref_)"
        >
          {{ ref_.title || ref_.id }}
        </el-link>
      </div>
    </template>

    <el-empty v-else description="点击「生成」按当前范围生成缺陷趋势摘要" :image-size="60" />
  </el-card>
</template>

<style scoped lang="scss">
.summary-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.summary-card__title {
  font-weight: 600;
}

.summary-card__loading {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-lg) 0;
  color: var(--color-neutral-500);
}

.summary-card__error {
  margin-bottom: var(--space-sm);
}

.summary-card__stale {
  margin: 0 0 var(--space-xs);
  color: var(--color-warning-strong);
  font-size: var(--font-size-sm);
}

.summary-card__text {
  margin: 0;
  color: var(--color-neutral-700);
  line-height: 1.7;
  white-space: pre-wrap;
}

.summary-card__citations {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}

.summary-card__citations-label {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}
</style>
