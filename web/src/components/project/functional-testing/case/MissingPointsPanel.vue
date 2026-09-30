<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import RequirementSelector from '@/components/project/functional-testing/requirement/RequirementSelector.vue'
import { useMissingPointsPanel } from '@/composables/project/functional-testing/case/useMissingPointsPanel'
import { useDrawerResize } from '@/composables/ai/useDrawerResize'
import {
  formatReqTagLabel,
  reqPoolLabel,
  sliceReqTags,
} from '@/components/project/functional-testing/minder/ai/aiPanelPresentation'
import { MagicStick, Close, InfoFilled, Plus } from '@element-plus/icons-vue'

/**
 * 遗漏分析抽屉（US-AI-007，交互设计 56 §1.1/§1.2）：
 * 两组输入（需求文本 / 需求池）→ 同步长调用 → 结果清单逐条勾选 → 选目标文档转用例生成。
 * 会话随组件常驻：关闭仅隐藏，不中断请求、不丢结果；切换文档才重置。
 * 透明遮罩 + 自绘 fixed 容器，理由同 AI 生成抽屉（el-drawer 无法关闭 overlay 与 focus-trap）。
 */
const props = defineProps<{ docId: string }>()
const visible = defineModel<boolean>({ required: true })

const {
  text,
  requirementIds,
  requirementTitles,
  reqSelectorVisible,
  stage,
  resumeVisible,
  inputsCollapsed,
  analyzing,
  result,
  checkedIndexes,
  hasAnyInput,
  allChecked,
  toggleAll,
  toggleItem,
  handleRequirementConfirm,
  removeRequirement,
  clearRequirements,
  toggleInputs,
  analyze,
  cancelAnalyze,
  documentOptions,
  docSelectVisible,
  targetDocId,
  openTargetSelect,
  toCaseGenerate,
} = useMissingPointsPanel(() => props.docId, visible)

const { onResizeStart } = useDrawerResize()

const drawerRef = ref<HTMLElement>()
const textRef = ref<InstanceType<typeof import('element-plus')['ElInput']>>()
const reqAddBtnRef = ref<InstanceType<typeof import('element-plus')['ElButton']>>()

/** 关闭后焦点归还的触发按钮（交互设计 56 §1.2 焦点管理） */
let triggerEl: HTMLElement | null = null

const stageText = computed(() => (stage.active.value ? stage.label('分析中') : ''))
const tagSlice = computed(() => sliceReqTags(requirementTitles.value))
const reqBarLabel = computed(() => reqPoolLabel(requirementTitles.value.length))
// 折叠开关须在「已有结果」或「当前已折叠」时可见：否则重新分析期间结果清空会让折叠态失去出口
const inputsToggleVisible = computed(() => inputsCollapsed.value || result.value !== null)
// 完成态主按钮降为链接样式 [重新分析]，其余（含分析中 loading）为 [开始分析]（56 §1.1）
const startIsLink = computed(() => !analyzing.value && result.value !== null)
const startButtonText = computed(() => (startIsLink.value ? '重新分析' : '开始分析'))
const startDisabled = computed(() => analyzing.value || !hasAnyInput.value)

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

function focusText(): void {
  textRef.value?.focus()
}

function handleClose(): void {
  // 分析中关闭不中断请求，会话常驻（交互设计 56 §1.2）
  if (analyzing.value) ElMessage.info('分析在后台继续，重新打开可查看结果')
  visible.value = false
  // 关闭后焦点归还触发按钮，避免落到 body（交互设计 56 §1.2）
  if (triggerEl && document.contains(triggerEl)) triggerEl.focus()
}

// Ctrl/Cmd + Enter 触发当前主操作；分析中主按钮禁用收不到按键
function handleHotkey(): void {
  if (analyzing.value) return
  void analyze()
}

function handleGlobalKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || !visible.value) return
  // 由内向外逐层关闭：需求选取器/目标文档弹窗先于抽屉；选取器自身处理 Esc，此处不越权
  if (reqSelectorVisible.value) return
  if (docSelectVisible.value) {
    event.stopPropagation()
    docSelectVisible.value = false
    return
  }
  event.stopPropagation()
  handleClose()
}

watch(visible, (open) => {
  if (open) {
    rememberTrigger()
    // 打开时焦点落在需求文本；分析中输入禁用时不抢焦点（交互设计 56 §1.2）
    if (!analyzing.value) void nextTick(focusText)
    window.addEventListener('keydown', handleGlobalKeydown, true)
  } else {
    window.removeEventListener('keydown', handleGlobalKeydown, true)
  }
})

// 选取器关闭后回焦 [选择需求]
watch(reqSelectorVisible, (open) => {
  if (!open) void nextTick(() => reqAddBtnRef.value?.$el?.focus?.())
})

onBeforeUnmount(() => window.removeEventListener('keydown', handleGlobalKeydown, true))
</script>

<template>
  <!-- 透明遮罩：不压暗画布，点击抽屉外空白处关闭；z-index 低于 Element Plus 弹层基线（2001），
       确保需求选取器与目标文档弹窗恒在抽屉之上（交互设计 56 §1.1 统一规格 2.9） -->
  <transition name="mp-fade">
    <div v-show="visible" class="mp-mask" @click="handleClose" />
  </transition>

  <transition name="mp-slide">
    <aside
      v-show="visible"
      ref="drawerRef"
      class="mp-drawer"
      role="dialog"
      aria-label="遗漏分析"
    >
      <div
        class="mp-drawer__resize"
        role="separator"
        aria-label="拖拽调整抽屉宽度"
        title="拖拽调整宽度"
        @pointerdown="onResizeStart"
      />

      <header class="mp-drawer__header">
        <span class="mp-title">
          <el-icon><MagicStick /></el-icon>遗漏分析
          <span v-if="analyzing" class="mp-busy">
            <span class="mp-busy__spin" aria-hidden="true" />分析中
          </span>
        </span>
        <el-button link aria-label="关闭面板" @click="handleClose">
          <el-icon><Close /></el-icon>
        </el-button>
      </header>

      <div class="mp-drawer__body" @keydown.enter.ctrl.prevent="handleHotkey" @keydown.enter.meta.prevent="handleHotkey">
        <div v-if="resumeVisible" class="mp-resume">
          <el-icon><InfoFilled /></el-icon>
          <span>已恢复上次会话，重新发起请点击下方「重新分析」</span>
        </div>

        <el-button
          v-if="inputsToggleVisible"
          link
          size="small"
          class="mp-inputs-toggle"
          @click="toggleInputs"
        >
          {{ inputsCollapsed ? '展开输入 ▾' : '收起输入 ▴' }}
        </el-button>

        <div v-show="!inputsCollapsed" class="mp-inputs">
          <div class="mp-field">
            <label class="mp-field__label" for="mpText">需求文本</label>
            <el-input
              id="mpText"
              ref="textRef"
              v-model="text"
              type="textarea"
              :rows="5"
              maxlength="20000"
              show-word-limit
              :disabled="analyzing"
              placeholder="粘贴需求描述文本（与需求池至少填一项）"
            />
          </div>

          <div class="mp-field">
            <div class="mp-field__bar">
              <span class="mp-field__label">{{ reqBarLabel }}</span>
              <span class="mp-field__ops">
                <el-button
                  v-if="requirementIds.length"
                  link
                  size="small"
                  :disabled="analyzing"
                  @click="clearRequirements"
                >
                  清空
                </el-button>
                <el-button
                  ref="reqAddBtnRef"
                  size="small"
                  :disabled="analyzing"
                  @click="reqSelectorVisible = true"
                >
                  <el-icon><Plus /></el-icon>选择需求
                </el-button>
              </span>
            </div>
            <div v-if="tagSlice.visible.length" class="mp-req-tags">
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
                class="mp-req-more"
                :title="`查看全部 ${requirementTitles.length} 条已选需求`"
                @click="reqSelectorVisible = true"
              >
                +{{ tagSlice.overflow }}
              </el-tag>
            </div>
            <span v-else class="mp-field__hint">未选择需求条目，将仅依据输入文本分析</span>
          </div>
        </div>

        <!-- 操作行：位于输入区正下方、结果列表上方（56 §1.1）；
             调用中左侧 4px 纤细虚假进度条 + 阶段文案与已耗时，右侧模型与主操作 -->
        <div class="mp-actions">
          <div v-if="analyzing" class="mp-actions__progress">
            <el-progress
              :percentage="100"
              :indeterminate="true"
              :duration="2"
              :stroke-width="4"
              :show-text="false"
            />
            <span class="mp-actions__stage">{{ stageText }}</span>
          </div>
          <AiModelSelect />
          <el-button v-if="analyzing" size="small" @click="cancelAnalyze">取消</el-button>
          <el-button
            size="small"
            :type="startIsLink ? undefined : 'primary'"
            :link="startIsLink"
            :loading="analyzing"
            :disabled="startDisabled"
            @click="analyze"
          >
            {{ startButtonText }}
          </el-button>
          <el-button
            v-if="!analyzing && result && result.points.length"
            size="small"
            type="primary"
            :disabled="!checkedIndexes.size"
            @click="openTargetSelect"
          >
            转用例生成（{{ checkedIndexes.size }}）
          </el-button>
        </div>

        <!-- 结果区独占余量、仅列表内部滚动，抽屉整体不滚动（56 §1.1） -->
        <template v-if="result">
          <div class="mp-result">
            <div class="mp-result-head">
              <el-checkbox
                :model-value="allChecked"
                :indeterminate="checkedIndexes.size > 0 && !allChecked"
                @update:model-value="(v: unknown) => toggleAll(v === true)"
              >
                全选
              </el-checkbox>
              <span class="mp-result-count">
                共 {{ result.points.length }} 条，已选 {{ checkedIndexes.size }} 条
              </span>
            </div>

            <div v-if="result.points.length" class="mp-list">
              <div v-for="(point, index) in result.points" :key="index" class="mp-item">
                <el-checkbox
                  :model-value="checkedIndexes.has(index)"
                  @update:model-value="(v: unknown) => toggleItem(index, v === true)"
                />
                <div class="mp-item__body">
                  <div class="mp-item__title">{{ point.title }}</div>
                  <div class="mp-item__desc">{{ point.description }}</div>
                  <div v-if="point.suggestedModulePath" class="mp-item__tags">
                    <el-tag size="small" effect="plain" type="info">
                      建议模块：{{ point.suggestedModulePath }}
                    </el-tag>
                  </div>
                  <div v-if="point.relatedCaseTitles.length" class="mp-item__related">
                    <span class="mp-item__related-label">相关用例：</span>
                    <el-tag
                      v-for="title in point.relatedCaseTitles"
                      :key="title"
                      size="small"
                      effect="plain"
                      class="mp-item__related-tag"
                    >
                      {{ title }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else description="未发现遗漏测试点" :image-size="72" />
          </div>
        </template>
      </div>
    </aside>
  </transition>

  <RequirementSelector
    v-model="reqSelectorVisible"
    :selected-ids="requirementIds"
    @confirm="handleRequirementConfirm"
  />

  <!-- 转用例生成的目标文档选择（详细设计 3.3）：默认预选勾选点中出现最多的建议模块对应文档 -->
  <el-dialog v-model="docSelectVisible" title="选择目标文档" width="440px" append-to-body>
    <div class="mp-doc-tip">默认已预选出现次数最多的建议模块，可更换</div>
    <el-select v-model="targetDocId" filterable placeholder="搜索文档路径" class="mp-doc-select">
      <el-option v-for="doc in documentOptions" :key="doc.id" :label="doc.path" :value="doc.id" />
    </el-select>
    <template #footer>
      <el-button @click="docSelectVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!targetDocId" @click="toCaseGenerate">生成用例</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
/* 遮罩透明：画布可见但页面被拦截不可交互；
   z-index 低于 Element Plus 弹层基线 2001，选取器与目标文档弹窗恒在其上（56 §1.1） */
.mp-mask {
  position: fixed;
  inset: 0;
  z-index: 1900;
  background: transparent;
}

/* 侧滑抽屉：fixed 悬浮于画布之上，宽度与 AI 生成抽屉共享记忆（左缘拖拽 480px–90vw） */
.mp-drawer {
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

.mp-drawer__resize {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 6px;
  z-index: 1;
  cursor: col-resize;
}

.mp-drawer__resize:hover {
  background: var(--color-primary-200);
}

.mp-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 13px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

/* 主体不滚动：结果列表独占余量并内部滚动，其余区块保持可见（56 §1.1） */
.mp-drawer__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 20px 22px;
}

.mp-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

/* 进行中徽标：标题旁转圈 +「分析中」（交互设计 45 §2.9） */
.mp-busy {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: var(--color-primary-600);
}

.mp-busy__spin {
  width: 12px;
  height: 12px;
  border: 2px solid var(--color-primary-200);
  border-top-color: var(--color-primary-500);
  border-radius: 50%;
  animation: mp-spin 0.7s linear infinite;
}

@keyframes mp-spin {
  to {
    transform: rotate(360deg);
  }
}

.mp-resume {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
}

.mp-inputs-toggle {
  align-self: flex-start;
  flex-shrink: 0;
  padding-left: 0;
}

.mp-inputs {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex-shrink: 0;
}

.mp-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.mp-field__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mp-field__label {
  font-size: var(--font-size-xs);
  color: var(--el-text-color-secondary);
}

.mp-field__ops {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.mp-field__hint {
  font-size: var(--font-size-xs);
  color: var(--el-text-color-placeholder);
}

.mp-req-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 「+N」溢出指示：点击打开选取器管理余量（52 §1.2） */
.mp-req-more {
  cursor: pointer;
}

.mp-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.mp-actions__progress {
  flex: 1;
  min-width: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.mp-actions__stage {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

/* 结果区：吸收剩余高度，全选行常驻、仅清单滚动（56 §1.1） */
.mp-result {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mp-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.mp-result-count {
  font-size: var(--font-size-xs);
  color: var(--el-text-color-secondary);
}

.mp-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

.mp-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-lg);
}

.mp-item__body {
  flex: 1;
  min-width: 0;
}

.mp-item__title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mp-item__desc {
  margin-top: 4px;
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--el-text-color-regular);
  word-break: break-word;
  white-space: pre-wrap;
}

.mp-item__tags {
  margin-top: 6px;
}

.mp-item__related {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-xs);
  color: var(--el-text-color-secondary);
}

.mp-item__related-label {
  flex-shrink: 0;
}

.mp-doc-tip {
  margin-bottom: 8px;
  font-size: var(--font-size-xs);
  color: var(--el-text-color-secondary);
}

.mp-doc-select {
  width: 100%;
}

.mp-slide-enter-active,
.mp-slide-leave-active {
  transition: transform 0.3s ease;
}

.mp-slide-enter-from,
.mp-slide-leave-to {
  transform: translateX(100%);
}

.mp-fade-enter-active,
.mp-fade-leave-active {
  transition: opacity 0.2s ease;
}

.mp-fade-enter-from,
.mp-fade-leave-to {
  opacity: 0;
}
</style>
