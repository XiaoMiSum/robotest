<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAiStream, type AiStreamController } from '@/composables/useAiStream'
import { useAiStore } from '@/stores/ai'
import type { AiCaseGenerateResult, AiGeneratedNode } from '@/types'
import { buildPreviewTree, filterCheckedTree, type AiPreviewNode } from './aiMount'
import AiPreviewTree from './AiPreviewTree.vue'

/**
 * 「AI 生成用例」抽屉（US-AI-001，交互设计 2.1/2.2）：
 * 需求文本 → SSE 流式输出 → done 后切结构化预览树（勾选取舍）→ 确认挂载（由父组件执行）。
 * 梯队一仅支持手动输入需求文本，需求池选取随 US-AI-004 交付后补充入口。
 */
const props = defineProps<{
  docId: string
  targetNodeId: string
  /** 挂载目标节点路径（根 > … > 目标），打开时由脑图组件计算 */
  targetPath: string
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  /** 用户确认挂载：经勾选过滤后的节点树，由脑图组件执行挂载 */
  mount: [nodes: AiGeneratedNode[]]
}>()

const aiStore = useAiStore()

type Phase = 'idle' | 'streaming' | 'done'
const phase = ref<Phase>('idle')
const requirementText = ref('')
const streamText = ref('')
const previewNodes = ref<AiPreviewNode[]>([])
const warnings = ref<string[]>([])
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
  if (!requirementText.value.trim()) {
    ElMessage.warning('请输入需求描述')
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
    url: '/project/ai/cases/generate',
    body: {
      documentId: props.docId,
      targetNodeId: props.targetNodeId,
      requirementText: requirementText.value,
      modelId: aiStore.effectiveModelId() ?? null,
    },
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
        previewNodes.value = buildPreviewTree(result.nodes)
        warnings.value = result.warnings ?? []
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

onBeforeUnmount(() => {
  controller?.cancel()
  clearSlowTimer()
})
</script>

<template>
  <el-drawer
    v-model="visible"
    size="440px"
    :close-on-click-modal="false"
    :modal="false"
    @close="handleClose"
  >
    <template #header>
      <span class="ai-panel-title"><el-icon><MagicStick /></el-icon> AI 生成用例</span>
    </template>

    <div class="ai-panel">
      <div class="ai-panel-target">挂载目标：{{ targetPath }}</div>

      <div class="ai-panel-input">
        <el-input
          v-model="requirementText"
          type="textarea"
          :rows="5"
          maxlength="20000"
          :disabled="phase === 'streaming'"
          placeholder="输入需求描述或模块说明，AI 将生成包含前置条件、执行步骤、预期结果的用例子树"
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
