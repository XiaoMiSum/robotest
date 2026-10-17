<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, Collection, EditPen } from '@element-plus/icons-vue'
import type { ApiGitRepoConfig } from '@/types'
import { fetchGitRepo } from '@/services/project/api-testing/gitRepo'
import { formatDateTime } from '@/utils/format'
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
  <div v-loading="editor.loading.value" class="scene-editor">
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
      :engine="editor.detail.value?.engine ?? null"
      :source-type="editor.detail.value?.sourceType ?? null"
      :source-status="editor.detail.value?.sourceStatus ?? null"
      :repo-label="repoLabel"
      :readonly-mode="readonlyMode"
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

    <div v-if="editor.detail.value || editor.isCreateMode.value" class="scene-editor__body">
      <!-- platform 导入源状态提示条（交互 10 §1.9）：源码已退化 / 仓库不可达时提示，不阻断编辑 -->
      <div
        v-if="isGitImport && !readonlyMode && sourceStatus && sourceStatus !== 'in_sync'"
        class="scene-editor__source-alert"
        role="alert"
      >
        {{ sourceStatus === 'repo_unreachable' ? '仓库不可达：执行入口已置灰' : '源码已退化：仓库侧解析失败，展示为导入时内容' }}
      </div>

      <!-- gitlab_ci 只读主体（交互 10 §1.9）：步骤区替换为只读信息区 + 单个用例信息 -->
      <div v-if="readonlyMode" class="scene-editor__readonly" data-test="scene-readonly">
        <div class="scene-editor__readonly-info">
          <div class="scene-editor__readonly-row">
            <span class="scene-editor__readonly-label">模块</span>
            <span>{{ moduleName ?? '未分配' }}</span>
          </div>
          <div class="scene-editor__readonly-row">
            <span class="scene-editor__readonly-label">仓库</span>
            <a v-if="repoUrl" :href="repoUrl" target="_blank" rel="noopener" class="scene-editor__readonly-link">{{ repo?.name ?? '查看仓库' }} ↗</a>
            <span v-else>—</span>
          </div>
          <div class="scene-editor__readonly-row">
            <span class="scene-editor__readonly-label">源引用</span>
            <a v-if="sourceRefUrl" :href="sourceRefUrl" target="_blank" rel="noopener" class="scene-editor__readonly-link">{{ detail?.sourceRef }} ↗</a>
            <span v-else>{{ detail?.sourceRef ?? '—' }}</span>
          </div>
        </div>

        <div class="scene-editor__readonly-section">
          <div class="scene-editor__section-head">用例（场景与测试方法一一对应，恒为单个用例）</div>
          <el-table v-if="caseInfo" :data="[caseInfo]">
            <el-table-column prop="methodName" label="方法名" min-width="160" />
            <el-table-column prop="caseName" label="用例名称" min-width="200" />
            <el-table-column label="源文件" min-width="240">
              <template #default="{ row }">
                <a v-if="sourceRefUrl" :href="sourceRefUrl" target="_blank" rel="noopener" class="scene-editor__readonly-link">{{ row.sourceFile ?? sourceFile }} ↗</a>
                <span v-else>{{ row.sourceFile ?? sourceFile ?? '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="scene-editor__right-empty">用例信息不可用</div>
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
                  <p>选中左侧步骤卡片后在右侧编辑</p>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="场景变量" name="variables">
            <div class="scene-editor__section-head">
              <span>场景变量</span>
            </div>
            <KeyValueTable
              v-model:entries="editVariables"
              placeholder-key="变量名"
              show-description
              :show-enabled="false"
            />
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

      <!-- 只读执行历史（交互 10 §1.9）：填补只读页下方空白，仅展示不可编辑 -->
      <div v-if="readonlyMode" class="scene-editor__readonly-history">
        <div class="scene-editor__section-head">执行历史</div>
        <div v-loading="historyLoading" class="scene-editor__history-panel">
          <div v-if="historyError" class="scene-editor__history-error" role="alert">
            <span>{{ historyError }}</span>
            <el-button link type="primary" @click="retryHistory">重试</el-button>
          </div>
          <div v-for="h in executionHistory" :key="h.id" class="scene-editor__history-row">
            <span class="scene-editor__history-status" :class="`is-${h.status}`">{{ h.status }}</span>
            <span class="scene-editor__history-time">{{ formatDateTime(h.executedAt) }}</span>
            <el-button
              v-if="h.reportId"
              link
              type="primary"
              class="scene-editor__history-report"
              @click="handleViewReport(h.reportId)"
            >报告</el-button>
          </div>
          <div v-if="!executionHistory.length && !historyError" class="scene-editor__empty-text">暂无执行记录</div>
          <el-pagination
            v-if="executionHistoryTotal > 0"
            :current-page="executionHistoryPage"
            :page-size="20"
            :total="executionHistoryTotal"
            layout="prev, pager, next"
            class="scene-editor__history-pager"
            @current-change="handleHistoryPageChange"
          />
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
.scene-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--space-md);
  overflow: hidden;
}

.scene-editor__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  overflow: hidden;
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

.scene-editor__steps-right {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--space-md);
  overflow: auto;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
  scrollbar-width: none;

  &::-webkit-scrollbar { display: none; }
}

// 右侧空态卡片（交互 10 §1.1）：居中图标 + 文案，填补分栏空白而非单行灰字
.scene-editor__right-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  margin: var(--space-md);
  padding: var(--space-xl) var(--space-lg);
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
  text-align: center;
  background: var(--color-neutral-50);
  border: 1px dashed var(--color-neutral-200);
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

// platform 导入源状态提示条（交互 10 §1.9）
.scene-editor__source-alert {
  flex-shrink: 0;
  padding: var(--space-sm) var(--space-md);
  background: rgba(var(--el-color-warning-rgb), 0.1);
  color: var(--el-color-warning);
  font-size: var(--font-size-sm);
  border-bottom: 1px solid var(--color-neutral-100);
}

// gitlab_ci 只读主体（交互 10 §1.9）：只读信息区 + 单个用例信息
.scene-editor__readonly {
  flex: 1;
  min-height: 0;
  padding: var(--space-md);
  overflow-y: auto;
}

.scene-editor__readonly-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-md);
  margin-bottom: var(--space-md);
  background: var(--color-neutral-50);
  border-radius: var(--radius-md);
}

.scene-editor__readonly-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-sm);
}

.scene-editor__readonly-label {
  flex-shrink: 0;
  width: 48px;
  color: var(--color-neutral-500);
}

.scene-editor__readonly-link {
  color: var(--color-primary-500);
  text-decoration: none;

  &:hover { text-decoration: underline; }
}

.scene-editor__readonly-section {
  :deep(.el-table) { --el-table-header-bg-color: var(--color-neutral-50); }
}

// 只读执行历史区（交互 10 §1.9）：填补只读页下方空白
.scene-editor__readonly-history {
  flex-shrink: 0;
  padding: 0 var(--space-md) var(--space-md);
}

.scene-editor__history-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
  color: var(--el-color-danger);
  font-size: var(--font-size-xs);
}

.scene-editor__history-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 6px 0;
  font-size: var(--font-size-xs);
  border-bottom: 1px solid var(--color-neutral-100);
}

.scene-editor__history-status {
  flex-shrink: 0;
  min-width: 52px;
  font-weight: 600;

  &.is-success { color: var(--el-color-success); }
  &.is-failed,
  &.is-error { color: var(--el-color-danger); }
  &.is-pending,
  &.is-running { color: var(--el-color-primary); }
}

.scene-editor__history-time {
  flex: 1;
  color: var(--color-neutral-500);
}

// [报告] 贴行右端：即便时间列为空也不会被挤到中间
.scene-editor__history-report {
  margin-left: auto;
}

.scene-editor__empty-text {
  padding: var(--space-sm) 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.scene-editor__history-pager {
  margin-top: var(--space-sm);
  justify-content: flex-end;
}
</style>
