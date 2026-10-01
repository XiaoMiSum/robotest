/**
 * 脑图节点操作（标记/编辑/工具栏命令）
 * 编辑内核由 contenteditable 接收器实现，双击节点由内核监听，
 * 这里只是工具栏/右键菜单的编辑入口
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { copySelected, cutSelected, pasteToSelected, hasClipboard } from '@/minder/clipboard'
import { DEFAULT_NODE_TEXT } from '@/minder/jumping'
import type { Minder } from '@/minder/types'

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
  const selectedPriority = ref('')
  const canUndo = ref(false)
  const canRedo = ref(false)

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
  }

  function markPriority(p: string) {
    const data = getSelectedNodeData()
    if (!data) return
    data.priority = p
    if (data.type !== 'case') data.type = 'case'
    getMinder()?.refresh()
    updateSelectedState()
  }

  function clearMark() {
    const data = getSelectedNodeData()
    if (!data) return
    data.type = 'normal'
    delete data.priority
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
  }

  return {
    selectedPriority,
    canUndo,
    canRedo,
    exec,
    editSelectedText,
    markAs,
    markPriority,
    clearMark,
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
