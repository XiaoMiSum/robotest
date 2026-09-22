import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import { useConversationList } from './useConversationList'
import type { AiConversation, AiConversationListResp } from '@/types'

/** 构造会话项（id 差异即可，标题/时间不影响分页分支断言） */
function conv(id: string): AiConversation {
  return { id, title: `会话${id}`, lastActiveAt: '2026-08-01T00:00:00.000Z' }
}

function page(ids: string[], nextCursor: string | null = null): AiConversationListResp {
  return { items: ids.map(conv), nextCursor }
}

/** 刷新微任务队列（watch 回调 + fetcher promise 链） */
const flush = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0))

describe('useConversationList 会话列表游标分页（详细设计 5.2/5.3）', () => {
  it('初始化即拉取首页，无游标；loading 置位后复位', async () => {
    const fetcher = vi.fn().mockResolvedValue(page(['c1', 'c2'], 'cur-1'))
    const { items, loading, hasMore } = useConversationList({ workspaceId: 'ws-1', fetcher })
    // immediate watch 同步触发 refresh
    expect(fetcher).toHaveBeenCalledWith(undefined, 20)
    expect(loading.value).toBe(true)
    await flush()
    expect(items.value.map((c) => c.id)).toEqual(['c1', 'c2'])
    expect(hasMore.value).toBe(true)
    expect(loading.value).toBe(false)
  })

  it('无工作空间时不拉取（列表保持空）', async () => {
    const fetcher = vi.fn().mockResolvedValue(page(['c1']))
    const { items } = useConversationList({ workspaceId: '', fetcher })
    await flush()
    expect(fetcher).not.toHaveBeenCalled()
    expect(items.value).toEqual([])
  })

  it('loadMore 携带 nextCursor 追加渲染，并更新游标', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(page(['c1'], 'cur-1'))
      .mockResolvedValueOnce(page(['c2', 'c3'], 'cur-2'))
    const { items, loadMore, hasMore } = useConversationList({ workspaceId: 'ws-1', fetcher, pageSize: 10 })
    await flush()
    await loadMore()
    expect(fetcher).toHaveBeenNthCalledWith(2, 'cur-1', 10)
    expect(items.value.map((c) => c.id)).toEqual(['c1', 'c2', 'c3'])
    expect(hasMore.value).toBe(true)
  })

  it('nextCursor 为空时 loadMore 不触发（终止条件）', async () => {
    const fetcher = vi.fn().mockResolvedValue(page(['c1']))
    const { loadMore, hasMore } = useConversationList({ workspaceId: 'ws-1', fetcher })
    await flush()
    expect(hasMore.value).toBe(false)
    await loadMore()
    await loadMore()
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('追加渲染按会话 id 去重（键集分页漂移补偿）', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(page(['c1', 'c2'], 'cur-1'))
      .mockResolvedValueOnce(page(['c2', 'c3']))
    const { items, loadMore } = useConversationList({ workspaceId: 'ws-1', fetcher })
    await flush()
    await loadMore()
    expect(items.value.map((c) => c.id)).toEqual(['c1', 'c2', 'c3'])
  })

  it('loadMore 进行中重复触发被防抖（loadingMore 互斥）', async () => {
    let resolveFetch!: (resp: AiConversationListResp) => void
    const fetcher = vi.fn().mockImplementationOnce(() => Promise.resolve(page(['c1'], 'cur-1')))
    fetcher.mockImplementation(
      () =>
        new Promise<AiConversationListResp>((resolve) => {
          resolveFetch = resolve
        }),
    )
    const { loadMore } = useConversationList({ workspaceId: 'ws-1', fetcher })
    await flush()
    const pending = loadMore()
    void loadMore()
    expect(fetcher).toHaveBeenCalledTimes(2) // 首页 1 次 + loadMore 1 次，第二次 loadMore 被互斥拦截
    resolveFetch(page(['c2']))
    await pending
    await flush()
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('prepend 本地置顶：新会话入列首行；同 id 重发替换不重复', async () => {
    const fetcher = vi.fn().mockResolvedValue(page(['c1', 'c2']))
    const { items, prepend } = useConversationList({ workspaceId: 'ws-1', fetcher })
    await flush()
    prepend(conv('c-new'))
    expect(items.value.map((c) => c.id)).toEqual(['c-new', 'c1', 'c2'])
    prepend({ id: 'c1', title: '更名后', lastActiveAt: '2026-08-02T00:00:00.000Z' })
    expect(items.value.map((c) => c.id)).toEqual(['c1', 'c-new', 'c2'])
    expect(items.value[0].title).toBe('更名后')
  })

  it('空间切换重置列表并重拉首页（会话按空间隔离）', async () => {
    const workspaceId = ref('ws-1')
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(page(['a1'], 'cur-a'))
      .mockResolvedValueOnce(page(['b1']))
    const { items, hasMore } = useConversationList({ workspaceId, fetcher })
    await flush()
    expect(items.value.map((c) => c.id)).toEqual(['a1'])
    workspaceId.value = 'ws-2'
    await nextTick()
    expect(fetcher).toHaveBeenCalledTimes(2)
    await flush()
    expect(items.value.map((c) => c.id)).toEqual(['b1'])
    expect(hasMore.value).toBe(false)
  })

  it('空间切换后旧空间的在途 refresh 响应被丢弃（seq 守卫防竞态覆盖）', async () => {
    let resolveOld!: (resp: AiConversationListResp) => void
    let resolveNew!: (resp: AiConversationListResp) => void
    const workspaceId = ref('ws-1')
    const fetcher = vi
      .fn()
      .mockImplementationOnce(
        () => new Promise<AiConversationListResp>((resolve) => { resolveOld = resolve }),
      )
      .mockImplementationOnce(
        () => new Promise<AiConversationListResp>((resolve) => { resolveNew = resolve }),
      )
    const { items } = useConversationList({ workspaceId, fetcher })
    // ws-1 首页请求在途；切换空间触发新 refresh（ws-2 请求亦在途）
    workspaceId.value = 'ws-2'
    await nextTick()
    // 新空间响应先落地
    resolveNew(page(['b1']))
    await flush()
    // 旧空间响应此刻才返回 → 必须被 seq 守卫丢弃
    resolveOld(page(['a1', 'a2'], 'cur-a'))
    await flush()
    expect(items.value.map((c) => c.id)).toEqual(['b1'])
  })

  it('loadMore 在途时切换空间，旧空间追加响应不污染新列表', async () => {
    let resolveMore!: (resp: AiConversationListResp) => void
    const workspaceId = ref('ws-1')
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(page(['c1'], 'cur-1')) // ws-1 首页
      .mockImplementationOnce(
        () => new Promise<AiConversationListResp>((resolve) => { resolveMore = resolve }),
      ) // ws-1 loadMore 在途
      .mockResolvedValueOnce(page(['b1'])) // ws-2 首页
    const { items, loadMore } = useConversationList({ workspaceId, fetcher })
    await flush()
    const pending = loadMore()
    workspaceId.value = 'ws-2'
    await nextTick()
    resolveMore(page(['c2', 'c3']))
    await pending
    await flush()
    expect(items.value.map((c) => c.id)).toEqual(['b1'])
  })

  it('首页拉取失败保留旧列表，不置 loading 死锁', async () => {
    const fetcher = vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(page(['c1']))
    const { items, loading, refresh } = useConversationList({ workspaceId: 'ws-1', fetcher })
    await flush()
    expect(loading.value).toBe(false)
    expect(items.value).toEqual([])
    await refresh()
    expect(items.value.map((c) => c.id)).toEqual(['c1'])
  })
})
