import { describe, it, expect } from 'vitest'
import { createHistory, jsonDiff, type History } from './history'
import type { Minder, MinderEventHandler, JsonPatch } from './types'

// 最小 RFC6902 应用器：仅覆盖 jsonDiff 会产出的 add/remove/replace 三种操作
function applyPatch(doc: Record<string, unknown>, patch: JsonPatch): void {
  const segments = patch.path
    .split('/')
    .slice(1)
    .map((s) => s.replace(/~1/g, '/').replace(/~0/g, '~'))
  const key = segments.pop() as string
  let target: Record<string, unknown> = doc
  for (const seg of segments) {
    target = target[seg] as Record<string, unknown>
  }
  if (patch.op === 'remove') delete target[key]
  else target[key] = patch.value
}

// 模拟 minder：维护 JSON 文档、事件分发，applyPatches 后触发 contentchange（与 core 行为一致）
function createMockMinder(initial: Record<string, unknown>) {
  let json = JSON.parse(JSON.stringify(initial)) as Record<string, unknown>
  const handlers = new Map<string, MinderEventHandler[]>()

  const minder = {
    on(events: string, handler: MinderEventHandler) {
      for (const type of events.split(' ')) {
        const list = handlers.get(type) ?? []
        list.push(handler)
        handlers.set(type, list)
      }
      return minder
    },
    off(events: string, handler: MinderEventHandler) {
      for (const type of events.split(' ')) {
        const list = handlers.get(type) ?? []
        handlers.set(
          type,
          list.filter((h) => h !== handler),
        )
      }
      return minder
    },
    fire(type: string) {
      for (const h of handlers.get(type) ?? []) h({ type })
      return minder
    },
    exportJson() {
      return JSON.parse(JSON.stringify(json)) as Record<string, unknown>
    },
    importJson(data: Record<string, unknown>) {
      json = JSON.parse(JSON.stringify(data)) as Record<string, unknown>
      minder.fire('import')
      return minder
    },
    applyPatches(patches: JsonPatch[]) {
      for (const p of patches) applyPatch(json, p)
      minder.fire('contentchange')
      return minder
    },
    select() {
      return minder
    },
  }

  return {
    minder: minder as unknown as Minder,
    // 模拟一次用户编辑：直接改文档后触发 contentchange
    edit(mutate: (doc: Record<string, unknown>) => void) {
      mutate(json)
      minder.fire('contentchange')
    },
    get(): Record<string, unknown> {
      return json
    },
  }
}

const doc = () => ({ root: { data: { text: '根' }, children: [] as unknown[] } })

describe('jsonDiff', () => {
  it('生成 replace 补丁', () => {
    expect(jsonDiff({ a: 1 }, { a: 2 })).toEqual([{ op: 'replace', path: '/a', value: 2 }])
  })

  it('生成 add 与 remove 补丁', () => {
    expect(jsonDiff({ a: 1 }, { a: 1, b: 2 })).toEqual([{ op: 'add', path: '/b', value: 2 }])
    expect(jsonDiff({ a: 1, b: 2 }, { a: 1 })).toEqual([{ op: 'remove', path: '/b' }])
  })

  it('嵌套对象递归 diff 且路径分段转义 ~ 与 /', () => {
    expect(jsonDiff({ 'a/b': { 'c~d': 1 } }, { 'a/b': { 'c~d': 2 } })).toEqual([
      { op: 'replace', path: '/a~1b/c~0d', value: 2 },
    ])
  })

  it('无差异时返回空补丁', () => {
    expect(jsonDiff({ a: { b: 1 } }, { a: { b: 1 } })).toEqual([])
  })
})

describe('createHistory', () => {
  it('编辑后可撤销，撤销后可重做', () => {
    const mock = createMockMinder(doc())
    const history = createHistory(mock.minder)
    expect(history.hasUndo()).toBe(false)

    mock.edit((d) => {
      ;(d.root as { data: { text: string } }).data.text = '新标题'
    })
    expect(history.hasUndo()).toBe(true)

    history.undo()
    expect((mock.get().root as { data: { text: string } }).data.text).toBe('根')
    expect(history.hasUndo()).toBe(false)
    expect(history.hasRedo()).toBe(true)

    history.redo()
    expect((mock.get().root as { data: { text: string } }).data.text).toBe('新标题')
    expect(history.hasUndo()).toBe(true)
    expect(history.hasRedo()).toBe(false)
  })

  it('新编辑清空重做栈', () => {
    const mock = createMockMinder(doc())
    const history = createHistory(mock.minder)
    mock.edit((d) => {
      ;(d.root as { data: { text: string } }).data.text = 'A'
    })
    history.undo()
    expect(history.hasRedo()).toBe(true)
    mock.edit((d) => {
      ;(d.root as { data: { text: string } }).data.text = 'B'
    })
    expect(history.hasRedo()).toBe(false)
  })

  it('撤销栈上限 100，溢出丢弃最早步骤', () => {
    const mock = createMockMinder(doc())
    const history = createHistory(mock.minder)
    for (let i = 1; i <= 105; i++) {
      mock.edit((d) => {
        ;(d.root as { data: { text: string } }).data.text = `第${i}次`
      })
    }
    let undoCount = 0
    while (history.hasUndo()) {
      history.undo()
      undoCount++
    }
    expect(undoCount).toBe(100)
    // 最早 5 步已被丢弃，只能回退到第 5 次编辑后的状态
    expect((mock.get().root as { data: { text: string } }).data.text).toBe('第5次')
  })

  it('冻结期（远端回放）不入撤销栈且快照对齐', () => {
    const mock = createMockMinder(doc())
    let frozen = false
    const history = createHistory(mock.minder, { isFrozen: () => frozen })

    frozen = true
    mock.edit((d) => {
      ;(d.root as { data: { text: string } }).data.text = '远端改动'
    })
    frozen = false
    expect(history.hasUndo()).toBe(false)

    // 冻结期已对齐快照：随后的本地编辑撤销只回退到远端改动后的状态
    mock.edit((d) => {
      ;(d.root as { data: { text: string } }).data.text = '本地改动'
    })
    history.undo()
    expect((mock.get().root as { data: { text: string } }).data.text).toBe('远端改动')
  })

  it('import 重置撤销与重做栈', () => {
    const mock = createMockMinder(doc())
    const history = createHistory(mock.minder)
    mock.edit((d) => {
      ;(d.root as { data: { text: string } }).data.text = 'A'
    })
    expect(history.hasUndo()).toBe(true)
    mock.minder.importJson(doc())
    expect(history.hasUndo()).toBe(false)
    expect(history.hasRedo()).toBe(false)
  })

  it('onChange 回调驱动按钮可用态', () => {
    const mock = createMockMinder(doc())
    const states: [boolean, boolean][] = []
    // 经可变容器引用：createHistory 构造期的 reset 会立即触发 onChange，此时实例尚未返回
    const box: { history?: History } = {}
    box.history = createHistory(mock.minder, {
      onChange: () => {
        if (box.history) states.push([box.history.hasUndo(), box.history.hasRedo()])
      },
    })
    mock.edit((d) => {
      ;(d.root as { data: { text: string } }).data.text = 'A'
    })
    box.history.undo()
    expect(states.at(-1)).toEqual([false, true])
  })
})
