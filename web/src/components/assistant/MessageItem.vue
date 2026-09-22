<script setup lang="ts">
import MarkdownView from '@/components/common/MarkdownView.vue'
import AssistantIcon from './AssistantIcons.vue'
import DslPreviewDialog from './DslPreviewDialog.vue'
import { useMessageItem, type AssistantMessageItem } from '@/composables/assistant/useMessageItem'

export type { AssistantMessageItem }

const props = defineProps<{ message: AssistantMessageItem }>()

const emit = defineEmits<{
  confirm: [confirmToken: string]
  cancel: [confirmToken: string]
  confirmDsl: [plan: import('@/minder/ai/dslRunner').DslPlan]
  cancelDsl: []
}>()

const {
  isUser,
  isTool,
  safeContent,
  userAvatarUrl,
  userAvatarChar,
  confirmStatus,
  countdownText,
  confirmWaiting,
  confirmStatusLabel,
  previewFields,
  handleConfirm,
  handleCancel,
  dslPreviewVisible,
  dslPlan,
  openDslPreview,
  handleConfirmDsl,
  handleCancelDsl,
} = useMessageItem({
  message: props.message,
  onConfirm: (token) => emit('confirm', token),
  onCancel: (token) => emit('cancel', token),
  onConfirmDsl: (plan) => emit('confirmDsl', plan),
  onCancelDsl: () => emit('cancelDsl'),
})
</script>

<template>
  <div class="msg" :class="[`msg--${message.role}`, { 'msg--streaming': message.streaming }]">
    <div v-if="isUser" class="msg__row msg__row--user">
      <div class="msg__bubble msg__bubble--user">{{ message.content }}</div>
      <span class="msg__avatar msg__avatar--user">
        <img v-if="userAvatarUrl" :src="userAvatarUrl" alt="" class="msg__avatar-img" />
        <template v-else>{{ userAvatarChar }}</template>
      </span>
    </div>

    <div v-else-if="isTool" class="msg__tool-card">
      <AssistantIcon name="wrench" :size="14" class="msg__tool-icon" />
      <span>{{ message.content || '工具执行' }}</span>
    </div>

    <div v-else class="msg__row msg__row--assistant">
      <span class="msg__avatar msg__avatar--ai">
        <AssistantIcon name="sparkles" :size="14" />
      </span>
      <div class="msg__assistant">
        <MarkdownView v-if="message.content" :content="safeContent" />
        <span v-if="message.streaming" class="msg__cursor">▍</span>
        <div v-if="message.streaming && message.slowHint" class="msg__slow-hint">响应较慢，可停止重试</div>

        <div v-if="message.toolProcesses?.length" class="msg__process">
          <div v-for="(proc, index) in message.toolProcesses" :key="index" class="msg__process-item">
            <span v-if="proc.status === 'running'" class="msg__process-spinner" aria-hidden="true"></span>
            <AssistantIcon v-else-if="proc.status === 'done'" name="check" :size="12" class="msg__process-ok" />
            <AssistantIcon v-else name="close" :size="12" class="msg__process-fail" />
            <span class="msg__process-badge" :class="`msg__process-badge--${proc.status}`">
              {{ proc.status === 'running' ? '运行中' : proc.status === 'done' ? '完成' : '失败' }}
            </span>
            <span class="msg__process-summary">{{ proc.summary }}</span>
          </div>
        </div>

        <div v-if="message.confirmCard" class="msg__confirm">
          <div class="msg__confirm-title">
            <AssistantIcon name="clipboard" :size="14" class="msg__confirm-title-icon" />
            待确认操作：{{ message.confirmCard.toolName }}
          </div>
        <table v-if="previewFields.length" class="msg__confirm-table">
          <tbody>
            <tr v-for="(field, index) in previewFields" :key="index">
              <td class="msg__confirm-key">{{ field[0] }}</td>
              <td class="msg__confirm-value">{{ field[1] }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="msg__confirm-empty">无参数明细</div>
        <div v-if="confirmWaiting" class="msg__confirm-countdown">剩余确认时间 {{ countdownText }}</div>
        <div v-else class="msg__confirm-finished">
          <span :class="{ 'msg__confirm-failed': confirmStatus === 'failed' }">{{ confirmStatusLabel }}</span>
          <span v-if="confirmStatus === 'failed' && message.confirmCard?.error" class="msg__confirm-error">
            {{ message.confirmCard.error }}
          </span>
        </div>
        <div v-if="confirmWaiting" class="msg__confirm-actions">
          <el-button size="small" @click="handleCancel">取消</el-button>
          <el-button size="small" type="primary" @click="handleConfirm">确认执行</el-button>
        </div>
      </div>

      <div v-if="message.dslCommands" class="msg__dsl">
        <el-button size="small" type="primary" plain @click="openDslPreview">
          查看编辑预览
        </el-button>
      </div>
      </div>
    </div>

    <DslPreviewDialog
      v-model="dslPreviewVisible"
      :plan="dslPlan"
      @confirm="handleConfirmDsl"
      @cancel="handleCancelDsl"
    />
  </div>
</template>

<style scoped lang="scss">
.msg {
  display: flex;
  flex-direction: column;
  animation: msg-enter var(--transition-slow) both;
}

.msg--user {
  align-items: flex-end;
}

.msg--assistant,
.msg--tool {
  align-items: flex-start;
}

.msg__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-md);
  width: 100%;
  min-width: 0;
}

.msg__row--user {
  justify-content: flex-end;
}

.msg__avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-lg);
}

.msg__avatar--ai {
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-700));
  color: var(--color-neutral-0);
}

.msg__avatar--user {
  background: var(--color-neutral-200);
  color: var(--color-neutral-600);
  font-size: 13px;
  font-weight: 700;
}

.msg__avatar-img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.msg__bubble {
  max-width: 82%;
  padding: 10px 14px;
  border-radius: var(--radius-xl);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.msg__bubble--user {
  background: var(--color-primary-500);
  color: var(--color-neutral-0);
  border-top-right-radius: var(--radius-md);
}

.msg__assistant {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--color-neutral-700);
}

.msg__cursor {
  color: var(--color-primary-500);
  animation: msg-cursor-blink 1s step-end infinite;
}

.msg__slow-hint {
  margin-top: var(--space-sm);
  font-size: 12px;
  color: var(--color-warning);
}

@keyframes msg-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes msg-cursor-blink {
  0%,
  100% {
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
}

.msg__tool-card {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  max-width: 82%;
  padding: 6px 10px;
  border-radius: var(--radius-lg);
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  font-size: 12px;
  color: var(--color-neutral-500);
}

.msg__tool-icon {
  color: var(--color-neutral-400);
}

.msg__process {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-50);
}

.msg__process-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 12px;
  color: var(--color-neutral-600);
}

.msg__process-spinner {
  flex-shrink: 0;
  width: 12px;
  height: 12px;
  border: 2px solid var(--color-primary-100);
  border-top-color: var(--color-primary-500);
  border-radius: 50%;
  animation: msg-spin 0.8s linear infinite;
}

@keyframes msg-spin {
  to {
    transform: rotate(360deg);
  }
}

.msg__process-ok {
  flex-shrink: 0;
  color: var(--color-success);
}

.msg__process-fail {
  flex-shrink: 0;
  color: var(--color-danger);
}

.msg__process-badge {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  line-height: 18px;
  font-weight: 500;
}

.msg__process-badge--running {
  background: var(--color-info-light);
  color: var(--color-info);
}

.msg__process-badge--done {
  color: var(--color-success);
}

.msg__process-badge--failed {
  color: var(--color-danger);
}

.msg__process-summary {
  min-width: 0;
  word-break: break-word;
}

.msg__confirm {
  margin-top: var(--space-sm);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: 10px 12px;
  background: var(--color-neutral-50);
}

.msg__confirm-title {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-neutral-800);
  margin-bottom: var(--space-sm);
}

.msg__confirm-title-icon {
  color: var(--color-primary-500);
}

.msg__confirm-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.msg__confirm-table td {
  padding: 4px 8px;
  border: 1px solid var(--color-neutral-100);
}

.msg__confirm-key {
  width: 90px;
  background: var(--color-neutral-100);
  color: var(--color-neutral-500);
}

.msg__confirm-value {
  color: var(--color-neutral-700);
}

.msg__confirm-empty {
  font-size: 12px;
  color: var(--color-neutral-400);
}

.msg__confirm-countdown {
  margin-top: var(--space-sm);
  font-size: 12px;
  color: var(--color-warning);
}

.msg__confirm-finished {
  margin-top: var(--space-sm);
  font-size: 12px;
  color: var(--color-neutral-500);
}

.msg__confirm-failed {
  color: var(--color-danger);
}

.msg__confirm-error {
  margin-left: var(--space-sm);
  color: var(--color-danger);
}

.msg__confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.msg__dsl {
  margin-top: var(--space-sm);
}
</style>
