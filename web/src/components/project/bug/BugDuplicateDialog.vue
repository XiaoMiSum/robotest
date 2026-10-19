<script setup lang="ts">
import { onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { formatShortId } from '@/utils/format'
import { useBugDuplicateCheck } from '@/composables/project/bug/useBugDuplicateCheck'
import { BUG_STATUS_LABEL } from '@/composables/project/bug/bugStatus'
import type { BugStatus } from '@/types'

const props = defineProps<{
  title: string
  steps: string
  /** AI 未启用时不发起检测（总册 4.5） */
  aiAvailable: boolean
}>()

const visible = defineModel<boolean>({ default: false })

const router = useRouter()

const { items, check, dispose } = useBugDuplicateCheck(
  () => props.title,
  () => props.steps,
  () => props.aiAvailable,
)

/**
 * 失焦触发入口（交互 3.1）：无命中或功能不可用时 visible 保持 false，静默不打扰；
 * 命中才弹窗展示明细。
 */
async function runCheck(): Promise<void> {
  await check()
  visible.value = items.value.length > 0
}

defineExpose({ check: runCheck })

onBeforeUnmount(dispose)

/** 相似度百分比展示：仅作排序信号，阈值不对外承诺（详设 3.8） */
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
  <el-dialog v-model="visible" title="相似缺陷检测" width="600px" append-to-body>
    <!-- 弹窗只在有命中时打开；复检期间保留上次结果，加载过程保持静默（交互 3.1） -->
    <ul v-if="items.length > 0" class="dup-dialog__list">
      <li v-for="item in items" :key="item.bugId" class="dup-dialog__item">
        <div class="dup-dialog__row">
          <el-link type="primary" :underline="false" @click="openBug(item.bugId)">
            {{ formatShortId(item.bugId) }}
          </el-link>
          <span class="dup-dialog__bug-title">{{ item.title }}</span>
          <el-tag size="small" effect="plain">{{ statusLabel(item.status) }}</el-tag>
          <el-tooltip content="相似度仅作排序信号，不对外承诺阈值" placement="top">
            <span
              class="dup-dialog__similarity"
              :class="{ 'dup-dialog__similarity--high': item.similarity >= 0.8 }"
            >
              <el-icon v-if="item.similarity >= 0.8"><Warning /></el-icon>
              相似度 {{ similarityText(item.similarity) }}
            </span>
          </el-tooltip>
        </div>
        <!-- 相似依据：向量命中的原文分块 -->
        <p class="dup-dialog__basis">{{ item.basis }}</p>
      </li>
    </ul>
    <p class="dup-dialog__hint">检测结果仅作建议，请确认后再提交；确认为重复时走既有「重复缺陷」解决方案</p>
  </el-dialog>
</template>

<style scoped lang="scss">
.dup-dialog__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.dup-dialog__item {
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.dup-dialog__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.dup-dialog__bug-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-neutral-700);
}

.dup-dialog__similarity {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  flex-shrink: 0;
}

.dup-dialog__similarity--high {
  color: var(--color-warning-strong);
}

.dup-dialog__basis {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dup-dialog__hint {
  margin: var(--space-sm) 0 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}
</style>
