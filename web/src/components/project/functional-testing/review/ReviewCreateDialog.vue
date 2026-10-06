<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { SelectedCaseNode, TestReviewCreatePayload } from '@/types'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import { useMemberOptions } from '@/composables/project/functional-testing/useMemberOptions'

const props = defineProps<{
  modelValue: boolean
  /** 预选用例（圈选确认回填），打开时写入表单，仍可经用例选择器调整 */
  initialSelectedNodes?: SelectedCaseNode[]
  submitting?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 校验通过后交由调用方落库（列表页直接创建 / 圈选确认转 createParams） */
  submit: [payload: TestReviewCreatePayload]
}>()

const { memberOptions, loadMemberOptions } = useMemberOptions()

const caseSelectorVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  title: '',
  description: '',
  participantIds: [] as string[],
  selectedNodes: [] as SelectedCaseNode[],
})
const rules: FormRules = {
  title: [{ required: true, message: '请输入评审标题', trigger: 'blur' }],
}

/** 每次打开重置表单；预选节点浅拷贝隔离，弹窗内调整不回写调用方列表 */
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.title = ''
    form.description = ''
    form.participantIds = []
    form.selectedNodes = (props.initialSelectedNodes ?? []).map((node) => ({
      documentId: node.documentId,
      caseIds: [...node.caseIds],
    }))
    void loadMemberOptions()
  },
)

function handleCaseSelected(nodes: SelectedCaseNode[]): void {
  form.selectedNodes = nodes
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!form.selectedNodes.length) {
    ElMessage.warning('请关联至少一个用例')
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
    selectedNodes: form.selectedNodes,
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
      <el-form-item label="关联用例">
        <el-button @click="caseSelectorVisible = true">选择用例</el-button>
        <span v-if="form.selectedNodes.length" class="review-create__case-count">
          已选 {{ form.selectedNodes.reduce((sum, n) => sum + n.caseIds.length, 0) }} 个用例
        </span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>

    <CaseSelector
      v-model="caseSelectorVisible"
      :initial-selected="form.selectedNodes"
      @confirm="handleCaseSelected"
    />
  </el-dialog>
</template>

<style scoped lang="scss">
.review-create__case-count {
  margin-left: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}
</style>
