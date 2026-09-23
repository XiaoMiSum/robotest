<script setup lang="ts">
import { useReviewAiSummary } from '@/composables/project/functional-testing/review/useReviewAiSummary'
import MarkdownView from '@/components/common/MarkdownView.vue'

/**
 * AI 评审摘要抽屉（US-AI-006，交互设计第 3 章）：
 * 打开先读持久化快照；生成时 statistics 帧即时渲染统计卡片、delta 流式渲染 Markdown 总结。
 * 仅评审发起人可见（父组件已按发起人 + aiEnabled + 已完成状态控制入口）。
 */
const props = defineProps<{ reviewId: string }>()
const visible = defineModel<boolean>({ required: true })

const {
  phase,
  statistics,
  summaryMarkdown,
  generatedAt,
  slowHint,
  statCards,
  generate,
  regenerate,
  stop,
  copy,
} = useReviewAiSummary(() => props.reviewId)
</script>

<template>
  <el-drawer v-model="visible" size="640px" :close-on-click-modal="true" modal-class="ai-summary-drawer-modal">
    <template #header>
      <span class="ai-summary-title"><el-icon><MagicStick /></el-icon> 评审摘要</span>
    </template>

    <div class="ai-summary">
      <!-- 操作行：模型选择 + 生成/停止，右侧对齐（交互设计 3.1，统一规格 2.9） -->
      <div class="ai-summary-actions">
        <AiModelSelect />
        <el-button size="small" :disabled="!summaryMarkdown" @click="copy">复制</el-button>
        <el-button
          v-if="phase !== 'streaming'"
          size="small"
          type="primary"
          @click="phase === 'done' ? regenerate() : generate()"
        >
          {{ phase === 'done' ? '重新生成' : '生成摘要' }}
        </el-button>
        <el-button v-else size="small" @click="stop">停止</el-button>
      </div>

      <!-- 统计卡片区：随 statistics 帧即时渲染（不依赖 LLM） -->
      <div v-if="statistics" class="ai-summary-stats">
        <div v-for="card in statCards" :key="card.key" class="ai-summary-card">
          <div class="ai-summary-card__value">{{ card.value }}</div>
          <div class="ai-summary-card__label">{{ card.label }}</div>
        </div>
      </div>
      <div v-if="statistics && statistics.failByDocument.length" class="ai-summary-faildist">
        <span
          v-for="doc in statistics.failByDocument"
          :key="doc.documentName"
          class="ai-summary-faildist__item"
        >{{ doc.documentName }}：{{ doc.failCount }}</span>
      </div>

      <el-alert
        v-if="slowHint"
        type="info"
        :closable="false"
        show-icon
        title="模型响应较慢，可点击「停止」后重试"
      />

      <!-- 文字总结区：流式 Markdown -->
      <div class="ai-summary-body">
        <div v-if="phase === 'idle' && !summaryMarkdown" class="ai-summary-empty">
          <el-empty description="点击「生成摘要」由 AI 汇总本次评审" :image-size="80" />
        </div>
        <MarkdownView v-else :content="summaryMarkdown || '正在生成…'" />
      </div>
      <div v-if="generatedAt" class="ai-summary-time">生成于 {{ generatedAt }}</div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.ai-summary-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.ai-summary-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

/* 透明遮罩：点击抽屉外空白处自动关闭，同时不压暗画布（交互设计 3.1，统一规格 2.9） */
:deep(.ai-summary-drawer-modal) {
  background: transparent;
}

.ai-summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-summary-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.ai-summary-card {
  padding: 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  text-align: center;
}

.ai-summary-card__value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ai-summary-card__label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-summary-faildist {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-summary-body {
  min-height: 160px;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.ai-summary-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
