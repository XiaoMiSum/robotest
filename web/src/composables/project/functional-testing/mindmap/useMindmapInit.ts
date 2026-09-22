import { watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDocumentNodes } from '@/services/project'
import { caseNodeToKm } from '@/minder/adapter'
import { loadMinderEngine } from '@/minder/loader'
import { KMEditor } from '@/minder/editor'
import { buildDslPlan, applyDslPlan } from '@/minder/ai/dslRunner'
import {
  useContextMenu,
  type ContextMenuAnchorNode,
} from '@/minder/useContextMenu'
import type { MountTargetSource } from '@/minder/ai/aiMount'
import type { DslHost } from '@/stores/assistantContext'
import type { Minder } from '@/minder/types'

interface KMEditorLike {
  minder: Minder
  history: { hasUndo(): boolean; hasRedo(): boolean }
  destroy(): void
}

export function useMindmapInit(options: {
  docId: () => string
  containerRef: { value: HTMLDivElement | undefined }
  loading: { value: boolean }
  selectedNodeId: { value: string }
  beginInit(): number
  isStale(token: number): boolean
  invalidate(): void
  getMinder(): Record<string, (...args: unknown[]) => unknown> | null
  minder: { value: unknown }
  updateSelectedState(): void
  destroyMinder(destroyFn?: () => void): void
  kmEditorRef: { value: KMEditorLike | null }
  persistence: {
    flushPersistenceNow(): void
    isApplyingRemote(): boolean
    applyLayoutOffsets(root: Record<string, unknown>, offsets?: Record<string, Record<string, unknown>>): void
    collectLiveNodes(): void
    schedulePersist(): void
    initBaseline(): void
  }
  yjs: {
    destroyYjs(): void
    syncToYjs(): void
    setupYjs(docId: string): void
  }
  layout: {
    updateTemplate(template: string): void
    currentTemplate: { value: string }
  }
  nodeOps: {
    canUndo: { value: boolean }
    canRedo: { value: boolean }
  }
  assistantContext: {
    registerMindMap(docId: string): void
    unregisterMindMap(): void
    registerDslHost(host: DslHost): void
    unregisterDslHost(): void
  }
  aiResetPanels(): void
  aiStopAiReadyPoll(): void
}) {
  const {
    docId, containerRef, loading, selectedNodeId, beginInit, isStale,
    invalidate, getMinder, minder, updateSelectedState,
    destroyMinder, kmEditorRef, persistence, yjs, layout, nodeOps,
    assistantContext, aiResetPanels, aiStopAiReadyPoll,
  } = options

  const {
    visible: menuVisible,
    pos: menuPos,
    onContextMenu,
    openAtSelection: openContextMenuAtSelection,
    close: closeContextMenu,
  } = useContextMenu({
    hasSelection: () => !!selectedNodeId.value,
    getSelectedNode: () =>
      getMinder()?.getSelectedNode?.() as ContextMenuAnchorNode | null | undefined,
  })

  function teardownMinder() {
    destroyMinder(kmEditorRef.value ? () => kmEditorRef.value?.destroy() : undefined)
    kmEditorRef.value = null
  }

  async function initMinder() {
    if (!containerRef.value || !docId()) return
    const token = beginInit()
    loading.value = true
    persistence.flushPersistenceNow()
    yjs.destroyYjs()
    teardownMinder()
    try {
      const docData = await fetchDocumentNodes(docId())
      const root = caseNodeToKm(docData.node)
      persistence.applyLayoutOffsets(root, docData.layout?.offsets)
      const template = docData.layout?.template || 'default'
      layout.updateTemplate(template)
      const kmData = { root, template, theme: 'fresh-blue' }

      await loadMinderEngine()
      if (isStale(token) || !containerRef.value) return

      const editor = new KMEditor(containerRef.value, {
        historyFrozen: () => persistence.isApplyingRemote(),
        onHistoryChange: () => {
          nodeOps.canUndo.value = kmEditorRef.value?.history.hasUndo() ?? false
          nodeOps.canRedo.value = kmEditorRef.value?.history.hasRedo() ?? false
        },
        onMenuRequest: openContextMenuAtSelection,
      })
      kmEditorRef.value = editor
      const instance: unknown = editor.minder
      minder.value = instance
      const m = instance as Record<string, (...args: unknown[]) => unknown>
      m.importJson(kmData)

      assistantContext.registerDslHost({
        documentId: docId(),
        buildPlan: (commands, selectedNodeId) =>
          buildDslPlan(editor.minder.getRoot() as MountTargetSource | null, commands, selectedNodeId),
        apply: (plan) => applyDslPlan(editor.minder, plan),
      })

      m.on('selectionchange', updateSelectedState)
      m.on('contentchange', () => {
        layout.updateTemplate(
          (m.queryCommandValue?.('template') as string) || layout.currentTemplate.value,
        )
        if (!persistence.isApplyingRemote()) {
          persistence.collectLiveNodes()
          yjs.syncToYjs()
          persistence.schedulePersist()
        }
      })

      persistence.initBaseline()

      await nextTick()
      if (isStale(token)) return
      yjs.setupYjs(docId())
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载脑图失败')
    } finally {
      loading.value = false
    }
  }

  watch(docId, (id) => {
    aiResetPanels()
    assistantContext.registerMindMap(id)
    void initMinder()
  })

  onMounted(() => {
    assistantContext.registerMindMap(docId())
    void initMinder()
  })

  onBeforeUnmount(() => {
    assistantContext.unregisterMindMap()
    assistantContext.unregisterDslHost()
    aiStopAiReadyPoll()
    invalidate()
    persistence.flushPersistenceNow()
    yjs.destroyYjs()
    teardownMinder()
  })

  return { menuVisible, menuPos, onContextMenu, closeContextMenu }
}
