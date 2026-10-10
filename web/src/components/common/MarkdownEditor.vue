<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue'
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
  defineProps<{ height?: string; placeholder?: string; disabled?: boolean }>(),
  {
    height: '320px',
    placeholder: '支持 Markdown 语法',
    disabled: false,
  },
)

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
  if (ready) {
    instance?.destroy()
    instance = undefined
    ready = false
  }
  // 未就绪时保留实例，由 after 回调自行销毁
})
</script>

<template>
  <div ref="container" class="markdown-editor" />
</template>
