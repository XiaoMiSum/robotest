<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAiStore } from '@/stores/ai'
import AssistantPanel from './AssistantPanel.vue'

/**
 * 全局智能助手悬浮入口（详细设计 5.1 AssistantFab，交互设计 1.1/2）：
 * 右下角悬浮球，点击打开/关闭助手面板；支持拖拽移动（pointer 事件），
 * 位移超过阈值视为拖拽、不触发打开（拖拽与点击互斥）。
 *
 * 面板状态机：open=false 时面板卸载（重开经 onMounted 重载会话列表）；
 * minimized=true 时面板仅 v-show 隐藏保持挂载（进行中流式回复不中断），
 * 悬浮球重现。aiEnabled 显隐由 BusinessLayout 的 v-if 控制（设计 5.1），
 * 此处仅在开关关闭时提示进行中回复被中断（交互设计 8.3）。
 */
const open = ref(false)
const minimized = ref(false)

const aiStore = useAiStore()

// 开关关闭：提示中断（面板卸载由 BusinessLayout 的 v-if 触发，卸载时 cancel 流）
watch(
  () => aiStore.aiEnabled,
  (enabled) => {
    if (!enabled && (open.value || minimized.value)) {
      ElMessage.warning('AI 功能已关闭，当前回复已中断')
    }
  },
)

// ==================== 悬浮球拖拽（pointer 事件） ====================

const FAB_SIZE = 48
const DRAG_THRESHOLD = 4
const EDGE_MARGIN = 8

const fabEl = ref<HTMLElement | null>(null)
// 拖拽后的自由位置（left/top）；null 表示仍贴默认右下角（走 CSS right/bottom）
const pos = ref<{ x: number; y: number } | null>(null)

let dragStart: { pointerX: number; pointerY: number; rect: DOMRect } | null = null
let dragging = false

function onPointerDown(e: PointerEvent): void {
  const el = fabEl.value
  if (!el) return
  dragStart = { pointerX: e.clientX, pointerY: e.clientY, rect: el.getBoundingClientRect() }
  dragging = false
  el.setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent): void {
  if (!dragStart) return
  const dx = e.clientX - dragStart.pointerX
  const dy = e.clientY - dragStart.pointerY
  if (!dragging && Math.hypot(dx, dy) > DRAG_THRESHOLD) dragging = true
  if (dragging) {
    // 限制在视口内，避免拖出屏幕后无法找回
    pos.value = {
      x: clamp(dragStart.rect.left + dx, EDGE_MARGIN, window.innerWidth - FAB_SIZE - EDGE_MARGIN),
      y: clamp(dragStart.rect.top + dy, EDGE_MARGIN, window.innerHeight - FAB_SIZE - EDGE_MARGIN),
    }
  }
}

function onPointerUp(): void {
  if (!dragStart) return
  dragStart = null
  // 拖拽结束不触发打开；纯点击（位移未超阈值）才切换面板
  if (!dragging) togglePanel()
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

// ==================== 面板状态机（交互设计 2） ====================

function togglePanel(): void {
  if (minimized.value) {
    // 最小化 → 恢复面板：仅取消 v-show 隐藏，流式回复继续
    minimized.value = false
  } else {
    // 关闭态 → 打开：重新挂载面板，会话列表重载并恢复最近会话
    open.value = true
  }
}

function handleMinimize(): void {
  minimized.value = true
}

function handleClose(): void {
  open.value = false
  minimized.value = false
}

// 拖拽后以 left/top 定位覆盖默认 right/bottom
const fabStyle = computed((): Record<string, string> => {
  if (!pos.value) return {}
  return { left: `${pos.value.x}px`, top: `${pos.value.y}px` }
})
</script>

<template>
  <div
    v-show="!open || minimized"
    ref="fabEl"
    class="assistant-fab"
    :style="fabStyle"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
  >
    <span class="assistant-fab__icon">✨</span>
  </div>

  <AssistantPanel
    v-if="open"
    :minimized="minimized"
    @minimize="handleMinimize"
    @close="handleClose"
  />
</template>

<style scoped lang="scss">
.assistant-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-600));
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  cursor: grab;
  user-select: none;
  // 触屏拖拽时不触发页面滚动（pointer 事件接管）
  touch-action: none;
  z-index: 90;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);

  &:hover {
    transform: scale(1.05);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.24);
  }

  &:active {
    cursor: grabbing;
  }
}

.assistant-fab__icon {
  font-size: 22px;
  line-height: 1;
}
</style>
