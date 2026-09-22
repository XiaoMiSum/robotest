<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAiStream, type AiStreamController } from '@/composables/ai/useAiStream'
import { useAiStore } from '@/stores/ai'
import { fetchReviewConclusion, toReviewConclusion } from '@/services/ai'
import type { AiReviewConclusion, AiReviewSummaryStats } from '@/types'
import { buildStatCards } from './reviewSummary'
import { verdictPresentation } from './conclusionPresentation'

/**
 * AI 评审结论抽屉（06 §5.2）：
 * 打开先读最近一次结论任务快照；生成时 statistics 帧即时渲染统计卡片、verdict 帧即时渲染判定，
 * done 帧覆盖渲染最终结果。仅评审发起人 + 评审已完成可见（父组件已按发起人 + aiEnabled + completed 控制入口）。
 */
const props = defineProps<{ reviewId: string }>()
const visible = defineModel<boolean>({ required: true })

const aiStore = useAiStore()

type Phase = 'idle' | 'streaming' | 'done'
const phase = ref<Phase>('idle')
const statistics = ref<AiReviewSummaryStats | null>(null)
const conclusion = ref<AiReviewConclusion | null>(null)
const verdict = ref<ReturnType<typeof verdictPresentation> | null>(null)
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

// 打开时读取最近一次结论任务：有结果直接渲染，不自动重新生成（与摘要抽屉一致）
async function loadExisting(): Promise<void> {
  try {
    const task = await fetchReviewConclusion(props.reviewId)
    const loaded = toReviewConclusion(task?.result)
    if (loaded) {
      const time = loaded.generatedAt ?? task?.updatedAt
      conclusion.value = { ...loaded, generatedAt: time }
      verdict.value = verdictPresentation(loaded.verdict)
      applyStatistics(loaded.statistics)
      phase.value = 'done'
    }
  } catch {
    // 读取失败按无结论处理，用户可点生成
  }
}

function generate(): void {
  phase.value = 'streaming'
  conclusion.value = null
  verdict.value = null
  slowTimer = setTimeout(() => {
    slowHint.value = true
  }, 10_000)

  controller = useAiStream({
    url: `/project/ai/reviews/${props.reviewId}/conclusion`,
    body: { modelId: aiStore.effectiveModelId() ?? null },
    onEvent(event) {
      clearSlowTimer()
      if (event.event === 'statistics') {
        applyStatistics(event.data as AiReviewSummaryStats)
      } else if (event.event === 'verdict') {
        // verdict 帧：判定在 statistics 帧之后、LLM 生成前到达（06 §5.2），即时点亮判定行
        verdict.value = verdictPresentation(event.data as AiReviewConclusion['verdict'])
      } else if (event.event === 'done') {
        const result = event.data as AiReviewConclusion
        applyStatistics(result.statistics)
        verdict.value = verdictPresentation(result.verdict)
        conclusion.value = result
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
    await ElMessageBox.confirm('将覆盖上一份结论，确定重新生成？', '重新生成结论', { type: 'warning' })
  } catch {
    return
  }
  generate()
}

function stop(): void {
  controller?.cancel()
  controller = null
  clearSlowTimer()
  conclusion.value = null
  verdict.value = null
  phase.value = 'idle'
}

async function copy(): Promise<void> {
  if (!conclusion.value?.reason) return
  try {
    await navigator.clipboard.writeText(conclusion.value.reason)
    ElMessage.success('已复制结论理由')
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