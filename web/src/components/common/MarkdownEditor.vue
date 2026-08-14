<script setup lang="ts">
import DOMPurify from 'dompurify'
import { MdEditor, type ToolbarNames } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

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

// 精简为缺陷描述场景常用能力，去掉图片上传等本期不支持的入口
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
  'table',
  '-',
  'revoke',
  'next',
  '=',
  'preview',
]
</script>

<template>
  <MdEditor
    v-model="value"
    language="zh-CN"
    :toolbars="toolbars"
    :footers="[]"
    :preview="false"
    :placeholder="placeholder"
    :sanitize="sanitize"
    :disabled="disabled"
    :style="{ height }"
  />
</template>
