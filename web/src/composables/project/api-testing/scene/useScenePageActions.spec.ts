import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiSceneStepItem } from '@/types'

const mocks = vi.hoisted(() => ({
  prefillDraftSteps: vi.fn((s: ApiSceneStepItem[]) => s),
  onMounted: vi.fn((cb: () => void) => { mocks._onMountedCb = cb }),
  _onMountedCb: null as (() => void) | null,
}))

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    onMounted: mocks.onMounted,
  }
})

vi.mock('@/pages/project/scenesModel', () => ({
  prefillDraftSteps: mocks.prefillDraftSteps,
}))

import { useScenePageActions } from './useScenePageActions'
import type { UseScenePageActionsOptions } from './useScenePageActions'

function makeStep(overrides?: Partial<ApiSceneStepItem>): ApiSceneStepItem {
  return {
    id: 'step-1',
    name: '步骤1',
    stepType: 'http',
    sortOrder: 0,
    enabled: true,
    sourceType: 'interface',
    sourceId: 'if-1',
    requestConfig: { method: 'GET', url: '/test' },
    variables: [],
    processors: [],
    validators: [],
    extractors: [],
    ...overrides,
  }
}

function makeOptions(overrides?: Partial<UseScenePageActionsOptions>): UseScenePageActionsOptions {
  return {
    editorIsCreateMode: ref(false),
    editorLoadModules: vi.fn().mockResolvedValue(undefined),
    editorLoadEnvironments: vi.fn().mockResolvedValue(undefined),
    editorLoadDetail: vi.fn().mockResolvedValue(undefined),
    editorHandleSave: vi.fn().mockResolvedValue(true),
    editorHandleRun: vi.fn().mockResolvedValue(undefined),
    editorPrefillFromCopy: vi.fn().mockResolvedValue(undefined),
    stepsDraftSteps: ref([]),
    stepsHandleInterfaceSelected: vi.fn(),
    stepsHandleDeleteStep: vi.fn().mockResolvedValue(undefined),
    stepsHandleCopyStep: vi.fn().mockResolvedValue(undefined),
    editVariables: ref([]),
    editProcessors: ref([]),
    sceneVariablePayload: vi.fn().mockReturnValue([]),
    executionHistoryPage: ref(1),
    loadHistory: vi.fn(),
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks._onMountedCb = null
})

describe('useScenePageActions', () => {
  describe('handleSave', () => {
    it('delegates to editorHandleSave with correct args', async () => {
      const o = makeOptions()
      const { handleSave } = useScenePageActions(o)
      const result = await handleSave()
      expect(o.editorHandleSave).toHaveBeenCalledWith({
        status: undefined,
        sceneVariablePayload: o.sceneVariablePayload,
        editProcessors: o.editProcessors,
        draftSteps: o.stepsDraftSteps,
      })
      expect(result).toBe(true)
    })

    it('passes status to editorHandleSave', async () => {
      const o = makeOptions()
      const { handleSave } = useScenePageActions(o)
      await handleSave('published')
      expect(o.editorHandleSave).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'published' }),
      )
    })

    it('returns false when editorHandleSave returns false', async () => {
      const o = makeOptions({
        editorHandleSave: vi.fn().mockResolvedValue(false),
      })
      const { handleSave } = useScenePageActions(o)
      const result = await handleSave()
      expect(result).toBe(false)
    })
  })

  describe('handleRun', () => {
    it('delegates to editorHandleRun with correct args', async () => {
      const o = makeOptions()
      const { handleRun } = useScenePageActions(o)
      await handleRun()
      expect(o.editorHandleRun).toHaveBeenCalledWith({
        sceneVariablePayload: o.sceneVariablePayload,
        draftSteps: o.stepsDraftSteps,
        saveFn: expect.any(Function),
      })
    })

    it('saveFn in handleRun delegates to handleSave', async () => {
      const editorHandleSave = vi.fn().mockResolvedValue(true)
      const o = makeOptions({ editorHandleSave })
      const { handleRun } = useScenePageActions(o)
      await handleRun()
      const saveFn = (o.editorHandleRun as ReturnType<typeof vi.fn>).mock.calls[0]![0]!.saveFn as () => Promise<boolean>
      const result = await saveFn()
      expect(editorHandleSave).toHaveBeenCalled()
      expect(result).toBe(true)
    })
  })

  describe('prefillFromCopy', () => {
    it('delegates to editorPrefillFromCopy with refs and prefillDraftSteps', async () => {
      const o = makeOptions()
      const { prefillFromCopy } = useScenePageActions(o)
      await prefillFromCopy()
      expect(o.editorPrefillFromCopy).toHaveBeenCalledWith(
        o.editVariables,
        o.editProcessors,
        o.stepsDraftSteps,
        mocks.prefillDraftSteps,
      )
    })
  })

  describe('handleInterfaceSelected', () => {
    it('calls stepsHandleInterfaceSelected with step and isCreateMode', () => {
      const o = makeOptions({ editorIsCreateMode: ref(true) })
      const { handleInterfaceSelected } = useScenePageActions(o)
      const step = makeStep()
      handleInterfaceSelected(step)
      expect(o.stepsHandleInterfaceSelected).toHaveBeenCalledWith(step, true)
    })

    it('passes false when isCreateMode is false', () => {
      const o = makeOptions({ editorIsCreateMode: ref(false) })
      const { handleInterfaceSelected } = useScenePageActions(o)
      const step = makeStep()
      handleInterfaceSelected(step)
      expect(o.stepsHandleInterfaceSelected).toHaveBeenCalledWith(step, false)
    })
  })

  describe('handleDeleteStep', () => {
    it('calls stepsHandleDeleteStep then editorLoadDetail when sceneId exists', async () => {
      const o = makeOptions({ sceneId: 'scene-1' })
      const { handleDeleteStep } = useScenePageActions(o)
      const step = makeStep()
      await handleDeleteStep(step)
      expect(o.stepsHandleDeleteStep).toHaveBeenCalledWith(step)
      expect(o.editorLoadDetail).toHaveBeenCalled()
    })

    it('skips editorLoadDetail when sceneId is absent', async () => {
      const o = makeOptions()
      const { handleDeleteStep } = useScenePageActions(o)
      const step = makeStep()
      await handleDeleteStep(step)
      expect(o.stepsHandleDeleteStep).toHaveBeenCalledWith(step)
      expect(o.editorLoadDetail).not.toHaveBeenCalled()
    })

    it('skips editorLoadDetail when sceneId is empty string', async () => {
      const o = makeOptions({ sceneId: '' })
      const { handleDeleteStep } = useScenePageActions(o)
      await handleDeleteStep(makeStep())
      expect(o.editorLoadDetail).not.toHaveBeenCalled()
    })
  })

  describe('handleCopyStep', () => {
    it('calls stepsHandleCopyStep then editorLoadDetail when sceneId exists', async () => {
      const o = makeOptions({ sceneId: 'scene-1' })
      const { handleCopyStep } = useScenePageActions(o)
      const step = makeStep()
      await handleCopyStep(step)
      expect(o.stepsHandleCopyStep).toHaveBeenCalledWith(step)
      expect(o.editorLoadDetail).toHaveBeenCalled()
    })

    it('skips editorLoadDetail when sceneId is absent', async () => {
      const o = makeOptions()
      const { handleCopyStep } = useScenePageActions(o)
      await handleCopyStep(makeStep())
      expect(o.stepsHandleCopyStep).toHaveBeenCalled()
      expect(o.editorLoadDetail).not.toHaveBeenCalled()
    })
  })

  describe('handleHistoryPageChange', () => {
    it('sets executionHistoryPage and calls loadHistory', () => {
      const o = makeOptions({ executionHistoryPage: ref(1) })
      const { handleHistoryPageChange } = useScenePageActions(o)
      handleHistoryPageChange(3)
      expect(o.executionHistoryPage.value).toBe(3)
      expect(o.loadHistory).toHaveBeenCalled()
    })

    it('can set page back to 1', () => {
      const o = makeOptions({ executionHistoryPage: ref(5) })
      const { handleHistoryPageChange } = useScenePageActions(o)
      handleHistoryPageChange(1)
      expect(o.executionHistoryPage.value).toBe(1)
    })
  })

  describe('onMounted', () => {
    it('calls editorLoadModules and editorLoadEnvironments', async () => {
      const o = makeOptions()
      useScenePageActions(o)
      const cb = mocks._onMountedCb!
      await cb()
      expect(o.editorLoadModules).toHaveBeenCalled()
      expect(o.editorLoadEnvironments).toHaveBeenCalled()
    })

    it('calls editorLoadDetail when sceneId exists and no copyFromId', async () => {
      const o = makeOptions({ sceneId: 'scene-1' })
      useScenePageActions(o)
      await mocks._onMountedCb!()
      expect(o.editorLoadDetail).toHaveBeenCalled()
    })

    it('calls prefillFromCopy when copyFromId and isCreateMode', async () => {
      const o = makeOptions({
        copyFromId: 'copy-1',
        editorIsCreateMode: ref(true),
      })
      useScenePageActions(o)
      await mocks._onMountedCb!()
      expect(o.editorPrefillFromCopy).toHaveBeenCalledWith(
        o.editVariables,
        o.editProcessors,
        o.stepsDraftSteps,
        mocks.prefillDraftSteps,
      )
    })

    it('calls editorLoadDetail when copyFromId but not createMode', async () => {
      const o = makeOptions({
        copyFromId: 'copy-1',
        editorIsCreateMode: ref(false),
        sceneId: 'scene-1',
      })
      useScenePageActions(o)
      await mocks._onMountedCb!()
      expect(o.editorLoadDetail).toHaveBeenCalled()
      expect(o.editorPrefillFromCopy).not.toHaveBeenCalled()
    })

    it('does not call editorLoadDetail when no sceneId and no copyFromId', async () => {
      const o = makeOptions()
      useScenePageActions(o)
      await mocks._onMountedCb!()
      expect(o.editorLoadDetail).not.toHaveBeenCalled()
      expect(o.editorPrefillFromCopy).not.toHaveBeenCalled()
    })

    it('calls editorLoadModules and editorLoadEnvironments before conditional logic', async () => {
      const callOrder: string[] = []
      const o = makeOptions({
        sceneId: 'scene-1',
        editorLoadModules: vi.fn().mockImplementation(() => { callOrder.push('modules'); return Promise.resolve() }),
        editorLoadEnvironments: vi.fn().mockImplementation(() => { callOrder.push('environments'); return Promise.resolve() }),
        editorLoadDetail: vi.fn().mockImplementation(() => { callOrder.push('detail'); return Promise.resolve() }),
      })
      useScenePageActions(o)
      await mocks._onMountedCb!()
      expect(callOrder[0]).toBe('modules')
      expect(callOrder[1]).toBe('environments')
      expect(callOrder[2]).toBe('detail')
    })
  })
})
