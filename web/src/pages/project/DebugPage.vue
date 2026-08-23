<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
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
import { useApiTestingUiStore } from '@/stores/apiTestingUi'

const HISTORY_TAB_ID = '__history__'

const tabs = ref<DebugTab[]>([createTab()])
const activeTabId = ref(tabs.value[0].id)
const showHistory = ref(false)

const activeTab = computed(() => tabs.value.find((tab) => tab.id === activeTabId.value) ?? tabs.value[0])
const canAddTab = computed(() => tabs.value.length < MAX_DEBUG_TABS)

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

// 最后一个标签关闭后自动创建空白标签（SRS 3.1 业务规则）
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
    // 恢复需完整快照，列表条目仅含摘要，按 id 二次获取
    const detail = await restoreDebugRecord(record.id)
    const tab = tabFromRestore(detail)
    tabs.value.push(tab)
    switchTab(tab.id)
    showHistory.value = false
  } catch {
    // 拦截器已统一提示错误信息
  }
}

onMounted(() => {
  window.addEventListener('keydown', onHotkey)
  consumePendingInterfaceRequest()
})

/** 接口列表行「调试」联动：消费 store 预填快照并打开新标签 */
function consumePendingInterfaceRequest() {
  const pending = useApiTestingUiStore().consumePendingRequest()
  if (!pending) return
  const tab = createTab()
  applyCurlToTab(tab, {
    method: pending.method,
    // 接口路径为相对路径，主机部分由用户结合环境补全
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
  // 接口定义的表单体为键值数组，调试标签缓存为对象（applyCurlToTab 再转 query-string 文本）
  if (body.type === 'form' && Array.isArray(body.content)) {
    const record: Record<string, string> = {}
    for (const item of body.content as { key: string; value: string }[]) {
      if (item.key) record[item.key] = item.value
    }
    return record
  }
  return body.content
}

onBeforeUnmount(() => window.removeEventListener('keydown', onHotkey))

function onHotkey(event: KeyboardEvent) {
  // Ctrl+Shift+H / Cmd+Shift+H 快速切换到历史记录（SRS 3.1）
  if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key.toLowerCase() === 'h') {
    event.preventDefault()
    switchTab(showHistory.value ? tabs.value[0]?.id ?? '' : HISTORY_TAB_ID)
  }
}
</script>

<template>
  <div class="debug-page">
    <div class="debug-page__tabbar">
      <div
        v-for="tab in tabs"
        :key="tab.id"
        class="debug-tab"
        :class="{ 'is-active': !showHistory && tab.id === activeTabId }"
        @click="switchTab(tab.id)"
        @dblclick="startRename(tab)"
      >
        <span v-if="renamingId !== tab.id" class="debug-tab__label">
          {{ tabTitle(tab) }}
          <i v-if="tab.dirty" class="debug-tab__dot">●</i>
        </span>
        <el-input
          v-else
          v-model="renamingValue"
          size="small"
          autofocus
          @keyup.enter="commitRename(tab)"
          @blur="commitRename(tab)"
        />
        <el-icon class="debug-tab__close" @click.stop="closeTab(tab)"><Close /></el-icon>
      </div>

      <el-tooltip content="新建标签" placement="top">
        <button class="debug-tabbar__add" :disabled="!canAddTab" @click="addTab">
          <el-icon><Plus /></el-icon>
        </button>
      </el-tooltip>

      <div class="debug-tabbar__spacer" />

      <el-button size="small" text type="primary" :icon="'Clock'" @click="curlVisible = true">
        导入 cURL
      </el-button>

      <div
        class="debug-tab debug-tab--history"
        :class="{ 'is-active': showHistory }"
        @click="switchTab(HISTORY_TAB_ID)"
      >
        <el-icon><Clock /></el-icon>历史记录
      </div>
    </div>

    <template v-if="!showHistory && activeTab">
      <div class="debug-page__main">
        <DebugRequestPanel
          v-model:tab="activeTab"
          class="debug-page__request"
          :executing="executing"
          @execute="handleExecute($event)"
        />
        <DebugResponseViewer class="debug-page__response" :response="activeTab.response" />
      </div>
    </template>

    <DebugHistoryView v-else class="debug-page__history" @restore="handleRestoreRecord" />

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
  </div>
</template>

<style lang="scss" scoped>
.debug-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--space-md);

  &__main {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: var(--space-md);
    flex: 1;
    min-height: 0;
  }

  &__request,
  &__response,
  &__history {
    min-height: 0;
  }

  &__curl-tip {
    margin: 0 0 var(--space-sm);
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
  }
}

.debug-page__tabbar {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-wrap: wrap;
}

.debug-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-sm, 4px);
  cursor: pointer;
  max-width: 220px;
  font-size: var(--font-size-xs);

  &:hover {
    border-color: var(--color-primary-300, #a0cfff);
  }

  &.is-active {
    border-color: var(--color-primary-500, #409eff);
    color: var(--color-primary-600, #337ecc);
  }

  &__label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__dot {
    color: var(--color-warning-500, #e6a23c);
    font-style: normal;
    font-size: 8px;
    vertical-align: super;
  }

  &__close {
    opacity: 0.45;

    &:hover {
      opacity: 1;
    }
  }

  &--history {
    color: var(--color-neutral-500);
  }
}

.debug-tabbar__add {
  border: 1px dashed var(--color-neutral-300);
  background: transparent;
  border-radius: var(--radius-sm, 4px);
  padding: 4px 6px;
  cursor: pointer;
  display: inline-flex;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.4;
  }
}

.debug-tabbar__spacer {
  flex: 1;
}
</style>
