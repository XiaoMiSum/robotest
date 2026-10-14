<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import 'vditor/dist/index.css'
import {
  normalizeMarkdownImageUrls,
  resolveMarkdownImages,
  uploadMarkdownImages,
} from '@/composables/common/markdownImage'
import {
  createEditorOptions,
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

function enterMaximize(): void {
  maximized.value = true
}

function exitMaximize(): void {
  maximized.value = false
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') exitMaximize()
}

// 最大化期间锁定背景滚动，Esc 可退出；高度以内联样式下发给 .vditor（inline 覆盖 inline，无需强制优先级）
function applyMaximizeHeight(on: boolean): void {
  const vditorEl = container.value?.querySelector<HTMLElement>('.vditor')
  if (!vditorEl) return
  vditorEl.style.height = on ? '100%' : props.height
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
})

const container = useTemplateRef<HTMLDivElement>('container')

let instance: VditorInstance | undefined
let stopResolve: (() => void) | undefined
// 构造后要先异步加载 i18n/lute 资源，就绪前实例方法不可用，一切依赖等 after 回调
let ready = false
let disposed = false

onMounted(() => {
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
})

watch(() => props.disabled, (disabled) => {
  if (!ready || !instance) return
  if (disabled) instance.disabled()
  else instance.enable()
})

onBeforeUnmount(() => {
  disposed = true
  stopResolve?.()
  stopResolve = undefined
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
  <div
    ref="container"
    class="markdown-editor"
    :class="{ 'markdown-editor--maximized': maximized }"
  />
</template>

<style scoped lang="scss">
.markdown-editor--maximized {
  position: fixed;
  inset: var(--space-lg);
  z-index: 3000;
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
}

.markdown-editor__mask {
  position: fixed;
  inset: 0;
  z-index: 2999;
  background: var(--el-overlay-color-lighter);
  cursor: zoom-out;
}
</style>
