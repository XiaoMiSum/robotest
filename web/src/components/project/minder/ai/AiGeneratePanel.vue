<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import RequirementSelector from '@/components/project/RequirementSelector.vue'
import { getDocumentRequirements } from '@/services/project'
import { useAiStream, type AiStreamController } from '@/composables/useAiStream'
import { useAiStore } from '@/stores/ai'
import type { AiCaseGenerateResult, AiGeneratedNode, RequirementSummary } from '@/types'
import { buildDocumentPreviewTree, buildPreviewTree, type AiPreviewNode, type MountTargetSource } from './aiMount'
import { AI_PANEL_MODES, type AiPanelMode } from './aiPanelModes'
import AiPreviewDialog from './AiPreviewDialog.vue'

/**
 * AI 生成弹窗（US-AI-001/002，交互设计 2.1/2.2/3.1）：
 * 文本输入 → SSE 流式输出 → done 后组装完整文档树预览（既有节点只读 + 生成节点 AI 徽标可勾选）→ 确认挂载（由父组件执行）。
 * 预览为纯前端本地快照，不写编辑内核/不落库；只有确认挂载后才经既有通道批量插入（交互设计 2.2 纯预览约束）。
 * 两种模式差异集中在 aiPanelModes 配置表；需求条目区（US-AI-004）供 generate/complete 消费。
 */
const props = defineProps<{
  mode: AiPanelMode
  docId: string
  targetNodeId: string
  /** 挂载目标节点路径（根 > … > 目标），打开时由脑图组件计算 */
  targetPath: string
  /** 脑图活树读取器（供组装完整文档树预览快照，纯只读遍历） */
  getDocTree: () => MountTargetSource | null
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
const previewNodes = ref<AiPreviewNode[]>([])
const warnings = ref<string[]>([])
/** 目标节点在预览组装时已缺失：回退仅展示生成节点树（挂载确认时走重选流程） */
const targetMissing = ref(false)
/** 已选需求池条目（US-AI-004），随请求体透传；打开时默认带入文档关联条目 */
const selectedRequirements = ref<RequirementSummary[]>([])
const requirementSelectorVisible = ref(false)
/** 超 10 秒未见首帧的可取消提示（AI 通用交互规范 2.3） */
const slowHint = ref(false)

/** 独立预览弹窗显隐：done 后点击 [查看预览] 打开，关闭后生成弹窗保留「完成」态（交互设计 2.2） */
const previewDialogVisible = ref(false)

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
  previewNodes.value = []
  warnings.value = []
  targetMissing.value = false
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
      // 流式 delta 不再逐字上屏（见模板：以虚假进度条占位），仅消费 done/error 终帧
      if (event.event === 'done') {
        const result = event.data as AiCaseGenerateResult
        warnings.value = result.warnings ?? []
        if (!result.nodes.length) {
          // 空结果属正常返回（无需补全/未解析出结构），回到可重试状态
          ElMessage.warning(config.emptyResultMessage)
          phase.value = 'idle'
          return
        }
        previewNodes.value = buildPreview(result.nodes)
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

/**
 * 组装预览树（交互设计 2.2）：优先完整文档树快照（生成节点插入目标位）；
 * 目标节点已被协同删除时回退为仅生成节点树，挂载确认时由父组件走重选流程（4.2）。
 */
function buildPreview(generatedNodes: AiGeneratedNode[]): AiPreviewNode[] {
  const tree = buildDocumentPreviewTree(props.getDocTree(), props.targetNodeId, generatedNodes)
  if (tree) {
    targetMissing.value = false
    return tree
  }
  targetMissing.value = true
  ElMessage.warning('挂载目标已被删除，请重新选择挂载位置')
  return buildPreviewTree(generatedNodes)
}

// 中途取消：已输出内容不保留，可重新生成（交互设计 2.2）
function stop(): void {
  controller?.cancel()
  controller = null
  clearSlowTimer()
  phase.value = 'idle'
}

function openPreview(): void {
  previewDialogVisible.value = true
}

function handlePreviewConfirm(nodes: AiGeneratedNode[]): void {
  // 挂载执行由父组件完成；成功后父组件会一并关闭生成弹窗（连带本预览弹窗）
  emit('mount', nodes)
}

function handleClose(): void {
  if (phase.value === 'streaming') stop()
  previewDialogVisible.value = false
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
  <el-dialog
    v-model="visible"
    width="720px"
    :close-on-click-modal="true"
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

      <!-- 生成中/生成完毕：SSE 无真实进度信息，以虚假进度条占位（交互设计 2.2，不再逐字上屏） -->
      <div v-if="phase === 'streaming'" class="ai-panel-output ai-panel-output--progress">
        <el-progress :percentage="100" :indeterminate="true" :duration="2" :stroke-width="10" :show-text="false" />
        <div class="ai-panel-progress-tip">正在生成测试用例，请稍候…</div>
      </div>
      <div v-else-if="phase === 'done'" class="ai-panel-output ai-panel-output--progress">
        <el-alert
          v-if="targetMissing"
          type="warning"
          :closable="false"
          show-icon
          title="挂载目标已被删除，查看预览后可重新选择挂载位置"
        />
        <el-progress v-else :percentage="100" :stroke-width="10" status="success" :show-text="false" />
        <div v-if="!targetMissing" class="ai-panel-progress-tip">生成完成，点击下方「查看预览」在脑图中核对并勾选取舍</div>
      </div>

      <div v-if="phase === 'done'" class="ai-panel-footer">
        <el-button type="primary" @click="openPreview">
          <el-icon><View /></el-icon>
          <span>查看预览</span>
        </el-button>
      </div>
    </div>

  </el-dialog>

  <!-- 独立预览弹窗：脑图文档形式，本地快照不落库（交互设计 2.1/2.2）。
       必须与生成弹窗平级而非嵌套：嵌套时外层 dialog 更新期间经 v-if 动态挂载
       append-to-body 子 dialog 会触发 Vue teleport anchor 崩溃（nextSibling null），
       Element Plus 官方亦不推荐嵌套 Dialog；平级渲染后两窗并存、预览置顶 -->
  <AiPreviewDialog
    v-if="previewDialogVisible"
    v-model="previewDialogVisible"
    :nodes="previewNodes"
    :target-path="targetPath"
    :target-missing="targetMissing"
    @confirm="handlePreviewConfirm"
  />
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

// 生成中/生成完毕：进度条竖向居中占位（SSE 无真实进度，动画仅表意）
.ai-panel-output--progress {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  border-style: dashed;
  background: var(--el-fill-color-light);
}

.ai-panel-progress-tip {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.ai-panel-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
