import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAssistantEntrance } from './useAssistantEntrance'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn(),
  hasPermission: vi.fn(),
}))

vi.mock('@/services/ai', () => ({ fetchAiStatus: mocks.fetchAiStatus }))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: mocks.hasPermission }),
}))

describe('composables/ai/useAssistantEntrance', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.hasPermission.mockReturnValue(true)
  })

  it('AI 可用且具备权限时入口可见', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    const { visible, refresh } = useAssistantEntrance()

    expect(visible.value).toBe(false)

    await refresh()

    expect(visible.value).toBe(true)
  })

  it('缺少 ai:task 权限时入口隐藏', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    mocks.hasPermission.mockReturnValue(false)
    const { visible, refresh } = useAssistantEntrance()

    await refresh()

    expect(visible.value).toBe(false)
    expect(mocks.hasPermission).toHaveBeenCalledWith('ai:task')
  })

  it('AI 未启用时入口隐藏', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: false })
    const { visible, refresh } = useAssistantEntrance()

    await refresh()

    expect(visible.value).toBe(false)
  })

  it('状态接口失败按不可用处理且不抛出（总册 4.5）', async () => {
    mocks.fetchAiStatus.mockRejectedValue(new Error('boom'))
    const { visible, refresh } = useAssistantEntrance()

    await expect(refresh()).resolves.toBeUndefined()

    expect(visible.value).toBe(false)
  })
})
