<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatShortId } from '@/utils/format'
import { useBugDuplicateCheck } from '@/composables/project/bug/useBugDuplicateCheck'
import { BUG_STATUS_LABEL } from '@/composables/project/bug/bugStatus'
import type { BugStatus } from '@/types'

const props = defineProps<{
  title: string
  steps: string
  /** AI 未启用时整个面板隐藏（总册 4.5） */
  aiAvailable: boolean
}>()

const router = useRouter()

const { items, loading, checked, vectorUnavailable, error, dispose } = useBugDuplicateCheck(
  () => props.title,
  () => props.steps,
  () => props.aiAvailable,
)

defineExpose({ dispose })

/** 相似度百分比展示：仅作排序信号，阈值不对外承诺（详设 3.8） */
function similarityText(value: number): string {
  return `${Math.round(value * 100)}%`
}

/** 高相似前置已由 composable 完成；>= 0.8 以 warning 图标标注（不单靠颜色表意） */
const highSimilarity = computed(() => items.value.some((item) => item.similarity >= 0.8))

function statusLabel(status: BugStatus): string {
  return BUG_STATUS_LABEL[status] ?? status
}

function openBug(bugId: string): void {
  void router.push(`/workspace/projects/bugs/${bugId}`)
}
</script>

<template>
  <div v-if="aiAvailable" class="dup-check">
    <div class="dup-check__head">
      <span class="dup-check__title">相似缺陷检测</span>
      <span v-if="vectorUnavailable" class="dup-check__vector-off">向量索引未就绪</span>
    </div>

    <!-- 向量未就绪：入口置灰 + 提示（交互 2.4） -->
    <el-alert
      v-if="vectorUnavailable"
      type="warning"
      title="向量索引未就绪，暂无法执行相似缺陷检测"
      description="向量能力未配置或正在全量重建，就绪后自动恢复检测"
      show-icon
      :closable="false"
    />

    <el-alert v-else-if="error" type="error" :title="error" show-icon :closable="false" />

    <div v-else-if="loading" class="dup-check__loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在检索相似缺陷…</span>
    </div>

    <!-- 检测结果仅作建议：不自动合并、不改状态（交互 3.1） -->
    <template v-else-if="checked">
      <el-empty v-if="items.length === 0" description="未发现相似缺陷" :image-size="60" />
      <ul v-else class="dup-check__list">
        <li v-for="item in items" :key="item.bugId" class="dup-check__item">
          <div class="dup-check__row">
            <el-link type="primary" :underline="false" @click="openBug(item.bugId)">
              {{ formatShortId(item.bugId) }}
            </el-link>
            <span class="dup-check__bug-title">{{ item.title }}</span>
            <el-tag size="small" effect="plain">{{ statusLabel(item.status) }}</el-tag>
            <el-tooltip content="相似度仅作排序信号，不对外承诺阈值" placement="top">
              <span
                class="dup-check__similarity"
                :class="{ 'dup-check__similarity--high': item.similarity >= 0.8 }"
              >
                <el-icon v-if="highSimilarity && item.similarity >= 0.8"><Warning /></el-icon>
                相似度 {{ similarityText(item.similarity) }}
              </span>
            </el-tooltip>
          </div>
          <!-- 相似依据：向量命中的原文分块 -->
          <p class="dup-check__basis">{{ item.basis }}</p>
        </li>
      </ul>
      <p class="dup-check__hint">检测结果仅作建议，请确认后再提交；确认为重复时走既有「重复缺陷」解决方案</p>
    </template>
  </div>
</template>

<style scoped lang="scss">
.dup-check__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}

.dup-check__title {
  font-weight: 600;
}

.dup-check__vector-off {
  color: var(--color-warning-strong);
  font-size: var(--font-size-sm);
}

.dup-check__loading {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md) 0;
  color: var(--color-neutral-500);
}

.dup-check__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.dup-check__item {
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.dup-check__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.dup-check__bug-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-neutral-700);
}

.dup-check__similarity {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  flex-shrink: 0;
}

.dup-check__similarity--high {
  color: var(--color-warning-strong);
}

.dup-check__basis {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dup-check__hint {
  margin: var(--space-sm) 0 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}
</style>
