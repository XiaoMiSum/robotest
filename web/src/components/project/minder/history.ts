import type { Minder, MinderEvent, MinderEventHandler, JsonPatch } from './types'

/**
 * 撤销/重做（移植自 kityminder-editor 的 history runtime）：
 * contentchange 时与上一快照做 JSON diff 生成反向补丁入撤销栈，
 * undo/redo 经 core 原生 applyPatches 增量回放（仅局部重渲染），
 * 回放自动触发 contentchange，既有同步/落库管道零改动生效。
 */
export interface History {
  reset(): void
  undo(): void
  redo(): void
  hasUndo(): boolean
  hasRedo(): boolean
  destroy(): void
}

export interface HistoryOptions {
  /** 返回真值时内容变更不入栈（远端协同回放不应产生本地撤销步骤） */
  isFrozen?: () => boolean
  /** 撤销/重做栈变化回调，驱动工具栏按钮可用态 */
  onChange?: () => void
}

const MAX_HISTORY = 100

// RFC6901：路径分段中的 ~ 与 / 需转义，才能与 core 的 applyPatches 解析对齐
function escapePathComponent(str: string): string {
  if (!str.includes('/') && !str.includes('~')) return str
  return str.replace(/~/g, '~0').replace(/\//g, '~1')
}

function deepClone<T>(obj: T): T {
  if (typeof obj === 'object' && obj !== null) return JSON.parse(JSON.stringify(obj)) as T
  return obj
}

type JsonRecord = Record<string, unknown>

// 内联移植 fast-json-patch 的 _generate：生成 mirror → obj 的 RFC6902 补丁
function generate(mirror: JsonRecord, obj: JsonRecord, patches: JsonPatch[], path: string): void {
  const newKeys = Object.keys(obj)
  const oldKeys = Object.keys(mirror)
  let deleted = false

  for (let t = oldKeys.length - 1; t >= 0; t--) {
    const key = oldKeys[t]
    const oldVal = mirror[key]
    if (Object.prototype.hasOwnProperty.call(obj, key)) {
      const newVal = obj[key]
      if (typeof oldVal === 'object' && oldVal !== null && typeof newVal === 'object' && newVal !== null) {
        generate(oldVal as JsonRecord, newVal as JsonRecord, patches, `${path}/${escapePathComponent(key)}`)
      } else if (oldVal !== newVal) {
        patches.push({ op: 'replace', path: `${path}/${escapePathComponent(key)}`, value: deepClone(newVal) })
      }
    } else {
      patches.push({ op: 'remove', path: `${path}/${escapePathComponent(key)}` })
      deleted = true
    }
  }

  if (!deleted && newKeys.length === oldKeys.length) return

  for (const key of newKeys) {
    if (!Object.prototype.hasOwnProperty.call(mirror, key)) {
      patches.push({ op: 'add', path: `${path}/${escapePathComponent(key)}`, value: deepClone(obj[key]) })
    }
  }
}

/** 生成 tree1 → tree2 的 JSON 补丁（导出供单测） */
export function jsonDiff(tree1: JsonRecord, tree2: JsonRecord): JsonPatch[] {
  const patches: JsonPatch[] = []
  generate(tree1, tree2, patches, '')
  return patches
}

export function createHistory(minder: Minder, options: HistoryOptions = {}): History {
  const { isFrozen, onChange } = options

  let lastSnap: JsonRecord = {}
  // 回放门闩：undo/redo 触发的 contentchange 不能再次入栈
  let patchLock = false
  let undoDiffs: JsonPatch[][] = []
  let redoDiffs: JsonPatch[][] = []

  function reset() {
    undoDiffs = []
    redoDiffs = []
    lastSnap = minder.exportJson()
    onChange?.()
  }

  // 撤销补丁是「当前 → 上一快照」的反向 diff，apply 即回退一步
  function makeUndoDiff(): boolean {
    const headSnap = minder.exportJson()
    const diff = jsonDiff(headSnap, lastSnap)
    if (!diff.length) return false
    undoDiffs.push(diff)
    while (undoDiffs.length > MAX_HISTORY) undoDiffs.shift()
    lastSnap = headSnap
    return true
  }

  function makeRedoDiff() {
    const revertSnap = minder.exportJson()
    redoDiffs.push(jsonDiff(revertSnap, lastSnap))
    lastSnap = revertSnap
  }

  function undo() {
    patchLock = true
    const undoDiff = undoDiffs.pop()
    if (undoDiff) {
      minder.applyPatches(undoDiff)
      makeRedoDiff()
    }
    patchLock = false
    onChange?.()
  }

  function redo() {
    patchLock = true
    const redoDiff = redoDiffs.pop()
    if (redoDiff) {
      minder.applyPatches(redoDiff)
      makeUndoDiff()
    }
    patchLock = false
    onChange?.()
  }

  const changed: MinderEventHandler = () => {
    if (patchLock) return
    // 冻结期（远端回放）只对齐快照，不产生本地撤销步骤
    if (isFrozen?.()) {
      lastSnap = minder.exportJson()
      return
    }
    if (makeUndoDiff()) {
      redoDiffs = []
      onChange?.()
    }
  }

  // 回放补丁后恢复受影响节点的选中态，保持撤销焦点连续
  const updateSelection: MinderEventHandler = (e: MinderEvent) => {
    if (!patchLock || !e.patch) return
    const patch = e.patch
    switch (patch.express) {
      case 'node.add':
        minder.select(patch.node.getChild(patch.index), true)
        break
      case 'node.remove':
      case 'data.replace':
      case 'data.remove':
      case 'data.add':
        minder.select(patch.node, true)
        break
    }
  }

  const onImport: MinderEventHandler = () => reset()

  reset()
  minder.on('contentchange', changed)
  minder.on('import', onImport)
  minder.on('patch', updateSelection)

  return {
    reset,
    undo,
    redo,
    hasUndo: () => undoDiffs.length > 0,
    hasRedo: () => redoDiffs.length > 0,
    destroy() {
      minder.off('contentchange', changed)
      minder.off('import', onImport)
      minder.off('patch', updateSelection)
    },
  }
}
