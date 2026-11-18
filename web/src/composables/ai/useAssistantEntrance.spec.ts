import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAssistantEntrance } from './useAssistantEntrance'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn(),
  routeMeta: { mode: 'project' } as { mode?: string },
}))

vi.mock('@/services/ai', () => ({ fetchAiStatus: mocks.fetchAiStatus }))
// 组合式函数在组件外调用，useRoute 须以桩替身（真实实现依赖组件注入上下文）
vi.mock('vue-router', () => ({
  useRoute: () => ({ meta: mocks.routeMeta }),
}))

describe('composables/ai/useAssistantEntrance', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.routeMeta.mode = 'project'
  })

  it('AI 可用时入口可见', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    const { visible, refresh } = useAssistantEntrance()

    expect(visible.value).toBe(false)

    await refresh()

    expect(visible.value).toBe(true)
  })

  it('AI 未启用时入口隐藏', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: false })
    const { visible, refresh } = useAssistantEntrance()

    await refresh()

    expect(visible.value).toBe(false)
  })

  it('空间级页面（mode=workspace）时入口隐藏：助手仅在项目上下文可用', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    mocks.routeMeta.mode = 'workspace'
    const { visible, refresh } = useAssistantEntrance()

    await refresh()

    expect(visible.value).toBe(false)
  })

  it('我的空间（mode=none）时入口隐藏', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    mocks.routeMeta.mode = 'none'
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
