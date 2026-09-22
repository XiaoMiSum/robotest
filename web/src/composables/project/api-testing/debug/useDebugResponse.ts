import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiDebugExecuteResp } from '@/types'

type ViewTab = 'body' | 'headers' | 'cookies'
type BodyMode = 'pretty' | 'raw' | 'preview'
type Lang = 'text' | 'json' | 'xml' | 'html' | 'javascript'

const STATUS_TEXT: Record<number, string> = {
  200: 'OK',
  201: 'Created',
  202: 'Accepted',
  204: 'No Content',
  301: 'Moved Permanently',
  302: 'Found',
  304: 'Not Modified',
  307: 'Temporary Redirect',
  308: 'Permanent Redirect',
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  405: 'Method Not Allowed',
  409: 'Conflict',
  410: 'Gone',
  422: 'Unprocessable Entity',
  429: 'Too Many Requests',
  500: 'Internal Server Error',
  502: 'Bad Gateway',
  503: 'Service Unavailable',
  504: 'Gateway Timeout',
}

const STATUS_DESC: Record<number, string> = {
  200: '请求成功',
  201: '已创建资源',
  202: '已接受处理',
  204: '无内容返回',
  301: '永久重定向',
  302: '临时重定向',
  304: '未修改（命中缓存）',
  400: '请求语法错误',
  401: '未认证或认证失效',
  403: '禁止访问',
  404: '资源不存在',
  405: '方法不允许',
  409: '资源冲突',
  410: '资源已删除',
  422: '请求语义错误',
  429: '请求过于频繁',
  500: '服务端内部错误',
  502: '网关错误',
  503: '服务不可用',
  504: '网关超时',
}

const STATUS_CONFIG: Record<number, { color: string; bg: string; label: string }> = {
  2: { color: '#49cc90', bg: '#eafaf1', label: '成功' },
  3: { color: '#61affe', bg: '#eaf3ff', label: '重定向' },
  4: { color: '#fca130', bg: '#fef6e8', label: '客户端错误' },
  5: { color: '#f93e3e', bg: '#feeaEA', label: '服务端错误' },
}

interface HighlightSegment {
  text: string
  highlight: boolean
}

interface CookieEntry {
  name: string
  value: string
  attributes: string[]
}

export function useDebugResponse(response: () => ApiDebugExecuteResp | null) {
  const activeTab = ref<ViewTab>('body')
  const bodyMode = ref<BodyMode>('pretty')
  const langOverride = ref<Lang | null>(null)

  watch(response, () => {
    activeTab.value = 'body'
    bodyMode.value = 'pretty'
    langOverride.value = null
    searchKeyword.value = ''
    searchExpanded.value = false
    currentMatch.value = 0
  })

  const statusConfig = computed(() => {
    const resp = response()
    if (resp?.status === 'error') return { color: '#f93e3e', bg: '#feeaEA', label: '请求失败' }
    const code = resp?.responseStatus
    if (!code) return { color: '#999', bg: '#f5f5f5', label: '—' }
    return STATUS_CONFIG[Math.floor(code / 100)] ?? { color: '#999', bg: '#f5f5f5', label: '' }
  })

  const statusCode = computed(() => {
    const resp = response()
    if (resp?.status === 'error') return 'ERROR'
    return resp?.responseStatus ? String(resp.responseStatus) : '—'
  })

  const statusTooltip = computed(() => {
    const code = response()?.responseStatus
    if (!code) return ''
    const text = STATUS_TEXT[code] ?? statusConfig.value.label
    const desc = STATUS_DESC[code] ?? ''
    return `${code} ${text}${desc ? ` — ${desc}` : ''}`
  })

  const bodyText = computed(() => {
    const body = response()?.responseBody
    if (body === null || body === undefined) return ''
    if (typeof body === 'string') return body
    try {
      return JSON.stringify(body, null, 2)
    } catch {
      return String(body)
    }
  })

  function detectLanguage(ct: string | undefined): Lang {
    if (!ct) return 'text'
    const value = ct.toLowerCase()
    if (value.includes('json')) return 'json'
    if (value.includes('html')) return 'html'
    if (value.includes('xml')) return 'xml'
    if (value.includes('javascript')) return 'javascript'
    return 'text'
  }

  const contentTypes = computed(() => {
    const headers = response()?.responseHeaders ?? {}
    const found: string[] = []
    for (const [name, value] of Object.entries(headers)) {
      if (name.toLowerCase() === 'content-type') found.push(value)
    }
    return found.join('; ')
  })

  const lang = computed<Lang>(() => langOverride.value ?? detectLanguage(contentTypes.value))

  const parsedJson = computed(() => {
    if (lang.value !== 'json' || !bodyText.value) return undefined
    try {
      const parsed: unknown = JSON.parse(bodyText.value)
      return typeof parsed === 'object' && parsed !== null ? parsed : undefined
    } catch {
      return undefined
    }
  })

  const headerEntries = computed(() => Object.entries(response()?.responseHeaders ?? {}))

  const cookieEntries = computed<CookieEntry[]>(() => {
    const headers = response()?.responseHeaders ?? {}
    const raw = Object.entries(headers)
      .filter(([name]) => name.toLowerCase() === 'set-cookie')
      .map(([, value]) => value)
      .join('\n')
    if (!raw) return []
    const result: CookieEntry[] = []
    for (const line of raw.split(/\r?\n/)) {
      const parts = line.split(';')
      const first = parts[0] ?? ''
      const eq = first.indexOf('=')
      if (eq <= 0) continue
      result.push({
        name: first.slice(0, eq).trim(),
        value: first.slice(eq + 1).trim(),
        attributes: parts.slice(1).map((p) => p.trim()).filter(Boolean),
      })
    }
    return result
  })

  function formatSize(size?: number): string {
    if (!size && size !== 0) return '—'
    if (size < 1024) return `${size} B`
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
    return `${(size / 1024 / 1024).toFixed(2)} MB`
  }

  const searchKeyword = ref('')
  const searchExpanded = ref(false)
  const searchInputRef = ref<HTMLInputElement>()

  const highlightResult = computed<{ segments: HighlightSegment[]; indexes: number[] } | null>(() => {
    const keyword = searchKeyword.value.trim()
    if (!keyword) return null
    const text = bodyText.value
    if (!text) return null
    const lower = text.toLowerCase()
    const kw = keyword.toLowerCase()
    const segments: HighlightSegment[] = []
    const indexes: number[] = []
    let cursor = 0
    let idx = lower.indexOf(kw)
    let count = 0
    while (idx >= 0 && count < 1000) {
      if (idx > cursor) segments.push({ text: text.slice(cursor, idx), highlight: false })
      segments.push({ text: text.slice(idx, idx + kw.length), highlight: true })
      indexes.push(segments.length - 1)
      cursor = idx + kw.length
      count += 1
      idx = lower.indexOf(kw, cursor)
    }
    if (cursor < text.length) segments.push({ text: text.slice(cursor), highlight: false })
    if (count === 0) return null
    return { segments, indexes }
  })

  const matchCount = computed(() => highlightResult.value?.indexes.length ?? 0)
  const currentMatch = ref(0)
  const currentMatchIndex = computed(() => {
    const indexes = highlightResult.value?.indexes
    if (!indexes?.length) return -1
    return indexes[Math.min(currentMatch.value, indexes.length - 1)]
  })

  function goTo(match: number) {
    const countVal = matchCount.value
    if (countVal === 0) return
    currentMatch.value = (match + countVal) % countVal
    void nextTick(() => {
      if (searchInputRef.value) searchInputRef.value.focus()
    })
  }

  function nextMatch() {
    goTo(currentMatch.value + 1)
  }

  function prevMatch() {
    goTo(currentMatch.value - 1)
  }

  function toggleSearch() {
    activeTab.value = 'body'
    searchExpanded.value = !searchExpanded.value
    if (!searchExpanded.value) {
      searchKeyword.value = ''
    }
    void nextTick(() => {
      if (searchExpanded.value && searchInputRef.value) searchInputRef.value.focus()
    })
  }

  const searchCountInfo = computed(() => {
    if (matchCount.value === 0 && searchKeyword.value.trim()) return '无匹配'
    return matchCount.value ? `${currentMatch.value + 1}/${matchCount.value}` : ''
  })

  const copying = ref(false)

  async function handleCopy() {
    const text = bodyText.value
    if (!text) return
    try {
      await navigator.clipboard.writeText(text)
      copying.value = true
      ElMessage.success('已复制响应体')
      setTimeout(() => { copying.value = false }, 1200)
    } catch {
      ElMessage.warning('复制失败，请手动选择复制')
    }
  }

  function onKeydown(event: KeyboardEvent) {
    const mod = event.ctrlKey || event.metaKey
    if (!response()) return
    if (mod && event.key.toLowerCase() === 'f') {
      event.preventDefault()
      toggleSearch()
      return
    }
    if (mod && event.key === 'g') {
      event.preventDefault()
      if (searchKeyword.value.trim()) {
        if (event.shiftKey) prevMatch()
        else nextMatch()
      }
      return
    }
    if (event.key === 'Escape' && searchExpanded.value) {
      toggleSearch()
    }
  }

  onMounted(() => window.addEventListener('keydown', onKeydown))
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

  return {
    activeTab,
    bodyMode,
    langOverride,
    statusConfig,
    statusCode,
    statusTooltip,
    bodyText,
    lang,
    parsedJson,
    headerEntries,
    cookieEntries,
    formatSize,
    searchKeyword,
    searchExpanded,
    searchInputRef,
    highlightResult,
    currentMatchIndex,
    searchCountInfo,
    nextMatch,
    prevMatch,
    toggleSearch,
    copying,
    handleCopy,
  }
}
