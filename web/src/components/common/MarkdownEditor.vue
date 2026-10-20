<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import 'vditor/dist/index.css'
import {
  normalizeMarkdownImageUrls,
  resolveMarkdownImages,
  uploadMarkdownImages,
} from '@/composables/common/markdownImage'
import {
  createEditorOptions,
  DEFAULT_PREVIEW_MAX_WIDTH,
  setEditorHeight,
  setPreviewMaxWidth,
  type VditorInstance,
} from '@/composables/common/vditorEditor'

// 薄封装 Vditor 所见即所得编辑器：全站统一 Markdown 编辑体验，对外接口保持不变
const value = defineModel<string>({ default: '' })

const props = withDefaults(
  defineProps<{
    height?: string
    placeholder?: string
    disabled?: boolean
    /** 开启后工具栏最右侧追加「预览」按钮，点击将编辑器最大化为带遮罩浮层 */
    maximizable?: boolean
  }>(),
  {
    height: '320px',
    placeholder: '支持 Markdown 语法',
    disabled: false,
    maximizable: false,
  },
)

/** Element Plus FullScreen 图标，作为自定义工具栏按钮图标 */
const MAXIMIZE_ICON =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">' +
  '<path fill="currentColor" d="m160 96.064 192 .192a32 32 0 0 1 0 64l-192-.192V352a32 32 0 0 1-64 0V96h64zm0 831.872V928H96V672a32 32 0 1 1 64 0v191.936l192-.192a32 32 0 1 1 0 64zM864 96.064V96h64v256a32 32 0 1 1-64 0V160.064l-192 .192a32 32 0 1 1 0-64zm0 831.872-192-.192a32 32 0 0 1 0-64l192 .192V672a32 32 0 1 1 64 0v256h-64z"/>' +
  '<path fill="currentColor" d="M256 320h512c0-78.592-12.608-142.4-36.928-192h-434.24C269.504 192.384 256 256.256 256 320m503.936 64H264.064a256.128 256.128 0 0 0 495.872 0M544 638.4V896h96a32 32 0 1 1 0 64H384a32 32 0 1 1 0-64h96V638.4A320 320 0 0 1 192 320c0-85.632 21.312-170.944 64-256h512c42.688 64.32 64 149.632 64 256a320 320 0 0 1-288 318.4"/>' +
  '</svg>'

const maximized = ref(false)

// 拖拽调高后的值优先于页面传入的自适应高度：本次停留期间不再跟随窗口变化（不持久化，卸载即还原）
const internalHeight = ref(props.height)
const userAdjusted = ref(false)

function enterMaximize(): void {
  maximized.value = true
}

function exitMaximize(): void {
  maximized.value = false
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') exitMaximize()
}

watch(maximized, (on) => {
  document.removeEventListener('keydown', handleKeydown)
  if (on) {
    document.addEventListener('keydown', handleKeydown)
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
  applyMaximizeHeight(on)
  // 最大化时放宽正文居中上限使内容撑满浮层；Vditor 监听 window resize 重算正文左右留白，
  // 等浮层样式生效后再触发，退出时同理还原为默认宽度下的居中排版
  if (instance) {
    setPreviewMaxWidth(instance, on ? Number.MAX_SAFE_INTEGER : DEFAULT_PREVIEW_MAX_WIDTH)
  }
  void nextTick(() => window.dispatchEvent(new Event('resize')))
})

const container = useTemplateRef<HTMLDivElement>('container')

// 挂载元素即 .vditor 本体（Vditor 在其上追加类名与内联高度）：高度切换只能落在它自己的内联样式上；
// 不能以 :class 驱动挂载元素，Vue 重写 class 属性时会抹掉 Vditor 追加的 .vditor 类致布局失效
function applyMaximizeHeight(on: boolean): void {
  if (!container.value) return
  container.value.style.height = on ? '100%' : internalHeight.value
}

let instance: VditorInstance | undefined
let stopResolve: (() => void) | undefined
// 构造后要先异步加载 i18n/lute 资源，就绪前实例方法不可用，一切依赖等 after 回调
let ready = false
let disposed = false

// ==================== 完整显示（内容高度自适应） ====================

const expanded = ref(false)
const overflowing = ref(false)

// 完整显示入口默认开放：仅内容实际显示不全时出现；展开后按钮常驻为「收起」直至手动还原
const showExpandControl = computed(
  () => !props.disabled && !maximized.value && (expanded.value || overflowing.value),
)

/** 展开高度 = 正文滚动高度 + 工具栏高度 + 2px 缓冲；内容增长时按最新滚动高度重算 */
function expandedHeightPx(): number | null {
  const area = container.value?.querySelector('.vditor-wysiwyg')
  if (!(area instanceof HTMLElement)) return null
  const toolbar = container.value?.querySelector('.vditor-toolbar')
  return area.scrollHeight + (toolbar instanceof HTMLElement ? toolbar.offsetHeight : 0) + 2
}

function applyExpandHeight(): void {
  if (!instance) return
  const height = expandedHeightPx()
  if (height !== null) setEditorHeight(instance, `${Math.ceil(height)}px`)
}

// 就绪后才量得到真实布局；禁用与最大化态不参与，按钮此时也不渲染
function measureOverflow(): void {
  if (!ready || maximized.value || props.disabled) {
    overflowing.value = false
    return
  }
  const area = container.value?.querySelector('.vditor-wysiwyg')
  overflowing.value = area instanceof HTMLElement && area.scrollHeight > area.clientHeight + 1
  if (expanded.value) applyExpandHeight()
}

function toggleExpand(): void {
  expanded.value = !expanded.value
  if (!ready || !instance) return
  if (expanded.value) applyExpandHeight()
  else setEditorHeight(instance, internalHeight.value)
}

// ==================== 拖拽调高（底部手柄） ====================

const MIN_RESIZE_HEIGHT = 200
// 无布局环境（如 jsdom）量不到真实高度时的兜底值
const RESIZE_HEIGHT_FALLBACK = 320

const resizing = ref(false)
let resizeStartY = 0
let resizeStartHeight = 0

/** 手柄仅编辑态可用：只读、最大化浮层与完整显示展开态（高度由内容决定）均不渲染 */
const showResizeHandle = computed(
  () => !props.disabled && !maximized.value && !expanded.value,
)

function parseHeightPx(height: string): number {
  const parsed = Number.parseInt(height, 10)
  return Number.isNaN(parsed) ? RESIZE_HEIGHT_FALLBACK : parsed
}

function handleResizeMouseDown(event: MouseEvent): void {
  if (!ready || !instance) return
  event.preventDefault()
  resizing.value = true
  resizeStartY = event.clientY
  resizeStartHeight = container.value?.offsetHeight || parseHeightPx(internalHeight.value)
  document.addEventListener('mousemove', handleResizeMouseMove)
  document.addEventListener('mouseup', handleResizeMouseUp)
  document.body.style.cursor = 'row-resize'
  document.body.style.userSelect = 'none'
}

function handleResizeMouseMove(event: MouseEvent): void {
  if (!resizing.value || !instance) return
  const next = Math.min(
    Math.max(resizeStartHeight + (event.clientY - resizeStartY), MIN_RESIZE_HEIGHT),
    window.innerHeight,
  )
  internalHeight.value = `${next}px`
  setEditorHeight(instance, `${next}px`)
}

function handleResizeMouseUp(): void {
  resizing.value = false
  document.removeEventListener('mousemove', handleResizeMouseMove)
  document.removeEventListener('mouseup', handleResizeMouseUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  userAdjusted.value = true
  // 拖完重测溢出，让「完整显示」按钮状态跟随新高度
  measureOverflow()
}

function stopResizeListening(): void {
  if (!resizing.value) return
  resizing.value = false
  document.removeEventListener('mousemove', handleResizeMouseMove)
  document.removeEventListener('mouseup', handleResizeMouseUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onMounted(() => {
  // 编辑区宽度随窗口变化，行数重排后溢出情况需要重新测量
  window.addEventListener('resize', measureOverflow)
  void (async () => {
    // 内核按需分包，不进首屏 bundle
    const { default: Vditor } = await import('vditor')
    if (disposed || !container.value) return
    instance = new Vditor(
      container.value,
      createEditorOptions({
        height: props.height,
        placeholder: props.placeholder,
        value: value.value,
        // 自定义按钮追加在工具栏末尾，即最右侧（交互 06 §2.5.2）
        extraMenuItems: props.maximizable
          ? [
              {
                name: 'preview-maximize',
                tip: '预览',
                tipPosition: 's',
                icon: MAXIMIZE_ICON,
                className: 'markdown-editor__maximize-btn',
                click: () => enterMaximize(),
              },
            ]
          : undefined,
        onReady: handleReady,
        onInput: (markdown) => {
          value.value = markdown
        },
        onUpload: handleUpload,
      }),
    )
  })()
})

function handleReady(): void {
  if (disposed) {
    instance?.destroy()
    instance = undefined
    return
  }
  ready = true
  if (props.disabled) instance?.disabled()
  // 构造到就绪之间 v-model 可能被页面更新，以最新值为准
  if (instance && normalizeMarkdownImageUrls(instance.getValue()) !== value.value) {
    instance.setValue(value.value)
  }
  if (container.value) stopResolve = resolveMarkdownImages(container.value)
  measureOverflow()
}

// Vditor 自定义 handler 不做校验也不回插：前置校验、上传、按原顺序插入图片引用都在此完成
async function handleUpload(files: File[]): Promise<void> {
  const images = await uploadMarkdownImages(files)
  if (images.length === 0 || !instance || !ready) return
  const markdown = images
    .map((image) => `![${image.name.replace(/[[\]]/g, '')}](${image.downloadUrl})`)
    .join('\n')
  instance.insertMD(markdown)
}

watch(value, (next) => {
  if (!ready || !instance) return
  // 编辑器 DOM 内是换签后的地址，归一化后再比较，避免回填打断正在输入的内容
  if (normalizeMarkdownImageUrls(instance.getValue()) !== next) instance.setValue(next)
  measureOverflow()
})

watch(() => props.disabled, (disabled) => {
  if (!ready || !instance) return
  if (disabled) instance.disabled()
  else instance.enable()
  measureOverflow()
})

// 外部驱动的高度变化（如页面按窗口高度自适应）需同步到实例；最大化、展开与拖拽后的值由各自逻辑接管，退出时按最新值还原
watch(() => props.height, (height) => {
  if (!ready || !instance || maximized.value || expanded.value) return
  if (userAdjusted.value) return
  internalHeight.value = height
  setEditorHeight(instance, height)
  measureOverflow()
})

onBeforeUnmount(() => {
  disposed = true
  stopResolve?.()
  stopResolve = undefined
  window.removeEventListener('resize', measureOverflow)
  stopResizeListening()
  // 最大化状态卸载时恢复背景滚动，避免样式残留
  document.removeEventListener('keydown', handleKeydown)
  if (maximized.value) {
    maximized.value = false
    document.body.style.overflow = ''
  }
  if (ready) {
    instance?.destroy()
    instance = undefined
    ready = false
  }
  // 未就绪时保留实例，由 after 回调自行销毁
})
</script>

<template>
  <!-- 遮罩衬在最大化浮层之下，层级低于编辑器（2999 / 3000），高于 Element Plus 弹层（2000+） -->
  <teleport to="body">
    <div v-if="maximized" class="markdown-editor__mask" @click="exitMaximize" />
  </teleport>
  <!-- 最大化态类挂在包裹层：直接以 :class 驱动挂载元素会抹掉 Vditor 追加的 .vditor 类；包裹层同时保证编辑器与表单其余控件同宽 -->
  <div class="markdown-editor-wrap" :class="{ 'markdown-editor-wrap--maximized': maximized }">
    <div ref="container" class="markdown-editor" />
    <!-- 高度拖拽热区：悬浮显示指示线，拖拽行为见上方「拖拽调高」逻辑 -->
    <div
      v-if="showResizeHandle"
      class="markdown-editor-resize"
      :class="{ 'markdown-editor-resize--active': resizing }"
      @mousedown.prevent="handleResizeMouseDown"
    />
  </div>
  <!-- 按钮在包裹层之外：最大化时包裹层变浮层，显隐由 showExpandControl 单独控制 -->
  <div v-if="showExpandControl" class="markdown-editor-expand">
    <el-button link type="primary" size="small" @click="toggleExpand">
      {{ expanded ? '收起' : '完整显示' }}
    </el-button>
  </div>
</template>

<style scoped lang="scss">
.markdown-editor-wrap {
  position: relative;
  width: 100%;
}

.markdown-editor-wrap--maximized {
  position: fixed;
  // 70% 视口宽高居中（top/left 各留 15%）
  top: 15%;
  left: 15%;
  width: 70%;
  height: 70%;
  z-index: 3000;
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
}

.markdown-editor-resize {
  height: 6px;
  cursor: row-resize;

  &::after {
    content: '';
    display: block;
    height: 2px;
    margin-top: 2px;
    background: transparent;
    transition: background var(--el-transition-duration);
  }

  // 悬浮与拖拽中显示指示线；拖拽时鼠标即使短暂划出手柄也保持可见
  &:hover::after,
  &--active::after {
    background: var(--el-border-color);
  }
}

.markdown-editor-expand {
  display: flex;
  justify-content: center;
  margin-top: var(--space-xs);
}

// 预览按钮独立贴工具栏最右侧边缘：内核工具栏项为 float:left，右浮才能与左侧按钮断开；className 落在 Vditor 动态生成的 <li> 上，须经 :deep 命中
.markdown-editor :deep(.markdown-editor__maximize-btn) {
  float: right;
  // 拥挤时与左侧按钮保持最小断开间隙
  margin-left: var(--space-xs);
}

.markdown-editor__mask {
  position: fixed;
  inset: 0;
  z-index: 2999;
  background: var(--el-overlay-color-lighter);
  cursor: zoom-out;
}
</style>
