import { onMounted } from 'vue'
import type { Ref } from 'vue'
import type { ApiSceneVariableItem, ApiSceneStepItem } from '@/types'
import type { SceneProcessorElement } from './useSceneProcessors'
import { prefillDraftSteps } from '@/pages/project/api-testing/scene/scenesModel'

export interface UseScenePageActionsOptions {
  sceneId?: string
  createMode?: boolean
  copyFromId?: string
  editorIsCreateMode: Ref<boolean>
  editorLoadModules: () => Promise<void>
  editorLoadEnvironments: () => Promise<void>
  editorLoadDetail: () => Promise<void>
  editorHandleSave: (options: {
    status?: string
    sceneVariablePayload?: () => ApiSceneVariableItem[]
    editProcessors?: Ref<SceneProcessorElement[]>
    draftSteps?: Ref<ApiSceneStepItem[]>
  }) => Promise<boolean>
  editorHandleRun: (options: {
    sceneVariablePayload?: () => ApiSceneVariableItem[]
    draftSteps?: Ref<ApiSceneStepItem[]>
    saveFn?: () => Promise<boolean>
  }) => Promise<void>
  editorPrefillFromCopy: (
    editVariables: Ref<{ key: string; value: string; description: string; enabled: boolean }[]>,
    editProcessors: Ref<SceneProcessorElement[]>,
    draftSteps: Ref<ApiSceneStepItem[]>,
    prefillFn: (steps: ApiSceneStepItem[]) => ApiSceneStepItem[],
  ) => Promise<void>
  stepsDraftSteps: Ref<ApiSceneStepItem[]>
  stepsHandleInterfaceSelected: (step: ApiSceneStepItem, isCreateMode: boolean) => void
  stepsHandleDeleteStep: (step: ApiSceneStepItem) => Promise<void>
  stepsHandleCopyStep: (step: ApiSceneStepItem) => Promise<void>
  editVariables: Ref<{ key: string; value: string; description: string; enabled: boolean }[]>
  editProcessors: Ref<SceneProcessorElement[]>
  sceneVariablePayload: () => ApiSceneVariableItem[]
  executionHistoryPage: Ref<number>
  loadHistory: () => void
}

export function useScenePageActions(o: UseScenePageActionsOptions) {
  async function handleSave(status?: string): Promise<boolean> {
    return o.editorHandleSave({ status, sceneVariablePayload: o.sceneVariablePayload as () => ApiSceneVariableItem[], editProcessors: o.editProcessors, draftSteps: o.stepsDraftSteps })
  }

  async function handleRun() {
    return o.editorHandleRun({ sceneVariablePayload: o.sceneVariablePayload as () => ApiSceneVariableItem[], draftSteps: o.stepsDraftSteps, saveFn: handleSave })
  }

  async function prefillFromCopy() {
    return o.editorPrefillFromCopy(o.editVariables, o.editProcessors, o.stepsDraftSteps, prefillDraftSteps)
  }

  function handleInterfaceSelected(step: ApiSceneStepItem) {
    o.stepsHandleInterfaceSelected(step, o.editorIsCreateMode.value)
  }

  async function handleDeleteStep(step: ApiSceneStepItem) {
    await o.stepsHandleDeleteStep(step)
    if (o.sceneId) await o.editorLoadDetail()
  }

  async function handleCopyStep(step: ApiSceneStepItem) {
    await o.stepsHandleCopyStep(step)
    if (o.sceneId) await o.editorLoadDetail()
  }

  function handleHistoryPageChange(page: number) {
    o.executionHistoryPage.value = page
    o.loadHistory()
  }

  onMounted(async () => {
    void o.editorLoadModules()
    void o.editorLoadEnvironments()
    if (o.copyFromId && o.editorIsCreateMode.value) {
      await prefillFromCopy()
    } else if (o.sceneId) {
      await o.editorLoadDetail()
    }
  })

  return {
    handleSave,
    handleRun,
    prefillFromCopy,
    handleInterfaceSelected,
    handleDeleteStep,
    handleCopyStep,
    handleHistoryPageChange,
  }
}
