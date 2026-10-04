<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { TraceEdgeCreatePayload, TraceEdgeType, TraceNodeType } from '@/types'
import { useTraceNodePicker, type TraceNodeOption } from '@/composables/project/trace/useTraceNodePicker'

/**
 * 人工建边（交互 04 §2.2 / 详设 3.5）：关系类型决定合法的源目标组合，
 * 同一对节点已存在有效边时由后端回执 1000018153 并引导改用修正动作。
 */
const props = defineProps<{
  /** 测试用例候选来自当前链路树，无全局用例列表接口 */
  caseOptions?: TraceNodeOption[]
}>()

const emit = defineEmits<{ submit: [payload: TraceEdgeCreatePayload] }>()

const visible = defineModel<boolean>({ required: true })

/** 类型组合与后端 validateCombo 一致：derivation 上游→下游，case_snapshot 用例→评审/计划 */
const DERIVATION_SOURCE: TraceNodeType[] = ['requirement', 'module', 'mindmap_document']
const DERIVATION_TARGET: TraceNodeType[] = ['module', 'mindmap_document', 'test_case']
const CASE_SNAPSHOT_SOURCE: TraceNodeType[] = ['test_case']
const CASE_SNAPSHOT_TARGET: TraceNodeType[] = ['test_review', 'test_plan']

const NODE_TYPE_LABEL: Record<string, string> = {
  requirement: '需求',
  module: '模块',
  mindmap_document: '脑图文档',
  test_case: '测试用例',
  test_review: '评审',
  test_plan: '测试计划',
}

const formRef = ref<FormInstance>()
const form = reactive({
  edgeType: 'derivation' as TraceEdgeType,
  sourceType: 'requirement' as TraceNodeType,
  sourceId: '',
  targetType: 'mindmap_document' as TraceNodeType,
  targetId: '',
})

const sourcePicker = useTraceNodePicker()
const targetPicker = useTraceNodePicker()

const sourceTypes = computed(() =>
  form.edgeType === 'derivation' ? DERIVATION_SOURCE : CASE_SNAPSHOT_SOURCE,
)
const targetTypes = computed(() =>
  form.edgeType === 'derivation' ? DERIVATION_TARGET : CASE_SNAPSHOT_TARGET,
)

/** 测试用例候选注入链路树节点，其余类型按接口取候选 */
const sourceOptions = computed(() =>
  form.sourceType === 'test_case' ? (props.caseOptions ?? []) : sourcePicker.options.value,
)
const targetOptions = computed(() =>
  form.targetType === 'test_case' ? (props.caseOptions ?? []) : targetPicker.options.value,
)

const rules: FormRules = {
  sourceId: [{ required: true, message: '请选择源节点', trigger: 'change' }],
  targetId: [{ required: true, message: '请选择目标节点', trigger: 'change' }],
}

function loadSourceOptions(keyword = ''): void {
  void sourcePicker.load(form.sourceType, keyword)
}

function loadTargetOptions(keyword = ''): void {
  void targetPicker.load(form.targetType, keyword)
}

watch(
  () => form.edgeType,
  () => {
    form.sourceType = sourceTypes.value[0]
    form.targetType = targetTypes.value[0]
    form.sourceId = ''
    form.targetId = ''
    loadSourceOptions()
    loadTargetOptions()
  },
)

watch(
  () => form.sourceType,
  () => {
    form.sourceId = ''
    loadSourceOptions()
  },
)

watch(
  () => form.targetType,
  () => {
    form.targetId = ''
    loadTargetOptions()
  },
)

watch(visible, (open) => {
  if (!open) return
  form.edgeType = 'derivation'
  form.sourceType = 'requirement'
  form.targetType = 'mindmap_document'
  form.sourceId = ''
  form.targetId = ''
  sourcePicker.reset()
  targetPicker.reset()
  loadSourceOptions()
  loadTargetOptions()
})

async function submit(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  emit('submit', {
    edgeType: form.edgeType,
    sourceType: form.sourceType,
    sourceId: form.sourceId,
    targetType: form.targetType,
    targetId: form.targetId,
  })
}
</script>

<template>
  <el-dialog v-model="visible" title="人工建边" width="560px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
      <el-form-item label="关系类型">
        <el-radio-group v-model="form.edgeType">
          <el-radio value="derivation">派生边</el-radio>
          <el-radio value="case_snapshot">快照引用边</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="源节点">
        <div class="trace-edge-form__row">
          <el-select v-model="form.sourceType" style="width: 140px">
            <el-option
              v-for="type in sourceTypes"
              :key="type"
              :value="type"
              :label="NODE_TYPE_LABEL[type]"
            />
          </el-select>
          <el-select
            v-model="form.sourceId"
            filterable
            remote
            clearable
            :loading="sourcePicker.loading.value"
            :remote-method="loadSourceOptions"
            placeholder="选择源节点"
            style="flex: 1"
          >
            <el-option v-for="option in sourceOptions" :key="option.id" :value="option.id" :label="option.label" />
          </el-select>
        </div>
        <p v-if="form.sourceType === 'test_case'" class="trace-edge-form__hint">
          测试用例候选取自当前链路中的用例节点
        </p>
      </el-form-item>
      <el-form-item label="目标节点">
        <div class="trace-edge-form__row">
          <el-select v-model="form.targetType" style="width: 140px">
            <el-option
              v-for="type in targetTypes"
              :key="type"
              :value="type"
              :label="NODE_TYPE_LABEL[type]"
            />
          </el-select>
          <el-select
            v-model="form.targetId"
            filterable
            remote
            clearable
            :loading="targetPicker.loading.value"
            :remote-method="loadTargetOptions"
            placeholder="选择目标节点"
            style="flex: 1"
          >
            <el-option v-for="option in targetOptions" :key="option.id" :value="option.id" :label="option.label" />
          </el-select>
        </div>
        <p v-if="form.targetType === 'test_case'" class="trace-edge-form__hint">
          测试用例候选取自当前链路中的用例节点
        </p>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit">建边</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.trace-edge-form__row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.trace-edge-form__hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-neutral-500);
}
</style>
