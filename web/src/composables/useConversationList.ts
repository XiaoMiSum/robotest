import { computed, ref, watch, type Ref } from 'vue'
import { fetchConversations } from '@/services/assistant'
import type { AiConversation, AiConversationListResp } from '@/types'

/**
 * 助手会话列表游标分页组合式函数（全局智能助手详细设计 5.2/5.3）：
 * 触底加载、nextCursor 终止、id 去重、本地置顶、空间切换重置。
 * fetcher 可注入（默认 fetchConversations），单测覆盖分页分支。
 */
export interface UseConversationListOptions {
  /** 当前工作空间 ID（支持响应式 ref/computed）；变化时重置列表（会话按空间隔离，5.2） */
  workspaceId: string | (() => string) | Ref<string>
  /** 会话列表拉取函数，注入便于单测（默认 fetchConversations） */
  fetcher?: (cursor?: string, size?: number) => Promise<AiConversationListResp>
  /** 每页条数（默认 20，上限由后端钳制 50） */
  pageSize?: number
}

export function useConversationList(options: UseConversationListOptions) {
  const fetcher = options.fetcher ?? fetchConversations
  const pageSize = options.pageSize ?? 20

  // 归一化 workspaceId：支持字符串/响应式 ref/函数 getter 三种形态
  const workspaceIdRef = computed<string>(() => {
    const raw = options.workspaceId
    if (typeof raw === 'function') return raw()
    if (raw && typeof raw === 'object' && 'value' in raw) return raw.value
    return raw
  })

  const items = ref<AiConversation[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const nextCursor = ref<string | null>(null)

  const hasMore = computed(() => nextCursor.value !== null)

  // 追加渲染按会话 id 去重防御（3.1 键集分页漂移补偿约定）
  function appendDedup(rows: AiConversation[]): void {
    const seen = new Set(items.value.map((c) => c.id))
    for (const row of rows) {
      if (seen.has(row.id)) continue
      seen.add(row.id)
      items.value.push(row)
    }
  }

  /** 首页重载（面板打开与空间切换时调用） */
  async function refresh(): Promise<void> {
    if (!workspaceIdRef.value || loading.value) return
    loading.value = true
    try {
      const resp = await fetcher(undefined, pageSize)
      items.value = resp.items
      nextCursor.value = resp.nextCursor
    } catch {
      // 拉取失败保留旧列表，下次触发可重试（不置 loading 死锁）
    } finally {
      loading.value = false
    }
  }

  /** 触底加载下一页；无更多或加载中不触发 */
  async function loadMore(): Promise<void> {
    if (loading.value || loadingMore.value || !hasMore.value) return
    loadingMore.value = true
    try {
      const resp = await fetcher(nextCursor.value ?? undefined, pageSize)
      appendDedup(resp.items)
      nextCursor.value = resp.nextCursor
    } catch {
      // 拉取失败保留游标，触底可重试
    } finally {
      loadingMore.value = false
    }
  }

  /** 本地置顶（发送消息/新建会话后调用，不重拉列表；与键集分页漂移补偿一致，3.1） */
  function prepend(conversation: AiConversation): void {
    items.value = [conversation, ...items.value.filter((c) => c.id !== conversation.id)]
  }

  // 空间切换重置会话列表（会话按空间隔离，5.2）
  watch(
    workspaceIdRef,
    () => {
      nextCursor.value = null
      items.value = []
      void refresh()
    },
    { immediate: true },
  )

  return { items, loading, loadingMore, hasMore, refresh, loadMore, prepend }
}
