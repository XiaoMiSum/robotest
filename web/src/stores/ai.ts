import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AiChatModelView, AiStatus } from '@/types'
import { fetchAiStatus } from '@/services/ai'

/** 用户对话模型选择的本地记忆键（全局一份，不分空间，见基础设施详细设计 4.11） */
const CHAT_MODEL_KEY = 'ai.chatModelId'

/**
 * 缓存 GET /api/workspace/ai/status 结果，供全部 AI 入口组件显隐判断与模型选择器渲染。
 *
 * 进入业务布局后调用 load() 一次；AI 配置变更后由用户刷新页面感知（后端 30s 缓存）。
 * 负责校验并回收 localStorage 中失效的对话模型选择（4.11）。
 */
export const useAiStore = defineStore('ai', () => {
  const status = ref<AiStatus | null>(null)
  const loaded = ref(false)
  const selectedModelId = ref<string | null>(localStorage.getItem(CHAT_MODEL_KEY))

  const aiEnabled = computed(() => status.value?.enabled === true)
  const semanticSearch = computed(() => status.value?.semanticSearch ?? 'unavailable')
  /** 语义检索降级中（向量重建或 Embedding 未配置），检索类入口据此提示 */
  const semanticDegraded = computed(
    () => aiEnabled.value && semanticSearch.value !== 'available',
  )
  /** 已启用对话模型清单（供 AiModelSelect 渲染） */
  const chatModels = computed<AiChatModelView[]>(() => status.value?.chatModels ?? [])

  /** 写入/清除用户的对话模型选择记忆 */
  function setSelectedModelId(id: string | null): void {
    selectedModelId.value = id
    if (id) {
      localStorage.setItem(CHAT_MODEL_KEY, id)
    } else {
      localStorage.removeItem(CHAT_MODEL_KEY)
    }
  }

  /**
   * 交互式调用应携带的 modelId：记忆值仍在已启用清单中则用之；
   * 失效（不存在/已停用/已删除）则清除记忆并返回 undefined（由后端回退系统默认）。
   */
  function effectiveModelId(): string | undefined {
    const remembered = selectedModelId.value
    if (remembered && chatModels.value.some((m) => m.id === remembered)) {
      return remembered
    }
    if (remembered) {
      setSelectedModelId(null)
    }
    return undefined
  }

  async function load(force = false): Promise<void> {
    if (loaded.value && !force) return
    try {
      status.value = await fetchAiStatus()
    } catch {
      // 状态查询失败按未启用处理，隐藏全部 AI 入口
      status.value = { enabled: false }
    } finally {
      loaded.value = true
      // 刷新清单后回收失效的本地选择
      if (selectedModelId.value && !chatModels.value.some((m) => m.id === selectedModelId.value)) {
        setSelectedModelId(null)
      }
    }
  }

  function reset(): void {
    status.value = null
    loaded.value = false
  }

  return {
    status,
    loaded,
    aiEnabled,
    semanticSearch,
    semanticDegraded,
    chatModels,
    selectedModelId,
    setSelectedModelId,
    effectiveModelId,
    load,
    reset,
  }
})
