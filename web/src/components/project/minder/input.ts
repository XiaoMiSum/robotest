import type { Minder, MinderEventHandler } from './types'
import type { Fsm } from './fsm'
import type { Receiver } from './receiver'

/**
 * 原位内联编辑（移植自 kityminder-editor 的 input runtime）：
 * 接收器元素定位到选中节点的文本渲染框上，进入 input 态时显示为输入框；
 * Enter/失焦提交（经 execCommand('text') 触发 contentchange，走同步与落库管道），
 * Esc 取消；布局/视图/选区变化时跟随重定位。
 */
export interface InputRuntime {
  editText(): void
  destroy(): void
}

export function createInput(options: { minder: Minder; fsm: Fsm; receiver: Receiver }): InputRuntime {
  const { minder, fsm, receiver } = options
  const element = receiver.element
  let positionTimer = 0

  // 输入框内的点击不能冒泡为画布 mousedown，否则会触发提交并切换选区
  element.onmousedown = (e) => e.stopPropagation()

  fsm.when('* -> input', enterInputMode)
  fsm.when('input -> *', (_exit, _enter, reason) => {
    if (reason === 'input-cancel') exitInputMode()
    else commitInputResult()
  })

  receiver.onblur(() => {
    if (fsm.state() === 'input') fsm.jump('normal', 'input-commit')
  })

  const commitBeforeMousedown: MinderEventHandler = () => {
    // core 在 mousedown 之后才变更选区，此刻提交仍作用于原节点
    if (fsm.state() === 'input') fsm.jump('normal', 'input-commit')
  }
  minder.on('beforemousedown', commitBeforeMousedown)

  const editOnDblclick: MinderEventHandler = () => {
    if (minder.getSelectedNode() && minder.getStatus() !== 'readonly') editText()
  }
  minder.on('dblclick', editOnDblclick)

  const followEvents = 'layoutallfinish viewchange viewchanged selectionchange'
  const follow: MinderEventHandler = (e) => {
    // viewchange 触发过于频繁，非编辑态无需实时跟随
    if (e.type === 'viewchange' && fsm.state() !== 'input') return
    updatePosition()
  }
  minder.on(followEvents, follow)

  function editText() {
    const node = minder.getSelectedNode()
    if (!node) return
    element.innerText = String(minder.queryCommandValue('text') ?? '')
    fsm.jump('input', 'input-request')
    receiver.selectAll()
  }

  function enterInputMode() {
    const node = minder.getSelectedNode()
    if (!node) return
    // 输入框字号与节点文本一致，保证原位编辑的视觉连续性
    const fontSize = Number(node.getData('font-size') ?? node.getStyle('font-size') ?? 14)
    element.style.fontSize = `${fontSize}px`
    element.style.minWidth = '0'
    element.style.minWidth = `${element.clientWidth}px`
    element.classList.add('input')
    element.focus({ preventScroll: true })
    updatePosition()
  }

  function commitInputResult() {
    // contenteditable 中的 &nbsp; 与首尾换行是编辑痕迹，非用户内容
    const text = element.innerText.replace(/\u00a0/g, ' ').replace(/^\n+|\n+$/g, '')
    const node = minder.getSelectedNode()
    const origin = String(minder.queryCommandValue('text') ?? '')
    // 延迟清空规避提交瞬间选区读取异常（沿用 kityminder-editor 的实践）
    window.setTimeout(() => {
      element.innerHTML = ''
    }, 0)
    exitInputMode()
    // 空文本视为取消，避免误清空节点标题；未变更不产生撤销步骤与落库
    if (!node || !text.trim() || text === origin) return
    minder.execCommand('text', text)
  }

  function exitInputMode() {
    element.classList.remove('input')
    receiver.selectAll()
  }

  function updatePosition() {
    // 合并同一帧内的多次重定位请求
    if (positionTimer) return
    positionTimer = window.setTimeout(() => {
      positionTimer = 0
      const node = minder.getSelectedNode()
      if (!node) return
      // 默认以 paper 为参照系（含视图平移/缩放变换），坐标即画布容器内像素；
      // 不可传 DOM 元素作 refer：kity.Matrix.getCTM 只认 kity 对象，否则退化为单位矩阵导致定位落在左上角
      const box = node.getRenderBox('TextRenderer')
      element.style.left = `${Math.round(box.x)}px`
      element.style.top = `${Math.round(box.y)}px`
    })
  }

  return {
    editText,
    destroy() {
      if (positionTimer) window.clearTimeout(positionTimer)
      minder.off('beforemousedown', commitBeforeMousedown)
      minder.off('dblclick', editOnDblclick)
      minder.off(followEvents, follow)
    },
  }
}
