<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AiGenerationScopeItem } from '@/types'
import AiScopeCheckList from '@/components/project/ai/AiScopeCheckList.vue'
import {
  useSelectionSubmit,
  type SelectionTaskType,
} from '@/composables/project/ai/useSelectionSubmit'

const props = defineProps<{
  modelValue: boolean
  requirements: AiGenerationScopeItem[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 提交成功已跳转任务详情页 */
  submitted: []
}>()

const { submitting, submitSelection } = useSelectionSubmit()

const checkedIds = ref<string[]>([])
const type = ref<SelectionTaskType>('review_selection')
const roundCount = ref(1)

const typeOptions: { value: SelectionTaskType; label: string }[] = [
  { value: 'review_selection', label: '评审圈选（单轮推荐）' },
  { value: 'plan_selection', label: '计划圈选（按轮次分组）' },
]

/** 每次打开重置表单，范围默认勾选已确认需求（交互 2.4 与 2.1 同口径） */
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    checkedIds.value = props.requirements
      .filter((item) => item.status === 'confirmed')
      .map((item) => item.id)
    type.value = 'review_selection'
    roundCount.value = 1
  },
)

const canSubmit = computed(() => checkedIds.value.length > 0)

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  const result = await submitSelection(type.value, {
    requirementIds: checkedIds.value,
    ...(type.value === 'plan_selection' ? { roundCount: roundCount.value } : {}),
  })
  if (result === 'submitted') {
    emit('update:modelValue', false)
    emit('submitted')
  }
}

function close(): void {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="AI 圈选建议"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div class="sel-dialog__section">
      <div class="sel-dialog__label">圈选范围（已选 {{ requirements.length }} 条需求）</div>
      <AiScopeCheckList
        v-model="checkedIds"
        :requirements="requirements"
        verb="圈选"
      />
    </div>

    <div class="sel-dialog__section">
      <div class="sel-dialog__label">推荐去向</div>
      <el-radio-group v-model="type">
        <el-radio v-for="option in typeOptions" :key="option.value" :value="option.value">
          {{ option.label }}
        </el-radio>
      </el-radio-group>
      <div v-if="type === 'plan_selection'" class="sel-dialog__rounds">
        <span class="sel-dialog__rounds-label">计划轮次（1–10）</span>
        <el-input-number v-model="roundCount" :min="1" :max="10" />
      </div>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit">
        发起圈选
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.sel-dialog__section + .sel-dialog__section {
  margin-top: var(--space-md);
}

.sel-dialog__label {
  margin-bottom: var(--space-xs);
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.sel-dialog__rounds {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.sel-dialog__rounds-label {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
}
</style>
