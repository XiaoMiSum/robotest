<script setup lang="ts">
import { useReviewAiConclusion } from '@/composables/project/functional-testing/review/useReviewAiConclusion'

/**
 * AI 评审结论抽屉（06 §5.2）：
 * 打开先读最近一次结论任务快照；生成时 statistics 帧即时渲染统计卡片、verdict 帧即时渲染判定，
 * done 帧覆盖渲染最终结果。仅评审发起人 + 评审已完成可见（父组件已按发起人 + aiEnabled + completed 控制入口）。
 */
const props = defineProps<{ reviewId: string }>()
const visible = defineModel<boolean>({ required: true })

const {
  phase,
  statistics,
  conclusion,
  verdict,
  slowHint,
  statCards,
  generate,
  regenerate,
  stop,
  copy,
} = useReviewAiConclusion(() => props.reviewId)
</script>

<template>
  <el-drawer v-model="visible" size="640px" :close-on-click-modal="true" modal-class="ai-conclusion-drawer-modal">
    <template #header>
      <span class="ai-conclusion-title"><el-icon><MagicStick /></el-icon> 评审结论</span>
    </template>

    <div class="ai-conclusion">
      <!-- 操作行：模型选择 + 复制/生成/停止，右侧对齐（对齐摘要抽屉交互） -->
      <div class="ai-conclusion-actions">
        <AiModelSelect />
        <el-button size="small" :disabled="!conclusion?.reason" @click="copy">复制</el-button>
        <el-button
          v-if="phase !== 'streaming'"
          size="small"
          type="primary"
          @click="phase === 'done' ? regenerate() : generate()"
        >
          {{ phase === 'done' ? '重新生成' : '生成结论' }}
        </el-button>
        <el-button v-else size="small" @click="stop">停止</el-button>
      </div>

      <!-- 判定行：verdict 帧/落库结果即时渲染，不依赖 LLM -->
      <div v-if="verdict" class="ai-conclusion-verdict">
        <el-tag :type="verdict.tagType" effect="dark" size="large">{{ verdict.label }}</el-tag>
        <span class="ai-conclusion-verdict__hint">{{ verdict.verdict }}</span>
      </div>

      <!-- 统计卡片区：随 statistics 帧即时渲染（复用摘要统计口径） -->
      <div v-if="statistics" class="ai-conclusion-stats">
        <div v-for="card in statCards" :key="card.key" class="ai-conclusion-card">
          <div class="ai-conclusion-card__value">{{ card.value }}</div>
          <div class="ai-conclusion-card__label">{{ card.label }}</div>
        </div>
      </div>

      <el-alert
        v-if="slowHint"
        type="info"
        :closable="false"
        show-icon
        title="模型响应较慢，可点击「停止」后重试"
      />

      <!-- 结论理由区 -->
      <div class="ai-conclusion-body">
        <div v-if="phase === 'idle' && !conclusion" class="ai-conclusion-empty">
          <el-empty description="点击「生成结论」由 AI 汇总本次评审" :image-size="80" />
        </div>
        <template v-else>
          <div class="ai-conclusion-reason">
            <div class="ai-conclusion-section-label">结论理由</div>
            <div class="ai-conclusion-reason__text">{{ conclusion?.reason || (phase === 'streaming' ? '正在生成…' : '') }}</div>
          </div>
          <div v-if="conclusion?.keyFindings?.length" class="ai-conclusion-findings">
            <div class="ai-conclusion-section-label">关键发现</div>
            <ol class="ai-conclusion-findings__list">
              <li v-for="(finding, index) in conclusion.keyFindings" :key="index" class="ai-conclusion-findings__item">
                {{ finding }}
              </li>
            </ol>
          </div>
        </template>
      </div>
      <div v-if="conclusion?.generatedAt" class="ai-conclusion-time">生成于 {{ conclusion.generatedAt }}</div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.ai-conclusion-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.ai-conclusion-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

/* 透明遮罩：点击抽屉外空白处自动关闭（与摘要抽屉一致） */
:deep(.ai-conclusion-drawer-modal) {
  background: transparent;
}

.ai-conclusion {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-conclusion-verdict {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-conclusion-verdict__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-conclusion-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.ai-conclusion-card {
  padding: 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  text-align: center;
}

.ai-conclusion-card__value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ai-conclusion-card__label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-conclusion-section-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.ai-conclusion-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 160px;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.ai-conclusion-empty {
  flex: 1;
}

.ai-conclusion-reason__text {
  line-height: 1.8;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
}

.ai-conclusion-findings__list {
  margin: 0;
  padding-left: 20px;
}

.ai-conclusion-findings__item {
  line-height: 1.8;
  color: var(--el-text-color-primary);
}

.ai-conclusion-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
