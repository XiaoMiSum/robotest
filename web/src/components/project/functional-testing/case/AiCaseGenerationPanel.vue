<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import { caseGenerationNodeOf, isCaseGenerationSuggestion } from '@/composables/project/ai/assistPresentation'
import type { ConfirmItemResult } from '@/composables/project/ai/useAiProposalReview'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { RequirementPick } from '@/composables/project/requirement/useRequirementPicker'
import type { AiAssistSourceRef, AiProposalConfirmResult, TestCaseNode } from '@/types'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    requirement: RequirementPick | null
    rows: AssistRow[]
    loading?: boolean
    confirming?: boolean
    running?: boolean
    failed?: boolean
    failedReason?: string
    progressText?: string
    retryable?: boolean
    receipt?: AiProposalConfirmResult[]
    loadError?: string
    /** 来源需求已变更：提案仅供参考，仍可插入（交互 2.3 同口径） */
    sourceStale?: boolean
  }>(),
  {
    loading: false,
    confirming: false,
    running: false,
    failed: false,
    failedReason: '',
    progressText: '',
    retryable: false,
    receipt: () => [],
    loadError: '',
    sourceStale: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  insert: [keys: string[]]
  retry: []
  retryItem: [key: string]
  refresh: []
  clearReceipt: []
  openSource: [ref: AiAssistSourceRef]
}>()

/** 提案 → 注入树：用例节点 id 即提案 key，插入与回执按此定位 */
const injectedNodes = computed<TestCaseNode[]>(() =>
  props.rows.flatMap((row) => {
    const view = row.suggestion
    return view && isCaseGenerationSuggestion(view)
      ? [caseGenerationNodeOf(row.key, row.title || row.key, view)]
      : []
  }),
)

const selectedKeys = ref<string[]>([])

function updateSelection(ids: string[]): void {
  selectedKeys.value = ids
}

// 重新执行后提案集合可能整体变化，清空旧勾选，避免已不存在的 key 混入插入与回执
watch(injectedNodes, () => {
  selectedKeys.value = []
})

const receiptItems = computed<ConfirmItemResult[]>(() =>
  props.receipt.map((item) => ({
    key: item.key,
    title: props.rows.find((row) => row.key === item.key)?.title ?? item.key,
    action: item.action,
    success: item.success,
    errorCode: item.errorCode,
    errorMsg: item.errorMsg ?? '',
    createdId: item.createdId,
  })),
)

const showReceipt = computed(() => receiptItems.value.length > 0)
const pendingKeys = computed(() =>
  selectedKeys.value.filter((key) => props.rows.some((row) => row.key === key)),
)
const canInsert = computed(
  () => !props.running && !props.confirming && !props.failed && pendingKeys.value.length > 0,
)

function insert(): void {
  if (!canInsert.value) return
  emit('insert', [...pendingKeys.value])
}

function close(): void {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="AI覆盖雷达"
    width="1100px"
    top="6vh"
    class="ai-generation-dialog"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="ai-generation">
      <header class="ai-generation__head">
        <div class="ai-generation__requirement">
          <span class="ai-generation__label">来源需求</span>
          <template v-if="requirement">
            <span v-if="requirement.code" class="ai-generation__code">{{ requirement.code }}</span>
            <span class="ai-generation__title">{{ requirement.title }}</span>
          </template>
          <span v-else>—</span>
        </div>
        <div class="ai-generation__head-actions">
          <el-button
            v-if="retryable"
            link
            type="primary"
            :disabled="confirming"
            @click="emit('retry')"
          >
            重新执行
          </el-button>
          <el-button link @click="close">关闭</el-button>
        </div>
      </header>

      <el-alert
        v-if="running"
        type="info"
        :closable="false"
        show-icon
        title="AI 正在生成用例…"
        :description="progressText || '生成完成后即可勾选并插入当前脑图'"
        class="ai-generation__alert"
      />
      <el-alert
        v-else-if="failed"
        type="error"
        :closable="false"
        show-icon
        :title="failedReason || '用例生成失败'"
        class="ai-generation__alert"
      >
        <el-button size="small" type="danger" plain @click="emit('retry')">再次执行</el-button>
      </el-alert>
      <el-alert
        v-else-if="loadError"
        type="error"
        :closable="false"
        show-icon
        :title="loadError"
        class="ai-generation__alert"
      >
        <el-button size="small" plain @click="emit('refresh')">重试</el-button>
      </el-alert>
      <el-alert
        v-if="sourceStale && !running"
        title="来源需求已变更"
        description="生成结果仅供参考，请核对后再插入脑图"
        type="warning"
        :closable="false"
        show-icon
        class="ai-generation__alert"
      />

      <div v-if="running || failed || loadError" class="ai-generation__placeholder">
        <el-skeleton v-if="running" :rows="6" animated />
        <el-empty v-else :image-size="60" description="暂无可插入的用例" />
      </div>
      <div v-else-if="rows.length === 0" class="ai-generation__placeholder">
        <el-empty :image-size="60" description="本次未生成候选用例" />
      </div>
      <div v-else class="ai-generation__tree">
        <CaseSelector
          :model-value="true"
          :injected-nodes="injectedNodes"
          inline
          @selection-change="updateSelection"
        />
      </div>

      <AiConfirmReceipt
        v-if="showReceipt"
        :receipt="receiptItems"
        :confirming="confirming"
        @retry="(item) => emit('retryItem', item.key)"
        @clear="emit('clearReceipt')"
      />

      <footer class="ai-generation__foot">
        <span class="ai-generation__stat">已勾选 {{ pendingKeys.length }} / {{ injectedNodes.length }} 条用例</span>
        <div class="ai-generation__actions">
          <el-button
            type="primary"
            size="small"
            :disabled="!canInsert"
            :loading="confirming"
            @click="insert"
          >
            插入选中用例
          </el-button>
        </div>
      </footer>
    </div>
  </el-dialog>
</template>

<style scoped lang="scss">
.ai-generation {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-height: 420px;
}

.ai-generation__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.ai-generation__requirement {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  min-width: 0;
}

.ai-generation__label {
  font-size: var(--font-size-sm);
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.ai-generation__code {
  font-size: var(--font-size-sm);
  color: var(--color-primary-600);
  flex-shrink: 0;
}

.ai-generation__title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-generation__head-actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-shrink: 0;
}

.ai-generation__alert {
  flex-shrink: 0;
}

.ai-generation__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  flex: 1;
  width: 100%;
}

.ai-generation__tree {
  flex: 1;
  min-height: 320px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  padding: var(--space-sm);
  overflow: hidden;
}

.ai-generation__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--space-sm);
  border-top: 1px solid var(--el-border-color-lighter);
}

.ai-generation__stat {
  font-size: var(--font-size-sm);
  color: var(--el-text-color-secondary);
}

.ai-generation__actions {
  display: flex;
  gap: var(--space-xs);
}
</style>
