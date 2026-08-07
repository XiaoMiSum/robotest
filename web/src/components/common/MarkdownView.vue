<script setup lang="ts">
import DOMPurify from 'dompurify'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'

// md-editor-v3 的 sanitize prop 默认是恒等函数（不消毒），
// 缺陷描述等场景展示的是他人写入的内容，必须显式传入 DOMPurify 防 Markdown XSS
const props = defineProps<{ content: string }>()

function sanitize(html: string): string {
  return DOMPurify.sanitize(html)
}
</script>

<template>
  <MdPreview
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
