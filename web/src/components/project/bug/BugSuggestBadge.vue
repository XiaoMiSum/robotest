<script setup lang="ts">
import { computed } from 'vue'
import type { BugSuggestionField } from '@/types'

const props = defineProps<{
  /** 表单当前值（用户已填值与建议不一致时以用户值为准，不覆盖） */
  modelValue: string
  label: string
  suggestion: BugSuggestionField<string> | null | undefined
  /** 采纳时写入表单的值（与展示文本不同源时显式给出，如 moduleId） */
  adoptValue?: string
}>()

const emit = defineEmits<{ adopt: [value: string] }>()

const hasSuggestion = computed(() => !!props.suggestion && !!props.suggestion.value)
/** 用户值与建议一致或已采纳 → 徽标收敛，避免重复提示 */
const adopted = computed(
  () => hasSuggestion.value && props.modelValue === (props.adoptValue ?? props.suggestion?.value),
)

function adopt(): void {
  if (!hasSuggestion.value || adopted.value) return
  emit('adopt', props.adoptValue ?? String(props.suggestion?.value ?? ''))
}
</script>

<template>
  <!-- 建议值标记：浅底徽标 + 图标，区别于用户已填值（视觉 4 节） -->
  <el-tooltip v-if="hasSuggestion && !adopted" :content="`AI 建议：${suggestion?.reason || '无理由'}`" placement="top">
    <el-tag type="primary" effect="plain" size="small" class="suggest-badge" @click.stop="adopt">
      <el-icon class="suggest-badge__icon"><MagicStick /></el-icon>
      {{ label }}：{{ suggestion?.value }}（采纳）
    </el-tag>
  </el-tooltip>
</template>

<style scoped lang="scss">
.suggest-badge {
  margin-left: var(--space-sm);
  cursor: pointer;
}

.suggest-badge__icon {
  margin-right: 2px;
}
</style>
