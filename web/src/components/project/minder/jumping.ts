import type { Minder } from './types'
import type { Fsm } from './fsm'
import type { Receiver, ReceiverKeyEvent } from './receiver'
import type { InputRuntime } from './input'
import type { History } from './history'

/**
 * 键盘导流（移植自 kityminder-editor 的 jumping runtime）：
 * normal 态下选中节点直接打字即进入编辑（打字即编辑）、F2 编辑；
 * 其余按键转发给 core 的键盘系统（Tab 插入子节点、Enter 插入兄弟、Delete 删除、方向键导航等）。
 * input 态下 Enter 提交、Esc 取消、Tab 拦截（避免焦点跳出接收器）。
 */

// 判断按键意图是否为“开始输入文字”：字母、数字、小键盘（除除号）、IME 组合键
function isIntendToInput(e: KeyboardEvent): boolean {
  if (e.ctrlKey || e.metaKey || e.altKey) return false
  const keyCode = e.keyCode
  if (keyCode >= 65 && keyCode <= 90) return true
  if (keyCode >= 48 && keyCode <= 57) return true
  if (keyCode !== 108 && keyCode >= 96 && keyCode <= 111) return true
  // 229/0：IME 输入法组合按键
  if (keyCode === 229 || keyCode === 0) return true
  return false
}

export function setupJumping(options: {
  minder: Minder
  fsm: Fsm
  receiver: Receiver
  input: InputRuntime
  history: History
}): void {
  const { minder, fsm, receiver, input, history } = options

  receiver.listen('normal', (e: ReceiverKeyEvent) => {
    receiver.enable()
    if (e.type !== 'keydown') return
    // 撤销/重做由自建 history 实现（core 无 Undo/Redo 命令），不能进 dispatchKeyEvent
    if (e.is('ctrl + z')) {
      e.preventDefault()
      history.undo()
      return true
    }
    if (e.is('ctrl + y|ctrl + shift + z')) {
      e.preventDefault()
      history.redo()
      return true
    }
    if (minder.getSelectedNode()) {
      if (e.is('f2')) {
        e.preventDefault()
        input.editText()
        return true
      }
      if (isIntendToInput(e)) {
        fsm.jump('input', 'user-input')
        return true
      }
    } else {
      // 无选中节点时清空接收器，避免残留字符被 IME 组合
      receiver.element.innerHTML = ''
    }
    // 交给 core 键盘系统处理（Tab/Enter/Delete/方向键等原生快捷键）
    minder.dispatchKeyEvent(e)
    return true
  })

  receiver.listen('input', (e: ReceiverKeyEvent) => {
    receiver.enable()
    if (e.type === 'keydown') {
      if (e.is('enter')) {
        e.preventDefault()
        fsm.jump('normal', 'input-commit')
        return true
      }
      if (e.is('esc')) {
        e.preventDefault()
        fsm.jump('normal', 'input-cancel')
        return true
      }
      // Tab 在 contenteditable 中会移动焦点，编辑态直接吞掉
      if (e.is('tab|shift + tab')) {
        e.preventDefault()
        return true
      }
    } else if (e.type === 'keyup' && e.is('esc')) {
      fsm.jump('normal', 'input-cancel')
      return true
    }
    return false
  })
}
