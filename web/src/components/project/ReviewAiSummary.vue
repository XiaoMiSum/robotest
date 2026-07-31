<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAiStream, type AiStreamController } from '@/composables/useAiStream'
import { useAiStore } from '@/stores/ai'
import { fetchReviewSummary } from '@/services/ai'
import type { AiReviewSummary, AiReviewSummaryStats } from '@/types'
import { buildStatCards } from './reviewSummary'
import MarkdownView from '@/components/common/MarkdownView.vue'

/**
 * AI 评审摘要抽屉（US-AI-006，交互设计第 3 章）：
 * 打开先读持久化快照；生成时 statistics 帧即时渲染统计卡片、delta 流式渲染 Markdown 总结。
 * 仅评审发起人可见（父组件已按发起人 + aiEnabled + 已完成状态控制入口）。
 */
const props = defineProps<{ reviewId: string }>()
const visible = defineModel<boolean>({ required: true })

const aiStore = useAiStore()

type Phase = 'idle' | 'streaming' | 'done'
const phase = ref<Phase>('idle')
const statistics = ref<AiReviewSummaryStats | null>(null)
const summaryMarkdown = ref('')
const generatedAt = ref<string | null>(null)
const slowHint = ref(false)

let controller: AiStreamController | null = null
let slowTimer: ReturnType<typeof setTimeout> | null = null

const statCards = ref<ReturnType<typeof buildStatCards>>([])

function applyStatistics(stats: AiReviewSummaryStats): void {
  statistics.value = stats
  statCards.value = buildStatCards(stats)
}

function clearSlowTimer(): void {
  if (slowTimer) clearTimeout(slowTimer)
  slowTimer = null
  slowHint.value = false
}

// 打开时读取持久化摘要：有快照直接渲染，不自动重新生成（交互设计 3.2「再次进入页面」）
async function loadExisting(): Promise<void> {
  try {
    const summary: AiReviewSummary | null = await fetchReviewSummary(props.reviewId)
    if (summary) {
      applyStatistics(summary.statistics)
      summaryMarkdown.value = summary.summaryMarkdown
      generatedAt.value = summary.generatedAt ?? null
      phase.value = 'done'
    }
  } catch {
    // 读取失败按无快照处理，用户可点生成
  }
}

function generate(): void {
  phase.value = 'streaming'
  summaryMarkdown.value = ''
  generatedAt.value = null
  slowTimer = setTimeout(() => {
    slowHint.value = true
  }, 10_000)

  controller = useAiStream({
    url: `/project/ai/reviews/${props.reviewId}/summary`,
    body: { modelId: aiStore.effectiveModelId() ?? null },
    onEvent(event) {
      clearSlowTimer()
      if (event.event === 'statistics') {
        applyStatistics(event.data as AiReviewSummaryStats)
      } else if (event.event === 'delta') {
        summaryMarkdown.value += (event.data as { content?: string }).content ?? ''
      } else if (event.event === 'done') {
        const result = event.data as AiReviewSummary
        applyStatistics(result.statistics)
        summaryMarkdown.value = result.summaryMarkdown
        phase.value = 'done'
      } else if (event.event === 'error') {
        ElMessage.error((event.data as { message?: string }).message ?? 'AI 调用失败')
        phase.value = 'idle'
      }
    },
    onError(error) {
      clearSlowTimer()
      ElMessage.error(error.message)
      phase.value = 'idle'
    },
    onClose() {
      clearSlowTimer()
      if (phase.value === 'streaming') phase.value = 'idle'
    },
  })
}

async function regenerate(): Promise<void> {
  try {
    await ElMessageBox.confirm('将覆盖上一份摘要，确定重新生成？', '重新生成', { type: 'warning' })
  } catch {
    return
  }
  generate()
}

function stop(): void {
  controller?.cancel()
  controller = null
  clearSlowTimer()
  // 中途取消已渲染文字不保留（交互设计 3.2）
  summaryMarkdown.value = ''
  phase.value = 'idle'
}

async function copy(): Promise<void> {
  if (!summaryMarkdown.value) return
  try {
    await navigator.clipboard.writeText(summaryMarkdown.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

onMounted(loadExisting)

onBeforeUnmount(() => {
  controller?.cancel()
  clearSlowTimer()
})
</script>

<template>
  <el-drawer v-model="visible" size="520px" :close-on-click-modal="false" :modal="false">
    <template #header>
      <div class="ai-summary-header">
        <span class="ai-summary-title"><el-icon><MagicStick /></el-icon> 评审摘要</span>
        <div class="ai-summary-tools">
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
      </div>
    </template>

    <div class="ai-summary">
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
.ai-summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.ai-summary-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.ai-summary-tools {
  display: flex;
  align-items: center;
  gap: 8px;
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
