import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick } from 'vue'
import type { EffectScope } from 'vue'
import type { AiConfig, AiProviderPreset, AiSettingSchemaGroup } from '@/types'

const mocks = vi.hoisted(() => {
  const preset: AiProviderPreset = {
    key: 'zhipu',
    name: '智谱 AI',
    scopes: ['chat', 'embedding'],
    defaultBaseUrl: { chat: 'https://open.bigmodel.cn/api/paas/v4' },
    modelHints: { chat: ['glm-4-plus'], embedding: ['embedding-3'] },
    uniqueParams: {
      chat: [
        { key: 'thinking.type', type: 'enum', defaultValue: 'disabled', options: ['enabled', 'disabled'], label: '思考模式', description: '' },
      ],
      embedding: [],
    },
  }
  const schema: AiSettingSchemaGroup[] = [
    {
      group: 'g',
      groupLabel: '分组',
      items: [
        { key: 'maxRetry', type: 'int', label: '重试', description: '', defaultValue: 3, min: 0, max: 10, step: 1 },
      ],
    },
  ]
  const build = (over: Partial<AiConfig> = {}): AiConfig => ({
    enabled: false,
    embedding: null,
    settings: { maxRetry: 3 },
    updatedAt: null,
    ...over,
  })
  const config = build()
  return {
    preset,
    schema,
    config,
    build,
    fetchProviders: vi.fn(() => Promise.resolve([preset])),
    fetchSchema: vi.fn(() => Promise.resolve(schema)),
    fetchConfig: vi.fn(() => Promise.resolve(config)),
    save: vi.fn(() => Promise.resolve(build({ enabled: true, settings: { maxRetry: 5 } }))),
    fetchTask: vi.fn(() => Promise.resolve(null)),
    retryTask: vi.fn(() => Promise.resolve()),
    fetchStats: vi.fn(() => Promise.resolve({ totalCalls: 0, totalTokens: 0, failedCalls: 0, items: [] })),
    test: vi.fn(() => Promise.resolve({ ok: true, latencyMs: 12, detail: 'ok' })),
  }
})

vi.mock('@/services/admin', () => ({
  fetchAiProviders: mocks.fetchProviders,
  fetchAiSettingsSchema: mocks.fetchSchema,
  fetchAiConfig: mocks.fetchConfig,
  saveAiConfig: mocks.save,
  fetchAiRebuildTask: mocks.fetchTask,
  retryAiRebuildTask: mocks.retryTask,
  fetchAiStatistics: mocks.fetchStats,
  testAiConnectivity: mocks.test,
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: () => {}, error: () => {}, warning: () => {} },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve('confirm')) },
}))

import { useAiConfigPage } from './useAiConfigPage'

const AUTO_SAVE_DEBOUNCE = 800

describe('useAiConfigPage 配置编排', () => {
  let scope: EffectScope

  // watch/onBeforeUnmount 需要活跃的 effect scope（无组件挂载时）
  function setup() {
    scope = effectScope()
    return scope.run(() => useAiConfigPage()) as ReturnType<typeof useAiConfigPage>
  }

  afterEach(() => {
    scope?.stop()
    vi.useRealTimers()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.config.enabled = false
    mocks.config.settings = { maxRetry: 3 }
  })

  it('loadAll 按 schema 填充表单并置 hydrated', async () => {
    const s = setup()
    await s.loadAll()
    expect(s.providers.value.length).toBe(1)
    expect(s.settingsSchema.value[0]?.items[0]?.key).toBe('maxRetry')
    expect(s.settingsForm.maxRetry).toBe(3)
    expect(s.hydrated.value).toBe(true)
  })

  it('settings 变更经防抖触发自动保存并回写后端结果', async () => {
    vi.useFakeTimers()
    const s = setup()
    await s.loadAll()
    s.settingsForm.maxRetry = 9
    await nextTick()
    vi.advanceTimersByTime(AUTO_SAVE_DEBOUNCE)
    await vi.advanceTimersByTimeAsync(0)
    await nextTick()
    expect(mocks.save).toHaveBeenCalled()
    expect(s.config.value?.settings.maxRetry).toBe(5)
  })

  it('开启总开关需已启用对话模型，否则拒绝', async () => {
    const s = setup()
    await s.loadAll()
    s.chatModelsEnabledCount.value = 0
    const allowed = await s.handleMasterBeforeChange()
    expect(allowed).toBe(false)
  })

  it('开启总开关且已有启用模型时放行', async () => {
    const s = setup()
    await s.loadAll()
    s.chatModelsEnabledCount.value = 1
    const allowed = await s.handleMasterBeforeChange()
    expect(allowed).toBe(true)
  })

  it('关闭总开关需二次确认，未确认则拒绝', async () => {
    const s = setup()
    await s.loadAll()
    s.form.enabled = true
    const { ElMessageBox } = await import('element-plus')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    const allowed = await s.handleMasterBeforeChange()
    expect(allowed).toBe(false)
  })

  it('handleSaveEmbedding 成功保存并提示', async () => {
    const s = setup()
    await s.loadAll()
    s.form.embedding.provider = 'zhipu'
    s.form.embedding.baseUrl = 'https://open.bigmodel.cn/api/paas/v4'
    s.form.embedding.model = 'embedding-3'
    s.form.embedding.dimension = 128
    s.form.embedding.apiKey = 'sk-test'
    mocks.save.mockResolvedValueOnce(
      mocks.build({
        embedding: {
          provider: 'zhipu',
          baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
          model: 'embedding-3',
          dimension: 128,
          apiKey: { configured: true, keySuffix: 'abcd' },
          extraParams: {},
        },
      }),
    )
    await s.handleSaveEmbedding()
    expect(mocks.save).toHaveBeenCalled()
    expect(s.saving.value).toBe(false)
  })

  it('handleTestEmbedding 透传 embedding 临时配置', async () => {
    const s = setup()
    await s.loadAll()
    s.form.embedding.provider = 'zhipu'
    s.form.embedding.baseUrl = 'https://open.bigmodel.cn/api/paas/v4'
    s.form.embedding.model = 'embedding-3'
    s.form.embedding.dimension = 128
    s.form.embedding.apiKey = 'sk-test'
    await s.handleTestEmbedding()
    expect(mocks.test).toHaveBeenCalledWith(
      expect.objectContaining({ target: 'embedding', embedding: expect.objectContaining({ provider: 'zhipu' }) }),
    )
    expect(s.testing.embedding).toBe(false)
  })

  it('Embedding 核心字段不完整时阻止保存和测试', async () => {
    const s = setup()
    await s.loadAll()
    s.form.embedding.provider = 'zhipu'
    await s.handleSaveEmbedding()
    await s.handleTestEmbedding()
    expect(mocks.save).not.toHaveBeenCalled()
    expect(mocks.test).not.toHaveBeenCalled()
  })
})
