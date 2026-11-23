<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import { caseGenerationNodeOf, isCaseGenerationSuggestion } from '@/composables/project/ai/assistPresentation'
import type { ConfirmItemResult } from '@/types'
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
  adopt: [keys: string[]]
  retry: []
  retryItem: [key: string]
  refresh: []
  clearReceipt: []
  openSource: [ref: AiAssistSourceRef]
}>()

const adoptedKeys = computed(() =>
  props.rows
    .filter((row) => row.confirmStatus === 'adopted' || row.confirmStatus === 'adopted_edited')
    .map((row) => row.key),
)

const showAdopted = ref(false)

/** 提案 → 注入树：已采纳用例默认不入树，开启展示后作为只读回看项出现 */
const injectedNodes = computed<TestCaseNode[]>(() => {
  const adopted = new Set(adoptedKeys.value)
  return props.rows
    .filter((row) => showAdopted.value || !adopted.has(row.key))
    .flatMap((row) => {
      const view = row.suggestion
      return view && isCaseGenerationSuggestion(view)
        ? [caseGenerationNodeOf(row.key, row.title || row.key, view)]
        : []
    })
})

const selectedKeys = ref<string[]>([])

function updateSelection(ids: string[]): void {
  selectedKeys.value = ids
}

// 切换「展示已采纳」只改变可见集合，保留仍存在的勾选，避免误清用户新选的用例
watch(injectedNodes, (nodes) => {
  const present = new Set(nodes.map((node) => node.id))
  selectedKeys.value = selectedKeys.value.filter((key) => present.has(key))
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
// 已采纳用例仅作只读回看，不参与本次采纳与回执
const adoptableKeys = computed(() => {
  const adopted = new Set(adoptedKeys.value)
  return pendingKeys.value.filter((key) => !adopted.has(key))
})

const canAdopt = computed(
  () => !props.running && !props.confirming && !props.failed && adoptableKeys.value.length > 0,
)

function adopt(): void {
  if (!canAdopt.value) return
  emit('adopt', [...adoptableKeys.value])
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
      <CaseSelector
        v-else
        class="ai-generation__selector"
        :model-value="true"
        :injected-nodes="injectedNodes"
        :adopted-case-ids="adoptedKeys"
        :show-adopted="showAdopted"
        inline
        @update:show-adopted="showAdopted = $event"
        @selection-change="updateSelection"
      />

      <AiConfirmReceipt
        v-if="showReceipt"
        :receipt="receiptItems"
        :confirming="confirming"
        @retry="(item) => emit('retryItem', item.key)"
        @clear="emit('clearReceipt')"
      />

      <footer class="ai-generation__foot">
        <span class="ai-generation__stat">
          已勾选 {{ pendingKeys.length }} / {{ injectedNodes.length }}；可采纳 {{ adoptableKeys.length }}
        </span>
        <div class="ai-generation__actions">
          <el-button
            link
            type="success"
            size="small"
            :disabled="!canAdopt"
            :loading="confirming"
            @click="adopt"
          >采纳</el-button>
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
  height: 100%;
  min-height: 0;
}

.ai-generation__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  flex-shrink: 0;
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
  min-height: 0;
  flex: 1;
  width: 100%;
}

.ai-generation__selector {
  flex: 1;
  min-height: 0;
}

.ai-generation__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--space-sm);
  border-top: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.ai-generation__stat {
  font-size: var(--font-size-sm);
  color: var(--el-text-color-secondary);
}

.ai-generation__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-xs);
}
</style>

// 弹窗根元素由 Element Plus Teleport 渲染，scoped 选择器无法命中；用 class 锚点限定布局（同 CaseSelector）。
<style lang="scss">
/*
 * CODE-008 例外：Dialog 根节点无组件 scopeId，仅在本弹窗类内建立高度链，避免影响其他弹窗。
 * 规则来源：docs/00-spec/10-engineering/01-frontend.md §8、docs/00-spec/50-ui/03-scroll-container.md UI-SC-01/02/06。
 */
.ai-generation-dialog {
  height: 88vh;
  display: flex;
  flex-direction: column;

  .el-dialog__header {
    padding: 12px 16px;
  }

  .el-dialog__body {
    padding: 12px 16px;
    position: relative;
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  .el-dialog__footer {
    padding: 12px 16px;
  }
}
</style>
