import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchDocumentNodes: vi.fn(),
  caseNodeToKm: vi.fn(() => ({ data: { id: 'root' }, children: [] })),
  loadMinderEngine: vi.fn().mockResolvedValue(undefined),
  KMEditor: vi.fn(),
  buildDslPlan: vi.fn(() => ({ commands: [] })),
  applyDslPlan: vi.fn(() => ({ ok: true })),
  ElMessage: { error: vi.fn() },
  _watchCb: null as ((...args: unknown[]) => void) | null,
  _watchSource: null as unknown,
  _onMountedCb: null as (() => void) | null,
  _onBeforeUnmountCb: null as (() => void) | null,
}))

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    watch: (source: unknown, cb: (...args: unknown[]) => void) => {
      mocks._watchSource = source
      mocks._watchCb = cb
    },
    onMounted: (cb: () => void) => { mocks._onMountedCb = cb },
    onBeforeUnmount: (cb: () => void) => { mocks._onBeforeUnmountCb = cb },
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/services/project', () => ({
  fetchDocumentNodes: mocks.fetchDocumentNodes,
}))

vi.mock('@/components/project/minder/adapter', () => ({
  caseNodeToKm: mocks.caseNodeToKm,
}))

vi.mock('@/components/project/minder/loader', () => ({
  loadMinderEngine: mocks.loadMinderEngine,
}))

vi.mock('@/components/project/minder/editor', () => ({
  KMEditor: mocks.KMEditor,
}))

vi.mock('@/components/project/minder/ai/dslRunner', () => ({
  buildDslPlan: mocks.buildDslPlan,
  applyDslPlan: mocks.applyDslPlan,
}))

vi.mock('@/components/project/minder/useContextMenu', () => {
  function simpleRef(v: unknown) {
    return { get value() { return v }, set value(n: unknown) { v = n } }
  }
  const menuVisible = simpleRef(false)
  const menuPos = simpleRef({ x: 0, y: 0 })
  return {
    useContextMenu: vi.fn(() => ({
      visible: menuVisible,
      pos: menuPos,
      onContextMenu: vi.fn(),
      openAtSelection: vi.fn(),
      close: vi.fn(),
    })),
  }
})

import { useMindmapInit } from './useMindmapInit'

function makeOptions(overrides?: Record<string, unknown>) {
  return {
    docId: vi.fn(() => 'doc-1'),
    containerRef: ref<HTMLDivElement | undefined>({} as HTMLDivElement),
    loading: ref(false),
    selectedNodeId: ref(''),
    beginInit: vi.fn(() => 1),
    isStale: vi.fn(() => false),
    invalidate: vi.fn(),
    getMinder: vi.fn(() => null),
    minder: ref<unknown>(null),
    updateSelectedState: vi.fn(),
    destroyMinder: vi.fn(),
    kmEditorRef: ref<null>(null),
    persistence: {
      flushPersistenceNow: vi.fn(),
      isApplyingRemote: vi.fn(() => false),
      applyLayoutOffsets: vi.fn(),
      collectLiveNodes: vi.fn(),
      schedulePersist: vi.fn(),
      initBaseline: vi.fn(),
    },
    yjs: {
      destroyYjs: vi.fn(),
      syncToYjs: vi.fn(),
      setupYjs: vi.fn(),
    },
    layout: {
      updateTemplate: vi.fn(),
      currentTemplate: ref('default'),
    },
    nodeOps: {
      canUndo: ref(false),
      canRedo: ref(false),
    },
    assistantContext: {
      registerMindMap: vi.fn(),
      unregisterMindMap: vi.fn(),
      registerDslHost: vi.fn(),
      unregisterDslHost: vi.fn(),
    },
    aiResetPanels: vi.fn(),
    aiStopAiReadyPoll: vi.fn(),
    ...overrides,
  }
}

function createFakeMinder() {
  const listeners = new Map<string, Function[]>()
  return {
    importJson: vi.fn(),
    on: vi.fn((event: string, fn: Function) => {
      if (!listeners.has(event)) listeners.set(event, [])
      listeners.get(event)!.push(fn)
    }),
    getRoot: vi.fn(() => ({ data: { id: 'root' } })),
    getSelectedNode: vi.fn(() => null),
    queryCommandValue: vi.fn(() => 'default'),
    fire: vi.fn(),
    _listeners: listeners,
  }
}

function createFakeEditor(minder: ReturnType<typeof createFakeMinder>) {
  return {
    minder,
    history: {
      hasUndo: vi.fn(() => false),
      hasRedo: vi.fn(() => false),
    },
    destroy: vi.fn(),
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks._watchCb = null
  mocks._watchSource = null
  mocks._onMountedCb = null
  mocks._onBeforeUnmountCb = null
  mocks.fetchDocumentNodes.mockResolvedValue({
    node: {
      id: 'root',
      title: 'Root',
      type: 'normal',
      priority: null,
      children: [],
    },
    layout: { template: 'default', offsets: undefined },
  })
  mocks.KMEditor.mockImplementation((_container: unknown, _opts: unknown) => {
    const minder = createFakeMinder()
    return createFakeEditor(minder)
  })
})

describe('useMindmapInit', () => {
  describe('返回值', () => {
    it('返回 menuVisible、menuPos、onContextMenu、closeContextMenu', () => {
      const result = useMindmapInit(makeOptions())
      expect(result).toHaveProperty('menuVisible')
      expect(result).toHaveProperty('menuPos')
      expect(result).toHaveProperty('onContextMenu')
      expect(result).toHaveProperty('closeContextMenu')
      expect(typeof result.onContextMenu).toBe('function')
      expect(typeof result.closeContextMenu).toBe('function')
    })
  })

  describe('watch 注册', () => {
    it('监听 docId 变化', () => {
      useMindmapInit(makeOptions())
      expect(mocks._watchCb).toBeTypeOf('function')
    })
  })

  describe('onMounted', () => {
    it('调用 assistantContext.registerMindMap', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      expect(opts.assistantContext.registerMindMap).toHaveBeenCalledWith('doc-1')
    })

    it('触发 initMinder', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.loading.value).toBe(false)
      })
      expect(opts.beginInit).toHaveBeenCalled()
      expect(opts.persistence.flushPersistenceNow).toHaveBeenCalled()
      expect(opts.yjs.destroyYjs).toHaveBeenCalled()
    })
  })

  describe('initMinder', () => {
    it('containerRef 为空时提前返回', async () => {
      const opts = makeOptions({ containerRef: ref(undefined) })
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.loading.value).toBe(false)
      })
      expect(opts.beginInit).not.toHaveBeenCalled()
    })

    it('docId 为空时提前返回', async () => {
      const opts = makeOptions({ docId: vi.fn(() => '') })
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.loading.value).toBe(false)
      })
      expect(opts.beginInit).not.toHaveBeenCalled()
    })

    it('fetchDocumentNodes 成功时设置 loading', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      expect(opts.loading.value).toBe(false)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.loading.value).toBe(false)
      })
      expect(opts.loading.value).toBe(false)
    })

    it('fetchDocumentNodes 成功后调用 caseNodeToKm', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.caseNodeToKm).toHaveBeenCalled()
      })
    })

    it('fetchDocumentNodes 成功后调用 loadMinderEngine', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.loadMinderEngine).toHaveBeenCalled()
      })
    })

    it('fetchDocumentNodes 成功后创建 KMEditor', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.KMEditor).toHaveBeenCalled()
      })
    })

    it('fetchDocumentNodes 成功后调用 yjs.setupYjs', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.yjs.setupYjs).toHaveBeenCalledWith('doc-1')
      })
    })

    it('fetchDocumentNodes 成功后调用 persistence.initBaseline', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.persistence.initBaseline).toHaveBeenCalled()
      })
    })

    it('fetchDocumentNodes 成功后调用 assistantContext.registerDslHost', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.assistantContext.registerDslHost).toHaveBeenCalled()
      })
    })

    it('fetchDocumentNodes 成功后 minder.value 被赋值', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.minder.value).not.toBeNull()
      })
    })

    it('fetchDocumentNodes 成功后调用 persistence.applyLayoutOffsets', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.persistence.applyLayoutOffsets).toHaveBeenCalled()
      })
    })

    it('layout 有 template 时调用 layout.updateTemplate', async () => {
      mocks.fetchDocumentNodes.mockResolvedValueOnce({
        node: { id: 'root', title: 'Root', type: 'normal', priority: null, children: [] },
        layout: { template: 'fish-bone', offsets: undefined },
      })
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.layout.updateTemplate).toHaveBeenCalledWith('fish-bone')
      })
    })

    it('layout 无 template 时使用 default', async () => {
      mocks.fetchDocumentNodes.mockResolvedValueOnce({
        node: { id: 'root', title: 'Root', type: 'normal', priority: null, children: [] },
        layout: { offsets: undefined },
      })
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.layout.updateTemplate).toHaveBeenCalledWith('default')
      })
    })
  })

  describe('isStale 中断', () => {
    it('isStale 为 true 时不创建 KMEditor', async () => {
      const opts = makeOptions({ isStale: vi.fn(() => true) })
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(opts.loading.value).toBe(false)
      })
      expect(mocks.KMEditor).not.toHaveBeenCalled()
    })
  })

  describe('fetchDocumentNodes 错误', () => {
    it('fetch 失败时调用 ElMessage.error', async () => {
      mocks.fetchDocumentNodes.mockRejectedValueOnce(new Error('网络错误'))
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
      })
    })

    it('fetch 非 Error 异常时使用默认消息', async () => {
      mocks.fetchDocumentNodes.mockRejectedValueOnce('string error')
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载脑图失败')
      })
    })
  })

  describe('onBeforeUnmount', () => {
    it('调用 assistantContext.unregisterMindMap', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onBeforeUnmountCb!()
      expect(opts.assistantContext.unregisterMindMap).toHaveBeenCalled()
    })

    it('调用 assistantContext.unregisterDslHost', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onBeforeUnmountCb!()
      expect(opts.assistantContext.unregisterDslHost).toHaveBeenCalled()
    })

    it('调用 aiStopAiReadyPoll', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onBeforeUnmountCb!()
      expect(opts.aiStopAiReadyPoll).toHaveBeenCalled()
    })

    it('调用 invalidate', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onBeforeUnmountCb!()
      expect(opts.invalidate).toHaveBeenCalled()
    })

    it('调用 persistence.flushPersistenceNow', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onBeforeUnmountCb!()
      expect(opts.persistence.flushPersistenceNow).toHaveBeenCalled()
    })

    it('调用 yjs.destroyYjs', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onBeforeUnmountCb!()
      expect(opts.yjs.destroyYjs).toHaveBeenCalled()
    })

    it('调用 destroyMinder', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onBeforeUnmountCb!()
      expect(opts.destroyMinder).toHaveBeenCalled()
    })
  })

  describe('watch docId', () => {
    it('docId 变化时调用 aiResetPanels', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._watchCb!('new-doc-id')
      expect(opts.aiResetPanels).toHaveBeenCalled()
    })

    it('docId 变化时调用 assistantContext.registerMindMap', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._watchCb!('new-doc-id')
      expect(opts.assistantContext.registerMindMap).toHaveBeenCalledWith('new-doc-id')
    })

    it('docId 变化时触发 initMinder', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._watchCb!('new-doc-id')
      await vi.waitFor(() => {
        expect(opts.loading.value).toBe(false)
      })
      expect(opts.beginInit).toHaveBeenCalled()
    })
  })

  describe('teardownMinder', () => {
    it('有 kmEditorRef 时传 destroy 回调', async () => {
      const fakeMinder = createFakeMinder()
      const fakeEditor = createFakeEditor(fakeMinder)
      const opts = makeOptions()
      const kmEditorRef = ref<null>(null)
      opts.kmEditorRef = kmEditorRef
      mocks.KMEditor.mockImplementationOnce(() => {
        kmEditorRef.value = fakeEditor as never
        return fakeEditor
      })
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(kmEditorRef.value).not.toBeNull()
      })
      mocks._onBeforeUnmountCb!()
      expect(opts.destroyMinder).toHaveBeenCalledWith(expect.any(Function))
    })

    it('kmEditorRef 为 null 时传 undefined', () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onBeforeUnmountCb!()
      expect(opts.destroyMinder).toHaveBeenCalledWith(undefined)
    })
  })

  describe('contentchange 事件处理', () => {
    it('非 remote 应用时调用 collectLiveNodes 和 syncToYjs 和 schedulePersist', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.KMEditor).toHaveBeenCalled()
      })
      const editorInstance = mocks.KMEditor.mock.results[0].value
      const minderInstance = editorInstance.minder
      const contentChangeCb = minderInstance.on.mock.calls.find(
        (c: unknown[]) => (c as string[])[0] === 'contentchange',
      )?.[1]
      expect(contentChangeCb).toBeDefined()
      opts.persistence.isApplyingRemote.mockReturnValue(false)
      contentChangeCb()
      expect(opts.persistence.collectLiveNodes).toHaveBeenCalled()
      expect(opts.yjs.syncToYjs).toHaveBeenCalled()
      expect(opts.persistence.schedulePersist).toHaveBeenCalled()
    })

    it('remote 应用时不调用 collectLiveNodes', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.KMEditor).toHaveBeenCalled()
      })
      const editorInstance = mocks.KMEditor.mock.results[0].value
      const minderInstance = editorInstance.minder
      const contentChangeCb = minderInstance.on.mock.calls.find(
        (c: unknown[]) => (c as string[])[0] === 'contentchange',
      )?.[1]
      opts.persistence.isApplyingRemote.mockReturnValue(true)
      contentChangeCb()
      expect(opts.persistence.collectLiveNodes).not.toHaveBeenCalled()
      expect(opts.yjs.syncToYjs).not.toHaveBeenCalled()
      expect(opts.persistence.schedulePersist).not.toHaveBeenCalled()
    })
  })

  describe('selectionchange 事件处理', () => {
    it('selectionchange 调用 updateSelectedState', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.KMEditor).toHaveBeenCalled()
      })
      const editorInstance = mocks.KMEditor.mock.results[0].value
      const minderInstance = editorInstance.minder
      const selectionCb = minderInstance.on.mock.calls.find(
        (c: unknown[]) => (c as string[])[0] === 'selectionchange',
      )?.[1]
      expect(selectionCb).toBeDefined()
      selectionCb()
      expect(opts.updateSelectedState).toHaveBeenCalled()
    })
  })

  describe('historyFrozen', () => {
    it('KMEditor 使用 persistence.isApplyingRemote 作为 historyFrozen', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.KMEditor).toHaveBeenCalled()
      })
      const editorOpts = mocks.KMEditor.mock.calls[0][1]
      opts.persistence.isApplyingRemote.mockReturnValue(true)
      expect(editorOpts.historyFrozen()).toBe(true)
      opts.persistence.isApplyingRemote.mockReturnValue(false)
      expect(editorOpts.historyFrozen()).toBe(false)
    })
  })

  describe('onHistoryChange', () => {
    it('更新 nodeOps.canUndo 和 canRedo', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.KMEditor).toHaveBeenCalled()
      })
      const editorOpts = mocks.KMEditor.mock.calls[0][1]
      const editorInstance = mocks.KMEditor.mock.results[0].value
      editorInstance.history.hasUndo.mockReturnValue(true)
      editorInstance.history.hasRedo.mockReturnValue(true)
      editorOpts.onHistoryChange()
      expect(opts.nodeOps.canUndo.value).toBe(true)
      expect(opts.nodeOps.canRedo.value).toBe(true)
    })

    it('kmEditorRef 为 null 时使用 false', async () => {
      const opts = makeOptions()
      useMindmapInit(opts)
      mocks._onMountedCb!()
      await vi.waitFor(() => {
        expect(mocks.KMEditor).toHaveBeenCalled()
      })
      const editorOpts = mocks.KMEditor.mock.calls[0][1]
      opts.kmEditorRef.value = null
      editorOpts.onHistoryChange()
      expect(opts.nodeOps.canUndo.value).toBe(false)
      expect(opts.nodeOps.canRedo.value).toBe(false)
    })
  })
})
