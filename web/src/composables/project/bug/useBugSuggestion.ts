import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { suggestBugForm } from '@/services/ai'
import type { AiBugSuggestion } from '@/types'

export function useBugSuggestion({
  title,
  reproSteps,
  runDedup,
  expandPanel,
}: {
  title: () => string
  reproSteps: () => string | undefined
  runDedup: () => void
  expandPanel: () => void
}) {
  const suggestion = ref<AiBugSuggestion | null>(null)
  const loading = ref(false)
  const titleState = ref<'idle' | 'accepted' | 'dismissed'>('idle')
  const severityAdopted = ref(false)
  const priorityAdopted = ref(false)

  // 建议请求进行中不阻塞输入与提交（非侵入原则）；标题为空时后端会校验拒绝，先在前端拦截
  async function requestSuggestion(): Promise<void> {
    if (!title().trim()) {
      ElMessage.warning('请先输入缺陷标题')
      return
    }
    loading.value = true
    // 新请求触发时自动展开面板，便于查看最新结果
    expandPanel()
    // 查重与建议并发发起，各区域独立 loading，互不阻断
    runDedup()
    try {
      suggestion.value = await suggestBugForm({
        title: title(),
        reproSteps: reproSteps()?.trim() || undefined,
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

  return {
    suggestion,
    loading,
    titleState,
    severityAdopted,
    priorityAdopted,
    requestSuggestion,
  }
}
