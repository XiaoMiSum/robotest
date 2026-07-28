import { ref } from 'vue'
import type { Minder, MinderNode } from './types'

/**
 * 应用内节点剪贴板（模块级单例，跨文档粘贴天然可用）：
 * 不使用系统剪贴板——kity 节点无法可靠序列化，应用内互通也无需读写权限。
 * id 与 layout_*_offset 在复制时即剥离：粘贴节点视同新建，
 * 由持久化管道的 uuidv7 归一化统一补发新 id，从根上避免 id 冲突；
 * 剪切后无论粘贴多少次都不会复用旧 id。
 */
export interface ClipboardTree {
  data: Record<string, unknown>
  children: ClipboardTree[]
}

// 白名单只留业务字段：旧 id 必须丢弃，位置偏移对新落点无意义
const KEEP_KEYS = ['text', 'type', 'priority'] as const

let clipboardTree: ClipboardTree | null = null

/** 粘贴入口禁用态标记（响应式，供菜单项使用） */
export const hasClipboard = ref(false)

export function clearClipboard(): void {
  clipboardTree = null
  hasClipboard.value = false
}

// 导出只依赖 data 与 getChildren 的结构化递归类型（MinderNode 天然满足），便于单测伪造
export interface SubtreeSource {
  data: Record<string, unknown>
  getChildren(): SubtreeSource[]
}

export function exportSubtree(node: SubtreeSource): ClipboardTree {
  const data: Record<string, unknown> = {}
  for (const key of KEEP_KEYS) {
    if (node.data[key] !== undefined) data[key] = node.data[key]
  }
  return { data, children: node.getChildren().map(exportSubtree) }
}

export function cloneTree(tree: ClipboardTree): ClipboardTree {
  return {
    data: { ...tree.data },
    children: tree.children.map(cloneTree),
  }
}

export function copySelected(minder: Minder): boolean {
  const node = minder.getSelectedNode()
  if (!node) return false
  clipboardTree = exportSubtree(node)
  hasClipboard.value = true
  return true
}

export type CutResult = 'ok' | 'no-node' | 'root'

export function cutSelected(minder: Minder): CutResult {
  const node = minder.getSelectedNode()
  if (!node) return 'no-node'
  // 根节点不可删除，剪切整体拒绝且不覆盖既有剪贴板（复制根节点不受限）
  if (node === minder.getRoot()) return 'root'
  clipboardTree = exportSubtree(node)
  hasClipboard.value = true
  minder.execCommand('RemoveNode')
  return 'ok'
}

export function pasteToSelected(minder: Minder): boolean {
  const target = minder.getSelectedNode()
  if (!target || !clipboardTree) return false
  // 每次粘贴独立深拷贝，同一份剪贴板可重复粘贴且互不串改
  const created = appendTree(minder, cloneTree(clipboardTree), target)
  minder.select(created, true)
  minder.refresh()
  // 递归 createNode 不走命令，不会自动触发 contentchange，
  // 手动补发一次以搭上 history / Yjs 同步 / 防抖落库管道
  minder.fire('contentchange')
  return true
}

function appendTree(minder: Minder, tree: ClipboardTree, parent: MinderNode): MinderNode {
  const node = minder.createNode(tree.data, parent)
  tree.children.forEach((child) => appendTree(minder, child, node))
  return node
}
