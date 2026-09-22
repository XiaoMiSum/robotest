<script setup lang="ts">
import { useAssistantPanel } from '@/composables/assistant/useAssistantPanel'
import { formatDateTime, truncateText } from '@/utils/format'
import MessageItem from './MessageItem.vue'
import AssistantIcon from './AssistantIcons.vue'
import AiModelSelect from '@/components/common/AiModelSelect.vue'

const props = defineProps<{ minimized?: boolean }>()
const emit = defineEmits<{
  minimize: []
  close: []
}>()

const {
  noWorkspace,
  conversationItems,
  conversationsLoading,
  conversationsLoadingMore,
  activeConversationId,
  messages,
  messagesLoading,
  streaming,
  input,
  messageScroller,
  sidebarScroller,
  minimized,
  handleSend,
  handleStop,
  switchConversation,
  handleNewConversation,
  handleDeleteConversation,
  handleConfirm,
  handleCancel,
  handleConfirmDsl,
  handleCancelDsl,
  handleSidebarScroll,
  onMinimize,
  onClose,
} = useAssistantPanel({
  minimized: props.minimized,
  onMinimize: () => emit('minimize'),
  onClose: () => emit('close'),
})
</script>

<template>
  <transition name="assistant-slide">
    <aside v-show="!minimized" class="assistant-panel">
      <header class="assistant-panel__header">
        <div class="assistant-panel__heading">
          <span class="assistant-panel__logo">
            <AssistantIcon name="sparkles" :size="14" />
          </span>
          <div class="assistant-panel__titles">
            <span class="assistant-panel__title">智能助手</span>
            <span class="assistant-panel__status">
              <span class="assistant-panel__status-dot" aria-hidden="true"></span>
              在线
            </span>
          </div>
        </div>
        <div class="assistant-panel__actions">
          <el-button text title="最小化" @click="onMinimize">
            <el-icon><Minus /></el-icon>
          </el-button>
          <el-button text title="关闭" @click="onClose">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </header>

      <div class="assistant-panel__body">
        <aside class="assistant-panel__sidebar">
          <div class="assistant-panel__new">
            <el-button size="small" type="primary" plain :disabled="noWorkspace" @click="handleNewConversation">
              <el-icon><Plus /></el-icon>新会话
            </el-button>
          </div>
          <el-scrollbar ref="sidebarScroller" class="assistant-panel__conv-scroll" @scroll="handleSidebarScroll">
            <div v-if="noWorkspace" class="assistant-panel__empty">请先选择工作空间</div>
            <template v-else>
              <div v-if="conversationsLoading" class="assistant-panel__skeleton">
                <el-skeleton :rows="6" animated />
              </div>
              <div
                v-for="conv in conversationItems"
                :key="conv.id"
                class="conv-item"
                :class="{ 'conv-item--active': conv.id === activeConversationId }"
                @click="switchConversation(conv.id)"
              >
                <div class="conv-item__title" :title="conv.title">{{ truncateText(conv.title, 14) }}</div>
                <div class="conv-item__meta">
                  <span class="conv-item__time">{{ formatDateTime(conv.lastActiveAt) }}</span>
                  <el-button text size="small" class="conv-item__clear" @click.stop="handleDeleteConversation(conv)">
                    清空
                  </el-button>
                </div>
              </div>
              <div v-if="conversationsLoadingMore" class="assistant-panel__skeleton">
                <el-skeleton :rows="2" animated />
              </div>
              <div v-if="!conversationItems.length && !conversationsLoading" class="assistant-panel__empty">
                暂无会话
              </div>
            </template>
          </el-scrollbar>
        </aside>

        <section class="assistant-panel__chat">
          <el-scrollbar ref="messageScroller" class="assistant-panel__messages">
            <div v-if="noWorkspace" class="assistant-panel__empty">请先选择工作空间后使用智能助手</div>
            <template v-else>
              <div v-if="messagesLoading" class="assistant-panel__loading">
                <el-skeleton :rows="4" animated />
              </div>
              <div v-else-if="!messages.length" class="assistant-panel__empty">开始与智能助手对话</div>
              <MessageItem
                v-for="msg in messages"
                :key="msg.id"
                :message="msg"
                @confirm="handleConfirm"
                @cancel="handleCancel"
                @confirm-dsl="handleConfirmDsl"
                @cancel-dsl="handleCancelDsl"
              />
            </template>
          </el-scrollbar>

          <footer class="assistant-panel__input">
            <el-input
              v-model="input"
              type="textarea"
              :rows="1"
              :autosize="{ minRows: 1, maxRows: 3 }"
              resize="none"
              :disabled="noWorkspace || streaming"
              placeholder="输入消息…"
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="assistant-panel__toolbar">
              <AiModelSelect />
              <div class="assistant-panel__toolbar-actions">
                <span class="assistant-panel__hint">Enter 发送</span>
                <el-button
                  v-if="!streaming"
                  class="assistant-panel__send"
                  type="primary"
                  size="small"
                  :disabled="noWorkspace || !input.trim()"
                  @click="handleSend"
                >
                  发送
                </el-button>
                <el-button v-else type="danger" size="small" @click="handleStop">停止</el-button>
              </div>
            </div>
          </footer>
        </section>
      </div>
    </aside>
  </transition>
</template>

<style scoped lang="scss">
.assistant-panel {
  position: fixed;
  top: var(--header-height);
  right: 0;
  bottom: 0;
  width: 780px;
  display: flex;
  flex-direction: column;
  background: var(--color-neutral-0);
  border-left: 1px solid var(--color-neutral-200);
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.08);
  z-index: 90;
}

.assistant-slide-enter-active,
.assistant-slide-leave-active {
  transition: transform 0.25s ease;
}

.assistant-slide-enter-from,
.assistant-slide-leave-to {
  transform: translateX(100%);
}

.assistant-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 var(--space-lg);
  border-bottom: 1px solid var(--color-neutral-100);
  flex-shrink: 0;
}

.assistant-panel__heading {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.assistant-panel__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-700));
  color: var(--color-neutral-0);
}

.assistant-panel__titles {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.assistant-panel__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.assistant-panel__status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-neutral-400);
}

.assistant-panel__status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-success);
}

.assistant-panel__actions {
  display: flex;
  align-items: center;
}

.assistant-panel__actions :deep(.el-button) {
  border-radius: var(--radius-md);
}

.assistant-panel__actions :deep(.el-button:hover) {
  background: var(--color-neutral-100);
}

.assistant-panel__body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.assistant-panel__sidebar {
  width: 220px;
  border-right: 1px solid var(--color-neutral-100);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.assistant-panel__new {
  display: flex;
  justify-content: flex-end;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-neutral-100);
  flex-shrink: 0;
}

.assistant-panel__conv-scroll {
  flex: 1;
  min-height: 0;
}

.assistant-panel__skeleton {
  padding: 12px;
}

.assistant-panel__empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--color-neutral-400);
}

.conv-item {
  position: relative;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast);

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 3px;
    height: 16px;
    border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
    background: transparent;
    transition: background-color var(--transition-fast);
  }

  &:hover {
    background: var(--color-neutral-100);

    .conv-item__clear {
      opacity: 1;
    }
  }

  &--active {
    background: var(--color-primary-50);

    &::before {
      background: var(--color-primary-500);
    }
  }
}

.conv-item__title {
  font-size: 13px;
  color: var(--color-neutral-700);
  margin-bottom: 2px;
}

.conv-item__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.conv-item__time {
  font-size: 11px;
  color: var(--color-neutral-400);
}

.conv-item__clear {
  opacity: 0;
  padding: 0 2px;
  font-size: 12px;
  transition: opacity var(--transition-fast);
}

.assistant-panel__chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.assistant-panel__messages {
  flex: 1;
  min-height: 0;
  padding: var(--space-lg);
}

.assistant-panel__loading {
  padding: 16px;
}

.assistant-panel__input {
  border-top: 1px solid var(--color-neutral-100);
  padding: var(--space-md) var(--space-lg);
  flex-shrink: 0;
}

.assistant-panel__input :deep(.el-textarea__inner) {
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-neutral-200);
  box-shadow: none;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.6;
  overflow-y: auto;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.assistant-panel__input :deep(.el-textarea__inner:focus) {
  border-color: var(--color-primary-500);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.assistant-panel__input :deep(.el-textarea__inner::placeholder) {
  color: var(--color-neutral-400);
}

.assistant-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-sm);
}

.assistant-panel__toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-left: auto;
}

.assistant-panel__hint {
  font-size: 11px;
  color: var(--color-neutral-400);
}

.assistant-panel__send {
  border: none;
  background: linear-gradient(135deg, var(--color-primary-500), var(--color-primary-600));
  color: var(--color-neutral-0);
  transition: background var(--transition-fast), box-shadow var(--transition-fast);

  &:hover {
    background: var(--color-primary-600);
  }

  &.is-disabled {
    background: var(--color-neutral-200);
    color: var(--color-neutral-400);
  }
}
</style>
