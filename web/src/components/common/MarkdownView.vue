<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'

// 薄封装 md-editor-v3 只读渲染，内置 XSS 消毒
defineProps<{ content: string }>()
</script>

<template>
  <MdPreview :model-value="content" language="zh-CN" class="markdown-view" />
</template>

<style scoped>
/* 去掉预览组件默认内边距，与表单文本对齐 */
.markdown-view :deep(.md-editor-preview-wrapper) {
  padding: 0;
}
</style>
