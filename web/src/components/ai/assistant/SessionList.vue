<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { AiAssistantConversation } from '@/types'
import { formatShortDateTime } from '@/utils/format'

/** 历史会话列表：纯展示 + emit（交互 05 §2.1 SessionList） */
const props = defineProps<{
  conversations: AiAssistantConversation[]
  currentId: string | null
  loading: boolean
  keyword: string
}>()

const emit = defineEmits<{
  select: [conversationId: string]
  search: [keyword: string]
}>()

/** 输入即时回显，检索请求的防抖由父级编排 */
const query = ref(props.keyword)

function onQueryChange(value: string): void {
  query.value = value
  emit('search', value)
}
</script>

<template>
  <div class="ai-session-list">
    <el-input
      :model-value="query"
      class="ai-session-list__search"
      placeholder="检索历史会话"
      clearable
      :prefix-icon="Search"
      @update:model-value="onQueryChange"
    />
    <div class="ai-session-list__body" role="list">
      <p v-if="loading && conversations.length === 0" class="ai-session-list__hint">加载中…</p>
      <p v-else-if="conversations.length === 0" class="ai-session-list__hint">
        {{ query.trim() ? '无匹配会话' : '暂无会话' }}
      </p>
      <template v-else>
        <button
          v-for="item in conversations"
          :key="item.id"
          type="button"
          role="listitem"
          class="ai-session-list__item"
          :class="{ 'ai-session-list__item--active': item.id === currentId }"
          :aria-label="`切换会话：${item.title}`"
          :aria-current="item.id === currentId ? 'true' : undefined"
          @click="emit('select', item.id)"
        >
          <span class="ai-session-list__item-main">
            <span class="ai-session-list__item-title">{{ item.title }}</span>
            <el-tag v-if="item.status === 'archived'" size="small" type="info">已归档</el-tag>
          </span>
          <span class="ai-session-list__item-time">{{ formatShortDateTime(item.lastMessageAt) }}</span>
        </button>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ai-session-list {
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid var(--color-neutral-100);

  &__search {
    padding: var(--space-sm) var(--space-sm) 0;
  }

  &__body {
    max-height: 200px;
    overflow-y: auto;
    padding: var(--space-sm);
  }

  &__hint {
    margin: var(--space-sm) 0;
    text-align: center;
    font-size: 13px;
    color: var(--color-neutral-400);
  }

  &__item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-sm);
    width: 100%;
    padding: var(--space-xs) var(--space-sm);
    border: none;
    border-radius: var(--radius-md);
    background: transparent;
    text-align: left;
    cursor: pointer;
    transition: background var(--transition-fast);

    &:hover {
      background: var(--color-neutral-50);
    }

    &--active {
      background: var(--color-primary-50);
    }
  }

  &__item-main {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    min-width: 0;
  }

  &__item-title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    color: var(--color-neutral-900);
  }

  &__item-time {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--color-neutral-400);
  }
}
</style>
