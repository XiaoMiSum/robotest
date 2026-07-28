import type { Minder, MinderEventHandler, MinderNode, RenderShape } from './types'
import type { Fsm } from './fsm'
import type { Receiver } from './receiver'

/**
 * 原位内联编辑（移植自 kityminder-editor 的 input runtime，去掉了可见输入框外观）：
 * 进入 input 态时隐藏节点自身的 SVG 文本，接收器以相同字号/字色透明叠合在文本位置上，
 * 形成“直接在节点内打字”的观感；
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
  let hiddenTextShape: RenderShape | null = null

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

  function getTextShape(node: MinderNode): RenderShape | null {
    return node.getRenderer('TextRenderer')?.getRenderShape() ?? null
  }

  function enterInputMode() {
    const node = minder.getSelectedNode()
    if (!node) return
    // 字号随视图缩放、行高取主题值，保证与节点文本逐行重合
    const zoom = minder.getZoomValue() / 100
    const fontSize = Number(node.getData('font-size') ?? node.getStyle('font-size') ?? 14)
    const lineHeight = Number(node.getStyle('line-height') ?? 1.4)
    element.style.fontSize = `${fontSize * zoom}px`
    element.style.lineHeight = String(lineHeight)
    // 隐藏节点自身文本，接收器用相同字色透明叠合，避免双重文字/输入框观感
    const shape = getTextShape(node)
    if (shape) {
      element.style.color = window.getComputedStyle(shape.node).fill || ''
      shape.setOpacity(0)
      hiddenTextShape = shape
    }
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
    // 必须先恢复可见再提交：execCommand('text') 复用同一 textGroup，不恢复会导致节点文字持续隐形
    hiddenTextShape?.setOpacity(1)
    hiddenTextShape = null
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
      // zoom 经 paper viewBox 实现，'paper' 参照系不含缩放变换，
      // 必须取 'screen' 坐标再换算回容器坐标才能在任意缩放下贴合
      const box = node.getRenderBox('TextRenderer', 'screen')
      const rect = minder.getRenderTarget().getBoundingClientRect()
      // SVG 文本盒顶部即首行文字顶部，而 HTML 行盒含半行距，向上补偿使两者逐行重合
      const zoom = minder.getZoomValue() / 100
      const fontSize = Number(node.getData('font-size') ?? node.getStyle('font-size') ?? 14)
      const lineHeight = Number(node.getStyle('line-height') ?? 1.4)
      const halfLeading = ((lineHeight - 1) / 2) * fontSize * zoom
      element.style.left = `${Math.round(box.x - rect.left)}px`
      element.style.top = `${Math.round(box.y - rect.top - halfLeading)}px`
    })
  }

  return {
    editText,
    destroy() {
      if (positionTimer) window.clearTimeout(positionTimer)
      hiddenTextShape?.setOpacity(1)
      hiddenTextShape = null
      minder.off('beforemousedown', commitBeforeMousedown)
      minder.off('dblclick', editOnDblclick)
      minder.off(followEvents, follow)
    },
  }
}
