import type { Minder } from './types'
import type { Fsm } from './fsm'
import type { Receiver, ReceiverKeyEvent } from './receiver'
import type { InputRuntime } from './input'
import type { History } from './history'
import { copySelected, cutSelected, pasteToSelected } from './clipboard'

/**
 * 键盘导流（移植自 kityminder-editor 的 jumping runtime，去掉了打字即编辑）：
 * normal 态下 F2 进入编辑（双击编辑由 input runtime 监听），普通打字吞掉；
 * Tab/Enter 新建子/兄弟节点（携默认名称，core 原生快捷键不带文本会产生空节点）；
 * 空格唤醒菜单（回调由组件层注入，内核不感知菜单 UI）；
 * 其余按键转发给 core 的键盘系统（Delete 删除、方向键导航等）。
 * input 态下 Enter 提交、Esc 取消、Tab 拦截（避免焦点跳出接收器）。
 */

// 新建节点的默认名称，快捷键/工具栏/右键菜单共用，保证各入口行为一致
export const DEFAULT_NODE_TEXT = '分支主题'

// 判断按键意图是否为“输入文字”：字母、数字、小键盘（除除号）、IME 组合键
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
  /** 空格唤醒菜单（组件层展示右键菜单，仅选中节点时触发） */
  onMenuRequest?: () => void
}): void {
  const { minder, fsm, receiver, input, history, onMenuRequest } = options

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
      // 复制/剪切/粘贴走应用内剪贴板（clipboard.ts）；只在 normal 态拦截，
      // input 编辑态的原生文本剪贴板不受影响
      if (e.is('ctrl + c')) {
        e.preventDefault()
        copySelected(minder)
        return true
      }
      if (e.is('ctrl + x')) {
        e.preventDefault()
        // 根节点剪切被拒绝时静默（菜单入口有置灰与提示）
        cutSelected(minder)
        return true
      }
      if (e.is('ctrl + v')) {
        // 必须拦截，否则系统剪贴板文本会被粘进 contenteditable 接收器
        e.preventDefault()
        pasteToSelected(minder)
        return true
      }
      // core 原生 Tab/Enter 快捷键不带文本会产生空节点，这里拦截并携默认名称
      if (e.is('tab')) {
        e.preventDefault()
        minder.execCommand('AppendChildNode', DEFAULT_NODE_TEXT)
        return true
      }
      if (e.is('enter')) {
        e.preventDefault()
        minder.execCommand('AppendSiblingNode', DEFAULT_NODE_TEXT)
        return true
      }
      if (e.is('space')) {
        e.preventDefault()
        onMenuRequest?.()
        return true
      }
      if (isIntendToInput(e)) {
        // 编辑需双击/F2 显式激活，普通打字吞掉，避免字符污染隐藏的接收器
        e.preventDefault()
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
