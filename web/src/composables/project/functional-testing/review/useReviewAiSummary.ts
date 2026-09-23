import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAiStream, type AiStreamController } from '@/composables/ai/useAiStream'
import { useAiStore } from '@/stores/ai'
import { fetchReviewSummary } from '@/services/ai'
import type { AiReviewSummary, AiReviewSummaryStats } from '@/types'
import { buildStatCards } from '@/components/project/functional-testing/review/reviewSummary'

export function useReviewAiSummary(getReviewId: () => string) {
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
      const summary: AiReviewSummary | null = await fetchReviewSummary(getReviewId())
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
      url: `/project/ai/reviews/${getReviewId()}/summary`,
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

  return {
    phase,
    statistics,
    summaryMarkdown,
    generatedAt,
    slowHint,
    statCards,
    applyStatistics,
    clearSlowTimer,
    loadExisting,
    generate,
    regenerate,
    stop,
    copy,
  }
}
