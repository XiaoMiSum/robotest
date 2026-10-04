import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type {
  AiEmbeddingConfig,
  AiModel,
  AiPromptDetail,
  AiPromptListItem,
  AiSettings,
  AiUsageStatistics,
} from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiSettings: vi.fn(),
  updateAiSettings: vi.fn(),
  fetchAiModels: vi.fn(),
  createAiModel: vi.fn(),
  updateAiModel: vi.fn(),
  deleteAiModel: vi.fn(),
  fetchAiEmbedding: vi.fn(),
  saveAiEmbedding: vi.fn(),
  fetchAiPrompts: vi.fn(),
  fetchAiPromptDetail: vi.fn(),
  saveAiPrompt: vi.fn(),
  resetAiPrompt: vi.fn(),
  fetchAiUsageStatistics: vi.fn(),
  submitAiTask: vi.fn(),
}))

vi.mock('@/services/aiAdmin', () => mocks)
vi.mock('@/services/ai', () => ({ submitAiTask: mocks.submitAiTask }))

import { usageCacheKey, useAiAdminStore } from './aiAdmin'

function makeSettings(overrides: Partial<AiSettings> = {}): AiSettings {
  return {
    enabled: true,
    defaultModelId: 'm1',
    defaultModelName: 'gpt-x',
    taskTimeoutSeconds: 600,
    taskMaxRetries: 2,
    modelReady: true,
    embeddingReady: false,
    available: true,
    ...overrides,
  }
}

function makeModel(overrides: Partial<AiModel> = {}): AiModel {
  return {
    id: 'm1',
    name: 'gpt-x',
    provider: 'openai',
    baseUrl: 'https://api.example.com/v1',
    modelName: 'gpt-x-mini',
    capabilities: ['chat'],
    priority: 100,
    enabled: true,
    inputPrice: 0.5,
    outputPrice: 1.5,
    keyConfigured: true,
    lastTest: null,
    ...overrides,
  }
}

function makeEmbedding(overrides: Partial<AiEmbeddingConfig> = {}): AiEmbeddingConfig {
  return {
    provider: 'openai',
    baseUrl: 'https://api.example.com/v1',
    embeddingModel: 'text-embedding-x',
    dimensions: 1536,
    operator: 'cosine',
    indexType: 'hnsw',
    enabled: true,
    keyConfigured: true,
    versions: [],
    requiresReindex: false,
    lastTest: null,
    ...overrides,
  }
}

function makePrompt(overrides: Partial<AiPromptListItem> = {}): AiPromptListItem {
  return {
    scene: 'requirement_split',
    name: '需求拆解',
    summary: '将需求拆分为…',
    source: 'default',
    updatedByName: null,
    updatedAt: null,
    ...overrides,
  }
}

function makePromptDetail(overrides: Partial<AiPromptDetail> = {}): AiPromptDetail {
  return {
    scene: 'requirement_split',
    name: '需求拆解',
    content: '原始内容',
    variables: [],
    source: 'default',
    version: 1,
    updatedAt: null,
    ...overrides,
  }
}

function makeUsage(): AiUsageStatistics {
  return {
    summary: {
      totalCalls: 10,
      failedCalls: 1,
      successRate: 0.9,
      totalTokens: 1000,
      avgLatencyMs: 800,
      totalCost: 0.42,
    },
    series: [{ key: '2026-10-01', keyName: null, calls: 10, failed: 1, tokens: 1000, avgLatencyMs: 800, cost: 0.42 }],
  }
}

describe('stores/aiAdmin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('用量缓存键：范围 + 分组，缺省 day', () => {
    expect(usageCacheKey({ from: '2026-09-01', to: '2026-10-01', groupBy: 'model' })).toBe(
      '2026-09-01|2026-10-01|model',
    )
    expect(usageCacheKey({})).toBe('||day')
  })

  it('四分组读取成功写入 state', async () => {
    mocks.fetchAiSettings.mockResolvedValue(makeSettings())
    mocks.fetchAiModels.mockResolvedValue({ list: [makeModel()], total: 1 })
    mocks.fetchAiEmbedding.mockResolvedValue(makeEmbedding())
    mocks.fetchAiPrompts.mockResolvedValue([makePrompt()])

    const store = useAiAdminStore()
    await Promise.all([
      store.loadSettings(),
      store.loadModels(),
      store.loadEmbedding(),
      store.loadPrompts(),
    ])

    expect(store.settings?.defaultModelName).toBe('gpt-x')
    expect(store.models).toHaveLength(1)
    expect(store.embedding?.dimensions).toBe(1536)
    expect(store.prompts[0]?.scene).toBe('requirement_split')
    expect(store.settingsError).toBe('')
    expect(store.modelsError).toBe('')
    expect(store.embeddingError).toBe('')
    expect(store.promptsError).toBe('')
  })

  it('读取失败落 *Error 状态（UI-PAGE-11 页面捕获重试）', async () => {
    mocks.fetchAiSettings.mockRejectedValue(new Error('无权限'))
    mocks.fetchAiModels.mockRejectedValue(new Error('服务不可用'))
    mocks.fetchAiEmbedding.mockRejectedValue(new Error('boom'))
    mocks.fetchAiPrompts.mockRejectedValue(new Error('boom'))

    const store = useAiAdminStore()
    await store.loadSettings()
    await store.loadModels()
    await store.loadEmbedding()
    await store.loadPrompts()

    expect(store.settingsError).toBe('无权限')
    expect(store.modelsError).toBe('服务不可用')
    expect(store.embeddingError).toBe('boom')
    expect(store.promptsError).toBe('boom')
    expect(store.settings).toBeNull()
    expect(store.models).toEqual([])
  })

  it('读取失败后重试成功清空错误', async () => {
    mocks.fetchAiModels.mockRejectedValueOnce(new Error('网络错误')).mockResolvedValueOnce({
      list: [makeModel()],
      total: 1,
    })
    const store = useAiAdminStore()
    await store.loadModels()
    expect(store.modelsError).toBe('网络错误')
    await store.loadModels()
    expect(store.modelsError).toBe('')
    expect(store.models).toHaveLength(1)
  })

  it('用量按缓存键懒加载：命中不重复请求，换范围重取', async () => {
    mocks.fetchAiUsageStatistics.mockResolvedValue(makeUsage())
    const store = useAiAdminStore()
    const query = { from: '2026-09-01', to: '2026-10-01' } as const

    await store.loadUsage(query)
    await store.loadUsage(query)
    expect(mocks.fetchAiUsageStatistics).toHaveBeenCalledTimes(1)
    expect(store.usage[usageCacheKey(query)]?.summary.totalCalls).toBe(10)

    await store.loadUsage({ ...query, groupBy: 'model' })
    expect(mocks.fetchAiUsageStatistics).toHaveBeenCalledTimes(2)
    expect(store.usageLoading).toBe(false)
  })

  it('用量加载失败落 usageError 且不缓存，可重试', async () => {
    mocks.fetchAiUsageStatistics.mockRejectedValueOnce(new Error('区间非法')).mockResolvedValueOnce(
      makeUsage(),
    )
    const store = useAiAdminStore()
    const query = { from: '2026-09-01', to: '2026-10-01' }

    await store.loadUsage(query)
    expect(store.usageError).toBe('区间非法')
    expect(store.usage[usageCacheKey(query)]).toBeUndefined()

    await store.loadUsage(query)
    expect(store.usageError).toBe('')
    expect(store.usage[usageCacheKey(query)]).toBeDefined()
  })

  it('提示词详情按场景缓存，未命中失败抛出', async () => {
    mocks.fetchAiPromptDetail.mockResolvedValue(makePromptDetail())
    const store = useAiAdminStore()

    const first = await store.loadPromptDetail('requirement_split')
    const second = await store.loadPromptDetail('requirement_split')
    expect(mocks.fetchAiPromptDetail).toHaveBeenCalledTimes(1)
    // 首次返回原始对象、二次读缓存为 reactive 包装，仅深等比较
    expect(second).toStrictEqual(first)

    mocks.fetchAiPromptDetail.mockRejectedValueOnce(new Error('场景不存在'))
    await expect(store.loadPromptDetail('other')).rejects.toThrow('场景不存在')
  })

  it('设置更新成功回写 state，失败向上抛出', async () => {
    mocks.updateAiSettings.mockResolvedValue(makeSettings({ enabled: false }))
    const store = useAiAdminStore()

    const updated = await store.updateSettings({ enabled: false })
    expect(updated.enabled).toBe(false)
    expect(store.settings?.enabled).toBe(false)

    mocks.updateAiSettings.mockRejectedValue(new Error('默认模型引用冲突'))
    await expect(store.updateSettings({ defaultModelId: 'x' })).rejects.toThrow('默认模型引用冲突')
  })

  it('模型创建 / 更新 / 删除后刷新列表', async () => {
    mocks.createAiModel.mockResolvedValue(makeModel())
    mocks.updateAiModel.mockResolvedValue(makeModel({ name: 'renamed' }))
    mocks.deleteAiModel.mockResolvedValue(undefined)
    mocks.fetchAiModels.mockResolvedValue({ list: [makeModel()], total: 1 })
    const store = useAiAdminStore()

    await store.createModel({
      name: 'gpt-x',
      provider: 'openai',
      baseUrl: 'https://api.example.com/v1',
      apiKey: 'sk-1',
      modelName: 'gpt-x-mini',
      capabilities: ['chat'],
    })
    await store.updateModel('m1', { name: 'renamed' })
    await store.removeModel('m1')

    expect(mocks.fetchAiModels).toHaveBeenCalledTimes(3)
    expect(store.models).toHaveLength(1)
  })

  it('模型变更失败直接抛出且不刷新列表', async () => {
    mocks.createAiModel.mockRejectedValue(new Error('模型名称重复'))
    const store = useAiAdminStore()

    await expect(
      store.createModel({
        name: 'gpt-x',
        provider: 'openai',
        baseUrl: 'https://api.example.com/v1',
        apiKey: 'sk-1',
        modelName: 'gpt-x-mini',
        capabilities: ['chat'],
      }),
    ).rejects.toThrow('模型名称重复')
    expect(mocks.fetchAiModels).not.toHaveBeenCalled()
  })

  it('保存向量配置回写 embedding（requiresReindex 透传给引导条）', async () => {
    mocks.saveAiEmbedding.mockResolvedValue(makeEmbedding({ requiresReindex: true, dimensions: 1024 }))
    const store = useAiAdminStore()

    const resp = await store.saveEmbedding({ dimensions: 1024 })
    expect(resp.requiresReindex).toBe(true)
    expect(store.embedding?.dimensions).toBe(1024)
    expect(store.reindexing).toBe(false)
  })

  it('提示词保存 / 重置后失效详情缓存并刷新列表', async () => {
    mocks.fetchAiPromptDetail.mockResolvedValue(makePromptDetail())
    mocks.saveAiPrompt.mockResolvedValue(makePromptDetail({ source: 'custom' }))
    mocks.resetAiPrompt.mockResolvedValue(makePromptDetail())
    mocks.fetchAiPrompts.mockResolvedValue([makePrompt({ source: 'custom' })])
    const store = useAiAdminStore()

    await store.loadPromptDetail('requirement_split')
    expect(store.promptDetails['requirement_split']).toBeDefined()

    await store.savePrompt('requirement_split', '新内容')
    expect(store.promptDetails['requirement_split']).toBeUndefined()
    expect(mocks.fetchAiPrompts).toHaveBeenCalledTimes(1)

    await store.resetPrompt('requirement_split')
    expect(mocks.resetAiPrompt).toHaveBeenCalledTimes(1)
    expect(mocks.fetchAiPrompts).toHaveBeenCalledTimes(2)
  })

  it('发起重建成功记录任务 id，失败落 reindexError', async () => {
    mocks.submitAiTask.mockResolvedValue({ taskId: 't1' })
    const store = useAiAdminStore()

    expect(await store.startReindex()).toBe(true)
    expect(store.reindexTaskId).toBe('t1')
    expect(store.reindexing).toBe(true)

    store.settleReindex(null)
    expect(store.reindexTaskId).toBeNull()
    expect(store.reindexing).toBe(false)
    expect(store.reindexError).toBe('')

    mocks.submitAiTask.mockRejectedValue(new Error('不支持的任务类型'))
    expect(await store.startReindex()).toBe(false)
    expect(store.reindexTaskId).toBeNull()
    expect(store.reindexError).toBe('不支持的任务类型')

    store.settleReindex('模型超时')
    expect(store.reindexError).toBe('模型超时')
  })

  it('enabledModels 仅含启用模型', async () => {
    mocks.fetchAiModels.mockResolvedValue({
      list: [makeModel(), makeModel({ id: 'm2', enabled: false })],
      total: 2,
    })
    const store = useAiAdminStore()
    await store.loadModels()
    expect(store.enabledModels.map((model) => model.id)).toEqual(['m1'])
  })
})
