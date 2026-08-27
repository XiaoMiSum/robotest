<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiDebugCurlImportResp, ApiDebugRecordItem, DebugTab } from '@/types'
import {
  applyCurlToTab,
  buildExecutePayload,
  createTab,
  markExecuted,
  MAX_DEBUG_TABS,
  tabTitle,
  tabFromRestore,
} from './debugModel'
import {
  executeDebug,
  importCurl,
  restoreDebugRecord,
} from '@/services/apiDebug'
import DebugRequestPanel from './debug/DebugRequestPanel.vue'
import DebugResponseViewer from './debug/DebugResponseViewer.vue'
import DebugHistoryView from './debug/DebugHistoryView.vue'
import SaveInterfaceDialog from './debug/SaveInterfaceDialog.vue'
import { useApiTestingUiStore } from '@/stores/apiTestingUi'

const router = useRouter()

const HISTORY_TAB_ID = '__history__'

const tabs = ref<DebugTab[]>([createTab()])
const activeTabId = ref(tabs.value[0].id)
const showHistory = ref(false)

const activeTab = computed(() => tabs.value.find((tab) => tab.id === activeTabId.value) ?? tabs.value[0])
const canAddTab = computed(() => tabs.value.length < MAX_DEBUG_TABS)

// ==================== 保存为接口定义 ====================

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
    .then(() => router.push({ name: 'InterfaceEditor', params: { interfaceId } }))
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

async function closeTab(tab: DebugTab) {
  if (tab.dirty && !await confirmDiscard()) return
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

function confirmDiscard(): Promise<boolean> {
  return ElMessageBox.confirm('关闭后内容将丢失，是否关闭？', '关闭标签', { type: 'warning' })
    .then(() => true)
    .catch(() => false)
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

// ==================== 执行 ====================

const executing = ref(false)

async function handleExecute(environmentId?: string) {
  if (executing.value || !activeTab.value.url.trim()) {
    if (!activeTab.value.url.trim()) ElMessage.warning('请输入请求 URL')
    return
  }
  executing.value = true
  try {
    const payload = buildExecutePayload(activeTab.value, environmentId || undefined)
    const resp = await executeDebug(payload)
    markExecuted(activeTab.value, resp)
  } catch {
    // 拦截器已统一提示错误信息
  } finally {
    executing.value = false
  }
}

// ==================== cURL 导入 ====================

const curlVisible = ref(false)
const curlText = ref('')
const curlLoading = ref(false)

async function handleImportCurl() {
  if (!curlText.value.trim()) return
  curlLoading.value = true
  try {
    const parsed: ApiDebugCurlImportResp = await importCurl(curlText.value)
    applyCurlToTab(activeTab.value, parsed)
    curlVisible.value = false
    curlText.value = ''
    ElMessage.success('cURL 解析成功，已回填当前标签')
  } catch {
    // 拦截器已统一提示错误信息
  } finally {
    curlLoading.value = false
  }
}

// ==================== 历史记录 ====================

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

// ==================== 可拖拽分隔线 ====================

const requestHeight = ref(50) // 百分比
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

// ==================== 热键 ====================

onMounted(() => {
  window.addEventListener('keydown', onHotkey)
  consumePendingInterfaceRequest()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onHotkey)
  document.removeEventListener('mousemove', onDividerMouseMove)
  document.removeEventListener('mouseup', onDividerMouseUp)
})

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
  tab.dirty = false
  tabs.value.push(tab)
  switchTab(tab.id)
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

function onHotkey(event: KeyboardEvent) {
  const mod = event.ctrlKey || event.metaKey
  // Ctrl+Shift+H：切换历史记录
  if (mod && event.shiftKey && event.key.toLowerCase() === 'h') {
    event.preventDefault()
    switchTab(showHistory.value ? tabs.value[0]?.id ?? '' : HISTORY_TAB_ID)
    return
  }
  // Ctrl+Enter：发送请求
  if (mod && event.key === 'Enter') {
    event.preventDefault()
    if (!showHistory.value && activeTab.value) {
      handleExecute()
    }
    return
  }
  // Ctrl+T：新建标签
  if (mod && event.key.toLowerCase() === 't') {
    event.preventDefault()
    addTab()
    return
  }
  // Ctrl+W：关闭当前标签
  if (mod && event.key.toLowerCase() === 'w') {
    event.preventDefault()
    if (!showHistory.value && activeTab.value) {
      closeTab(activeTab.value)
    }
  }
}

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

function methodColor(method: string): string {
  return METHOD_COLORS[method.toUpperCase()] ?? '#999'
}

// 中键点击标签关闭（与浏览器/Postman 行为一致）
function handleAuxClick(e: MouseEvent, tab: DebugTab) {
  if (e.button === 1) {
    e.preventDefault()
    closeTab(tab)
  }
}
</script>

<template>
  <div ref="containerRef" class="debug-page">
    <el-card shadow="never" class="debug-page__card">
      <template #header>
        <!-- Tab Bar -->
        <div class="debug-page__tabbar">
          <div class="debug-page__tabs">
            <div
              v-for="tab in tabs"
              :key="tab.id"
              class="debug-tab"
              :class="{ 'is-active': !showHistory && tab.id === activeTabId }"
              @click="switchTab(tab.id)"
              @dblclick="startRename(tab)"
              @auxclick="handleAuxClick($event, tab)"
            >
              <span v-if="renamingId !== tab.id" class="debug-tab__method" :style="{ background: methodColor(tab.method) }">
                {{ tab.method }}
              </span>
              <span v-if="renamingId !== tab.id" class="debug-tab__label">
                {{ tabTitle(tab) }}
                <i v-if="tab.dirty" class="debug-tab__dot">●</i>
              </span>
              <el-input
                v-else
                v-model="renamingValue"
                autofocus
                class="debug-tab__rename"
                @keyup.enter="commitRename(tab)"
                @blur="commitRename(tab)"
              />
              <el-icon class="debug-tab__close" @click.stop="closeTab(tab)"><Close /></el-icon>
            </div>

            <button class="debug-tabbar__add" :disabled="!canAddTab" @click="addTab">
              <el-icon><Plus /></el-icon>
            </button>
          </div>

          <div class="debug-tabbar__right">
            <el-button text @click="curlVisible = true">
              <el-icon class="mr-1"><Download /></el-icon>
              导入 cURL
            </el-button>
            <div
              class="debug-tab debug-tab--history"
              :class="{ 'is-active': showHistory }"
              @click="switchTab(HISTORY_TAB_ID)"
            >
              <el-icon><Clock /></el-icon>
              <span>历史记录</span>
            </div>
          </div>
        </div>
      </template>

      <!-- Main Content -->
      <template v-if="!showHistory && activeTab">
        <div class="debug-page__body" :style="{ '--req-h': requestHeight + '%' }">
          <DebugRequestPanel
            v-model:tab="activeTab"
            class="debug-page__request"
            :executing="executing"
            :can-save="canSave"
            @execute="handleExecute($event)"
            @save="handleSave"
          />
          <div class="debug-page__divider" @mousedown="onDividerMouseDown">
            <div class="debug-page__divider-line" />
          </div>
          <DebugResponseViewer class="debug-page__response" :response="activeTab.response" />
        </div>
      </template>

      <DebugHistoryView v-else class="debug-page__history" @restore="handleRestoreRecord" />
    </el-card>

    <!-- cURL Import Dialog -->
    <el-dialog v-model="curlVisible" title="导入 cURL" width="560">
      <p class="debug-page__curl-tip">粘贴 Chrome / Charles / Fiddler 导出的 cURL 命令，仅解析不执行</p>
      <el-input
        v-model="curlText"
        type="textarea"
        :rows="7"
        placeholder="curl -X POST https://example.com/api -H 'Content-Type: application/json' -d '{...}'"
      />
      <template #footer>
        <el-button @click="curlVisible = false">取消</el-button>
        <el-button type="primary" :loading="curlLoading" :disabled="!curlText.trim()" @click="handleImportCurl">
          解析并回填
        </el-button>
      </template>
    </el-dialog>

    <!-- Save as Interface Dialog -->
    <SaveInterfaceDialog
      :visible="saveVisible"
      :record-id="saveRecordId"
      @update:visible="saveVisible = $event"
      @saved="handleSaved"
    />
  </div>
</template>

<style lang="scss" scoped>
.debug-page {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__card {
    display: flex;
    flex-direction: column;
    height: 100%;
    border-radius: var(--radius-lg);

    :deep(.el-card__header) {
      padding: 0;
      border-bottom: none;
    }

    :deep(.el-card__body) {
      flex: 1;
      min-height: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
  }

  // ==================== Tab Bar ====================
  &__tabbar {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    padding: 6px 12px;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
    background: var(--color-neutral-50, #fafafa);
    min-height: 40px;
  }

  &__tabs {
    display: flex;
    align-items: center;
    gap: 2px;
    flex: 1;
    min-width: 0;
    overflow-x: auto;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  // ==================== Body (request + divider + response) ====================
  &__body {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;
  }

  &__request {
    height: var(--req-h, 50%);
    min-height: 80px;
    overflow: auto;
    border-bottom: none;
  }

  &__divider {
    height: 6px;
    cursor: row-resize;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    position: relative;
    z-index: 1;

    &:hover .debug-page__divider-line,
    &:active .debug-page__divider-line {
      background: var(--color-primary-300, #a0cfff);
    }
  }

  &__divider-line {
    width: 40px;
    height: 2px;
    border-radius: 1px;
    background: var(--color-neutral-200, #dcdfe6);
    transition: background 0.15s;
  }

  &__response {
    flex: 1;
    min-height: 80px;
    overflow: auto;
  }

  &__history {
    flex: 1;
    min-height: 0;
  }

  &__curl-tip {
    margin: 0 0 var(--space-sm);
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
  }
}

// ==================== Tab Item ====================
.debug-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
  max-width: 200px;
  font-size: 12px;
  color: var(--color-neutral-600, #606266);
  transition: background 0.15s, color 0.15s;
  white-space: nowrap;
  user-select: none;

  &:hover {
    background: var(--color-neutral-100, #e8e8e8);
  }

  &.is-active {
    background: var(--color-bg, #fff);
    color: var(--color-neutral-800, #303133);
    font-weight: 500;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  }

  &--history {
    color: var(--color-neutral-500, #909399);

    &:hover {
      color: var(--color-primary-500, #409eff);
      background: var(--color-neutral-100, #e8e8e8);
    }

    &.is-active {
      background: var(--color-bg, #fff);
      color: var(--color-primary-500, #409eff);
      font-weight: 500;
    }
  }

  &__method {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 3px;
    color: #fff;
    font-size: 10px;
    font-weight: 700;
    font-family: ui-monospace, SFMono-Regular, monospace;
    letter-spacing: 0.5px;
    padding: 1px 5px;
    flex-shrink: 0;
    min-width: 30px;
  }

  &__label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__dot {
    color: var(--color-warning-500, #e6a23c);
    font-style: normal;
    font-size: 8px;
    line-height: 1;
  }

  &__close {
    opacity: 0;
    font-size: 12px;
    flex-shrink: 0;
    transition: opacity 0.15s;

    &:hover {
      color: var(--color-danger-500, #f56c6c);
    }
  }

  &:hover &__close {
    opacity: 0.6;
  }

  &__rename {
    width: 120px;
  }
}

.debug-tabbar__add {
  border: 1px dashed var(--color-neutral-300, #c0c4cc);
  background: transparent;
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  color: var(--color-neutral-400, #909399);
  transition: border-color 0.15s, color 0.15s;
  flex-shrink: 0;

  &:hover:not(:disabled) {
    border-color: var(--color-primary-400, #a0cfff);
    color: var(--color-primary-500, #409eff);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.3;
  }
}

.debug-tabbar__right {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-shrink: 0;
}

.mr-1 {
  margin-right: 4px;
}
</style>
