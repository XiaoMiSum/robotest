<script setup lang="ts">
import { computed } from 'vue'
import type { AiGenerationScopeItem } from '@/types'
import { REQUIREMENT_STATUS_META } from '@/composables/project/requirement/requirementPresentation'

const props = defineProps<{
  requirements: AiGenerationScopeItem[]
  modelValue: string[]
  /** 状态不可选时的动词文案：生成 / 圈选（交互 2.1 同口径置灰） */
  verb?: string
  /** 单次提交条数上限（缺省不限制） */
  max?: number
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()

const checked = computed({
  get: () => props.modelValue,
  set: (value: string[]) => emit('update:modelValue', value),
})

function statusReason(item: AiGenerationScopeItem): string {
  const meta = REQUIREMENT_STATUS_META[item.status as keyof typeof REQUIREMENT_STATUS_META]
  const verb = props.verb ?? '选择'
  return meta ? `${meta.label}需求不可${verb}` : `当前状态不可${verb}`
}

const overLimit = computed(
  () => props.max !== undefined && props.modelValue.length > props.max,
)
</script>

<template>
  <el-checkbox-group v-model="checked" class="ai-scope">
    <div v-for="item in requirements" :key="item.id" class="ai-scope__item">
      <el-checkbox :value="item.id" :disabled="item.status !== 'confirmed'">
        <span class="ai-scope__code">{{ item.code }}</span>{{ item.title }}
      </el-checkbox>
      <span v-if="item.status !== 'confirmed'" class="ai-scope__reason">
        {{ statusReason(item) }}
      </span>
    </div>
  </el-checkbox-group>
  <div v-if="overLimit" class="ai-scope__reason">
    单次最多 {{ max }} 条需求，请减少勾选
  </div>
</template>

<style scoped lang="scss">
.ai-scope {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  max-height: 240px;
  overflow: auto;
  padding: var(--space-xs) 0;
}

.ai-scope__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  min-width: 0;
}

.ai-scope__code {
  margin-right: var(--space-xs);
  color: var(--color-neutral-500);
  font-family: var(--font-family-mono, monospace);
  font-size: var(--font-size-sm);
}

.ai-scope__reason {
  flex-shrink: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}
</style>
