import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AiStatus } from '@/types'
import { fetchAiStatus } from '@/services/ai'

/**
 * 缓存 GET /api/workspace/ai/status 结果，供全部 AI 入口组件显隐判断。
 *
 * 进入业务布局后调用 load() 一次；AI 配置变更后由用户刷新页面感知（后端 30s 缓存）。
 */
export const useAiStore = defineStore('ai', () => {
  const status = ref<AiStatus | null>(null)
  const loaded = ref(false)

  const aiEnabled = computed(() => status.value?.enabled === true)
  const semanticSearch = computed(() => status.value?.semanticSearch ?? 'unavailable')
  /** 语义检索降级中（向量重建或 Embedding 未配置），检索类入口据此提示 */
  const semanticDegraded = computed(
    () => aiEnabled.value && semanticSearch.value !== 'available',
  )

  async function load(force = false): Promise<void> {
    if (loaded.value && !force) return
    try {
      status.value = await fetchAiStatus()
    } catch {
      // 状态查询失败按未启用处理，隐藏全部 AI 入口
      status.value = { enabled: false }
    } finally {
      loaded.value = true
    }
  }

  function reset(): void {
    status.value = null
    loaded.value = false
  }

  return { status, loaded, aiEnabled, semanticSearch, semanticDegraded, load, reset }
})
