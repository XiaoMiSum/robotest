import { defineStore } from 'pinia'
import {
  createAiModel,
  deleteAiModel,
  fetchAiEmbedding,
  fetchAiModels,
  fetchAiPromptDetail,
  fetchAiPrompts,
  fetchAiSettings,
  fetchAiUsageStatistics,
  resetAiPrompt,
  saveAiEmbedding,
  saveAiPrompt,
  updateAiModel,
  updateAiSettings,
} from '@/services/aiAdmin'
import { submitAiTask } from '@/services/ai'
import type {
  AiEmbeddingConfig,
  AiEmbeddingSavePayload,
  AiModel,
  AiModelCreatePayload,
  AiModelUpdatePayload,
  AiPromptDetail,
  AiPromptListItem,
  AiPromptSource,
  AiSettings,
  AiSettingsUpdatePayload,
  AiUsageQuery,
  AiUsageStatistics,
} from '@/types'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/** 用量缓存键（详设/交互 5：指标页签切换复用缓存，范围变化换键重取） */
export function usageCacheKey(query: AiUsageQuery): string {
  return `${query.from ?? ''}|${query.to ?? ''}|${query.groupBy ?? 'day'}`
}

/**
 * AI 配置中心单 store（交互设计 5）：设置、模型、向量、提示词、用量四分组共享，
 * 仅管理端页面首次进入时装载；密钥明文不入 state，只保留 keyConfigured 标记。
 * 读取类动作捕获错误落 *Error 供页面展示与重试（UI-PAGE-11）；
 * 变更类动作直接抛出，由调用方在弹窗 / toast 处理。
 */
export const useAiAdminStore = defineStore('aiAdmin', {
  state: () => ({
    settings: null as AiSettings | null,
    settingsError: '',
    models: [] as AiModel[],
    modelsError: '',
    embedding: null as AiEmbeddingConfig | null,
    embeddingError: '',
    prompts: [] as AiPromptListItem[],
    promptsError: '',
    /** 提示词详情按场景缓存：编辑弹窗重复打开不重复请求 */
    promptDetails: {} as Record<string, AiPromptDetail>,
    /** 用量按 usageCacheKey 缓存；同一范围 + 指标页签切换直接命中 */
    usage: {} as Record<string, AiUsageStatistics>,
    usageLoading: false,
    usageError: '',
    /** 全量重建进行中：任务 id 常驻跨分组；进度轮询由 aiTask store 承担 */
    reindexTaskId: null as string | null,
    reindexError: '',
  }),
  getters: {
    /** 已启用模型（页头默认模型下拉的数据源） */
    enabledModels: (state): AiModel[] => state.models.filter((model) => model.enabled),
    /** 重建进行中：期间向量分组维度输入只读（交互 2.1.3） */
    reindexing: (state): boolean => state.reindexTaskId !== null,
  },
  actions: {
    // ---------- 读取（错误落 state，不抛出） ----------

    async loadSettings() {
      this.settingsError = ''
      try {
        this.settings = await fetchAiSettings()
      } catch (error) {
        this.settingsError = errorMessage(error, '加载 AI 设置失败')
      }
    },

    async loadModels() {
      this.modelsError = ''
      try {
        this.models = (await fetchAiModels()).list
      } catch (error) {
        this.modelsError = errorMessage(error, '加载模型列表失败')
      }
    },

    async loadEmbedding() {
      this.embeddingError = ''
      try {
        this.embedding = await fetchAiEmbedding()
      } catch (error) {
        this.embeddingError = errorMessage(error, '加载向量 API 配置失败')
      }
    },

    async loadPrompts() {
      this.promptsError = ''
      try {
        this.prompts = await fetchAiPrompts()
      } catch (error) {
        this.promptsError = errorMessage(error, '加载提示词列表失败')
      }
    },

    /** 按范围懒加载用量；已缓存直接命中，失败落 usageError 供页面重试 */
    async loadUsage(query: AiUsageQuery) {
      const key = usageCacheKey(query)
      if (this.usage[key]) return
      this.usageLoading = true
      this.usageError = ''
      try {
        this.usage[key] = await fetchAiUsageStatistics(query)
      } catch (error) {
        this.usageError = errorMessage(error, '加载用量统计失败')
      } finally {
        this.usageLoading = false
      }
    },

    /** 提示词详情（编辑弹窗数据源）：命中缓存返回缓存，失败抛给调用方内联展示 */
    async loadPromptDetail(scene: string): Promise<AiPromptDetail> {
      const cached = this.promptDetails[scene]
      if (cached) return cached
      const detail = await fetchAiPromptDetail(scene)
      this.promptDetails[scene] = detail
      return detail
    },

    // ---------- 变更（成功改 state，失败抛出） ----------

    async updateSettings(payload: AiSettingsUpdatePayload): Promise<AiSettings> {
      const settings = await updateAiSettings(payload)
      this.settings = settings
      return settings
    },

    async createModel(payload: AiModelCreatePayload): Promise<void> {
      await createAiModel(payload)
      await this.loadModels()
    },

    async updateModel(modelId: string, payload: AiModelUpdatePayload): Promise<void> {
      await updateAiModel(modelId, payload)
      await this.loadModels()
    },

    async removeModel(modelId: string): Promise<void> {
      await deleteAiModel(modelId)
      await this.loadModels()
    },

    /** 保存向量配置：维度 / 算子变更时响应 requiresReindex 触发重建引导条 */
    async saveEmbedding(payload: AiEmbeddingSavePayload): Promise<AiEmbeddingConfig> {
      const embedding = await saveAiEmbedding(payload)
      this.embedding = embedding
      return embedding
    },

    async savePrompt(scene: string, content: string): Promise<void> {
      await saveAiPrompt(scene, content)
      delete this.promptDetails[scene]
      await this.loadPrompts()
    },

    async resetPrompt(scene: string): Promise<void> {
      await resetAiPrompt(scene)
      delete this.promptDetails[scene]
      await this.loadPrompts()
    },

    // ---------- 全量重建引导条（交互 2.1.3） ----------

    /** 发起全量重建（vector_reindex），进行中记录任务 id；失败落 reindexError */
    async startReindex(): Promise<boolean> {
      try {
        const task = await submitAiTask('vector_reindex')
        this.reindexTaskId = task.taskId
        this.reindexError = ''
        return true
      } catch (error) {
        this.reindexError = errorMessage(error, '发起向量重建失败')
        return false
      }
    },

    /** 重建任务到达终态：清除进行中标记，失败保留原因供「重试」 */
    settleReindex(failedMessage: string | null) {
      this.reindexTaskId = null
      this.reindexError = failedMessage ?? ''
    },
  },
})

/** 提示词来源展示口径（default = 内置默认，custom = 已自定义） */
export function promptSourceLabel(source: AiPromptSource): string {
  return source === 'custom' ? '自定义' : '内置默认'
}
