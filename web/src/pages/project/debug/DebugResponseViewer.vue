<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiDebugExecuteResp } from '@/types'
import JsonResponseView from './JsonResponseView.vue'

const props = defineProps<{
  response: ApiDebugExecuteResp | null
}>()

type ViewTab = 'body' | 'headers' | 'cookies'
type BodyMode = 'pretty' | 'raw' | 'preview'
type Lang = 'text' | 'json' | 'xml' | 'html' | 'javascript'

const activeTab = ref<ViewTab>('body')
const bodyMode = ref<BodyMode>('pretty')
const langOverride = ref<Lang | null>(null)

watch(
  () => props.response,
  () => {
    activeTab.value = 'body'
    bodyMode.value = 'pretty'
    langOverride.value = null
    searchKeyword.value = ''
    searchExpanded.value = false
    currentMatch.value = 0
  },
)

// ==================== 状态码 ====================

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

const statusConfig = computed(() => {
  if (props.response?.status === 'error') return { color: '#f93e3e', bg: '#feeaEA', label: '请求失败' }
  const code = props.response?.responseStatus
  if (!code) return { color: '#999', bg: '#f5f5f5', label: '—' }
  return STATUS_CONFIG[Math.floor(code / 100)] ?? { color: '#999', bg: '#f5f5f5', label: '' }
})

const statusCode = computed(() => {
  if (props.response?.status === 'error') return 'ERROR'
  return props.response?.responseStatus ? String(props.response.responseStatus) : '—'
})

const statusTooltip = computed(() => {
  const code = props.response?.responseStatus
  if (!code) return ''
  const text = STATUS_TEXT[code] ?? statusConfig.value.label
  const desc = STATUS_DESC[code] ?? ''
  return `${code} ${text}${desc ? ` — ${desc}` : ''}`
})

// ==================== Body ====================

const bodyText = computed(() => {
  const body = props.response?.responseBody
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
  const headers = props.response?.responseHeaders ?? {}
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

// ==================== Headers / Cookies ====================

const headerEntries = computed(() => Object.entries(props.response?.responseHeaders ?? {}))

interface CookieEntry {
  name: string
  value: string
  attributes: string[]
}

const cookieEntries = computed<CookieEntry[]>(() => {
  const headers = props.response?.responseHeaders ?? {}
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

// ==================== 搜索（Ctrl+F / Ctrl+G） ====================

const searchKeyword = ref('')
const searchExpanded = ref(false)
const searchInputRef = ref<HTMLInputElement>()

interface HighlightSegment {
  text: string
  highlight: boolean
}

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

// ==================== 复制 ====================

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

// ==================== 热键 ====================

function onKeydown(event: KeyboardEvent) {
  const mod = event.ctrlKey || event.metaKey
  if (!props.response) return
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
</script>

<template>
  <div class="resp-view">
    <template v-if="!response">
      <div class="resp-view__empty">
        <el-icon class="resp-view__empty-icon"><Position /></el-icon>
        <p>点击「发送」查看响应结果</p>
      </div>
    </template>

    <template v-else>
      <!-- Status Bar -->
      <div class="resp-view__status-bar">
        <el-tooltip v-if="statusTooltip" :content="statusTooltip" placement="bottom">
          <span
            class="resp-view__status-badge"
            :style="{ color: statusConfig.color, background: statusConfig.bg }"
          >
            {{ statusCode }}
          </span>
        </el-tooltip>
        <span
          v-else
          class="resp-view__status-badge"
          :style="{ color: statusConfig.color, background: statusConfig.bg }"
        >
          {{ statusCode }}
        </span>
        <span class="resp-view__meta-item">
          <span class="resp-view__meta-label">Time</span>
          <span class="resp-view__meta-value">{{ response.durationMs != null ? `${response.durationMs} ms` : '—' }}</span>
        </span>
        <span class="resp-view__meta-item">
          <span class="resp-view__meta-label">Size</span>
          <span class="resp-view__meta-value">{{ formatSize(response.size) }}</span>
        </span>
        <span v-if="response.errorMessage" class="resp-view__error" :title="response.errorMessage">
          {{ response.errorMessage }}
        </span>
      </div>

      <!-- Response Tabs -->
      <div class="resp-view__tabs">
        <div class="resp-view__tab-group">
          <button
            v-for="item in (['body', 'headers', 'cookies'] as const)"
            :key="item"
            class="resp-view__tab"
            :class="{ 'is-active': activeTab === item }"
            @click="activeTab = item"
          >
            {{
              item === 'body'
                ? 'Body'
                : item === 'headers'
                  ? `Headers (${headerEntries.length})`
                  : `Cookies (${cookieEntries.length})`
            }}
          </button>
        </div>
        <div class="resp-view__tab-actions">
          <template v-if="searchExpanded && activeTab === 'body'">
            <el-input
              ref="searchInputRef"
              v-model="searchKeyword"
              placeholder="搜索响应体"
              clearable
              class="resp-view__search-input"
              @keyup.enter="nextMatch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <span v-if="searchCountInfo" class="resp-view__search-count">{{ searchCountInfo }}</span>
            <button class="resp-view__icon-btn" @click="prevMatch">
              <el-icon><ArrowUp /></el-icon>
            </button>
            <button class="resp-view__icon-btn" @click="nextMatch">
              <el-icon><ArrowDown /></el-icon>
            </button>
          </template>
          <el-tooltip content="搜索响应体 (Ctrl+F)" placement="top">
            <button class="resp-view__icon-btn" :class="{ 'is-active': searchExpanded }" @click="toggleSearch">
              <el-icon><Search /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip :content="copying ? '已复制' : '复制响应体'" placement="top">
            <button class="resp-view__icon-btn" @click="handleCopy">
              <el-icon v-if="copying"><Check /></el-icon>
              <el-icon v-else><CopyDocument /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </div>

      <!-- Body 子工具栏（Pretty/Raw/Preview + 类型） -->
      <div v-if="activeTab === 'body'" class="resp-view__mode-bar">
        <div class="resp-view__mode-group">
          <button
            v-for="mode in (['pretty', 'raw', 'preview'] as const)"
            :key="mode"
            class="resp-view__mode"
            :class="{ 'is-active': bodyMode === mode }"
            @click="bodyMode = mode"
          >
            {{ mode.charAt(0).toUpperCase() + mode.slice(1) }}
          </button>
        </div>
        <div class="resp-view__mode-right">
          <span class="resp-view__mode-label">类型</span>
          <el-select
            :model-value="lang"
            class="resp-view__lang-select"
            @update:model-value="langOverride = $event"
          >
            <el-option v-for="l in (['text', 'json', 'xml', 'html', 'javascript'] as const)" :key="l" :label="l" :value="l" />
          </el-select>
        </div>
      </div>

      <!-- Content -->
      <div class="resp-view__content">
        <template v-if="activeTab === 'body'">
          <template v-if="bodyMode === 'pretty' && lang === 'json' && parsedJson && !searchKeyword.trim()">
            <div class="resp-view__tree">
              <JsonResponseView :value="parsedJson" path="root" :depth="0" />
            </div>
          </template>
          <template v-else-if="bodyMode === 'preview' && lang === 'html'">
            <iframe class="resp-view__preview" :srcdoc="bodyText" sandbox="allow-scripts" title="响应预览" />
          </template>
          <pre v-else class="resp-view__body">
            <template v-if="highlightResult">
              <template v-for="(seg, i) in highlightResult.segments" :key="i">
                <mark
                  v-if="seg.highlight"
                  class="resp-view__mark"
                  :class="{ 'is-current': i === currentMatchIndex }"
                >{{ seg.text }}</mark><template v-else>{{ seg.text }}</template>
              </template>
            </template>
            <template v-else>{{ bodyText || '（空响应体）' }}</template>
          </pre>
        </template>

        <table v-else-if="activeTab === 'headers'" class="resp-view__headers">
          <thead>
            <tr>
              <th>Name</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="[name, value] in headerEntries" :key="name + value">
              <td class="resp-view__header-name">{{ name }}</td>
              <td class="resp-view__header-value">{{ value }}</td>
            </tr>
            <tr v-if="!headerEntries.length">
              <td colspan="2" class="resp-view__empty-row">无响应头</td>
            </tr>
          </tbody>
        </table>

        <table v-else class="resp-view__headers resp-view__cookies">
          <thead>
            <tr>
              <th>Name</th>
              <th>Value</th>
              <th>Attributes</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cookie in cookieEntries" :key="cookie.name + cookie.value">
              <td class="resp-view__cookie-name">{{ cookie.name }}</td>
              <td class="resp-view__cookie-value">{{ cookie.value }}</td>
              <td class="resp-view__cookie-attrs">{{ cookie.attributes.join('; ') || '—' }}</td>
            </tr>
            <tr v-if="!cookieEntries.length">
              <td colspan="3" class="resp-view__empty-row">响应头中无 Set-Cookie</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.resp-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;

  // ==================== Empty State ====================
  &__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--color-neutral-300, #c0c4cc);
    gap: 8px;

    &-icon {
      font-size: 32px;
      opacity: 0.4;
    }

    p {
      font-size: 13px;
      margin: 0;
    }
  }

  // ==================== Status Bar ====================
  &__status-bar {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 8px 10px;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
    background: var(--color-neutral-50, #fafafa);
    flex-shrink: 0;
  }

  &__status-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 10px;
    border-radius: 4px;
    font-size: 13px;
    font-weight: 700;
    font-family: ui-monospace, SFMono-Regular, monospace;
    cursor: help;
  }

  &__meta-item {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__meta-label {
    font-size: 11px;
    color: var(--color-neutral-400, #909399);
    text-transform: uppercase;
  }

  &__meta-value {
    font-size: 12px;
    font-weight: 500;
    color: var(--color-neutral-700, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
  }

  &__error {
    margin-left: auto;
    font-size: 12px;
    color: var(--color-danger-500, #f56c6c);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 300px;
  }

  // ==================== Tabs ====================
  &__tabs {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 10px;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
    flex-shrink: 0;
  }

  &__tab-group {
    display: flex;
  }

  &__tab {
    padding: 8px 14px;
    font-size: 12px;
    font-weight: 500;
    color: var(--color-neutral-500, #909399);
    background: none;
    border: none;
    border-bottom: 2px solid transparent;
    cursor: pointer;
    transition: color 0.15s, border-color 0.15s;
    white-space: nowrap;

    &:hover {
      color: var(--color-neutral-700, #606266);
    }

    &.is-active {
      color: var(--color-primary-500, #409eff);
      border-bottom-color: var(--color-primary-500, #409eff);
    }
  }

  &__tab-actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__icon-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border: none;
    background: none;
    border-radius: 4px;
    color: var(--color-neutral-400, #909399);
    cursor: pointer;
    font-size: 14px;
    transition: color 0.15s, background 0.15s;

    &:hover {
      color: var(--color-neutral-700, #606266);
      background: var(--color-neutral-100, #e8e8e8);
    }

    &.is-active {
      color: var(--color-primary-500, #409eff);
      background: var(--color-primary-50, #ecf5ff);
    }
  }

  &__search-input {
    width: 160px;
  }

  &__search-count {
    font-size: 12px;
    color: var(--color-neutral-400, #909399);
    white-space: nowrap;
  }

  // ==================== Body Mode Bar ====================
  &__mode-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 10px;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
    flex-shrink: 0;
  }

  &__mode-group {
    display: flex;
    gap: 2px;
  }

  &__mode {
    padding: 4px 10px;
    font-size: 12px;
    border: none;
    background: none;
    border-radius: 4px;
    cursor: pointer;
    color: var(--color-neutral-500, #909399);
    transition: all 0.15s;

    &:hover {
      color: var(--color-neutral-700, #606266);
    }

    &.is-active {
      background: var(--color-neutral-100, #e8e8e8);
      color: var(--color-neutral-800, #303133);
      font-weight: 500;
    }
  }

  &__mode-right {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__mode-label {
    font-size: 11px;
    color: var(--color-neutral-400, #909399);
    text-transform: uppercase;
  }

  &__lang-select {
    width: 110px;
  }

  // ==================== Content ====================
  &__content {
    flex: 1;
    overflow: auto;
    min-height: 0;
  }

  // ==================== Body ====================
  &__body {
    margin: 0;
    padding: 12px 10px;
    font-family: ui-monospace, SFMono-Regular, monospace;
    font-size: 12px;
    line-height: 1.6;
    color: #d4d4d4;
    background: #1e1e1e;
    min-height: 100%;
    white-space: pre-wrap;
    word-break: break-all;
  }

  &__tree {
    min-height: 100%;
    padding: 8px 10px;
    background: #1e1e1e;
  }

  &__preview {
    width: 100%;
    height: 100%;
    border: none;
    background: #fff;
  }

  &__mark {
    background: #ffd54d;
    color: #1e1e1e;
    border-radius: 2px;
    padding: 0 1px;

    &.is-current {
      background: #ff9800;
      color: #fff;
      outline: 1px solid #e65100;
    }
  }

  // ==================== Headers / Cookies ====================
  &__headers {
    width: 100%;
    border-collapse: collapse;

    th {
      position: sticky;
      top: 0;
      background: var(--color-neutral-50, #fafafa);
      text-align: left;
      font-weight: 500;
      font-size: 11px;
      color: var(--color-neutral-400, #909399);
      text-transform: uppercase;
      padding: 8px 10px;
      border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
    }

    td {
      padding: 6px 10px;
      font-size: 12px;
      vertical-align: top;
      border-bottom: 1px solid var(--color-neutral-50, #fafafa);
      word-break: break-all;
    }

    tr:hover td {
      background: var(--color-neutral-50, #fafafa);
    }
  }

  &__header-name {
    font-weight: 500;
    color: var(--color-neutral-700, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
    white-space: nowrap;
  }

  &__header-value {
    color: var(--color-neutral-600, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
  }

  &__cookie-name {
    font-weight: 500;
    color: var(--color-neutral-700, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
    white-space: nowrap;
  }

  &__cookie-value {
    color: var(--color-neutral-600, #606266);
    font-family: ui-monospace, SFMono-Regular, monospace;
  }

  &__cookie-attrs {
    color: var(--color-neutral-400, #909399);
    font-size: 11px;
  }

  &__empty-row {
    text-align: center;
    color: var(--color-neutral-300, #c0c4cc);
    padding: 24px 0 !important;
    font-size: 13px;
  }
}
</style>