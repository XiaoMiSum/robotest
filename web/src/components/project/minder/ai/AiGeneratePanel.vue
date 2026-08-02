<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import RequirementSelector from '@/components/project/RequirementSelector.vue'
import { getDocumentRequirements } from '@/services/project'
import { useAiStream, type AiStreamController } from '@/composables/useAiStream'
import { useAiStore } from '@/stores/ai'
import type { AiCaseGenerateResult, AiGeneratedNode, RequirementSummary } from '@/types'
import { buildPreviewTree, filterCheckedTree, type AiPreviewNode } from './aiMount'
import { AI_PANEL_MODES, type AiPanelMode } from './aiPanelModes'
import AiPreviewTree from './AiPreviewTree.vue'

/**
 * AI 生成抽屉（US-AI-001/002，交互设计 2.1/2.2/3.1）：
 * 文本输入 → SSE 流式输出 → done 后切结构化预览树（勾选取舍）→ 确认挂载（由父组件执行）。
 * 两种模式差异集中在 aiPanelModes 配置表；需求条目区（US-AI-004）供 generate/complete 消费。
 */
const props = defineProps<{
  mode: AiPanelMode
  docId: string
  targetNodeId: string
  /** 挂载目标节点路径（根 > … > 目标），打开时由脑图组件计算 */
  targetPath: string
  /** 外部跳转带入的预填文本（如遗漏测试点转用例生成） */
  initialText?: string
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  /** 用户确认挂载：经勾选过滤后的节点树，由脑图组件执行挂载 */
  mount: [nodes: AiGeneratedNode[]]
}>()

const aiStore = useAiStore()
const config = AI_PANEL_MODES[props.mode]

type Phase = 'idle' | 'streaming' | 'done'
const phase = ref<Phase>('idle')
const inputText = ref(props.initialText ?? '')
const streamText = ref('')
const previewNodes = ref<AiPreviewNode[]>([])
const warnings = ref<string[]>([])
/** 已选需求池条目（US-AI-004），随请求体透传；打开时默认带入文档关联条目 */
const selectedRequirements = ref<RequirementSummary[]>([])
const requirementSelectorVisible = ref(false)
/** 超 10 秒未见首帧的可取消提示（AI 通用交互规范 2.3） */
const slowHint = ref(false)

const previewTreeRef = ref<InstanceType<typeof AiPreviewTree>>()
const outputRef = ref<HTMLDivElement>()

let controller: AiStreamController | null = null
let slowTimer: ReturnType<typeof setTimeout> | null = null

function clearSlowTimer(): void {
  if (slowTimer) clearTimeout(slowTimer)
  slowTimer = null
  slowHint.value = false
}

function generate(): void {
  if (!config.inputOptional && !inputText.value.trim() && !selectedRequirements.value.length) {
    ElMessage.warning('请输入需求描述或选择需求条目')
    return
  }
  phase.value = 'streaming'
  streamText.value = ''
  previewNodes.value = []
  warnings.value = []
  slowTimer = setTimeout(() => {
    slowHint.value = true
  }, 10_000)

  controller = useAiStream({
    url: config.url,
    body: config.buildBody({
      docId: props.docId,
      targetNodeId: props.targetNodeId,
      text: inputText.value,
      modelId: aiStore.effectiveModelId() ?? null,
      requirementIds: selectedRequirements.value.map((r) => r.id),
    }),
    onEvent(event) {
      clearSlowTimer()
      if (event.event === 'delta') {
        streamText.value += ((event.data as { content?: string }).content ?? '')
        // 流式输出跟随滚动到底部
        requestAnimationFrame(() => {
          outputRef.value?.scrollTo({ top: outputRef.value.scrollHeight })
        })
      } else if (event.event === 'done') {
        const result = event.data as AiCaseGenerateResult
        warnings.value = result.warnings ?? []
        if (!result.nodes.length) {
          // 空结果属正常返回（无需补全/未解析出结构），回到可重试状态
          ElMessage.warning(config.emptyResultMessage)
          phase.value = 'idle'
          return
        }
        previewNodes.value = buildPreviewTree(result.nodes)
        phase.value = 'done'
      } else if (event.event === 'error') {
        const data = event.data as { message?: string }
        ElMessage.error(data.message ?? 'AI 调用失败')
        phase.value = 'idle'
      }
    },
    onError(error) {
      clearSlowTimer()
      ElMessage.error(error.message)
      phase.value = 'idle'
    },
    onClose() {
      clearSlowTimer()
      // done 帧未到达即关闭（服务端异常中断），回到可重试状态
      if (phase.value === 'streaming') phase.value = 'idle'
    },
  })
}

// 中途取消：已输出内容不保留，可重新生成（交互设计 2.2）
function stop(): void {
  controller?.cancel()
  controller = null
  clearSlowTimer()
  streamText.value = ''
  phase.value = 'idle'
}

function confirmMount(): void {
  const checked = previewTreeRef.value?.getCheckedKeySet() ?? new Set<string>()
  const nodes = filterCheckedTree(previewNodes.value, checked)
  if (!nodes.length) {
    ElMessage.warning('请至少勾选一个要挂载的节点')
    return
  }
  emit('mount', nodes)
}

function handleClose(): void {
  if (phase.value === 'streaming') stop()
  visible.value = false
}

// ==================== 需求条目区（US-AI-004） ====================

function handleRequirementConfirm(selected: RequirementSummary[]): void {
  selectedRequirements.value = selected
}

function removeRequirement(id: string): void {
  selectedRequirements.value = selectedRequirements.value.filter((r) => r.id !== id)
}

/** 打开时默认带入文档关联条目（交互设计 6.1），仅 generate/complete 消费 */
async function loadDocumentRequirements(): Promise<void> {
  try {
    selectedRequirements.value = await getDocumentRequirements(props.docId)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载文档关联需求失败')
  }
}

// 每次打开重新同步文档关联条目
watch(
  visible,
  (open) => {
    if (open) void loadDocumentRequirements()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  controller?.cancel()
  clearSlowTimer()
})
</script>

<template>
  <el-drawer
    v-model="visible"
    size="440px"
    :modal="true"
    :close-on-click-modal="true"
    modal-class="ai-drawer-modal"
    @close="handleClose"
  >
    <template #header>
      <span class="ai-panel-title"><el-icon><MagicStick /></el-icon> {{ config.title }}</span>
    </template>

    <div class="ai-panel">
      <div class="ai-panel-target">挂载目标：{{ targetPath }}</div>

      <!-- 需求条目区（US-AI-004）：选取需求池条目作为生成上下文 -->
      <div class="ai-panel-reqs">
        <div class="ai-panel-reqs__bar">
          <span class="ai-panel-reqs__label">需求条目</span>
          <el-button
            size="small"
            :disabled="phase === 'streaming'"
            @click="requirementSelectorVisible = true"
          >
            {{ selectedRequirements.length ? '调整条目' : '选择条目' }}
          </el-button>
        </div>
        <div v-if="selectedRequirements.length" class="ai-panel-reqs__tags">
          <el-tag
            v-for="item in selectedRequirements"
            :key="item.id"
            closable
            @close="removeRequirement(item.id)"
          >
            {{ item.title }}
          </el-tag>
        </div>
        <div v-else class="ai-panel-reqs__empty">未选择需求条目，将仅依据输入文本生成</div>
      </div>

      <RequirementSelector
        v-model="requirementSelectorVisible"
        :selected-ids="selectedRequirements.map((r) => r.id)"
        :draft-text="mode === 'generate' ? inputText : undefined"
        @confirm="handleRequirementConfirm"
      />

      <div class="ai-panel-input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="5"
          maxlength="20000"
          :disabled="phase === 'streaming'"
          :placeholder="config.inputPlaceholder"
        />
        <div class="ai-panel-actions">
          <AiModelSelect />
          <el-button
            v-if="phase !== 'streaming'"
            type="primary"
            size="small"
            @click="generate"
          >
            <el-icon><MagicStick /></el-icon>
            <span>{{ phase === 'done' ? '重新生成' : '生成' }}</span>
          </el-button>
          <el-button v-else size="small" @click="stop">停止</el-button>
        </div>
      </div>

      <el-alert
        v-if="slowHint"
        type="info"
        :closable="false"
        show-icon
        title="模型响应较慢，可点击「停止」后重试"
      />
      <el-alert
        v-for="(warning, index) in warnings"
        :key="index"
        type="warning"
        :closable="false"
        show-icon
        :title="warning"
      />

      <!-- 生成中：流式原始文本；完成后：结构化预览树（交互设计 2.2） -->
      <div v-if="phase === 'streaming'" ref="outputRef" class="ai-panel-output">
        <pre class="ai-panel-stream">{{ streamText || '正在生成…' }}</pre>
      </div>
      <div v-else-if="phase === 'done'" class="ai-panel-output">
        <AiPreviewTree ref="previewTreeRef" :nodes="previewNodes" />
      </div>

      <div v-if="phase === 'done'" class="ai-panel-footer">
        <el-button type="primary" @click="confirmMount">确认挂载</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
/* 透明遮罩：点击抽屉外空白处自动关闭，同时不压暗画布（交互设计 2.2） */
:deep(.ai-drawer-modal) {
  background: transparent;
}

.ai-panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.ai-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.ai-panel-target {
  padding: 8px 12px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-panel-reqs__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.ai-panel-reqs__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.ai-panel-reqs__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ai-panel-reqs__empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.ai-panel-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.ai-panel-output {
  flex: 1;
  min-height: 120px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
}

.ai-panel-stream {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--el-text-color-regular);
}

.ai-panel-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
