import type { Minder, MinderEventHandler } from './types'
import type { Fsm } from './fsm'

/**
 * 键盘接收器（移植自 kityminder-editor 的 receiver runtime）：
 * 用隐藏的 contenteditable 元素统一承接键盘输入（含 IME 组合输入），
 * 打字、快捷键共用同一入口，再按状态机当前状态分发给各监听器。
 */
export interface ReceiverKeyEvent extends KeyboardEvent {
  is(keyExpression: string): boolean
}

export type ReceiverListener = (e: ReceiverKeyEvent) => unknown

export interface Receiver {
  element: HTMLDivElement
  selectAll(): void
  enable(): void
  disable(): void
  listen(state: string, listener: ReceiverListener): void
  onblur(handler: (e: FocusEvent) => void): void
  destroy(): void
}

// 键名到 KeyboardEvent.key 的映射，仅覆盖编辑内核用到的表达式
const NAMED_KEYS: Record<string, string> = {
  space: ' ',
  enter: 'enter',
  esc: 'escape',
  tab: 'tab',
  f2: 'f2',
  del: 'delete',
  backspace: 'backspace',
}

function matchKeyExpression(e: KeyboardEvent, expr: string): boolean {
  let ctrl = false
  let alt = false
  let shift = false
  let keyName = ''
  for (const part of expr.toLowerCase().split(/\s*\+\s*/)) {
    if (part === 'ctrl' || part === 'cmd') ctrl = true
    else if (part === 'alt') alt = true
    else if (part === 'shift') shift = true
    else keyName = NAMED_KEYS[part] ?? part
  }
  if ((e.ctrlKey || e.metaKey) !== ctrl || e.altKey !== alt || e.shiftKey !== shift) return false
  return e.key.toLowerCase() === keyName
}

export function createReceiver(container: HTMLElement, minder: Minder, fsm: Fsm): Receiver {
  const element = document.createElement('div')
  element.contentEditable = 'true'
  element.setAttribute('tabindex', '-1')
  element.classList.add('km-receiver')
  container.appendChild(element)

  const listeners: { state: string; listener: ReceiverListener }[] = []

  function dispatchKeyEvent(e: KeyboardEvent) {
    const event = e as ReceiverKeyEvent
    event.is = (keyExpression: string) => keyExpression.split('|').some((sub) => matchKeyExpression(event, sub))
    for (const { state, listener } of listeners) {
      if (state !== '*' && state !== fsm.state()) continue
      // 监听器返回真值表示事件已被消费，停止继续分发
      if (listener(event)) return
    }
  }
  element.onkeydown = dispatchKeyEvent
  element.onkeyup = dispatchKeyEvent

  function selectAll() {
    // 保持全选状态，下一次打字即整体替换旧内容（打字即编辑的关键）
    if (!element.innerHTML) element.innerHTML = '&nbsp;'
    const range = document.createRange()
    const selection = window.getSelection()
    range.selectNodeContents(element)
    selection?.removeAllRanges()
    selection?.addRange(range)
    // preventScroll：接收器跟随节点定位，可能位于可视区外，聚焦时不能拖动画布容器滚动
    element.focus({ preventScroll: true })
  }

  const focusReceiver: MinderEventHandler = () => selectAll()
  minder.on('beforemousedown', focusReceiver)
  minder.on('receiverfocus', focusReceiver)

  return {
    element,
    selectAll,
    enable() {
      element.setAttribute('contenteditable', 'true')
    },
    disable() {
      element.setAttribute('contenteditable', 'false')
    },
    listen(state, listener) {
      listeners.push({ state, listener })
    },
    onblur(handler) {
      element.onblur = handler
    },
    destroy() {
      minder.off('beforemousedown', focusReceiver)
      minder.off('receiverfocus', focusReceiver)
      element.remove()
    },
  }
}
