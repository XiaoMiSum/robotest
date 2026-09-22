<script setup lang="ts">
import type { AiSettingSchemaGroup, AiSettingSchemaItem } from '@/types'
import { isSettingModified, weightsSum } from '@/composables/admin/aiConfigForm'

defineProps<{
  groups: AiSettingSchemaGroup[]
  form: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'reset', item: AiSettingSchemaItem): void
}>()

function settingModified(item: AiSettingSchemaItem, form: Record<string, unknown>): boolean {
  return isSettingModified(item, form[item.key])
}

function currentWeightsSum(item: AiSettingSchemaItem, form: Record<string, unknown>): number {
  return weightsSum(form[item.key])
}

// 网格内占整行的项：权重组合（object）恒整行；组内奇数项时最后一项补整行避免右侧留空
function settingItemIsFull(item: AiSettingSchemaItem, index: number, total: number): boolean {
  return item.type === 'object' || (index === total - 1 && total % 2 === 1)
}
</script>

<template>
  <el-card shadow="never" class="ai-settings-section">
    <template #header>
      <div class="ai-settings-section__header">
        <el-icon class="ai-settings-section__icon"><Setting /></el-icon>
        <span>系统配置项</span>
        <span class="ai-settings-section__sub">修改即自动保存</span>
      </div>
    </template>
    <div v-for="group in groups" :key="group.group" class="ai-settings-section__group">
      <div class="ai-settings-section__group-title">{{ group.groupLabel }}</div>
      <div class="ai-settings-section__grid">
        <el-form-item
          v-for="(item, index) in group.items"
          :key="item.key"
          :label="item.label"
          label-position="top"
          :class="{ 'ai-settings-section__item--full': settingItemIsFull(item, index, group.items.length) }"
        >
          <div class="ai-settings-section__control">
            <!-- 权重组合 -->
            <template v-if="item.type === 'object'">
              <div v-if="form[item.key]" class="ai-settings-section__weights">
                <el-input-number
                  v-for="sub in ['w1', 'w2', 'w3']"
                  :key="sub"
                  v-model="(form[item.key] as Record<string, number>)[sub]"
                  :min="0"
                  :max="1"
                  :step="0.1"
                  :controls="false"
                />
                <span
                  class="ai-settings-section__weights-sum"
                  :class="{ 'is-error': Math.abs(currentWeightsSum(item, form) - 1) > 0.001 }"
                >
                  Σ {{ currentWeightsSum(item, form).toFixed(2) }}
                </span>
              </div>
            </template>
            <!-- 多选 -->
            <el-select
              v-else-if="item.type === 'string[]'"
              v-model="form[item.key] as string[]"
              multiple
              class="ai-settings-section__multi"
            >
              <el-option v-for="opt in item.options ?? []" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <!-- 数字 -->
            <el-input-number
              v-else
              v-model="form[item.key] as number"
              class="ai-settings-section__number"
              :min="item.min ?? undefined"
              :max="item.max ?? undefined"
              :step="item.step ?? 1"
            />
            <span v-if="settingModified(item, form)" class="ai-settings-section__modified">
              <el-tag size="small" type="warning" effect="light">已修改</el-tag>
              <el-button link type="primary" size="small" @click="emit('reset', item)">恢复默认</el-button>
            </span>
          </div>
          <span class="ai-settings-section__hint">{{ item.description }}（默认 {{ item.defaultValue }}）</span>
        </el-form-item>
      </div>
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.ai-settings-section {
  > :deep(.el-card__header) {
    padding-bottom: 4px;
  }
}

.ai-settings-section__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.ai-settings-section__icon {
  color: var(--color-primary-500);
  font-size: 16px;
}

.ai-settings-section__sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-neutral-400);
  margin-left: var(--space-sm);
}

.ai-settings-section__group-title {
  font-size: 13px;
  color: var(--color-neutral-500);
  border-left: 3px solid var(--color-primary-500);
  padding-left: var(--space-sm);
  margin: var(--space-md) 0;
}

.ai-settings-section__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--space-xl);
}

.ai-settings-section__item--full {
  grid-column: 1 / -1;
}

.ai-settings-section__control {
  width: 100%;
}

.ai-settings-section__weights {
  display: flex;
  align-items: center;
  gap: var(--space-sm);

  :deep(.el-input-number) {
    width: 90px;
  }
}

.ai-settings-section__weights-sum {
  font-size: 12px;
  color: var(--color-neutral-500);
  margin-left: var(--space-sm);

  &.is-error {
    color: var(--color-danger, #f56c6c);
  }
}

.ai-settings-section__number {
  width: 100%;
}

.ai-settings-section__multi {
  width: 100%;
}

.ai-settings-section__modified {
  display: inline-flex;
  align-items: center;
  margin-top: var(--space-sm);
  gap: var(--space-sm);
}

.ai-settings-section__hint {
  color: var(--color-neutral-400);
  font-size: 12px;
  line-height: 1.5;
}
</style>