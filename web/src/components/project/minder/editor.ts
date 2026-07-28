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
