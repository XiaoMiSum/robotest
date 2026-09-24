import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Ref } from 'vue'
import { ref } from 'vue'
import type { AiChatModel, AiProviderPreset } from '@/types'

const mocks = vi.hoisted(() => {
  const chatModels: AiChatModel[] = []
  const fn = () => Promise.resolve(chatModels)
  return {
    chatModels,
    fetch: vi.fn(fn),
    create: vi.fn(() => Promise.resolve(chatModels[0] as AiChatModel)),
    update: vi.fn(() => Promise.resolve(chatModels[0] as AiChatModel)),
    remove: vi.fn(() => Promise.resolve()),
    setDefault: vi.fn(() => Promise.resolve()),
    setEnabled: vi.fn(() => Promise.resolve()),
    test: vi.fn(() => Promise.resolve({ ok: true, latencyMs: 12, detail: 'ok' })),
  }
})

vi.mock('@/services/admin', () => ({
  fetchAiChatModels: mocks.fetch,
  createAiChatModel: mocks.create,
  updateAiChatModel: mocks.update,
  deleteAiChatModel: mocks.remove,
  setAiChatModelDefault: mocks.setDefault,
  setAiChatModelEnabled: mocks.setEnabled,
  testAiConnectivity: mocks.test,
}))

// ElMessage/ElMessageBox 在 jsdom 下为空壳即可，确认/提示分支不实际弹出
vi.mock('element-plus', () => ({
  ElMessage: { success: () => {}, error: () => {}, warning: () => {} },
  ElMessageBox: {
    confirm: vi.fn(() => Promise.resolve('confirm')),
  },
}))

import { useAiChatModels } from './useAiChatModels'

function model(over: Partial<AiChatModel> = {}): AiChatModel {
  return {
    id: 'm1',
    name: 'GPT-4o',
    provider: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o',
    apiKey: { configured: true, keySuffix: '1234' },
    extraParams: {},
    enabled: true,
    isDefault: false,
    updatedBy: null,
    updatedAt: null,
    ...over,
  }
}

describe('useAiChatModels 模型编辑流', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.chatModels.length = 0
    mocks.chatModels.push(model())
  })

  function setup() {
    const saving = ref(false)
    const deps = {
      presetOf: () => undefined as AiProviderPreset | undefined,
      parsing: { parseJsonObject: (text: string, _label: string) => JSON.parse(text) as Record<string, unknown> },
      saving: { saving: saving as Ref<boolean> },
    }
    return { ...useAiChatModels(deps), saving }
  }

  it('openCreate 复位表单并打开弹窗', () => {
    const s = setup()
    s.openCreateModel()
    expect(s.modelDialogVisible.value).toBe(true)
    expect(s.modelDialogMode.value).toBe('create')
    expect(s.editingModelId.value).toBeNull()
    expect(s.modelForm.name).toBe('')
  })

  it('openEdit 从行数据填充表单（密钥不回显明文）', async () => {
    const s = setup()
    await s.refresh()
    s.openEditModel(s.chatModels.value[0] as AiChatModel)
    expect(s.modelForm.name).toBe('GPT-4o')
    expect(s.modelForm.provider).toBe('openai')
    expect(s.modelForm.apiKeyConfigured).toBe(true)
    expect(s.modelForm.keySuffix).toBe('1234')
    expect(s.modelForm.apiKey).toBe('')
  })

  it('handleModelSave（新建）后刷新列表并关闭弹窗', async () => {
    const s = setup()
    s.openCreateModel()
    s.modelForm.name = 'DeepSeek'
    s.modelForm.baseUrl = 'https://api.deepseek.com/v1'
    s.modelForm.model = 'deepseek-chat'
    s.modelForm.apiKey = 'sk-test'
    await s.handleModelSave()
    expect(mocks.create).toHaveBeenCalledTimes(1)
    expect(mocks.fetch).toHaveBeenCalled()
    expect(s.modelDialogVisible.value).toBe(false)
    expect(s.saving.value).toBe(false)
  })

  it('handleModelSave 非法 JSON 阻止提交', async () => {
    const s = setup()
    s.openCreateModel()
    s.modelForm.name = 'DeepSeek'
    s.modelForm.baseUrl = 'https://api.deepseek.com/v1'
    s.modelForm.model = 'deepseek-chat'
    s.modelForm.apiKey = 'sk-test'
    s.modelForm.customParams = '{ bad'
    await s.handleModelSave()
    expect(mocks.create).not.toHaveBeenCalled()
    expect(s.modelDialogVisible.value).toBe(true)
  })

  it('必填字段不完整时阻止对话模型保存', async () => {
    const s = setup()
    s.openCreateModel()
    s.modelForm.name = 'DeepSeek'
    await s.handleModelSave()
    expect(mocks.create).not.toHaveBeenCalled()
    expect(s.modelDialogVisible.value).toBe(true)
  })

  it('handleToggleEnabled 停用时二次确认后调用 setEnabled(false)', async () => {
    const s = setup()
    await s.refresh()
    await s.handleToggleEnabled(s.chatModels.value[0] as AiChatModel)
    expect(mocks.setEnabled).toHaveBeenCalledWith('m1', false)
    expect(mocks.fetch).toHaveBeenCalled()
  })

  it('handleDeleteModel 调用删除并刷新', async () => {
    const s = setup()
    await s.refresh()
    await s.handleDeleteModel(s.chatModels.value[0] as AiChatModel)
    expect(mocks.remove).toHaveBeenCalledWith('m1')
    expect(mocks.fetch).toHaveBeenCalled()
  })

  it('handleSetDefault 调用设为默认', async () => {
    const s = setup()
    await s.refresh()
    await s.handleSetDefault(s.chatModels.value[0] as AiChatModel)
    expect(mocks.setDefault).toHaveBeenCalledWith('m1')
  })

  it('handleRowTest 复用已保存模型 id', async () => {
    const s = setup()
    await s.refresh()
    await s.handleRowTest(s.chatModels.value[0] as AiChatModel)
    expect(mocks.test).toHaveBeenCalledWith({ target: 'chat', modelId: 'm1' })
    expect(s.rowTestingId.value).toBeNull()
  })

  it('handleModelDialogTest 使用临时配置透传', async () => {
    const s = setup()
    s.openCreateModel()
    s.modelForm.name = 'DeepSeek'
    s.modelForm.baseUrl = 'https://api.deepseek.com/v1'
    s.modelForm.model = 'deepseek-chat'
    s.modelForm.apiKey = 'sk-test'
    await s.handleModelDialogTest()
    expect(mocks.test).toHaveBeenCalledWith(
      expect.objectContaining({ target: 'chat', chat: expect.any(Object) }),
    )
    expect(s.testing.modelDialog).toBe(false)
  })

  it('enabledCount 仅统计启用模型', async () => {
    const s = setup()
    mocks.chatModels.length = 0
    mocks.chatModels.push(model({ enabled: true }), model({ id: 'm2', enabled: false }))
    await s.refresh()
    expect(s.enabledCount.value).toBe(1)
  })
})