<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { suggestBugForm } from '@/services/ai'
import type { AiBugSuggestion, BugPriority, BugSeverity } from '@/types'

/**
 * 缺陷表单 AI 建议卡（US-AI-008，交互设计 2.1/1.1 表单侧）：
 * 仅回填表单待用户确认（一键替换标题 / 采纳等级），不产生任何自动提交。
 * 调用失败仅 Toast 轻提示，不影响表单任何操作。
 */
const props = defineProps<{
  title: string
  reproSteps?: string
}>()

const emit = defineEmits<{
  applyTitle: [title: string]
  applySeverity: [severity: BugSeverity]
  applyPriority: [priority: BugPriority]
}>()

const severityLabel: Record<BugSeverity, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<BugPriority, string> = { high: '高', medium: '中', low: '低' }

const suggestion = ref<AiBugSuggestion | null>(null)
const loading = ref(false)
const titleState = ref<'idle' | 'accepted' | 'dismissed'>('idle')
const severityAdopted = ref(false)
const priorityAdopted = ref(false)

// 建议请求进行中不阻塞输入与提交（非侵入原则）；标题为空时后端会校验拒绝，先在前端拦截
async function requestSuggestion(): Promise<void> {
  if (!props.title.trim()) {
    ElMessage.warning('请先输入缺陷标题')
    return
  }
  loading.value = true
  try {
    suggestion.value = await suggestBugForm({
      title: props.title,
      reproSteps: props.reproSteps?.trim() || undefined,
    })
    titleState.value = 'idle'
    severityAdopted.value = false
    priorityAdopted.value = false
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : 'AI 建议获取失败')
  } finally {
    loading.value = false
  }
}

function acceptTitle(): void {
  if (!suggestion.value) return
  emit('applyTitle', suggestion.value.optimizedTitle)
  titleState.value = 'accepted'
}

function dismissTitle(): void {
  titleState.value = 'dismissed'
}

function adoptSeverity(): void {
  if (!suggestion.value) return
  emit('applySeverity', suggestion.value.severity)
  severityAdopted.value = true
}

function adoptPriority(): void {
  if (!suggestion.value) return
  emit('applyPriority', suggestion.value.priority)
  priorityAdopted.value = true
}
</script>

<template>
  <el-card shadow="never" class="bug-ai-suggest">
    <template #header>
      <span class="bug-ai-suggest__header"><el-icon><MagicStick /></el-icon> AI 建议</span>
    </template>

    <template v-if="suggestion && titleState !== 'dismissed'">
      <div class="bug-ai-suggest__item">
        <div class="bug-ai-suggest__label">优化标题</div>
        <div class="bug-ai-suggest__title">
          {{ suggestion.optimizedTitle }}
          <el-tag v-if="titleState === 'accepted'" size="small" type="success" effect="light">已采纳</el-tag>
        </div>
        <div v-if="titleState === 'idle'" class="bug-ai-suggest__actions">
          <el-button size="small" type="primary" @click="acceptTitle">一键替换</el-button>
          <el-button size="small" @click="dismissTitle">忽略</el-button>
        </div>
      </div>

      <el-divider class="bug-ai-suggest__divider" />

      <div class="bug-ai-suggest__item">
        <div class="bug-ai-suggest__label">等级建议</div>
        <div class="bug-ai-suggest__values">
          <el-tag
            size="small"
            class="bug-ai-suggest__value"
            :class="{ 'bug-ai-suggest__value--adopted': severityAdopted }"
            @click="adoptSeverity"
          >
            {{ severityLabel[suggestion.severity] }}
          </el-tag>
          <span v-if="severityAdopted" class="bug-ai-suggest__adopted-tip">已回填</span>
        </div>
      </div>

      <div class="bug-ai-suggest__item">
        <div class="bug-ai-suggest__label">优先级建议</div>
        <div class="bug-ai-suggest__values">
          <el-tag
            size="small"
            class="bug-ai-suggest__value"
            :class="{ 'bug-ai-suggest__value--adopted': priorityAdopted }"
            @click="adoptPriority"
          >
            {{ priorityLabel[suggestion.priority] }}
          </el-tag>
          <span v-if="priorityAdopted" class="bug-ai-suggest__adopted-tip">已回填</span>
        </div>
      </div>

      <div class="bug-ai-suggest__reason">{{ suggestion.reason }}</div>
    </template>

    <div v-else class="bug-ai-suggest__placeholder">
      输入标题后点击下方按钮，获取标题、严重等级与优先级建议
    </div>

    <div class="bug-ai-suggest__trigger">
      <el-button :loading="loading" @click="requestSuggestion">
        <el-icon><MagicStick /></el-icon>{{ loading ? '建议生成中…' : 'AI 建议' }}
      </el-button>
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.bug-ai-suggest__header {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  font-size: var(--font-size-base);
  color: var(--color-primary-600);
}

.bug-ai-suggest__placeholder {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  line-height: 1.6;
}

.bug-ai-suggest__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bug-ai-suggest__label {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.bug-ai-suggest__title {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
  line-height: 1.6;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bug-ai-suggest__actions {
  display: flex;
  gap: var(--space-sm);
}

.bug-ai-suggest__divider {
  margin: var(--space-sm) 0;
}

.bug-ai-suggest__values {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

// 建议值以可点击标签呈现，悬停高亮暗示可回填
.bug-ai-suggest__value {
  cursor: pointer;

  &:hover {
    opacity: 0.8;
  }

  &--adopted {
    opacity: 0.6;
  }
}

.bug-ai-suggest__adopted-tip {
  font-size: var(--font-size-xs);
  color: var(--color-success);
}

.bug-ai-suggest__reason {
  margin-top: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  line-height: 1.6;
}

.bug-ai-suggest__trigger {
  margin-top: var(--space-md);
}
</style>
