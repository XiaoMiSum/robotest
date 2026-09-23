import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAiStream, type AiStreamController } from '@/composables/ai/useAiStream'
import { useAiStore } from '@/stores/ai'
import { fetchReviewConclusion, toReviewConclusion } from '@/services/ai'
import type { AiReviewConclusion, AiReviewSummaryStats } from '@/types'
import { buildStatCards } from '@/components/project/functional-testing/review/reviewSummary'
import { verdictPresentation } from '@/components/project/functional-testing/review/conclusionPresentation'

export function useReviewAiConclusion(getReviewId: () => string) {
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
      const task = await fetchReviewConclusion(getReviewId())
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
      url: `/project/ai/reviews/${getReviewId()}/conclusion`,
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

  return {
    phase,
    statistics,
    conclusion,
    verdict,
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
