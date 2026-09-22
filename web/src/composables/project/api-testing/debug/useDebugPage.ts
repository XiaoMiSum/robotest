import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiDebugRecordItem, DebugTab } from '@/types'
import {
  applyCurlToTab,
  buildExecutePayload,
  createTab,
  ensureUrlScheme,
  markExecuted,
  MAX_DEBUG_TABS,
  tabTitle,
  tabFromRestore,
} from '@/pages/project/api-testing/debug/debugModel'
import { executeDebug, restoreDebugRecord } from '@/services/project/debug'
import { parseCurl } from '@/pages/project/api-testing/debug/curlParser'
import { useApiTestingUiStore } from '@/stores/apiTestingUi'

const HISTORY_TAB_ID = '__history__'

const METHOD_COLORS: Record<string, string> = {
  GET: '#61affe',
  POST: '#49cc90',
  PUT: '#fca130',
  PATCH: '#50e3c2',
  DELETE: '#f93e3e',
  OPTIONS: '#0d5aa7',
  HEAD: '#9012fe',
  CONNECT: '#e8d44d',
}

function normalizeBodyType(type?: string): 'none' | 'json' | 'form' | 'raw' | 'binary' {
  if (type === 'json' || type === 'form' || type === 'raw' || type === 'binary') return type
  return 'none'
}

function normalizeBodyContent(body?: { type?: string; content?: unknown } | null): unknown {
  if (!body) return undefined
  if (body.type === 'form' && Array.isArray(body.content)) {
    const record: Record<string, string> = {}
    for (const item of body.content as { key: string; value: string }[]) {
      if (item.key) record[item.key] = item.value
    }
    return record
  }
  return body.content
}

export function useDebugPage(emit: (e: 'view-interface', interfaceId: string) => void) {
  const tabs = ref<DebugTab[]>([createTab()])
  const activeTabId = ref(tabs.value[0].id)
  const showHistory = ref(false)
  const debugEnvironmentId = ref('')

  const activeTab = computed(() => tabs.value.find((tab) => tab.id === activeTabId.value) ?? tabs.value[0])
  const canAddTab = computed(() => tabs.value.length < MAX_DEBUG_TABS)

  const saveVisible = ref(false)
  const saveRecordId = computed(() => activeTab.value.response?.debugRecordId ?? '')
  const canSave = computed(() => Boolean(saveRecordId.value))

  function handleSave() {
    if (!canSave.value) return
    saveVisible.value = true
  }

  function handleSaved(interfaceId: string) {
    ElMessageBox.confirm('已保存为接口定义，是否前往查看？', '保存成功', {
      confirmButtonText: '查看接口',
      cancelButtonText: '留在调试',
      type: 'success',
    })
      .then(() => emit('view-interface', interfaceId))
      .catch(() => {})
  }

  function switchTab(id: string) {
    activeTabId.value = id
    showHistory.value = id === HISTORY_TAB_ID
  }

  function addTab() {
    if (!canAddTab.value) return
    const tab = createTab()
    tabs.value.push(tab)
    switchTab(tab.id)
  }

  function closeTab(tab: DebugTab) {
    const index = tabs.value.findIndex((item) => item.id === tab.id)
    tabs.value = tabs.value.filter((item) => item.id !== tab.id)
    if (!tabs.value.length) {
      addTab()
      return
    }
    if (tab.id === activeTabId.value) {
      switchTab(tabs.value[Math.max(0, index - 1)].id)
    }
  }

  const renamingId = ref('')
  const renamingValue = ref('')

  function startRename(tab: DebugTab) {
    renamingId.value = tab.id
    renamingValue.value = tab.name
  }

  function commitRename(tab: DebugTab) {
    if (renamingId.value === tab.id && renamingValue.value.trim() !== '') {
      tab.name = renamingValue.value.trim()
    }
    renamingId.value = ''
  }

  const executing = ref(false)

  async function handleExecute(environmentId?: string) {
    if (executing.value || !activeTab.value.url.trim()) {
      if (!activeTab.value.url.trim()) ElMessage.warning('请输入请求 URL')
      return
    }
    executing.value = true
    try {
      activeTab.value.url = ensureUrlScheme(activeTab.value.url)
      const payload = buildExecutePayload(activeTab.value, environmentId || undefined)
      const resp = await executeDebug(payload)
      markExecuted(activeTab.value, resp)
    } catch {
      // 拦截器已统一提示错误信息
    } finally {
      executing.value = false
    }
  }

  const curlVisible = ref(false)
  const curlText = ref('')

  function handleImportCurl() {
    const text = curlText.value.trim()
    if (!text) return
    try {
      const parsed = parseCurl(text)
      applyCurlToTab(activeTab.value, {
        method: parsed.method,
        url: parsed.url,
        headers: parsed.headers,
        body: { type: parsed.bodyType ?? 'none', content: parsed.bodyContent },
      })
      curlVisible.value = false
      curlText.value = ''
      ElMessage.success('cURL 解析成功，已回填当前标签')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : 'cURL 解析失败')
    }
  }

  async function handleRestoreRecord(record: ApiDebugRecordItem) {
    try {
      const detail = await restoreDebugRecord(record.id)
      const tab = tabFromRestore(detail)
      tabs.value.push(tab)
      switchTab(tab.id)
      showHistory.value = false
    } catch {
      // 拦截器已统一提示错误信息
    }
  }

  const requestHeight = ref(50)
  const isDragging = ref(false)
  const containerRef = ref<HTMLElement>()

  function onDividerMouseDown(e: MouseEvent) {
    e.preventDefault()
    isDragging.value = true
    document.addEventListener('mousemove', onDividerMouseMove)
    document.addEventListener('mouseup', onDividerMouseUp)
    document.body.style.cursor = 'row-resize'
    document.body.style.userSelect = 'none'
  }

  function onDividerMouseMove(e: MouseEvent) {
    if (!isDragging.value || !containerRef.value) return
    const rect = containerRef.value.getBoundingClientRect()
    const y = e.clientY - rect.top
    const pct = (y / rect.height) * 100
    requestHeight.value = Math.min(Math.max(pct, 20), 80)
  }

  function onDividerMouseUp() {
    isDragging.value = false
    document.removeEventListener('mousemove', onDividerMouseMove)
    document.removeEventListener('mouseup', onDividerMouseUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  function consumePendingInterfaceRequest() {
    const pending = useApiTestingUiStore().consumePendingRequest()
    if (!pending) return
    const tab = createTab()
    applyCurlToTab(tab, {
      method: pending.method,
      url: pending.path,
      headers: (pending.headers ?? []).map((row) => ({ ...row })),
      body: {
        type: normalizeBodyType(pending.body?.type),
        content: normalizeBodyContent(pending.body),
      },
      params: (pending.params ?? []).map((row) => ({ ...row })),
    })
    tab.name = pending.name || `${pending.method} ${pending.path}`
    tabs.value.push(tab)
    switchTab(tab.id)
  }

  function onHotkey(event: KeyboardEvent) {
    const mod = event.ctrlKey || event.metaKey
    if (mod && event.shiftKey && event.key.toLowerCase() === 'h') {
      event.preventDefault()
      switchTab(showHistory.value ? tabs.value[0]?.id ?? '' : HISTORY_TAB_ID)
      return
    }
    if (mod && event.key === 'Enter') {
      event.preventDefault()
      if (!showHistory.value && activeTab.value) {
        handleExecute()
      }
      return
    }
    if (mod && event.key.toLowerCase() === 't') {
      event.preventDefault()
      addTab()
      return
    }
    if (mod && event.key.toLowerCase() === 'w') {
      event.preventDefault()
      if (!showHistory.value && activeTab.value) {
        closeTab(activeTab.value)
      }
    }
  }

  function methodColor(method: string): string {
    return METHOD_COLORS[method.toUpperCase()] ?? '#999'
  }

  function handleAuxClick(e: MouseEvent, tab: DebugTab) {
    if (e.button === 1) {
      e.preventDefault()
      closeTab(tab)
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', onHotkey)
    consumePendingInterfaceRequest()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onHotkey)
    document.removeEventListener('mousemove', onDividerMouseMove)
    document.removeEventListener('mouseup', onDividerMouseUp)
  })

  return {
    tabs,
    activeTabId,
    showHistory,
    debugEnvironmentId,
    activeTab,
    canAddTab,
    saveVisible,
    saveRecordId,
    canSave,
    executing,
    curlVisible,
    curlText,
    requestHeight,
    isDragging,
    containerRef,
    renamingId,
    renamingValue,
    handleSave,
    handleSaved,
    switchTab,
    addTab,
    closeTab,
    startRename,
    commitRename,
    handleExecute,
    handleImportCurl,
    handleRestoreRecord,
    onDividerMouseDown,
    onDividerMouseMove,
    onDividerMouseUp,
    methodColor,
    handleAuxClick,
    tabTitle,
    HISTORY_TAB_ID,
  }
}
