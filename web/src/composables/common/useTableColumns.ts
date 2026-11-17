import { ref } from 'vue'

export interface TableColumnMeta {
  key: string
  label: string
  /** 不可隐藏列（选择列、固定操作列）：强制展示且面板内禁用勾选 */
  pinned?: boolean
}

const KEY_PREFIX = 'robotest_list_columns'
// 与 auth store 的 USER_KEY 同源；composable 不依赖 Pinia 实例，便于独立测试与复用
const USER_KEY = 'robotest_user'

function storageKey(pageKey: string, userId: string): string {
  return `${KEY_PREFIX}:${pageKey}:${userId || 'anonymous'}`
}

function defaultUserId(): string {
  try {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return ''
    const user = JSON.parse(raw) as { id?: unknown }
    return typeof user.id === 'string' ? user.id : ''
  } catch {
    return ''
  }
}

export function useTableColumns({
  pageKey,
  columns,
  getUserId = defaultUserId,
}: {
  pageKey: string
  columns: TableColumnMeta[]
  getUserId?: () => string
}) {
  const pinnedKeys = columns.filter((c) => c.pinned).map((c) => c.key)
  const optionalKeys = columns.filter((c) => !c.pinned).map((c) => c.key)
  const defaultKeys = [...pinnedKeys, ...optionalKeys]

  function read(): string[] {
    try {
      const raw = localStorage.getItem(storageKey(pageKey, getUserId()))
      if (!raw) return defaultKeys
      const parsed: unknown = JSON.parse(raw)
      if (!Array.isArray(parsed)) return defaultKeys
      const saved = parsed.filter((k): k is string => typeof k === 'string')
      // 过滤已下线列、强制补回不可隐藏列，避免版本迭代后出现空表或丢失操作入口
      const visible = new Set(saved.filter((k) => optionalKeys.includes(k)))
      return [...pinnedKeys, ...optionalKeys.filter((k) => visible.has(k))]
    } catch {
      return defaultKeys
    }
  }

  const visibleKeys = ref<string[]>(read())

  function persist(keys: string[]) {
    try {
      localStorage.setItem(storageKey(pageKey, getUserId()), JSON.stringify(keys))
    } catch {
      // 隐私模式/配额不足时降级为仅当前会话有效，不影响列表使用
    }
  }

  function isVisible(key: string): boolean {
    return visibleKeys.value.includes(key)
  }

  function setVisible(key: string, visible: boolean) {
    if (pinnedKeys.includes(key)) return
    const next = visible
      ? [...pinnedKeys, ...optionalKeys.filter((k) => k === key || visibleKeys.value.includes(k))]
      : visibleKeys.value.filter((k) => k !== key)
    visibleKeys.value = next
    persist(next)
  }

  function selectAll() {
    visibleKeys.value = defaultKeys
    persist(defaultKeys)
  }

  function reset() {
    visibleKeys.value = defaultKeys
    persist(defaultKeys)
  }

  return { visibleKeys, isVisible, setVisible, selectAll, reset, columns }
}
