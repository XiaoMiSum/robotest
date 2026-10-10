<script setup lang="ts">
import DOMPurify from 'dompurify'
import { MdEditor, type ToolbarNames } from 'md-editor-v3'
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import 'md-editor-v3/lib/style.css'
import { resolveMarkdownImages, uploadMarkdownImages } from '@/composables/common/markdownImage'

// 薄封装 md-editor-v3：统一中文、精简工具栏与默认高度，便于全站复用与后续替换
const value = defineModel<string>({ default: '' })

withDefaults(defineProps<{ height?: string; placeholder?: string; disabled?: boolean }>(), {
  height: '320px',
  placeholder: '支持 Markdown 语法',
  disabled: false,
})

// md-editor-v3 的 sanitize prop 默认是恒等函数（不消毒）；
// 工具栏可切换预览模式渲染 HTML，必须显式传入 DOMPurify 防 Markdown XSS
function sanitize(html: string): string {
  return DOMPurify.sanitize(html)
}

// 粘贴与工具栏选图都经此回调，成功后把稳定下载路径写回正文（详设 1.14 正文图片）
async function handleUploadImg(
  files: Array<File>,
  callback: (urls: Array<string>) => void,
): Promise<void> {
  const urls = await uploadMarkdownImages(files)
  if (urls.length > 0) callback(urls)
}

const toolbars: ToolbarNames[] = [
  'bold',
  'italic',
  'strikeThrough',
  '-',
  'title',
  'quote',
  'unorderedList',
  'orderedList',
  'task',
  '-',
  'code',
  'codeRow',
  'link',
  'image',
  'table',
  '-',
  'revoke',
  'next',
  '=',
  'preview',
]

// 预览区图片存的是稳定下载路径，渲染后需换签为签名地址才可显示（文件管理详设 3.3）
const editorRef = useTemplateRef<InstanceType<typeof MdEditor>>('editor')
let stopResolve: (() => void) | undefined

onMounted(() => {
  const root = editorRef.value?.$el
  if (root instanceof HTMLElement) stopResolve = resolveMarkdownImages(root)
})

onBeforeUnmount(() => stopResolve?.())
</script>

<template>
  <MdEditor
    ref="editor"
    v-model="value"
    language="zh-CN"
    :toolbars="toolbars"
    :footers="[]"
    :preview="false"
    :placeholder="placeholder"
    :sanitize="sanitize"
    :disabled="disabled"
    :on-upload-img="handleUploadImg"
    :style="{ height }"
  />
</template>
