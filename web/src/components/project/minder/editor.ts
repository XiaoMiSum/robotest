import type { Minder } from './types'
import { Fsm } from './fsm'
import { createReceiver, type Receiver } from './receiver'
import { createInput, type InputRuntime } from './input'
import { createHistory, type History } from './history'
import { setupJumping } from './jumping'

/**
 * 编辑内核组装器（对应 kityminder-editor 的 editor.js）：
 * 创建 minder（禁用 core 自带 keyReceiver，由 receiver 统一接管键盘），
 * 组装 fsm / receiver / input / history / jumping 五个 runtime。
 * 仅 edit 模式使用；review/plan 只读模式仍直接创建裸 minder。
 */
export interface KMEditorOptions {
  minderOptions?: Record<string, unknown>
  /** 返回真值时内容变更不入撤销栈（如远端协同回放期） */
  historyFrozen?: () => boolean
  /** 撤销/重做栈变化回调，驱动工具栏按钮可用态 */
  onHistoryChange?: () => void
  /** 空格唤醒菜单回调（组件层展示右键菜单） */
  onMenuRequest?: () => void
}

export class KMEditor {
  readonly minder: Minder
  readonly fsm: Fsm
  readonly receiver: Receiver
  readonly input: InputRuntime
  readonly history: History

  constructor(container: HTMLElement, options: KMEditorOptions = {}) {
    const km = window.kityminder
    if (!km) throw new Error('kityminder-core 尚未加载')
    this.minder = new km.Minder({
      renderTo: container,
      // 键盘统一走 contenteditable 接收器，禁用 core 内置的 keyReceiver
      enableKeyReceiver: false,
      ...options.minderOptions,
    })
    this.fsm = new Fsm('normal')
    this.receiver = createReceiver(container, this.minder, this.fsm)
    this.input = createInput({ minder: this.minder, fsm: this.fsm, receiver: this.receiver })
    this.history = createHistory(this.minder, {
      isFrozen: options.historyFrozen,
      onChange: options.onHistoryChange,
    })
    setupJumping({
      minder: this.minder,
      fsm: this.fsm,
      receiver: this.receiver,
      input: this.input,
      history: this.history,
      onMenuRequest: options.onMenuRequest,
    })
    this.setupDragRebound()
  }

  /**
   * 拖拽落空自动回弹：core 的 dragtree 在未命中 drop/排序目标时不清除
   * 拖拽过程写入的 layout_*_offset 偏移，节点会悬停在被拖到的任意位置造成布局错乱。
   * 本产品为结构化用例树，不需要自由摆放，拖拽结束后统一清除残留偏移并重排。
   * 时序：statuschange（dragtree 离开）先于 core dragEnd 的 contentchange 触发，
   * 清理后数据即恢复拖拽前状态 → history 空 diff 不入栈、Yjs 同步到的是干净状态。
   */
  private setupDragRebound(): void {
    this.minder.on('statuschange', (e) => {
      if (e.lastStatus !== 'dragtree') return
      let dirty = false
      this.minder.getRoot().traverse((node) => {
        for (const key of Object.keys(node.data)) {
          if (/^layout_.+_offset$/.test(key)) {
            delete node.data[key]
            dirty = true
          }
        }
      })
      // 命中 drop/排序目标的拖拽 core 已自行清偏移，此处无残留则不必重排
      if (dirty) this.minder.layout(300)
    })
  }

  /** 对当前选中节点开始原位编辑（工具栏/右键菜单/单击编辑入口） */
  editText(): void {
    this.input.editText()
  }

  destroy(): void {
    this.history.destroy()
    this.input.destroy()
    this.receiver.destroy()
    this.minder.destroy()
  }
}
