<script setup lang="ts">
import { useDocumentRequirements } from '@/composables/project/functional-testing/minder/ai/useDocumentRequirements'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import RequirementSelector from '@/components/project/functional-testing/requirement/RequirementSelector.vue'
import { type AiStreamController, useAiStream } from '@/composables/ai/useAiStream'
import { useDrawerResize } from '@/composables/ai/useDrawerResize'
import { useStageTimer } from '@/composables/ai/useStageTimer'
import { useAiStore } from '@/stores/ai'
import type { AiCaseGenerateResult, AiGeneratedNode, RequirementSummary } from '@/types'
import {
  type AiPreviewNode,
  buildPreviewTree,
  findNodeById,
  type MountTargetSource,
} from '@/minder/ai/aiMount'
import { AI_PANEL_MODES, type AiPanelMode } from '@/minder/ai/aiPanelModes'
import {
  buildSummaryCards,
  formatReqTagLabel,
  reqPoolLabel,
  sliceReqTags,
} from './aiPanelPresentation'
import AiPreviewDialog from './AiPreviewDialog.vue'

/**
 * AI 生成抽屉（US-AI-001/002，交互设计 45 §2.9 / 49 / 50）：
 * 文本输入 → SSE 流式 → done 后组装生成节点树预览（仅用例节点可勾选，内部结构随用例级联）→ 确认挂载（由父组件执行）。
 * 预览为纯前端本地快照，不写编辑内核/不落库；只有确认挂载后才经既有通道批量插入（交互设计 2.2 纯预览约束）。
 * 两种模式差异集中在 aiPanelModes 配置表；需求条目区（US-AI-004）供 generate/complete 消费。
 * 会话随组件常驻：关闭抽屉仅隐藏，不中断 SSE、不丢结果；切换文档（resetToken）才全量重置。
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
  /** 会话重置信号：父组件自增时本组件全量重置（complete 模式目标节点变化 / docId 变化时置位） */
  resetToken: number
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
const {
  selectedRequirements,
  loadDocumentRequirements,
} = useDocumentRequirements(() => props.docId)

const requirementSelectorVisible = ref(false)
/** 超 10 秒未见首帧的可取消提示（AI 通用交互规范 2.3） */
const slowHint = ref(false)
/** 必填校验内联红字：呈现于需求输入下方，不使用全局 toast（交互设计 45 §2.9） */
const inputError = ref(false)
/** 本会话已发起过生成：重开抽屉时展示「已恢复上次会话」（交互设计 49 §1.2） */
const hasSession = ref(false)
const resumeVisible = ref(false)
/** 文档关联条目仅在会话首开带入一次，会话内改选不被后续打开覆盖（交互设计 52 §1.3） */
const requirementsSeeded = ref(false)

/** 独立预览弹窗显隐：done 后点击 [查看预览] 打开，关闭后生成抽屉保留「完成」态（交互设计 2.2） */
const previewDialogVisible = ref(false)

const drawerRef = ref<HTMLElement>()
const inputRef = ref<InstanceType<typeof import('element-plus')['ElInput']>>()
const reqAddBtnRef = ref<InstanceType<typeof import('element-plus')['ElButton']>>()

/** 关闭后焦点归还的触发按钮（交互设计 45 §2.9 焦点管理） */
let triggerEl: HTMLElement | null = null

const stage = useStageTimer()
const { onResizeStart } = useDrawerResize()

const tagSlice = computed(() => sliceReqTags(selectedRequirements.value))
const reqBarLabel = computed(() => reqPoolLabel(selectedRequirements.value.length))
const summaryCards = computed(() => buildSummaryCards(previewNodes.value))
const stageText = computed(() =>
  stage.active.value ? stage.label(config.inProgressLabel) : '',
)

let controller: AiStreamController | null = null
let slowTimer: ReturnType<typeof setTimeout> | null = null

function clearSlowTimer(): void {
  if (slowTimer) clearTimeout(slowTimer)
  slowTimer = null
  slowHint.value = false
}

/** 必填校验：generate 模式文本与需求条目皆空才拦（交互设计 49 §1.2），失败不切阶段 */
function validate(): boolean {
  if (config.inputOptional) return true
  inputError.value = inputText.value.trim() === '' && selectedRequirements.value.length === 0
  return !inputError.value
}

function generate(): void {
  if (!validate()) return
  phase.value = 'streaming'
  previewNodes.value = []
  warnings.value = []
  targetMissing.value = false
  hasSession.value = true
  resumeVisible.value = false
  stage.start()
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
          stage.stop()
          phase.value = 'idle'
          return
        }
        previewNodes.value = buildPreview(result.nodes)
        stage.stop()
        phase.value = 'done'
      } else if (event.event === 'error') {
        const data = event.data as { message?: string }
        ElMessage.error(data.message ?? 'AI 调用失败')
        stage.stop()
        phase.value = 'idle'
      }
    },
    onError(error) {
      clearSlowTimer()
      ElMessage.error(error.message)
      stage.stop()
      phase.value = 'idle'
    },
    onClose() {
      clearSlowTimer()
      // done 帧未到达即关闭（服务端异常中断），回到可重试状态
      if (phase.value === 'streaming') {
        stage.stop()
        phase.value = 'idle'
      }
    },
  })
}

/**
 * 组装预览树（交互设计 2.2）：仅展示本次生成节点树（文档既有数据不再并入预览）；
 * 目标存在性校验保留在预览组装期（findNodeById），缺失时提示重选挂载位置（4.2）。
 */
function buildPreview(generatedNodes: AiGeneratedNode[]): AiPreviewNode[] {
  targetMissing.value = findNodeById(props.getDocTree(), props.targetNodeId) === null
  if (targetMissing.value) {
    ElMessage.warning('挂载目标已被删除，请重新选择挂载位置')
  }
  // 补全模式：全部生成节点默认勾选、可逐项取舍（buildPreviewTree 的 selectAll，交互设计 3.1）
  return buildPreviewTree(generatedNodes, 'ai', false, props.mode === 'complete')
}

// 中途取消：已输出内容不保留，可重新生成（交互设计 2.2）
function stop(): void {
  controller?.cancel()
  controller = null
  clearSlowTimer()
  stage.stop()
  phase.value = 'idle'
}

function openPreview(): void {
  previewDialogVisible.value = true
}

function handlePreviewConfirm(nodes: AiGeneratedNode[]): void {
  // 挂载执行由父组件完成；成功后父组件会一并关闭生成抽屉（连带本预览弹窗）
  emit('mount', nodes)
}

function rememberTrigger(): void {
  const active = document.activeElement
  if (
    active instanceof HTMLElement &&
    active !== document.body &&
    !drawerRef.value?.contains(active)
  ) {
    triggerEl = active
  }
}

function handleClose(): void {
  // 仅关闭抽屉与预览弹窗；不中断进行中的 SSE，会话结果随组件常驻保留（交互设计 2.2 会话保持）
  if (phase.value === 'streaming') ElMessage.info(config.runningCloseHint)
  previewDialogVisible.value = false
  visible.value = false
  // 关闭后焦点归还触发按钮，避免落到 body（交互设计 45 §2.9）
  if (triggerEl && document.contains(triggerEl)) triggerEl.focus()
}

/** 全量重置会话（切换文档 / complete 目标节点变化 / [新会话] 时调用，交互设计 49 §1.2） */
function resetSession(): void {
  stop()
  inputText.value = props.initialText ?? ''
  previewNodes.value = []
  warnings.value = []
  targetMissing.value = false
  selectedRequirements.value = []
  previewDialogVisible.value = false
  inputError.value = false
  hasSession.value = false
  resumeVisible.value = false
  requirementsSeeded.value = false
}

/** [新会话]：中断流式并清空输入、需求与结果，回到发起态（交互设计 49 §1.2） */
function startNewSession(): void {
  stop()
  inputText.value = ''
  selectedRequirements.value = []
  previewNodes.value = []
  warnings.value = []
  inputError.value = false
  hasSession.value = false
  resumeVisible.value = false
  ElMessage.success('已开启新会话')
}

// ==================== 需求条目区（US-AI-004） ====================

function handleRequirementConfirm(selected: RequirementSummary[]): void {
  selectedRequirements.value = selected
  inputError.value = false
}

function removeRequirement(id: string): void {
  selectedRequirements.value = selectedRequirements.value.filter((r) => r.id !== id)
  inputError.value = false
}

function clearRequirements(): void {
  selectedRequirements.value = []
  validate()
}

// ==================== 快捷键与焦点（交互设计 45 §2.9） ====================

/** Ctrl/Cmd + Enter 触发当前主操作；流式中输入禁用天然收不到按键 */
function handleHotkey(): void {
  if (phase.value === 'streaming') return
  generate()
}

function handleGlobalKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || !visible.value) return
  // 由内向外逐层关闭：选取器/预览先于抽屉；选取器自身处理 Esc，此处不越权
  if (requirementSelectorVisible.value) return
  if (previewDialogVisible.value) {
    event.stopPropagation()
    previewDialogVisible.value = false
    return
  }
  event.stopPropagation()
  handleClose()
}

watch(visible, (open) => {
  if (open) {
    rememberTrigger()
    resumeVisible.value = hasSession.value
    if (!requirementsSeeded.value) {
      requirementsSeeded.value = true
      void loadDocumentRequirements()
    }
    // 打开聚焦首个输入控件；流式中禁用时不抢焦点（交互设计 45 §2.9）
    if (phase.value !== 'streaming') {
      void nextTick(() => inputRef.value?.focus())
    }
    window.addEventListener('keydown', handleGlobalKeydown, true)
  } else {
    window.removeEventListener('keydown', handleGlobalKeydown, true)
  }
})

// 选取器关闭后回焦 [选择需求]
watch(requirementSelectorVisible, (open) => {
  if (!open) void nextTick(() => reqAddBtnRef.value?.$el?.focus?.())
})

// 输入即清除内联校验提示
watch(inputText, () => {
  if (inputError.value && inputText.value.trim()) inputError.value = false
})

// 切换文档：断开 SSE 并重置会话（会话绑定文档生命周期，交互设计 2.2）
watch(
  () => props.docId,
  () => resetSession(),
)

// 会话重置信号：complete 模式目标节点变化 / 重新发起时父组件自增触发
watch(
  () => props.resetToken,
  () => resetSession(),
)

onBeforeUnmount(() => {
  controller?.cancel()
  clearSlowTimer()
  stage.stop()
  window.removeEventListener('keydown', handleGlobalKeydown, true)
})
</script>

<template>
  <!-- 透明遮罩：不压暗画布，点击抽屉外空白处关闭，打开期间页面被拦截不可交互（45 §2.9）；
       z-index 低于 Element Plus 弹层基线（2001），确保预览弹窗/选取器/消息恒在抽屉之上 -->
  <transition name="ai-panel-fade">
    <div v-show="visible" class="ai-panel-mask" @click="handleClose" />
  </transition>

  <!-- 非阻断侧滑面板：自绘 fixed 容器而非 el-drawer——modal=false 时其 overlay 仍渲染全屏包装层拦截点击，
       且 focus-trap 会把外部键盘焦点强行拉回抽屉（均无 prop 可关）；会话随组件常驻保持 -->
  <transition name="ai-panel-slide">
    <aside
      v-show="visible"
      ref="drawerRef"
      class="ai-panel-drawer"
      role="dialog"
      :aria-label="config.title"
    >
      <div
        class="ai-panel-drawer__resize"
        role="separator"
        aria-label="拖拽调整抽屉宽度"
        title="拖拽调整宽度"
        @pointerdown="onResizeStart"
      />
      <header class="ai-panel-drawer__header">
        <span class="ai-panel-title">
          <el-icon><MagicStick /></el-icon>
          {{ config.title }}
          <span v-if="phase === 'streaming'" class="ai-panel-busy">
            <span class="ai-panel-busy__spin" aria-hidden="true" />{{ config.inProgressLabel }}
          </span>
        </span>
        <el-button link aria-label="关闭面板" @click="handleClose"><el-icon><Close /></el-icon></el-button>
      </header>

      <div class="ai-panel-drawer__body">
        <div v-if="resumeVisible" class="ai-panel-resume">
          <el-icon><InfoFilled /></el-icon>
          <span>已恢复上次会话</span>
          <el-button link size="small" @click="startNewSession">
            <el-icon><Refresh /></el-icon>新会话
          </el-button>
        </div>

        <div class="ai-field">
          <span class="ai-field__label">挂载目标</span>
          <el-tooltip :content="targetPath" placement="bottom">
            <span class="ai-path">{{ targetPath }}</span>
          </el-tooltip>
        </div>

        <div class="ai-field">
          <div class="ai-field__head">
            <span class="ai-field__label">{{ reqBarLabel }}</span>
            <span class="ai-field__ops">
              <el-button v-if="selectedRequirements.length" link size="small" @click="clearRequirements">
                清空
              </el-button>
              <el-button
                ref="reqAddBtnRef"
                size="small"
                :disabled="phase === 'streaming'"
                @click="requirementSelectorVisible = true"
              >
                <el-icon><Plus /></el-icon>选择需求
              </el-button>
            </span>
          </div>
          <div v-if="selectedRequirements.length" class="ai-req-tags">
            <el-tag
              v-for="item in tagSlice.visible"
              :key="item.id"
              size="small"
              closable
              :title="item.title"
              @close="removeRequirement(item.id)"
            >
              {{ formatReqTagLabel(item.title) }}
            </el-tag>
            <el-tag
              v-if="tagSlice.overflow"
              size="small"
              class="ai-req-more"
              :title="`查看全部 ${selectedRequirements.length} 条已选需求`"
              @click="requirementSelectorVisible = true"
            >
              +{{ tagSlice.overflow }}
            </el-tag>
          </div>
          <span v-else class="ai-field__hint">未选择需求条目，将仅依据输入文本生成</span>
        </div>

        <div class="ai-field">
          <label class="ai-field__label" for="aiGenInput">需求输入</label>
          <el-input
            id="aiGenInput"
            ref="inputRef"
            v-model="inputText"
            type="textarea"
            :rows="5"
            maxlength="20000"
            show-word-limit
            :disabled="phase === 'streaming'"
            :placeholder="config.inputPlaceholder"
            @keydown.enter.ctrl.prevent="handleHotkey"
            @keydown.enter.meta.prevent="handleHotkey"
          />
          <span v-if="inputError" class="ai-inline-err">请输入需求描述或选择需求条目</span>
        </div>

        <!-- 操作行：位于输入区正下方、右对齐随内容滚动（45 §2.9）；
             调用中左侧以 4px 纤细虚假进度条 + 阶段文案与已耗时占位 -->
        <div class="ai-actions">
          <div v-if="phase === 'streaming'" class="ai-actions__progress">
            <el-progress
              :percentage="100"
              :indeterminate="true"
              :duration="2"
              :stroke-width="4"
              :show-text="false"
            />
            <span class="ai-actions__stage">{{ stageText }}</span>
          </div>
          <AiModelSelect />
          <el-button v-if="phase === 'idle'" size="small" type="primary" @click="generate">
            <el-icon><MagicStick /></el-icon>
            <span>{{ config.startButtonText }}</span>
          </el-button>
          <el-button v-else-if="phase === 'streaming'" size="small" @click="stop">停止</el-button>
          <template v-else>
            <el-button size="small" @click="generate">
              <el-icon><MagicStick /></el-icon>
              <span>{{ config.retryButtonText }}</span>
            </el-button>
            <el-button size="small" type="primary" @click="openPreview">
              <el-icon><View /></el-icon>
              <span>查看预览</span>
            </el-button>
          </template>
        </div>

        <el-alert
          v-if="slowHint"
          type="info"
          :closable="false"
          show-icon
          title="模型响应较慢，可点击「停止」后重试"
        />

        <!-- 流式态不渲染输出区（无提示文字，进度条仅存于操作行）；完成态输出区见 49 §1.2 -->
        <template v-if="phase === 'done'">
          <el-alert
            v-for="(warning, index) in warnings"
            :key="index"
            type="warning"
            :closable="false"
            show-icon
            :title="warning"
          />
          <el-alert
            v-if="targetMissing"
            type="warning"
            :closable="false"
            show-icon
            title="挂载目标已被删除，查看预览后可重新选择挂载位置"
          />
          <el-alert
            type="success"
            :closable="false"
            show-icon
            :title="config.doneTipMessage"
          />
          <!-- 完成态结果卡片：标题 + 优先级标签 + 步骤摘要，只读无勾选框；勾选取舍在预览弹窗完成 -->
          <div v-if="summaryCards.length" class="ai-cards">
            <div v-for="(card, index) in summaryCards" :key="index" class="ai-card">
              <span class="ai-card__head">
                <span class="ai-card__title">{{ card.title }}</span>
                <el-tag v-if="card.priority" size="small" effect="plain">{{ card.priority }}</el-tag>
                <span class="ai-flag">AI</span>
              </span>
              <span v-if="card.steps" class="ai-card__steps">{{ card.steps }}</span>
            </div>
          </div>
        </template>
      </div>
    </aside>
  </transition>

  <RequirementSelector
    v-model="requirementSelectorVisible"
    :selected-ids="selectedRequirements.map((r) => r.id)"
    @confirm="handleRequirementConfirm"
  />

  <!-- 独立预览弹窗：脑图文档形式，本地快照不落库（交互设计 2.1/2.2）。
       必须与生成抽屉平级而非嵌套：嵌套时外层 dialog 更新期间经 v-if 动态挂载
       append-to-body 子 dialog 会触发 Vue teleport anchor 崩溃（nextSibling null），
       Element Plus 官方亦不推荐嵌套 Dialog；平级渲染后两窗并存、预览置顶 -->
  <AiPreviewDialog
    v-if="previewDialogVisible"
    v-model="previewDialogVisible"
    :nodes="previewNodes"
    :target-path="targetPath"
    :target-missing="targetMissing"
    :confirm-button-text="config.confirmButtonText"
    :title="config.previewTitle"
    :count-label="config.countLabel"
    @confirm="handlePreviewConfirm"
  />
</template>

<style scoped lang="scss">
/* 遮罩透明：画布可见但页面被拦截不可交互（交互设计 45 §2.9）；
   z-index 低于 Element Plus 弹层基线 2001，预览弹窗与选取器恒在其上 */
.ai-panel-mask {
  position: fixed;
  inset: 0;
  z-index: 1900;
  background: transparent;
}

/* 非阻断侧滑面板：fixed 悬浮于画布之上，无压暗效果 */
.ai-panel-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 1910;
  display: flex;
  flex-direction: column;
  width: var(--ai-dw, 640px);
  max-width: 90vw;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
}

/* 抽屉左缘拖拽调宽把手（交互设计 45 §2.9） */
.ai-panel-drawer__resize {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  z-index: 1;
}

.ai-panel-drawer__resize:hover {
  background: var(--color-primary-200);
}

.ai-panel-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 13px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.ai-panel-drawer__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 20px 22px;
}

.ai-panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

/* 进行中徽标：标题旁转圈 + 阶段文案（交互设计 45 §2.9） */
.ai-panel-busy {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: var(--color-primary-600);
}

.ai-panel-busy__spin {
  width: 12px;
  height: 12px;
  border: 2px solid var(--color-primary-200);
  border-top-color: var(--color-primary-500);
  border-radius: 50%;
  animation: ai-panel-spin 0.7s linear infinite;
}

@keyframes ai-panel-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 会话恢复标识：重开已有会话时置顶提示，[新会话] 一键回发起态（49 §1.2） */
.ai-panel-resume {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);

  .el-button {
    margin-left: auto;
  }
}

.ai-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.ai-field__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ai-field__label {
  font-size: var(--font-size-xs);
  color: var(--el-text-color-secondary);
}

.ai-field__ops {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ai-field__hint {
  font-size: var(--font-size-xs);
  color: var(--el-text-color-placeholder);
}

/* 挂载目标路径：超长省略，悬浮提示完整路径（49 §1.2） */
.ai-path {
  align-self: flex-start;
  max-width: 100%;
  padding: 4px 9px;
  font-size: var(--font-size-sm);
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-req-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 「+N」溢出指示：点击打开选取器管理余量（49 §1.2） */
.ai-req-more {
  cursor: pointer;
}

/* 必填校验内联红字：呈现于输入区下方，不使用全局 toast（45 §2.9） */
.ai-inline-err {
  font-size: var(--font-size-xs);
  color: var(--el-color-danger);
}

/* 操作行：右对齐、随内容滚动，进度与阶段反馈居左占满余量 */
.ai-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.ai-actions__progress {
  flex: 1;
  min-width: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-actions__stage {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

/* 完成态结果卡片：标题 + 优先级标签 + 步骤摘要，只读无勾选框（49 2026-09-30） */
.ai-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-lg);
  background: var(--el-bg-color);
}

.ai-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-card__title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ai-card__steps {
  font-size: var(--font-size-xs);
  color: var(--el-text-color-regular);
  line-height: 1.6;
  word-break: break-word;
}

.ai-flag {
  display: inline-flex;
  align-items: center;
  height: 16px;
  padding: 0 5px;
  border: 1px solid var(--color-ai-badge);
  border-radius: 999px;
  font-size: var(--font-size-2xs, 10px);
  font-weight: 700;
  line-height: 1;
  color: var(--color-ai-badge);
}

/* 右侧滑入/滑出，观感与 el-drawer 一致；遮罩淡入淡出 */
.ai-panel-slide-enter-active,
.ai-panel-slide-leave-active {
  transition: transform 0.3s ease;
}

.ai-panel-slide-enter-from,
.ai-panel-slide-leave-to {
  transform: translateX(100%);
}

.ai-panel-fade-enter-active,
.ai-panel-fade-leave-active {
  transition: opacity 0.2s ease;
}

.ai-panel-fade-enter-from,
.ai-panel-fade-leave-to {
  opacity: 0;
}
</style>
