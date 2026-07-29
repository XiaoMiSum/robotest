<script setup lang="ts">
import { MdEditor, type ToolbarNames } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

// 薄封装 md-editor-v3：统一中文、精简工具栏与默认高度，便于全站复用与后续替换
const value = defineModel<string>({ default: '' })

withDefaults(defineProps<{ height?: string; placeholder?: string }>(), {
  height: '320px',
  placeholder: '支持 Markdown 语法',
})

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
    :style="{ height }"
  />
</template>
