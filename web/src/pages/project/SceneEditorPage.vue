<script setup lang="ts">
import { MagicStick, Collection } from '@element-plus/icons-vue'
import ExtractorAssetPicker from '@/components/api-testing/ExtractorAssetPicker.vue'
import { useEditorSplit } from '@/composables/useEditorSplit'
import { useSceneHistory } from '@/composables/useSceneHistory'
import { useSceneDebug } from '@/composables/useSceneDebug'
import { useSceneVariables } from '@/composables/useSceneVariables'
import { useEnvironmentRefOptions } from '@/composables/useEnvironmentRefOptions'
import { useSceneProcessors } from '@/composables/useSceneProcessors'
import { useAssetPicker } from '@/composables/useAssetPicker'
import { useSceneEditor } from '@/composables/useSceneEditor'
import { useSceneSteps } from '@/composables/useSceneSteps'
import { useScenePageActions } from '@/composables/useScenePageActions'
import StepCanvas from './scenes/StepCanvas.vue'
import SceneStepInlineEditor from './scenes/SceneStepInlineEditor.vue'
import SceneProcessorPane from './scenes/SceneProcessorPane.vue'
import InterfacePickerDialog from './scenes/InterfacePickerDialog.vue'
import StepDebugResultDialog from './scenes/StepDebugResultDialog.vue'
import SceneVariableHelperDialog from './scenes/SceneVariableHelperDialog.vue'
import ReportDetailDialog from './scenes/ReportDetailDialog.vue'
import FunctionHelperDialog from './FunctionHelperDialog.vue'
import KeyValueTable from './debug/KeyValueTable.vue'
import SceneEditorHeader from './scenes/SceneEditorHeader.vue'
import SceneEditorFooter from './scenes/SceneEditorFooter.vue'

const props = defineProps<{ sceneId?: string; createMode?: boolean; moduleId?: string; copyFromId?: string }>()
const emit = defineEmits<{ (e: 'back'): void; (e: 'title-update', name: string): void; (e: 'dirty-change', dirty: boolean): void }>()

const editor = useSceneEditor(props, emit as (e: string, ...args: unknown[]) => void)

const stepSplit = useEditorSplit(0.42)
const preSplit = useEditorSplit(0.42)
const postSplit = useEditorSplit(0.42)

const steps = useSceneSteps({
  sceneId: props.sceneId,
  detail: editor.detail,
  bumpAutosave: editor.bumpAutosave,
})

const {
  editVariables, sceneVariablePayload, sceneVariablesForHelper,
  showFunctionHelper, showVariableHelper, envVariables, envVariablesName, openVariableHelper,
} = useSceneVariables(editor.detail, editor.editEnvironmentId, editor.environmentOptions)

const { httpRefOptions, dsRefOptions } = useEnvironmentRefOptions(editor.editEnvironmentId)

const {
  editProcessors, selectedProcessorIdx,
  httpRefSelectOptions, dsRefSelectOptions, procHttpRef, procDsRef,
  procTags, procDisplayName, processorIndexes,
  addProcessor, removeProcessor, updateProcessor, selectProcessor,
  setProcessorType, moveProcessor,
  procDrag, procOnDragStart, procOnDragOver, procOnDrop, copyProcessor,
} = useSceneProcessors(editor.detail, httpRefOptions, dsRefOptions, editor.sceneSection)

const {
  assetPickerVisible, assetPickerLoading, assetPickerItems,
  assetPickerKeyword, assetPickerKind, ASSET_TITLE,
  loadAssetPicker, openAssetPicker, openExtractorPickerForProcessor, handleAssetPicked,
} = useAssetPicker(editProcessors)

const {
  executionHistory, executionHistoryTotal, executionHistoryPage,
  historyLoading, loadHistory,
  reportDialogVisible, reportDetailId, handleViewReport,
} = useSceneHistory(() => props.sceneId)

const {
  debugResult, showDebugResult,
  handleDebugStep, handleDraftDebugDisabled,
} = useSceneDebug(() => props.sceneId)

const {
  handleSave, handleRun,
  handleInterfaceSelected, handleDeleteStep, handleCopyStep,
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
  stepsHandleCopyStep: steps.handleCopyStep,
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
      :module-options="editor.moduleOptions.value"
      :environment-options="editor.environmentOptions.value"
      :current-priority-color="editor.currentPriorityColor.value"
      :priority-options="editor.SCENE_PRIORITY_OPTIONS"
      :execution-history="executionHistory"
      :execution-history-total="executionHistoryTotal"
      :execution-history-page="executionHistoryPage"
      :history-loading="historyLoading"
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
      @view-report="handleViewReport"
      @load-history="loadHistory"
    />

    <div v-if="editor.detail.value || editor.isCreateMode.value" class="scene-editor__body">
      <div class="scene-editor__tabs-wrap">
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
                    <el-button size="small" link type="primary" @click="steps.handleQuickAddStep">从接口添加</el-button>
                    <el-button size="small" link type="primary" @click="editor.isCreateMode.value ? steps.handleDraftAddStep() : steps.handleAddStep()">+ 添加步骤</el-button>
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
                  @copy="(s) => editor.isCreateMode.value ? steps.handleDraftCopyStep(s) : handleCopyStep(s)"
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
              :http-ref-select-options="httpRefSelectOptions"
              :ds-ref-select-options="dsRefSelectOptions"
              :proc-http-ref="procHttpRef"
              :proc-ds-ref="procDsRef"
              @select="selectProcessor"
              @add="addProcessor('pre')"
              @remove="(p) => removeProcessor('pre', p)"
              @move="(p, d) => moveProcessor('pre', p, d)"
              @copy="copyProcessor"
              @update="(i, v) => updateProcessor(i, v)"
              @set-testclass="(i, v) => setProcessorType(i, v)"
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
              :http-ref-select-options="httpRefSelectOptions"
              :ds-ref-select-options="dsRefSelectOptions"
              :proc-http-ref="procHttpRef"
              :proc-ds-ref="procDsRef"
              @select="selectProcessor"
              @add="addProcessor('post')"
              @remove="(p) => removeProcessor('post', p)"
              @move="(p, d) => moveProcessor('post', p, d)"
              @copy="copyProcessor"
              @update="(i, v) => updateProcessor(i, v)"
              @set-testclass="(i, v) => setProcessorType(i, v)"
              @open-asset-picker="openAssetPicker('post')"
              @open-extractor-picker="openExtractorPickerForProcessor(selectedProcessorIdx!)"
              @drag-start="(f: number, e: DragEvent) => procOnDragStart('post', f, e)"
              @drag-over="procOnDragOver"
              @drop="(p: number) => procOnDrop('post', p)"
            />
          </el-tab-pane>
        </el-tabs>

        <div class="scene-editor__tabs-extra">
          <el-button link size="small" @click="showFunctionHelper = true">
            <el-icon><MagicStick /></el-icon> 函数助手
          </el-button>
          <el-button link size="small" @click="openVariableHelper">
            <el-icon><Collection /></el-icon> 变量助手
          </el-button>
        </div>
      </div>
    </div>

    <SceneEditorFooter
      v-if="editor.detail.value || editor.isCreateMode.value"
      :auto-save-label="editor.autoSaveLabel.value"
      :is-create-mode="editor.isCreateMode.value"
    />

    <ExtractorAssetPicker
      v-model="assetPickerVisible"
      :loading="assetPickerLoading"
      :items="assetPickerItems"
      :keyword="assetPickerKeyword"
      :title="ASSET_TITLE[assetPickerKind]"
      @update:keyword="assetPickerKeyword = $event"
      @search="loadAssetPicker"
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
  border-left: 1px solid var(--color-neutral-100);
  overflow: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar { display: none; }
}

.scene-editor__right-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.scene-editor__splitter {
  flex: 0 0 8px;
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-neutral-100);

  .scene-editor__splitter-line {
    width: 1px;
    height: 100%;
    background: var(--color-neutral-300);
  }

  &:hover,
  &.is-dragging {
    background: var(--color-primary-50, #eff6ff);
    .scene-editor__splitter-line { background: var(--color-primary-400, #60a5fa); }
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
  gap: var(--space-xs);
  padding: 7px 8px;
  background: #fff;
}

.scene-editor__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
  font-weight: 600;
}

.scene-editor__section-actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}
</style>
