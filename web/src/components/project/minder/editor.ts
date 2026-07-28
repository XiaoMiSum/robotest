import type { Minder } from './types'
import { Fsm } from './fsm'
import { createReceiver, type Receiver } from './receiver'
import { createInput, type InputRuntime } from './input'
import { setupJumping } from './jumping'

/**
 * 编辑内核组装器（对应 kityminder-editor 的 editor.js）：
 * 创建 minder（禁用 core 自带 keyReceiver，由 receiver 统一接管键盘），
 * 组装 fsm / receiver / input / jumping 四个 runtime。
 * 仅 edit 模式使用；review/plan 只读模式仍直接创建裸 minder。
 */
export class KMEditor {
  readonly minder: Minder
  readonly fsm: Fsm
  readonly receiver: Receiver
  readonly input: InputRuntime

  constructor(container: HTMLElement, minderOptions: Record<string, unknown> = {}) {
    const km = window.kityminder
    if (!km) throw new Error('kityminder-core 尚未加载')
    this.minder = new km.Minder({
      renderTo: container,
      // 键盘统一走 contenteditable 接收器，禁用 core 内置的 keyReceiver
      enableKeyReceiver: false,
      ...minderOptions,
    })
    this.fsm = new Fsm('normal')
    this.receiver = createReceiver(container, this.minder, this.fsm)
    this.input = createInput({ minder: this.minder, fsm: this.fsm, receiver: this.receiver })
    setupJumping({ minder: this.minder, fsm: this.fsm, receiver: this.receiver, input: this.input })
  }

  /** 对当前选中节点开始原位编辑（工具栏/右键菜单/单击编辑入口） */
  editText(): void {
    this.input.editText()
  }

  destroy(): void {
    this.input.destroy()
    this.receiver.destroy()
    this.minder.destroy()
  }
}
