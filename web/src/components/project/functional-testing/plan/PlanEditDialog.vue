<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { RequirementPick } from '@/composables/project/requirement/useRequirementPicker'
import type { TestPlanDetail, TestPlanUpdatePayload } from '@/types'
import RequirementPickerDialog from '@/components/common/RequirementPickerDialog.vue'
import { useMemberOptions } from '@/composables/project/functional-testing/useMemberOptions'

const props = defineProps<{
  modelValue: boolean
  detail: TestPlanDetail | null
  submitting?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: TestPlanUpdatePayload]
}>()

const { memberOptions, loadMemberOptions } = useMemberOptions()

const requirementPickerVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  description: '',
  executorId: '',
  startTime: null as string | null,
  endTime: null as string | null,
  environment: '',
  requirementIds: [] as string[],
  requirements: [] as RequirementPick[],
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
}

// 详情异步返回后才打开弹窗，以打开时机回填，避免覆盖用户正在编辑的内容
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible || !props.detail) return
    form.name = props.detail.name
    form.description = props.detail.description ?? ''
    form.executorId = props.detail.executor?.id ?? ''
    form.startTime = props.detail.startTime
    form.endTime = props.detail.endTime
    form.environment = props.detail.environment ?? ''
    form.requirementIds = [...props.detail.requirementIds]
    form.requirements = props.detail.requirements.map((item) => ({
      id: item.id,
      code: item.code,
      title: item.title,
    }))
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
  emit('submit', {
    name: form.name.trim(),
    description: form.description.trim(),
    executorId: form.executorId || undefined,
    startTime: form.startTime,
    endTime: form.endTime,
    environment: form.environment.trim(),
    requirementIds: [...form.requirementIds],
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑计划"
    width="600px"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入计划名称" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="计划描述（可选）" />
      </el-form-item>
      <el-form-item label="负责人">
        <el-select v-model="form.executorId" filterable clearable placeholder="选择负责人" style="width: 100%">
          <el-option v-for="m in memberOptions" :key="m.userId" :label="m.name || m.username" :value="m.userId" />
        </el-select>
      </el-form-item>
      <el-form-item label="开始时间">
        <el-date-picker
          v-model="form.startTime"
          type="date"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="选择日期"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="结束时间">
        <el-date-picker
          v-model="form.endTime"
          type="date"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="选择日期"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="执行环境">
        <el-input v-model="form.environment" placeholder="如：staging / production" />
      </el-form-item>
      <el-form-item label="关联需求">
        <el-button @click="requirementPickerVisible = true">选择需求</el-button>
        <span v-if="form.requirementIds.length" class="plan-edit__requirement-count">
          已选 {{ form.requirementIds.length }} 个需求
        </span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>

    <RequirementPickerDialog
      v-model="requirementPickerVisible"
      :initial="form.requirements"
      @confirm="handleRequirementConfirmed"
    />
  </el-dialog>
</template>

<style scoped lang="scss">
.plan-edit__requirement-count {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}
</style>
