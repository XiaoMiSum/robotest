<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue'
import 'vditor/dist/index.css'
import { resolveMarkdownImages, rewriteToSignedUrls } from '@/composables/common/markdownImage'
import { VDITOR_CDN } from '@/composables/common/vditorEditor'

// 流式输出会高频更新，渲染又是异步的：合并短间隔更新并串行排队，保证最终内容胜出
const RENDER_DEBOUNCE_MS = 200

// 只读渲染与编辑器同一引擎同一套正文样式（关闭态、需求详情、AI 回复共用）
const props = defineProps<{ content: string }>()

const container = useTemplateRef<HTMLDivElement>('container')

let stopResolve: (() => void) | undefined
let chain: Promise<void> = Promise.resolve()
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false

async function renderNow(): Promise<void> {
  const element = container.value
  if (!element || disposed) return
  const content = props.content
  try {
    // 内核按需分包，不进首屏 bundle
    const { default: Vditor } = await import('vditor')
    if (disposed) return
    await Vditor.preview(element, content, {
      mode: 'light',
      lang: 'zh_CN',
      cdn: VDITOR_CDN,
      // 展示他人写入的正文，沿用内置 GFM 过滤（替代原 dompurify）
      markdown: { sanitize: true },
      // 渲染前优先用换签缓存替换为签名地址，未命中的交给 DOM 观察器兜底
      transform: rewriteToSignedUrls,
    })
  } catch {
    // 运行资源不可达时保留上一版内容，不打断页面（静态资源随应用一并部署）
  }
}

function enqueueRender(): void {
  // 链式串行：同一时刻只有一个渲染在跑，排队的读到的是最新 content
  chain = chain.then(renderNow, renderNow)
}

function scheduleRender(immediate: boolean): void {
  if (timer !== undefined) clearTimeout(timer)
  if (immediate) {
    enqueueRender()
    return
  }
  timer = setTimeout(() => {
    timer = undefined
    enqueueRender()
  }, RENDER_DEBOUNCE_MS)
}

onMounted(() => {
  if (container.value) stopResolve = resolveMarkdownImages(container.value)
  scheduleRender(true)
})

watch(() => props.content, () => scheduleRender(false))

onBeforeUnmount(() => {
  disposed = true
  if (timer !== undefined) clearTimeout(timer)
  stopResolve?.()
})
</script>

<template>
  <div ref="container" class="markdown-view" />
</template>
