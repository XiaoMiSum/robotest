<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiDebugRecordItem } from '@/types'
import {
  deleteDebugRecord,
  fetchDebugRecords,
  renameDebugRecord,
} from '@/services/project/api-testing/debug'
import { formatDateTime, parseDateTime } from '@/utils/format'
import { methodBadgeColor } from '@/composables/project/api-testing/debug/useDebugPage'

const emit = defineEmits<{ (e: 'restore', record: ApiDebugRecordItem): void }>()

// 交互设计 1.7：滚动到底自动加载，无分页器；每页 100 条（API 契约 pageSize 上限 100）
const PAGE_SIZE = 100

const loading = ref(false)
const error = ref<string | null>(null)
const records = ref<ApiDebugRecordItem[]>([])
const total = ref(0)
const loadedPage = ref(1)
const pendingPage = ref(1)
const keyword = ref('')
const listRef = ref<HTMLElement | null>(null)

const hasMore = computed(() => records.value.length < total.value)

let searchTimer: ReturnType<typeof setTimeout> | undefined
let listRequestId = 0

function errorMessage(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback
}

// 与环境列表一致：搜索防抖 300ms 走服务端过滤
function handleSearchInput() {
  clearTimeout(searchTimer)
  listRequestId += 1
  searchTimer = setTimeout(() => {
    void fetchPage(1)
  }, 300)
}

onMounted(() => void fetchPage(1))
onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  listRequestId += 1
  loading.value = false
})

async function fetchPage(page: number): Promise<void> {
  const requestId = ++listRequestId
  pendingPage.value = page
  loading.value = true
  error.value = null
  try {
    const data = await fetchDebugRecords(page, PAGE_SIZE, keyword.value.trim() || undefined)
    if (requestId !== listRequestId) return
    // 翻页期间新记录插入会使服务端偏移量后移，合并按 id 去重防止重复条目
    const merged = page === 1 ? data.list : [...records.value, ...data.list]
    const seen = new Set<string>()
    records.value = merged.filter((item) => {
      if (seen.has(item.id)) return false
      seen.add(item.id)
      return true
    })
    total.value = data.total
    loadedPage.value = page
  } catch (err) {
    if (requestId !== listRequestId) return
    const message = errorMessage(err, '加载调试记录失败')
    error.value = message
    ElMessage.error(message)
  } finally {
    if (requestId === listRequestId) {
      loading.value = false
    }
  }
}

// 仅重试失败的那一页：追加加载失败时保留已加载内容，从断点续传
function retry(): void {
  void fetchPage(pendingPage.value)
}

function handleListScroll(): void {
  const el = listRef.value
  if (!el || loading.value || error.value || !hasMore.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 48) {
    void fetchPage(loadedPage.value + 1)
  }
}

// ==================== 时间分组（今天 / 昨天 / 更早，交互设计 3.1） ====================

interface RecordGroup {
  label: string
  items: ApiDebugRecordItem[]
}

const groupedRecords = computed<RecordGroup[]>(() => {
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfYesterday = startOfToday - 24 * 3600 * 1000
  const groups: RecordGroup[] = [
    { label: '今天', items: [] },
    { label: '昨天', items: [] },
    { label: '更早', items: [] },
  ]
  for (const record of records.value) {
    const time = parseDateTime(record.executedAt)?.getTime() ?? 0
    if (time >= startOfToday) groups[0].items.push(record)
    else if (time >= startOfYesterday) groups[1].items.push(record)
    else groups[2].items.push(record)
  }
  return groups.filter((group) => group.items.length)
})

// ==================== 行操作 ====================

async function handleDelete(record: ApiDebugRecordItem) {
  try {
    await ElMessageBox.confirm(`确定删除调试记录「${record.name ?? record.url ?? ''}」？`, '删除记录', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteDebugRecord(record.id)
    // 滚动加载没有可回退的页码，删除后从头刷新已加载内容
    await fetchPage(1)
    ElMessage.success('已删除')
  } catch (err) {
    ElMessage.error(errorMessage(err, '删除调试记录失败'))
  }
}

const renamingId = ref('')
const renamingName = ref('')

function startRename(record: ApiDebugRecordItem) {
  renamingId.value = record.id
  renamingName.value = record.name ?? ''
}

async function commitRename(record: ApiDebugRecordItem) {
  const name = renamingName.value.trim()
  renamingId.value = ''
  if (!name || name === record.name) return
  try {
    await renameDebugRecord(record.id, name)
    record.name = name
    ElMessage.success('已重命名')
  } catch (err) {
    ElMessage.error(errorMessage(err, '重命名调试记录失败'))
  }
}

function handleRestore(record: ApiDebugRecordItem) {
  emit('restore', record)
}

function responseCodeClass(record: ApiDebugRecordItem): string {
  if (record.responseStatus == null) return ''
  return record.responseStatus < 400 ? 'history__code--ok' : 'history__code--fail'
}

// 示例时间列为 MM-DD HH:mm：年份已由「今天/昨天/更早」分组隐含（交互设计 1.7）
function formatMetaTime(value: string): string {
  const text = formatDateTime(value)
  return text.length > 5 ? text.slice(5) : text
}
</script>

<template>
  <!-- 首屏加载走整屏遮罩；已有时改用列表尾部提示，避免追加加载时遮罩阻断滚动 -->
  <div v-loading="loading && !records.length" class="history">
    <div class="history__toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索名称或 URL"
        class="history__search"
        @input="handleSearchInput"
        @clear="handleSearchInput"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <span class="history__total">共 {{ total }} 条</span>
    </div>

    <div ref="listRef" class="history__list" @scroll="handleListScroll">
      <div v-if="error" class="history__error" role="alert">
        <span>{{ error }}</span>
        <el-button link type="primary" @click="retry">重试</el-button>
      </div>

      <section v-for="group in groupedRecords" :key="group.label" class="history__group">
        <h4 class="history__group-title">{{ group.label }}</h4>
        <div v-for="record in group.items" :key="record.id" class="history__item">
          <!-- 实底方法徽标与状态码色块对齐示例（交互设计 1.7） -->
          <span class="history__method" :style="{ background: methodBadgeColor(record.method) }">
            {{ record.method }}
          </span>
          <!-- 无响应状态时渲染空列，保证状态码之后的各列不因缺值错位 -->
          <span class="history__code" :class="responseCodeClass(record)">
            {{ record.responseStatus ?? '' }}
          </span>

          <template v-if="renamingId === record.id">
            <el-input
              v-model="renamingName"
              autofocus
              class="history__rename"
              @keyup.enter="commitRename(record)"
              @blur="commitRename(record)"
            />
          </template>
          <button v-else class="history__item-main" @click="handleRestore(record)" @dblclick="startRename(record)">
            <span class="history__item-name">{{ record.name || record.url || '(未命名)' }}</span>
            <span class="history__item-url">{{ record.url }}</span>
          </button>

          <!-- 时间与耗时分列展示；耗时缺值时保留空列占位，保证操作列对齐 -->
          <span class="history__item-time">{{ formatMetaTime(record.executedAt) }}</span>
          <span class="history__item-cost">
            {{ record.durationMs != null ? `${record.durationMs}ms` : '' }}
          </span>

          <el-tooltip content="恢复到新标签" placement="top">
            <el-button link type="primary" @click="handleRestore(record)">恢复</el-button>
          </el-tooltip>
          <!-- 行内操作平铺（交互设计 1.7），不收纳进下拉菜单 -->
          <el-button link type="primary" @click="startRename(record)">重命名</el-button>
          <el-button link type="danger" @click="handleDelete(record)">删除</el-button>
        </div>
      </section>

      <div v-if="!loading && !error && !records.length" class="history__empty">暂无调试记录</div>
      <div v-if="loading && records.length" class="history__loading">加载中…</div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.history {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-height: 0;

  &__toolbar {
    display: flex;
    align-items: center;
    gap: var(--space-md);
  }

  &__search {
    width: 260px;
  }

  &__total {
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
  }

  &__list {
    flex: 1;
    overflow-y: auto;
    min-height: 0;
  }

  &__error {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-md);
    margin-bottom: var(--space-sm);
    color: var(--color-danger);
    font-size: var(--font-size-sm);
  }

  &__group-title {
    margin: var(--space-sm) 0;
    font-size: var(--font-size-xs);
    font-weight: 600;
    color: var(--color-neutral-400);
  }

  // 实底方法徽标：与调试页签条徽标同款排版（交互稿方法色板）；固定列宽保证多行对齐
  &__method {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    padding: 1px 5px;
    border-radius: 3px;
    color: var(--color-neutral-0);
    font-size: 10px;
    font-weight: 700;
    font-family: var(--font-mono);
    letter-spacing: 0.5px;
    flex-shrink: 0;
  }

  // 状态码列固定 40px：无响应状态的空值行同样占位，避免后续列错位
  &__code {
    width: 40px;
    text-align: center;
    padding: 1px 6px;
    border-radius: 3px;
    font-family: var(--font-mono);
    font-size: var(--font-size-xs);
    font-weight: 600;
    flex-shrink: 0;

    &--ok {
      color: var(--color-success-strong);
      background: var(--color-success-light);
    }

    &--fail {
      color: var(--color-danger-strong);
      background: var(--color-danger-light);
    }
  }

  &__item {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    padding: 6px var(--space-sm);
    border-radius: var(--radius-sm, 4px);

    &:hover {
      background: var(--color-neutral-50);
    }
  }

  &__item-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    padding: 0;
    border: none;
    background: transparent;
    cursor: pointer;
    text-align: left;
  }

  &__item-name {
    font-size: var(--font-size-xs);
    color: var(--color-neutral-700);
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__item-url {
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-family: ui-monospace, monospace;
  }

  // 重命名输入态占满主名列，编辑中其余列保持对齐
  &__rename {
    flex: 1;
    min-width: 0;
  }

  // 时间与耗时各自固定列宽，耗时右对齐使数字末位对齐（交互设计 1.7 信息列）
  &__item-time {
    width: 100px;
    flex-shrink: 0;
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
    white-space: nowrap;
  }

  &__item-cost {
    width: 64px;
    flex-shrink: 0;
    text-align: right;
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
    white-space: nowrap;
  }

  &__empty {
    margin-top: 80px;
    text-align: center;
    color: var(--color-neutral-300);
    font-size: var(--font-size-sm);
  }

  &__loading {
    padding: var(--space-sm) 0;
    text-align: center;
    color: var(--color-neutral-400);
    font-size: var(--font-size-xs);
  }
}
</style>
