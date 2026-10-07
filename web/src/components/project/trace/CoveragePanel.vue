<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { RequirementCoverageStatus, TraceCoverage } from '@/types'
import { traceCoverageMeta } from '@/composables/project/trace/tracePresentation'

/**
 * 覆盖修正面板（交互 04 §2.3）：依据展示 + 人工修正；
 * 无结论时放开「发起覆盖分析」，进行中展示任务进度、失败后可重试。
 */
const props = defineProps<{
  record: TraceCoverage | null
  loading: boolean
  loadError: string
  saving: boolean
  canEdit: boolean
  aiAvailable: boolean
  busy: boolean
  taskRunning: boolean
  taskProgress: number | null
  taskFailed: boolean
  taskError: string
}>()

const emit = defineEmits<{
  retry: []
  save: [payload: { coverageStatus: Exclude<RequirementCoverageStatus, 'pending'>; note?: string }]
  analyze: []
  retryAnalyze: []
}>()

const formRef = ref<FormInstance>()
const form = reactive({
  coverageStatus: '' as Exclude<RequirementCoverageStatus, 'pending'> | '',
  note: '',
})

const rules: FormRules = {
  coverageStatus: [{ required: true, message: '请选择覆盖结论', trigger: 'change' }],
}

const statusOptions: { value: Exclude<RequirementCoverageStatus, 'pending'>; label: string }[] = [
  { value: 'covered', label: '完整覆盖' },
  { value: 'partial', label: '部分覆盖' },
  { value: 'uncovered', label: '未覆盖' },
]

const currentMeta = computed(() => traceCoverageMeta(props.record?.coverageStatus ?? null))

/** AI 判定依据为自由结构（用例集合 / 缺口说明 / 理由摘要），按键值对就近展示 */
const evidenceEntries = computed(() => {
  const evidence = props.record?.evidence
  if (!evidence) return []
  return Object.entries(evidence).map(([key, value]) => ({
    key,
    text: typeof value === 'string' ? value : JSON.stringify(value),
  }))
})

const isManual = computed(() => props.record?.reviewedBy != null)

/** 无结论可看才放开 AI 入口：已有结论先看结论，人工结论 AI 不会覆盖，重跑无意义 */
const canAnalyze = computed(() => evidenceEntries.value.length === 0 && !isManual.value)

const showAnalyzeHint = computed(() => props.aiAvailable && canAnalyze.value)

watch(
  () => props.record,
  (next) => {
    form.coverageStatus = next?.coverageStatus ?? ''
    form.note = next?.reviewedNote ?? ''
  },
  { immediate: true },
)

async function submit(): Promise<void> {
  if (!formRef.value || form.coverageStatus === '') return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  emit('save', {
    coverageStatus: form.coverageStatus,
    note: form.note.trim() || undefined,
  })
}
</script>

<template>
  <section class="coverage-panel" aria-label="覆盖修正">
    <header class="coverage-panel__head">
      <span class="coverage-panel__title">覆盖判定</span>
      <el-tag v-if="currentMeta" :type="currentMeta.tagType" size="small" effect="light">
        {{ currentMeta.label }}
      </el-tag>
      <el-tag v-else size="small" effect="plain" type="info">未分析</el-tag>
      <el-tag v-if="isManual" size="small" effect="plain">人工结论</el-tag>
      <span class="coverage-panel__actions">
        <el-button
          v-if="aiAvailable && taskFailed"
          size="small"
          :loading="busy"
          @click="emit('retryAnalyze')"
        >
          重试
        </el-button>
        <el-button
          v-if="aiAvailable && canAnalyze && !taskRunning && !taskFailed"
          type="primary"
          size="small"
          :loading="busy"
          @click="emit('analyze')"
        >
          发起覆盖分析
        </el-button>
      </span>
    </header>

    <el-progress
      v-if="taskRunning"
      :percentage="taskProgress ?? 0"
      :stroke-width="8"
    />

    <el-alert
      v-if="taskFailed && taskError"
      type="warning"
      :title="taskError"
      show-icon
      :closable="false"
    />

    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="coverage-panel__error"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="emit('retry')">重试</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="coverage-panel__body">
      <template v-if="evidenceEntries.length > 0">
        <p class="coverage-panel__label">判定依据</p>
        <dl class="coverage-panel__evidence">
          <template v-for="entry in evidenceEntries" :key="entry.key">
            <dt>{{ entry.key }}</dt>
            <dd>{{ entry.text }}</dd>
          </template>
        </dl>
        <p v-if="record?.aiAnalyzedAt" class="coverage-panel__time">AI 分析于 {{ record.aiAnalyzedAt }}</p>
      </template>
      <p v-else-if="!loading && !loadError" class="coverage-panel__empty">
        {{
          showAnalyzeHint
            ? '该需求暂无覆盖分析记录，可发起 AI 分析或在下方给出人工结论'
            : '该需求无覆盖分析记录，可在下方直接给出人工结论'
        }}
      </p>
    </div>

    <el-form
      v-if="canEdit"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="coverage-panel__form"
    >
      <el-form-item label="人工修正" prop="coverageStatus">
        <div class="coverage-panel__row">
          <el-select v-model="form.coverageStatus" placeholder="覆盖结论" style="width: 150px">
            <el-option v-for="option in statusOptions" :key="option.value" v-bind="option" />
          </el-select>
          <el-button type="primary" :loading="saving" @click="submit">保存结论</el-button>
        </div>
      </el-form-item>
      <el-form-item label="修正说明">
        <el-input
          v-model="form.note"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="补充判定说明，保存后 AI 分析不再覆盖该结论"
        />
      </el-form-item>
    </el-form>
  </section>
</template>

<style scoped lang="scss">
.coverage-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.coverage-panel__head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.coverage-panel__title {
  font-weight: 600;
  color: var(--color-neutral-800);
}

.coverage-panel__label {
  margin: 0;
  font-size: 12px;
  color: var(--color-neutral-500);
}

.coverage-panel__body {
  min-height: 72px;
}

.coverage-panel__evidence {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 4px 12px;
  margin: 0;
  font-size: 13px;
}

.coverage-panel__evidence dt {
  color: var(--color-neutral-500);
}

.coverage-panel__evidence dd {
  margin: 0;
  color: var(--color-neutral-800);
  word-break: break-word;
}

.coverage-panel__time,
.coverage-panel__empty {
  margin: 0;
  font-size: 12px;
  color: var(--color-neutral-500);
}

.coverage-panel__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.coverage-panel__row {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
