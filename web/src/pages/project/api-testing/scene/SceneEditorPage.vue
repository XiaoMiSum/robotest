<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, Collection, EditPen } from '@element-plus/icons-vue'
import type { ApiGitRepoConfig } from '@/types'
import { fetchGitRepo } from '@/services/project/api-testing/gitRepo'
import { shortenSourcePath } from '@/composables/project/api-testing/scene/scenesModel'
import { flattenModuleNames } from '@/composables/project/api-testing/interface/interfacesModel'
import ExtractorAssetPicker from '@/components/project/api-testing/ExtractorAssetPicker.vue'
import { useEditorSplit } from '@/composables/project/api-testing/scene/useEditorSplit'
import { useSceneHistory } from '@/composables/project/api-testing/scene/useSceneHistory'
import { useSceneDebug } from '@/composables/project/api-testing/scene/useSceneDebug'
import { useSceneVariables } from '@/composables/project/api-testing/scene/useSceneVariables'
import { useEnvironmentRefOptions } from '@/composables/project/api-testing/environment/useEnvironmentRefOptions'
import { useSceneProcessors } from '@/composables/project/api-testing/scene/useSceneProcessors'
import { useAssetPicker } from '@/composables/project/api-testing/scene/useAssetPicker'
import { useSceneEditor } from '@/composables/project/api-testing/scene/useSceneEditor'
import { useSceneSteps } from '@/composables/project/api-testing/scene/useSceneSteps'
import { useScenePageActions } from '@/composables/project/api-testing/scene/useScenePageActions'
import StepCanvas from './StepCanvas.vue'
import SceneStepInlineEditor from './SceneStepInlineEditor.vue'
import SceneProcessorPane from './SceneProcessorPane.vue'
import InterfacePickerDialog from './InterfacePickerDialog.vue'
import StepDebugResultDialog from './StepDebugResultDialog.vue'
import SceneVariableHelperDialog from './SceneVariableHelperDialog.vue'
import ReportDetailDialog from './ReportDetailDialog.vue'
import FunctionHelperDialog from '@/pages/project/api-testing/function/FunctionHelperDialog.vue'
import KeyValueTable from '@/components/project/api-testing/KeyValueTable.vue'
import SceneEditorHeader from './SceneEditorHeader.vue'

const props = defineProps<{ sceneId?: string; createMode?: boolean; moduleId?: string; copyFromId?: string }>()
const emit = defineEmits<{ (e: 'back'): void; (e: 'title-update', name: string): void; (e: 'dirty-change', dirty: boolean): void }>()

const editor = useSceneEditor(props, emit as (e: string, ...args: unknown[]) => void)

// ==================== GitLab 来源展示（交互 10 §1.9） ====================
const router = useRouter()
const detail = editor.detail

/** platform 导入附加元素 / gitlab_ci 只读形态的判定依据（详设 5.3 派生 engine） */
const isGitImport = computed(() => detail.value?.sourceType === 'gitlab')
const readonlyMode = computed(() => detail.value?.engine === 'gitlab_ci')
const sourceStatus = computed(() => detail.value?.sourceStatus ?? null)

// 仓库信息仅导入场景需要（徽标 hover 与源引用跳转）；未配置或失败静默降级，不影响编辑主流程
const repo = ref<ApiGitRepoConfig | null>(null)
watch(
  isGitImport,
  (needed) => {
    if (!needed) return
    void fetchGitRepo()
      .then((r) => { repo.value = r })
      .catch(() => { repo.value = null })
  },
  { immediate: true },
)

const repoLabel = computed(() =>
  repo.value ? `${repo.value.name} · ${repo.value.repoPath}` : '',
)

// 源侧稳定键/引用：gitlab_ci 为「类全限定名#方法名」+「文件路径#方法名」，platform 导入为 yaml 路径
const sourceFile = computed(() => {
  const ref = detail.value?.sourceRef
  if (!ref) return null
  return ref.includes('#') ? ref.slice(0, ref.indexOf('#')) : ref
})
const sourceRefUrl = computed(() => {
  const r = repo.value
  if (!r || !sourceFile.value) return null
  return `${r.gitlabBaseUrl.replace(/\/+$/, '')}/${r.repoPath}/-/blob/${r.defaultBranch}/${sourceFile.value}`
})
const repoUrl = computed(() => {
  const r = repo.value
  return r ? `${r.gitlabBaseUrl.replace(/\/+$/, '')}/${r.repoPath}` : null
})

const caseInfo = computed(() => detail.value?.caseInfo ?? null)

const moduleName = computed(() => {
  const id = detail.value?.moduleId
  if (!id) return null
  return flattenModuleNames(editor.moduleTree.value).get(id) ?? null
})

// 运行状态徽标：以最近一次执行为准，触发中恒为执行中（对齐 demo ci-run 四态）
const ciRunState = computed<'idle' | 'running' | 'success' | 'failed'>(() => {
  if (editor.running.value) return 'running'
  const latest = executionHistory.value[0]
  if (!latest) return 'idle'
  if (latest.status === 'pending' || latest.status === 'running') return 'running'
  if (latest.status === 'success') return 'success'
  return 'failed'
})

function goGitLabPage() {
  void router.push({ path: '/workspace/projects/api-testing', query: { tab: 'gitlab', gl: 'import' } })
}

const stepSplit = useEditorSplit(0.42)
const preSplit = useEditorSplit(0.42)
const postSplit = useEditorSplit(0.42)

const steps = useSceneSteps({
  sceneId: props.sceneId,
  detail: editor.detail,
})

// 进入步骤页签默认选中第一个步骤：右侧明细不空置，无需手动点选（交互 10 §1.2）
watch(
  [() => editor.sceneSection.value, () => (editor.isCreateMode.value ? steps.draftSteps.value : steps.sorted.value).length],
  () => {
    if (editor.sceneSection.value !== 'steps' || steps.selectedStep.value) return
    const first = (editor.isCreateMode.value ? steps.draftSteps.value : steps.sorted.value)[0]
    if (first) steps.selectedStep.value = first
  },
  { immediate: true },
)

const {
  editVariables, sceneVariablePayload, sceneVariablesForHelper,
  showFunctionHelper, showVariableHelper, envVariables, envVariablesName, openVariableHelper,
} = useSceneVariables(editor.detail, editor.editEnvironmentId, editor.environmentOptions)

const { httpRefOptions, dsRefOptions } = useEnvironmentRefOptions(editor.editEnvironmentId)

const {
  editProcessors, selectedProcessorIdx,
  procTags, procDisplayName, processorIndexes,
  addProcessor, removeProcessor, updateProcessor, selectProcessor,
  moveProcessor,
  procDrag, procOnDragStart, procOnDragOver, procOnDrop, copyProcessor,
} = useSceneProcessors(editor.detail, editor.sceneSection)

const {
  assetPickerVisible, assetPickerLoading, assetPickerItems, assetPickerError,
  assetPickerKeyword, assetPickerKind, ASSET_TITLE,
  retryAssetPicker, openAssetPicker, openExtractorPickerForProcessor, handleAssetPicked,
} = useAssetPicker(editProcessors)

const {
  executionHistory, executionHistoryTotal, executionHistoryPage,
  historyLoading, historyError, loadHistory, retryHistory,
  reportDialogVisible, reportDetailId, handleViewReport,
} = useSceneHistory(() => props.sceneId)

const {
  debugResult, showDebugResult,
  handleDebugStep, handleDraftDebugDisabled,
} = useSceneDebug(() => props.sceneId)

const {
  handleSave, handleRun,
  handleInterfaceSelected, handleDeleteStep,
  handleHistoryPageChange,
} = useScenePageActions({
  sceneId: props.sceneId,
  createMode: props.createMode,
  copyFromId: props.copyFromId,
  editorIsCreateMode: editor.isCreateMode,
  editorLoadModules: editor.loadModules,
  editorLoadEnvironments: editor.loadEnvironments,
  editorLoadDetail: editor.loadDetail,
  editorHandleSave: editor.handleSave,
  editorHandleRun: editor.handleRun,
  editorPrefillFromCopy: editor.prefillFromCopy,
  stepsDraftSteps: steps.draftSteps,
  stepsHandleInterfaceSelected: steps.handleInterfaceSelected,
  stepsHandleDeleteStep: steps.handleDeleteStep,
  editVariables,
  editProcessors,
  sceneVariablePayload,
  executionHistoryPage,
  loadHistory,
})
</script>

<template>
  <div v-loading="editor.loading.value" class="scene-editor" :class="{ 'is-ci': readonlyMode }">
    <SceneEditorHeader
      v-if="editor.detail.value || editor.isCreateMode.value"
      :edit-name="editor.editName.value"
      :edit-description="editor.editDescription.value"
      :edit-module-id="editor.editModuleId.value"
      :edit-environment-id="editor.editEnvironmentId.value"
      :edit-priority="editor.editPriority.value"
      :edit-status="editor.editStatus.value"
      :show-description="editor.showDescription.value"
      :is-create-mode="editor.isCreateMode.value"
      :saving="editor.saving.value"
      :running="editor.running.value"
      :source-type="editor.detail.value?.sourceType ?? null"
      :source-status="editor.detail.value?.sourceStatus ?? null"
      :repo-label="repoLabel"
      :readonly-mode="readonlyMode"
      :module-name="moduleName"
      :run-state="ciRunState"
      :module-options="editor.moduleOptions.value"
      :environment-options="editor.environmentOptions.value"
      :current-priority-color="editor.currentPriorityColor.value"
      :priority-options="editor.SCENE_PRIORITY_OPTIONS"
      :execution-history="executionHistory"
      :execution-history-total="executionHistoryTotal"
      :execution-history-page="executionHistoryPage"
      :history-loading="historyLoading"
      :history-error="historyError"
      @update:edit-name="editor.editName.value = $event"
      @update:edit-description="editor.editDescription.value = $event"
      @update:edit-module-id="editor.editModuleId.value = $event"
      @update:edit-environment-id="editor.editEnvironmentId.value = $event"
      @update:edit-priority="editor.editPriority.value = $event"
      @update:show-description="editor.showDescription.value = $event"
      @update:execution-history-page="handleHistoryPageChange"
      @save="handleSave"
      @delete="editor.handleDeleteScene"
      @run="handleRun"
      @go-gitlab="goGitLabPage"
      @view-report="handleViewReport"
      @load-history="retryHistory"
    />

    <!-- 源状态提示条（交互 10 §1.9，对齐 demo）：位于导航与主体之间，仅异常态出现；CI 态文案随引擎而定 -->
    <div
      v-if="editor.detail.value && isGitImport && sourceStatus && sourceStatus !== 'in_sync'"
      class="scene-editor__source-alert"
      role="alert"
    >
      {{ sourceStatus === 'repo_unreachable'
        ? '仓库不可达：[运行] 已置灰，源引用不可跳转仓库侧文件'
        : '源码解析失败，当前展示为上次成功导入的内容（BR-1.12）；不阻断查看与执行' }}
    </div>

    <div
      v-if="editor.detail.value || editor.isCreateMode.value"
      class="scene-editor__body"
      :class="{ 'is-card': readonlyMode }"
    >
      <!-- gitlab_ci 只读主体（交互 10 §1.9，对齐 demo api-scenes.html edit-ci）：信息网格 + 单个用例 kv + 只读说明 -->
      <div v-if="readonlyMode" class="scene-editor__readonly" data-test="scene-readonly">
        <div class="ci-info">
          <div>
            <div class="ci-info__k">所属模块</div>
            <div class="ci-info__v">{{ moduleName ?? '未分配' }}</div>
          </div>
          <div>
            <div class="ci-info__k">来源类型</div>
            <div class="ci-info__v">GitLab 流水线 <span class="ci-info__engine">gitlab_ci</span></div>
          </div>
          <div>
            <div class="ci-info__k">仓库</div>
            <div class="ci-info__v">
              <a v-if="repoUrl" :href="repoUrl" target="_blank" rel="noopener">{{ repo?.name ?? '查看仓库' }} ↗</a>
              <span v-else>—</span>
            </div>
          </div>
          <div>
            <div class="ci-info__k">默认分支</div>
            <div class="ci-info__v ci-info__mono">{{ repo?.defaultBranch ?? '—' }}</div>
          </div>
          <div>
            <div class="ci-info__k">源引用</div>
            <div class="ci-info__v">
              <a v-if="sourceRefUrl" :href="sourceRefUrl" target="_blank" rel="noopener">{{ shortenSourcePath(detail?.sourceRef) }} ↗</a>
              <span v-else>{{ shortenSourcePath(detail?.sourceRef) || '—' }}</span>
            </div>
          </div>
        </div>

        <div class="ci-cases">
          <div class="ci-cases__head">
            <span class="ci-cases__title">用例</span>
          </div>
          <div v-if="caseInfo" class="ci-info ci-info--case">
            <div>
              <div class="ci-info__k">方法名</div>
              <div class="ci-info__v ci-info__mono">{{ caseInfo.methodName }}</div>
            </div>
            <div>
              <div class="ci-info__k">用例名称（@Test description）</div>
              <div class="ci-info__v">{{ caseInfo.caseName }}</div>
            </div>
            <div>
              <div class="ci-info__k">源文件</div>
              <div class="ci-info__v">
                <a v-if="sourceRefUrl" :href="sourceRefUrl" target="_blank" rel="noopener">{{ shortenSourcePath(caseInfo.sourceFile ?? sourceFile) }} ↗</a>
                <span v-else>{{ shortenSourcePath(caseInfo.sourceFile ?? sourceFile) || '—' }}</span>
              </div>
            </div>
          </div>
          <div v-else class="ci-cases__empty">用例信息不可用</div>
          <p class="ci-hint">只读形态：不提供步骤编排、场景变量与处理器编辑；用例与步骤的维护一律在仓库侧完成。</p>
        </div>
      </div>

      <div v-else class="scene-editor__tabs-wrap">
        <el-tabs v-model="editor.sceneSection.value" class="scene-editor__left-tabs">
          <el-tab-pane label="步骤" name="steps">
            <div
              :ref="stepSplit.register"
              class="scene-editor__steps-layout"
              :class="{ 'is-dragging': stepSplit.dragging.value }"
            >
              <div class="scene-editor__steps-left" :style="{ flexBasis: `${stepSplit.ratio.value * 100}%` }">
                <div class="scene-editor__section-head">
                  <span>{{ (editor.isCreateMode.value ? steps.draftSteps.value : steps.sorted.value).length }} 个步骤</span>
                  <div class="scene-editor__section-actions">
                    <el-button link type="primary" @click="steps.handleQuickAddStep">从接口添加</el-button>
                    <el-button link type="primary" @click="editor.isCreateMode.value ? steps.handleDraftAddStep() : steps.handleAddStep()">+ 添加步骤</el-button>
                  </div>
                </div>
                <StepCanvas
                  v-if="editor.detail.value || editor.isCreateMode.value"
                  :steps="editor.isCreateMode.value ? steps.draftSteps.value : steps.sorted.value"
                  :selected-id="steps.selectedStep.value?.id ?? null"
                  :is-executing="false"
                  @edit="steps.handleSelectStep"
                  @delete="(s) => editor.isCreateMode.value ? steps.handleDraftDeleteStep(s) : handleDeleteStep(s)"
                  @toggle="(s) => editor.isCreateMode.value ? steps.handleDraftToggleStep(s) : steps.handleToggleStep(s)"
                  @reorder="(s) => editor.isCreateMode.value ? steps.handleDraftReorderSteps(s) : steps.handleReorderSteps(s)"
                  @copy="(s) => editor.isCreateMode.value ? steps.handleDraftCopyStep(s) : steps.handleCopyStep(s)"
                  @debug="(s) => editor.isCreateMode.value ? handleDraftDebugDisabled(s) : handleDebugStep(s)"
                  @insert-before="steps.handleAddStep"
                  @add="editor.isCreateMode.value ? steps.handleDraftAddStep() : steps.handleAddStep()"
                />
              </div>

              <div
                class="scene-editor__splitter"
                :class="{ 'is-dragging': stepSplit.dragging.value }"
                @mousedown.prevent="stepSplit.start"
              >
                <div class="scene-editor__splitter-line" />
              </div>

              <div class="scene-editor__steps-right">
                <SceneStepInlineEditor
                  v-if="steps.selectedStep.value"
                  :key="steps.selectedStep.value.id"
                  :step="steps.selectedStep.value"
                  :draft="editor.isCreateMode.value"
                  :environment-id="editor.editEnvironmentId.value"
                />
                <div v-else class="scene-editor__right-empty">
                  <el-icon :size="28"><EditPen /></el-icon>
                  <p>暂无执行步骤，请先新增</p>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="场景变量" name="variables">
            <!-- 固定高度容器包裹；页签标题已承担命名，内部不重复标题、列头由列内占位符承担（交互 10 §1.3） -->
            <div class="scene-editor__kv-pane">
              <KeyValueTable
                v-model:entries="editVariables"
                placeholder-key="变量名"
                show-description
                :show-enabled="false"
                :show-header="false"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="前置处理器" name="pre">
            <SceneProcessorPane
              type="pre"
              :split="preSplit"
              :processors="editProcessors"
              :selected-idx="selectedProcessorIdx"
              :indexes="processorIndexes('pre')"
              :tags="procTags"
              :display-name="procDisplayName"
              :drag="procDrag"
              :http-ref-options="httpRefOptions"
              :ds-ref-options="dsRefOptions"
              @select="selectProcessor"
              @add="addProcessor('pre')"
              @remove="(p) => removeProcessor('pre', p)"
              @move="(p, d) => moveProcessor('pre', p, d)"
              @copy="copyProcessor"
              @update="(i, v) => updateProcessor(i, v)"
              @open-asset-picker="openAssetPicker('pre')"
              @open-extractor-picker="openExtractorPickerForProcessor(selectedProcessorIdx!)"
              @drag-start="(f: number, e: DragEvent) => procOnDragStart('pre', f, e)"
              @drag-over="procOnDragOver"
              @drop="(p: number) => procOnDrop('pre', p)"
            />
          </el-tab-pane>

          <el-tab-pane label="后置处理器" name="post">
            <SceneProcessorPane
              type="post"
              :split="postSplit"
              :processors="editProcessors"
              :selected-idx="selectedProcessorIdx"
              :indexes="processorIndexes('post')"
              :tags="procTags"
              :display-name="procDisplayName"
              :drag="procDrag"
              :http-ref-options="httpRefOptions"
              :ds-ref-options="dsRefOptions"
              @select="selectProcessor"
              @add="addProcessor('post')"
              @remove="(p) => removeProcessor('post', p)"
              @move="(p, d) => moveProcessor('post', p, d)"
              @copy="copyProcessor"
              @update="(i, v) => updateProcessor(i, v)"
              @open-asset-picker="openAssetPicker('post')"
              @open-extractor-picker="openExtractorPickerForProcessor(selectedProcessorIdx!)"
              @drag-start="(f: number, e: DragEvent) => procOnDragStart('post', f, e)"
              @drag-over="procOnDragOver"
              @drop="(p: number) => procOnDrop('post', p)"
            />
          </el-tab-pane>
        </el-tabs>

        <div class="scene-editor__tabs-extra">
          <el-button link @click="showFunctionHelper = true">
            <el-icon><MagicStick /></el-icon> 函数助手
          </el-button>
          <el-button link @click="openVariableHelper">
            <el-icon><Collection /></el-icon> 变量助手
          </el-button>
        </div>
      </div>
    </div>

    <ExtractorAssetPicker
      v-model="assetPickerVisible"
      :loading="assetPickerLoading"
      :items="assetPickerItems"
      :error="assetPickerError"
      :keyword="assetPickerKeyword"
      :title="ASSET_TITLE[assetPickerKind]"
      @update:keyword="assetPickerKeyword = $event"
      @search="retryAssetPicker"
      @confirm="handleAssetPicked"
    />

    <InterfacePickerDialog v-model="steps.showInterfacePicker.value" @select="handleInterfaceSelected" />
    <StepDebugResultDialog v-model="showDebugResult" :result="debugResult" />
    <FunctionHelperDialog v-model="showFunctionHelper" />
    <SceneVariableHelperDialog
      v-model="showVariableHelper"
      :environment-variables="envVariables"
      :environment-name="envVariablesName"
      :scene-variables="sceneVariablesForHelper"
    />
    <ReportDetailDialog v-model="reportDialogVisible" :report-id="reportDetailId" :scene-id="props.sceneId ?? null" />
  </div>
</template>

<style scoped lang="scss">
// 头部信息栏与页签条之间只留 4px：两者上下内边距不再叠加（交互 10 §1.1）
.scene-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--space-xs);
  overflow: hidden;
}

// gitlab_ci 只读形态按 demo .scene-ed：导航卡 / 提示条 / 主体卡之间 12px
.scene-editor.is-ci {
  gap: var(--space-md);
}

// 只读主体卡片（对齐 demo .card.ed-body：白底 + 1px 描边 + 圆角 + 浅阴影）
.scene-editor__body.is-card {
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);

  // 只读页文本一律不加粗：信息值与用例标题取常规字重
  .ci-info__v,
  .ci-cases__title {
    font-weight: 400;
  }
}

// 页面不设外层卡片容器：tabs 与内容区直接承接头部信息（交互 10 §1.1）
.scene-editor__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

// 左右两栏各自为带边框卡片、等高（交互 10 §1.1），中间由透明分割条隔开
.scene-editor__steps-layout {
  height: 100%;
  display: flex;
  align-items: stretch;
}

.scene-editor__steps-left {
  flex: 0 1 auto;
  min-width: 260px;
  min-height: 0;
  padding: var(--space-md);
  overflow-y: auto;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
  scrollbar-width: none;

  &::-webkit-scrollbar { display: none; }
}

// 明细不设父容器：由 step-inline 自身的头部带与内容分区承担层次（交互 10 §1.1）
// 不加内边距，使明细的盒子与左侧列表卡片的外框上下对齐
.scene-editor__steps-right {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar { display: none; }
}

// 右侧空态卡片（交互 10 §1.1）：居中图标 + 文案，填补分栏空白而非单行灰字
// 右侧空态：不设外边距使其与左侧列表卡片等高，白底实线框（交互 10 §1.1）
.scene-editor__right-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  padding: var(--space-xl) var(--space-lg);
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
  text-align: center;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);

  p {
    margin: 0;
  }
}

// 分割条只作拖拽热区：平时不显示灰条与竖线，拖拽过程中给出反馈（交互 10 §1.1）
.scene-editor__splitter {
  flex: 0 0 var(--space-md);
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;

  .scene-editor__splitter-line {
    display: none;
    width: 1px;
    height: 100%;
    background: var(--color-primary-400);
  }

  &.is-dragging .scene-editor__splitter-line {
    display: block;
  }
}

.scene-editor__tabs-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.scene-editor__left-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) { margin-bottom: 0; flex-shrink: 0; }
  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }
  :deep(.el-tab-pane) {
    height: 100%;
    overflow-y: auto;
  }
}

.scene-editor__tabs-extra {
  position: absolute;
  top: 1px;
  right: 8px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 7px 8px;
  background: var(--color-neutral-0);
  // 助手入口是次级动作：中性色起步、主色悬停，不与页签高亮争主视觉
  --el-button-text-color: var(--color-neutral-600);
  --el-button-hover-link-text-color: var(--color-primary-500);
}

// 场景变量明细：容器高度与步骤 / 处理器页签的容器一致（撑满页签面板）、内部滚动，
// 不重复页签标题（交互 10 §1.3）
.scene-editor__kv-pane {
  box-sizing: border-box;
  height: 100%;
  overflow: auto;
  padding: var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
}

// 区块标题：字号字重 + 底部分隔线，与右侧操作行拉开层级（交互 10 §1.2 / §1.5）
.scene-editor__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: 8px;
  margin-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-neutral-100);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-800);
}

.scene-editor__section-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

// 源状态提示条（交互 10 §1.9，对齐 demo .alert--warning）：位于导航与主体之间，仅异常态出现
.scene-editor__source-alert {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 14px;
  background: var(--color-warning-light);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-md);
  color: var(--color-warning-strong);
  font-size: var(--font-size-sm);
}

// gitlab_ci 只读主体（交互 10 §1.9，对齐 demo edit-ci）：内距交由内部信息区与用例区各自承担
.scene-editor__readonly {
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow-y: auto;
}

// 只读信息网格（demo .ci-info）：键小值粗、4 列自适应换行
.ci-info {
  display: grid;
  /** 只读信息网格固定 3 列两行，与用例 kv 行列对齐 */
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-sm) var(--space-md);
  padding: 14px var(--space-lg) 0;
}

// 用例区的 kv 行：贴住标题、只留底部 4px（demo 内联 padding:0 0 4px）
.ci-info--case {
  padding: 0 0 4px;
}

.ci-info__k {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.ci-info__v {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-800);

  a {
    color: var(--color-primary-500);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.ci-info__mono {
  font-family: var(--font-mono);
}

// 引擎标识（demo .tag--outline.tag--sm）：等宽小号描边，不与状态色混用
.ci-info__engine {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border: 1px solid var(--color-neutral-300);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-neutral-600);
  font-family: var(--font-mono);
  font-size: 10px;
}

// 用例区（demo .ci-cases）：标题行 + kv + 只读说明
.ci-cases {
  padding: 10px var(--space-lg) 14px;
}

.ci-cases__head {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  padding-bottom: var(--space-sm);
}

.ci-cases__title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
}

.ci-cases__empty {
  padding: var(--space-xs) 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.ci-hint {
  margin: 10px 0 0;
  font-size: var(--font-size-xs);
  line-height: 1.6;
  color: var(--color-neutral-500);
}
</style>
