<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { suggestBugForm } from '@/services/ai'
import type { AiBugSuggestion, BugPriority, BugSeverity } from '@/types'

/**
 * 缺陷表单 AI 建议面板（US-AI-008，交互设计 2.1/1.1 表单侧）：
 * 入口按钮由父组件置于标题输入框 #append（方案 A：内嵌按钮 + 内联面板），
 * 本组件仅渲染建议面板，并经 defineExpose 暴露 requestSuggestion / loading 供按钮调用。
 * 仅回填表单待用户确认（一键替换标题 / 采纳等级），不产生任何自动提交；
 * 调用失败仅 Toast 轻提示，不影响表单任何操作；无建议结果时不占位（2.7 零影响原则）。
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

// 父组件标题输入框 #append 按钮经 ref 调用；loading 解包后驱动按钮 loading 态
defineExpose({ requestSuggestion, loading })

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
  <!-- 无建议结果时不渲染，避免占位（2.7 布局零侵入）；忽略建议后面板收起 -->
  <div v-if="suggestion && titleState !== 'dismissed'" class="bug-ai-suggest">
    <div class="bug-ai-suggest__head">
      <el-icon><MagicStick /></el-icon><span>AI 建议</span>
    </div>

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
  </div>
</template>

<style scoped lang="scss">
// 内联建议面板：primary 语义高亮（背景 50 / 边框 200 / 左侧 accent 条），与普通表单卡片区分以突出 AI 能力
.bug-ai-suggest {
  margin-bottom: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-200);
  border-left: 3px solid var(--color-primary-500);
  border-radius: var(--radius-md);
}

.bug-ai-suggest__head {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  font-size: var(--font-size-base);
  color: var(--color-primary-600);
  margin-bottom: var(--space-sm);
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
</style>
