<script setup lang="ts">
import DOMPurify from 'dompurify'
import { MdPreview } from 'md-editor-v3'
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import 'md-editor-v3/lib/preview.css'
import { resolveMarkdownImages } from '@/composables/common/markdownImage'

// md-editor-v3 的 sanitize prop 默认是恒等函数（不消毒），
// 缺陷描述等场景展示的是他人写入的内容，必须显式传入 DOMPurify 防 Markdown XSS
const props = defineProps<{ content: string }>()

function sanitize(html: string): string {
  return DOMPurify.sanitize(html)
}

// 正文图片存的是稳定下载路径，只读渲染同样要换签后才可显示（文件管理详设 3.3）
const previewRef = useTemplateRef<InstanceType<typeof MdPreview>>('preview')
let stopResolve: (() => void) | undefined

onMounted(() => {
  const root = previewRef.value?.$el
  if (root instanceof HTMLElement) stopResolve = resolveMarkdownImages(root)
})

onBeforeUnmount(() => stopResolve?.())
</script>

<template>
  <MdPreview
    ref="preview"
    :model-value="props.content"
    :sanitize="sanitize"
    language="zh-CN"
    class="markdown-view"
  />
</template>

<style scoped>
/* 去掉预览组件默认内边距，与表单文本对齐 */
.markdown-view :deep(.md-editor-preview-wrapper) {
  padding: 0;
}
</style>
