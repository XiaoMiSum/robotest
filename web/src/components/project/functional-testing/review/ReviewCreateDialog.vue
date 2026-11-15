<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { RequirementPick } from '@/composables/project/requirement/useRequirementPicker'
import type { TestReviewCreatePayload } from '@/types'
import RequirementPickerDialog from '@/components/common/RequirementPickerDialog.vue'
import { useMemberOptions } from '@/composables/project/functional-testing/useMemberOptions'

const props = defineProps<{
  modelValue: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 校验通过后交由调用方落库（列表页直接创建） */
  submit: [payload: TestReviewCreatePayload]
}>()

const { memberOptions, loadMemberOptions } = useMemberOptions()

const requirementPickerVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  title: '',
  description: '',
  participantIds: [] as string[],
  requirementIds: [] as string[],
  requirements: [] as RequirementPick[],
})
const rules: FormRules = {
  title: [{ required: true, message: '请输入评审标题', trigger: 'blur' }],
}

/** 每次打开重置表单 */
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.title = ''
    form.description = ''
    form.participantIds = []
    form.requirementIds = []
    form.requirements = []
    void loadMemberOptions()
  },
)

function handleRequirementConfirmed(ids: string[], picks: RequirementPick[]): void {
  form.requirementIds = ids
  form.requirements = picks
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!form.requirementIds.length) {
    ElMessage.warning('请关联至少一个需求')
    return
  }
  if (!form.participantIds.length) {
    ElMessage.warning('请选择至少一个参与者')
    return
  }
  emit('submit', {
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    participantIds: form.participantIds,
    requirementIds: form.requirementIds,
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="发起评审"
    width="560px"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入评审标题" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="评审描述（可选）" />
      </el-form-item>
      <el-form-item label="参与者">
        <el-select v-model="form.participantIds" multiple filterable placeholder="选择参与者" style="width: 100%">
          <el-option v-for="m in memberOptions" :key="m.userId" :label="m.name || m.username" :value="m.userId" />
        </el-select>
      </el-form-item>
      <el-form-item label="关联需求">
        <el-button @click="requirementPickerVisible = true">选择需求</el-button>
        <span v-if="form.requirementIds.length" class="review-create__requirement-count">
          已选 {{ form.requirementIds.length }} 个需求
        </span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>

    <RequirementPickerDialog
      v-model="requirementPickerVisible"
      :initial="form.requirements"
      @confirm="handleRequirementConfirmed"
    />
  </el-dialog>
</template>

<style scoped lang="scss">
.review-create__requirement-count {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}
</style>
