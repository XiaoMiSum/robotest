<script setup lang="ts">
import { ArrowDown, ArrowUp, Box, Close, Delete, Edit, Minus, Plus } from '@element-plus/icons-vue'

/** 面板头：纯展示 + emit；归档会话只读（交互 05 §2.1 PanelHeader） */
defineProps<{
  /** 会话标题；无会话时由父级传「助手」（交互 05 §2.1 标题位） */
  title: string
  hasConversation: boolean
  archived: boolean
  listOpen: boolean
}>()

const emit = defineEmits<{
  'toggle-list': []
  rename: []
  archive: []
  delete: []
  'new-session': []
  close: []
  'drag-start': [event: MouseEvent]
}>()

/** 拖动仅从头栏空白处发起，按钮 / 输入区保持原生交互 */
function onDragStart(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('button, input, textarea, a')) return
  emit('drag-start', event)
}
</script>

<template>
  <header class="ai-panel-header" @mousedown="onDragStart">
    <span class="ai-panel-header__title" :title="title">{{ title }}</span>
    <div class="ai-panel-header__actions">
      <el-button
        text
        size="small"
        class="ai-panel-header__list-toggle"
        :aria-expanded="listOpen"
        aria-label="历史会话"
        @click="emit('toggle-list')"
      >
        会话
        <el-icon class="ai-panel-header__caret">
          <component :is="listOpen ? ArrowUp : ArrowDown" />
        </el-icon>
      </el-button>
      <el-button
        text
        circle
        size="small"
        aria-label="重命名会话"
        :disabled="!hasConversation"
        :icon="Edit"
        @click="emit('rename')"
      />
      <el-button
        text
        circle
        size="small"
        aria-label="归档会话"
        :disabled="!hasConversation || archived"
        :icon="Box"
        @click="emit('archive')"
      />
      <el-button
        text
        circle
        size="small"
        aria-label="删除会话"
        :disabled="!hasConversation"
        :icon="Delete"
        @click="emit('delete')"
      />
      <el-button
        text
        circle
        size="small"
        aria-label="新会话"
        :icon="Plus"
        @click="emit('new-session')"
      />
      <span class="ai-panel-header__divider" />
      <el-button
        text
        circle
        size="small"
        aria-label="收起面板"
        :icon="Minus"
        @click="emit('close')"
      />
      <el-button
        text
        circle
        size="small"
        aria-label="关闭面板"
        :icon="Close"
        @click="emit('close')"
      />
    </div>
  </header>
</template>

<style scoped lang="scss">
.ai-panel-header {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-xs) var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-100);
  cursor: move;
  user-select: none;

  &__title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 14px;
    font-weight: 600;
    color: var(--color-neutral-900);
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 2px;
    flex-shrink: 0;
  }

  &__list-toggle {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    margin-right: var(--space-xs);
  }

  &__caret {
    font-size: 12px;
  }

  &__divider {
    width: 1px;
    height: 16px;
    margin: 0 var(--space-xs);
    background: var(--color-neutral-200);
  }
}
</style>
