/**
 * 脑图节点操作（标记/编辑/工具栏命令）
 * 编辑内核由 contenteditable 接收器实现，双击节点由内核监听，
 * 这里只是工具栏/右键菜单的编辑入口
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { recommendPriority, type AiPriorityRecommendResp } from '@/services/ai'
import { useAiStore } from '@/stores/ai'
import { copySelected, cutSelected, pasteToSelected, hasClipboard } from '@/components/project/functional-testing/minder/clipboard'
import { DEFAULT_NODE_TEXT } from '@/components/project/functional-testing/minder/jumping'
import type { MountTargetSource } from '@/components/project/functional-testing/minder/ai/aiMount'
import type { Minder } from '@/components/project/functional-testing/minder/types'

interface KMEditorLike {
  minder: Minder
  history: { undo(): void; redo(): void; hasUndo(): boolean; hasRedo(): boolean }
  destroy(): void
  editText(): void
}

export function useMindmapNodeOps(
  getMinder: () => Minder | null,
  getSelectedNodeData: () => Record<string, unknown> | null,
  updateSelectedState: () => void,
  kmEditorRef: { value: KMEditorLike | null },
) {
  const aiStore = useAiStore()

  const selectedPriority = ref('')
  const selectedAiGenerated = ref(false)
  const canUndo = ref(false)
  const canRedo = ref(false)
  const priorityRecommendation = ref<AiPriorityRecommendResp | null>(null)
  let priorityRecSeq = 0

  function getLiveRoot(): MountTargetSource | null {
    const m = getMinder()
    return (m as unknown as { getRoot?: () => MountTargetSource | null })?.getRoot?.() ?? null
  }

  function findNodePath(node: MountTargetSource | null, id: string): string[] | null {
    if (!node) return null
    const title = (node.data.text as string) ?? ''
    if (node.data.id === id) return [title]
    for (const child of node.getChildren()) {
      const sub = findNodePath(child, id)
      if (sub) return [title, ...sub]
    }
    return null
  }

  function triggerPriorityRecommend() {
    const root = getLiveRoot()
    const data = getSelectedNodeData()
    if (!root || !data) return
    const nodeId = (data.id as string) || ''
    const title = (data.text as string) ?? ''
    if (!nodeId || !title.trim()) return
    const path = findNodePath(root, nodeId) ?? []
    const seq = ++priorityRecSeq
    priorityRecommendation.value = null
    recommendPriority(title.trim(), path.slice(0, -1))
      .then((resp) => {
        if (seq !== priorityRecSeq || !resp.priority) return
        priorityRecommendation.value = resp
      })
      .catch(() => { /* 非侵入原则：LLM 失败静默 */ })
  }

  function exec(command: string, ...args: unknown[]) {
    getMinder()?.execCommand(command, ...args)
    kmEditorRef.value?.minder.fire('receiverfocus')
  }

  function editSelectedText() {
    kmEditorRef.value?.editText()
  }

  function markAs(type: string) {
    const data = getSelectedNodeData()
    if (!data) return
    data.type = type
    if (type === 'case' && !data.priority) data.priority = 'P2'
    if (type !== 'case') delete data.priority
    getMinder()?.refresh()
    updateSelectedState()
    if (type === 'case' && aiStore.aiEnabled) triggerPriorityRecommend()
  }

  function markPriority(p: string) {
    const data = getSelectedNodeData()
    if (!data) return
    priorityRecSeq++
    priorityRecommendation.value = null
    data.priority = p
    if (data.type !== 'case') data.type = 'case'
    getMinder()?.refresh()
    updateSelectedState()
  }

  function applyPriorityRecommendation() {
    const rec = priorityRecommendation.value
    if (!rec?.priority) return
    markPriority(rec.priority)
  }

  function clearMark() {
    const data = getSelectedNodeData()
    if (!data) return
    data.type = 'normal'
    delete data.priority
    getMinder()?.refresh()
    updateSelectedState()
  }

  function removeAiFlag() {
    const data = getSelectedNodeData()
    if (!data || data.aiGenerated !== true) return
    data.aiGenerated = false
    getMinder()?.refresh()
    updateSelectedState()
  }

  function addChild() { exec('AppendChildNode', DEFAULT_NODE_TEXT) }
  function addSibling() { exec('AppendSiblingNode', DEFAULT_NODE_TEXT) }
  function deleteNode() { exec('RemoveNode') }

  function copyNode() {
    const editor = kmEditorRef.value
    if (editor) copySelected(editor.minder)
  }

  function cutNode() {
    const editor = kmEditorRef.value
    if (!editor) return
    if (cutSelected(editor.minder) === 'root') ElMessage.warning('根节点不能剪切')
  }

  function pasteNode() {
    const editor = kmEditorRef.value
    if (editor) pasteToSelected(editor.minder)
  }

  function undo() { kmEditorRef.value?.history.undo() }
  function redo() { kmEditorRef.value?.history.redo() }

  function onSelectionChange(data: Record<string, unknown> | null) {
    selectedPriority.value = data ? (data.priority as string) || '' : ''
    selectedAiGenerated.value = data ? data.aiGenerated === true : false
    priorityRecSeq++
    priorityRecommendation.value = null
  }

  return {
    aiStore,
    selectedPriority,
    selectedAiGenerated,
    canUndo,
    canRedo,
    priorityRecommendation,
    exec,
    editSelectedText,
    markAs,
    markPriority,
    applyPriorityRecommendation,
    clearMark,
    removeAiFlag,
    addChild,
    addSibling,
    deleteNode,
    copyNode,
    cutNode,
    pasteNode,
    undo,
    redo,
    hasClipboard,
    onSelectionChange,
  }
}
