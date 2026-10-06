<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ProjectModule } from '@/types'
import type {
  AiGenerationConfig,
  AiGenerationGranularity,
  AiGenerationPlacement,
  AiGenerationScopeItem,
} from '@/types'
import AiScopeCheckList from '@/components/project/ai/AiScopeCheckList.vue'
import { useGenerationSubmit } from '@/composables/project/ai/useGenerationSubmit'

/** 服务端输入条数上限（生成链详设 3.2，1–50） */
const SCOPE_MAX = 50

const props = defineProps<{
  modelValue: boolean
  requirements: AiGenerationScopeItem[]
  /** 目录树（列表 / 详情页已剥离文档节点），指定模块落位时选用 */
  moduleTree: ProjectModule[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 提交成功已跳转任务详情页 */
  submitted: []
  /** 输入状态已变化，调用方刷新范围回显（交互 2.5） */
  stale: []
}>()

const { submitting, submitGeneration } = useGenerationSubmit()

const checkedIds = ref<string[]>([])
const placement = ref<AiGenerationPlacement>('new_top_level')
const targetModuleId = ref('')
const granularity = ref<AiGenerationGranularity>('standard')

const placementOptions: { value: AiGenerationPlacement; label: string }[] = [
  { value: 'new_top_level', label: '自动识别（新建顶级目录）' },
  { value: 'attach', label: '指定模块' },
]

const granularityOptions: { value: AiGenerationGranularity; label: string }[] = [
  { value: 'concise', label: '精简' },
  { value: 'standard', label: '标准' },
  { value: 'detailed', label: '详细' },
]

/** 每次打开重置表单，范围默认勾选已确认需求（交互 2.1） */
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    checkedIds.value = props.requirements
      .filter((item) => item.status === 'confirmed')
      .map((item) => item.id)
    placement.value = 'new_top_level'
    targetModuleId.value = ''
    granularity.value = 'standard'
  },
)

const overLimit = computed(() => checkedIds.value.length > SCOPE_MAX)

const canSubmit = computed(
  () =>
    checkedIds.value.length > 0 &&
    !overLimit.value &&
    (placement.value === 'new_top_level' || targetModuleId.value !== ''),
)

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  const config: AiGenerationConfig = {
    placement: placement.value,
    granularity: granularity.value,
    ...(placement.value === 'attach' ? { targetModuleId: targetModuleId.value } : {}),
  }
  const result = await submitGeneration(checkedIds.value, config)
  if (result === 'submitted') {
    emit('update:modelValue', false)
    emit('submitted')
  } else if (result === 'stale') {
    emit('stale')
  }
}

function close(): void {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="AI 生成测试设计"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div class="gen-dialog__section">
      <div class="gen-dialog__label">
        生成范围（已选 {{ requirements.length }} 条需求）
      </div>
      <AiScopeCheckList
        v-model="checkedIds"
        :requirements="requirements"
        verb="生成"
        :max="SCOPE_MAX"
      />
    </div>

    <div class="gen-dialog__section">
      <div class="gen-dialog__label">目标模块落位</div>
      <el-radio-group v-model="placement">
        <el-radio v-for="option in placementOptions" :key="option.value" :value="option.value">
          {{ option.label }}
        </el-radio>
      </el-radio-group>
      <el-tree-select
        v-if="placement === 'attach'"
        v-model="targetModuleId"
        :data="moduleTree"
        check-strictly
        clearable
        placeholder="选择目标模块"
        class="gen-dialog__module"
      />
    </div>

    <div class="gen-dialog__section">
      <div class="gen-dialog__label">用例粒度偏好</div>
      <el-select v-model="granularity" style="width: 200px">
        <el-option v-for="option in granularityOptions" :key="option.value" v-bind="option" />
      </el-select>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit">
        开始生成
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.gen-dialog__section + .gen-dialog__section {
  margin-top: var(--space-md);
}

.gen-dialog__label {
  margin-bottom: var(--space-xs);
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.gen-dialog__module {
  width: 100%;
  margin-top: var(--space-xs);
}
</style>
