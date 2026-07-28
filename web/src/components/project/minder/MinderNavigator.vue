<script setup lang="ts">
/**
 * 脑图导航器（写法参照 kityminder-editor 的 navigator）：
 * 缩放条 / 定位根节点 / 抓手 / 缩略图 / 全屏，悬浮于画布左下角。
 * 缩放、抓手、定位命令均 enableReadOnly，三种模式（edit/review/plan）共用。
 */
import { onMounted, onBeforeUnmount, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { NavMinder, KityBox, KityPoint, MinderEvent } from './types'

const props = defineProps<{
  // kity 实例经 shallowRef 透传，此处收窄为导航器所需的视图层接口
  minder: unknown
}>()

const m = props.minder as NavMinder

const zoom = ref(100)
const handActive = ref(false)
const isNavOpen = ref(window.localStorage.getItem('minder-navigator-open') !== 'false')
const isFullscreen = ref(false)
const rootRef = ref<HTMLDivElement>()
const previewRef = ref<HTMLDivElement>()

// ==================== 缩放条 ====================
// 与缩放条 CSS 高度保持一致（SVG 前无法测量 DOM，用常量避免布局抖动）
const PAN_HEIGHT = 66

function zoomRatio(value: number): number {
  const stack = m.getOption('zoom') as number[] | undefined
  if (!stack?.length) return 0.5
  const min = stack[0]
  const max = stack[stack.length - 1]
  return 1 - (value - min) / (max - min)
}

const indicatorY = computed(() => zoomRatio(zoom.value) * PAN_HEIGHT)
const originY = computed(() => zoomRatio(100) * PAN_HEIGHT)
const atMaxZoom = computed(() => zoomRatio(zoom.value) === 0)
const atMinZoom = computed(() => zoomRatio(zoom.value) === 1)

function zoomIn() { m.execCommand('zoom-in') }
function zoomOut() { m.execCommand('zoom-out') }
function restoreSize() { m.execCommand('zoom', 100) }

function onZoomEvent(e: MinderEvent) {
  zoom.value = e.zoom ?? 100
}

// ==================== 定位 / 抓手 / 全屏 ====================
function locateRoot() {
  m.execCommand('camera', m.getRoot(), 600)
}

function toggleHand() {
  m.execCommand('hand')
}

function onStatusChange() {
  handActive.value = m.queryCommandState('hand') === 1
}

// 原生 Fullscreen API（零依赖）：对最近的脑图容器全屏，保留工具栏
function toggleFullscreen() {
  const target = rootRef.value?.closest('.mindmap-container') ?? document.documentElement
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    target.requestFullscreen?.().catch(() => ElMessage.error('您的浏览器不支持全屏操作'))
  }
}

function onFullscreenChange() {
  isFullscreen.value = Boolean(document.fullscreenElement)
}

// ==================== 缩略图 ====================
// kity 全局对象的最小结构化类型（C1：禁止 any）
interface KityShape {
  fill(color: unknown): KityShape
  stroke(color: unknown, width?: string): KityShape
  setPathData(data: unknown): KityShape
  setBox(box: KityBox): KityShape
}

interface KityPaperEvent {
  getPosition(refer: string): KityPoint
}

interface KityPaper {
  put<T>(shape: T): T
  setViewBox(x: number, y: number, width: number, height: number): void
  setStyle(name: string, value: unknown): void
  on(type: string, handler: (e: KityPaperEvent) => void): void
  remove(): void
}

interface NavKityStatic {
  Paper: new (container: HTMLElement) => KityPaper
  Path: new () => KityShape
  Rect: new (width?: number, height?: number) => KityShape
  Box: new () => KityBox
}

let paper: KityPaper | null = null
let nodeThumb: KityShape | null = null
let connectionThumb: KityShape | null = null
let visibleRect: KityShape | null = null
let contentView: KityBox | null = null
let visibleView: KityBox | null = null
let dragging = false

function updateContentView() {
  if (!paper || !nodeThumb || !connectionThumb) return
  const view = m.getRenderContainer().getBoundaryBox()
  contentView = view
  const padding = 30
  paper.setViewBox(view.x - padding - 0.5, view.y - padding - 0.5, view.width + padding * 2 + 1, view.height + padding * 2 + 1)

  const nodePathData: unknown[] = []
  const connectionPathData: unknown[] = []
  m.getRoot().traverse((node) => {
    const box = node.getLayoutBox()
    nodePathData.push('M', box.x, box.y, 'h', box.width, 'v', box.height, 'h', -box.width, 'z')
    const connection = node.getConnection()
    if (connection && node.parent && node.parent.isExpanded()) {
      connectionPathData.push(connection.getPathData())
    }
  })
  paper.setStyle('background', m.getStyle('background'))
  if (nodePathData.length) {
    nodeThumb.fill(m.getStyle('root-background')).setPathData(nodePathData)
  } else {
    nodeThumb.setPathData(null)
  }
  if (connectionPathData.length) {
    connectionThumb.stroke(m.getStyle('connect-color'), '0.5%').setPathData(connectionPathData)
  } else {
    connectionThumb.setPathData(null)
  }
  updateVisibleView()
}

function updateVisibleView() {
  if (!visibleRect || !contentView) return
  visibleView = m.getViewDragger().getView()
  visibleRect.setBox(visibleView.intersect(contentView))
}

// 点击/拖动缩略图，把画布视野中心移动到对应位置
function moveView(center: KityPoint, duration?: number) {
  if (!visibleView) return
  center.x = -center.x
  center.y = -center.y
  const box = m.getPaper().getViewPortMatrix().transformBox(visibleView)
  m.getViewDragger().moveTo(center.offset(box.width / 2, box.height / 2), duration)
}

function bindNav() {
  m.on('layout layoutallfinish', updateContentView)
  m.on('viewchange', updateVisibleView)
}

function unbindNav() {
  m.off('layout layoutallfinish', updateContentView)
  m.off('viewchange', updateVisibleView)
}

function setupPreviewer(): boolean {
  const kity = window.kity as NavKityStatic | undefined
  if (!kity || !previewRef.value) return false
  paper = new kity.Paper(previewRef.value)
  nodeThumb = paper.put(new kity.Path())
  connectionThumb = paper.put(new kity.Path())
  visibleRect = paper.put(new kity.Rect(100, 100).stroke('red', '1%') as KityShape)
  contentView = new kity.Box()
  visibleView = new kity.Box()

  paper.on('mousedown', (e) => {
    dragging = true
    moveView(e.getPosition('top'), 200)
  })
  paper.on('mousemove', (e) => {
    if (dragging) moveView(e.getPosition('top'))
  })
  return true
}

function onWindowMouseUp() {
  dragging = false
}

function toggleNavOpen() {
  isNavOpen.value = !isNavOpen.value
  window.localStorage.setItem('minder-navigator-open', String(isNavOpen.value))
  if (isNavOpen.value) {
    // v-show 切换后需等 DOM 可见才能正确计算缩略图视口
    requestAnimationFrame(() => {
      if (!paper) setupPreviewer()
      bindNav()
      updateContentView()
    })
  } else {
    unbindNav()
  }
}

// ==================== 生命周期 ====================
onMounted(() => {
  m.on('zoom', onZoomEvent)
  m.on('statuschange', onStatusChange)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  window.addEventListener('mouseup', onWindowMouseUp)
  if (isNavOpen.value && setupPreviewer()) {
    bindNav()
    updateContentView()
  }
})

onBeforeUnmount(() => {
  m.off('zoom', onZoomEvent)
  m.off('statuschange', onStatusChange)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.removeEventListener('mouseup', onWindowMouseUp)
  unbindNav()
  paper?.remove()
  paper = null
})
</script>

<template>
  <div ref="rootRef" class="minder-navigator">
    <div class="nav-bar">
      <button class="nav-btn" :class="{ disabled: atMaxZoom }" title="放大" @click="zoomIn">＋</button>
      <div class="zoom-pan">
        <div class="origin" :style="{ transform: `translateY(${originY}px)` }" title="回到 100%" @click="restoreSize" />
        <div class="indicator" :style="{ transform: `translateY(${indicatorY}px)` }" />
      </div>
      <button class="nav-btn" :class="{ disabled: atMinZoom }" title="缩小" @click="zoomOut">－</button>
      <div class="nav-divider" />
      <button class="nav-btn" :class="{ active: handActive }" title="抓手（拖拽画布）" @click="toggleHand">✋</button>
      <button class="nav-btn" title="定位根节点" @click="locateRoot">◎</button>
      <button class="nav-btn" :class="{ active: isNavOpen }" title="缩略图导航" @click="toggleNavOpen">🗺</button>
      <button class="nav-btn" :title="isFullscreen ? '退出全屏' : '全屏'" @click="toggleFullscreen">⛶</button>
    </div>
    <div v-show="isNavOpen" ref="previewRef" class="nav-previewer" />
  </div>
</template>

<style scoped lang="scss">
.minder-navigator {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 15;
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.nav-bar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 4px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  box-shadow: var(--el-box-shadow-light);
}

.nav-btn {
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color-light);
  }

  &.active {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }

  &.disabled {
    color: var(--el-text-color-disabled);
    cursor: default;
  }
}

.zoom-pan {
  position: relative;
  width: 26px;
  /* 高度与脚本内 PAN_HEIGHT 常量对应 */
  height: 66px;
  margin: 2px 0;

  &::before {
    content: '';
    position: absolute;
    left: 50%;
    top: 0;
    bottom: 0;
    width: 2px;
    margin-left: -1px;
    background: var(--el-border-color);
    border-radius: 1px;
  }
}

.origin {
  position: absolute;
  left: 50%;
  margin-left: -4px;
  width: 8px;
  height: 2px;
  background: var(--el-text-color-secondary);
  cursor: pointer;
}

.indicator {
  position: absolute;
  left: 50%;
  margin-left: -5px;
  margin-top: -4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--el-color-primary);
  transition: transform 200ms;
  pointer-events: none;
}

.nav-divider {
  width: 18px;
  height: 1px;
  margin: 2px 0;
  background: var(--el-border-color-lighter);
}

.nav-previewer {
  width: 160px;
  height: 120px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  box-shadow: var(--el-box-shadow-light);
  overflow: hidden;
  cursor: crosshair;

  :deep(svg) {
    width: 100%;
    height: 100%;
  }
}
</style>
