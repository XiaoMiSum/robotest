// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { AiChatModelView, AiStatus } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn<() => Promise<AiStatus>>(),
}))

vi.mock('@/services/ai', () => ({
  fetchAiStatus: mocks.fetchAiStatus,
}))

import { useAiStore } from './ai'

const DEFAULT_MODEL: AiChatModelView = { id: 'm-default', name: '默认模型', isDefault: true }

function statusWith(models: AiChatModelView[]): AiStatus {
  return { enabled: true, semanticSearch: 'available', chatModels: models }
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('ai store — effectiveModelId', () => {
  it('记忆值仍在清单中时携带记忆值', async () => {
    mocks.fetchAiStatus.mockResolvedValue(
      statusWith([DEFAULT_MODEL, { id: 'm-2', name: '次选模型', isDefault: false }]),
    )
    localStorage.setItem('ai.chatModelId', 'm-2')
    const ai = useAiStore()
    await ai.load()

    expect(ai.effectiveModelId()).toBe('m-2')
  })

  it('记忆失效时清除记忆并回退系统默认 id', async () => {
    mocks.fetchAiStatus.mockResolvedValue(statusWith([DEFAULT_MODEL]))
    const ai = useAiStore()
    await ai.load()
    ai.setSelectedModelId('m-gone')

    expect(ai.effectiveModelId()).toBe('m-default')
    expect(ai.selectedModelId).toBeNull()
    expect(localStorage.getItem('ai.chatModelId')).toBeNull()
  })

  it('无记忆时携带系统默认模型 id（与选择器展示一致）', async () => {
    mocks.fetchAiStatus.mockResolvedValue(
      statusWith([DEFAULT_MODEL, { id: 'm-2', name: '次选模型', isDefault: false }]),
    )
    const ai = useAiStore()
    await ai.load()

    expect(ai.effectiveModelId()).toBe('m-default')
  })

  it('清单为空（未加载/清单无默认）时返回 undefined 交后端兜底', () => {
    const ai = useAiStore()

    expect(ai.effectiveModelId()).toBeUndefined()
  })

  it('清单无默认标记时回退 undefined（与选择器展示口径一致）', async () => {
    mocks.fetchAiStatus.mockResolvedValue(statusWith([{ id: 'm-2', name: '次选模型', isDefault: false }]))
    const ai = useAiStore()
    await ai.load()

    expect(ai.effectiveModelId()).toBeUndefined()
  })
})
