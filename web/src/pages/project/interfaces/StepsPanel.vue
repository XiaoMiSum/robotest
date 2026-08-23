<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiInterfaceStepPayload } from '@/types'
import { createInterfaceStep, deleteInterfaceStep, sortInterfaceStep } from '@/services/apiInterface'
import { moveStep, reindexSteps } from '../interfacesModel'

const props = defineProps<{ interfaceId: string; steps: (ApiInterfaceStepPayload & { id: string })[] }>()
const emit = defineEmits<{ (e: 'change', steps: (ApiInterfaceStepPayload & { id: string })[]): void }>()

const STEP_TYPE_OPTIONS = [
  { value: 'script', label: '脚本' },
  { value: 'sql', label: 'SQL' },
  { value: 'sleep', label: '等待' },
]

const localSteps = ref<(ApiInterfaceStepPayload & { id?: string })[]>([])
const draft = ref<ApiInterfaceStepPayload>(emptyDraft())

function emptyDraft(): ApiInterfaceStepPayload {
  return { name: '', stepType: 'script', sortOrder: 0, enabled: true, requestConfig: {} }
}

watch(
  () => props.steps,
  (steps) => {
    localSteps.value = steps.map((step) => ({ ...step }))
    draft.value = emptyDraft()
  },
  { immediate: true, deep: false },
)

const sortedSteps = computed(() => [...localSteps.value].sort((a, b) => a.sortOrder - b.sortOrder))

async function addStep() {
  if (!draft.value.name.trim()) {
    ElMessage.warning('请填写步骤名称')
    return
  }
  const created = await createInterfaceStep(props.interfaceId, {
    ...draft.value,
    name: draft.value.name.trim(),
    sortOrder: localSteps.value.length,
  })
  const step: ApiInterfaceStepPayload & { id: string } = { ...draft.value, id: created, sortOrder: localSteps.value.length }
  emit('change', reindexSteps([...localSteps.value, step]) as (ApiInterfaceStepPayload & { id: string })[])
}

async function removeStep(step: ApiInterfaceStepPayload & { id: string }) {
  await ElMessageBox.confirm(`删除步骤「${step.name}」？`, '删除步骤', { type: 'warning' })
  await deleteInterfaceStep(props.interfaceId, step.id)
  emit('change', reindexSteps(localSteps.value.filter((item) => item.id !== step.id)) as (ApiInterfaceStepPayload & { id: string })[])
  ElMessage.success('步骤已删除')
}

type StepRow = ApiInterfaceStepPayload & { id: string }

function onMove(row: unknown, delta: number) {
  return handleMove(row as StepRow, delta)
}

function onRemove(row: unknown) {
  return removeStep(row as StepRow)
}

async function handleMove(step: StepRow, delta: number) {
  const next = moveStep(localSteps.value, step.id!, delta) as (ApiInterfaceStepPayload & { id: string })[]
  if (next === localSteps.value) return
  emit('change', next)
  // 服务端逐项落库排序；失败时静默，刷新后以服务端为准
  for (const item of next) {
    await sortInterfaceStep(props.interfaceId, item.id, item.sortOrder).catch(() => undefined)
  }
}
</script>

<template>
  <div class="steps-panel">
    <el-table :data="sortedSteps" size="small" data-test="steps-table">
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ STEP_TYPE_OPTIONS.find((option) => option.value === row.stepType)?.label ?? row.stepType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="70">
        <template #default="{ row }">
          <el-checkbox :model-value="row.enabled" disabled />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="right">
        <template #default="{ row }">
          <el-button link size="small" :disabled="row.sortOrder === 0" @click="onMove(row, -1)">上移</el-button>
          <el-button link size="small" :disabled="row.sortOrder === sortedSteps.length - 1" @click="onMove(row, 1)">下移</el-button>
          <el-button link size="small" type="danger" @click="onRemove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无公共步骤</template>
    </el-table>

    <div class="steps-panel__add">
      <el-input v-model="draft.name" placeholder="步骤名称" size="small" style="width: 200px" data-test="step-name-input" />
      <el-select v-model="draft.stepType" size="small" style="width: 120px">
        <el-option v-for="option in STEP_TYPE_OPTIONS" :key="option.value" :value="option.value" :label="option.label" />
      </el-select>
      <el-button size="small" type="primary" plain data-test="step-add-btn" @click="addStep">添加步骤</el-button>
    </div>
    <p class="steps-panel__hint">前置脚本 / SQL / 等待元件的参数编辑器与提取器、断言将随「测试场景」模块一起开放。</p>
  </div>
</template>

<style scoped lang="scss">
.steps-panel__add {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}

.steps-panel__hint {
  margin: var(--space-sm) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}
</style>
