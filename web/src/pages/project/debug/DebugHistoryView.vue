<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiDebugRecordItem } from '@/types'
import {
  deleteDebugRecord,
  fetchDebugRecords,
  renameDebugRecord,
} from '@/services/apiDebug'
import { formatDateTime } from '@/utils/format'

const emit = defineEmits<{ (e: 'restore', record: ApiDebugRecordItem): void }>()

const PAGE_SIZE = 10

const loading = ref(false)
const records = ref<ApiDebugRecordItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const keyword = ref('')

let searchTimer: ReturnType<typeof setTimeout> | undefined
// 与环境列表一致：搜索防抖 300ms 走服务端过滤
function handleSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pageNo.value = 1
    void loadList()
  }, 300)
}

onMounted(() => void loadList())
onBeforeUnmount(() => clearTimeout(searchTimer))

async function loadList() {
  loading.value = true
  try {
    const page = await fetchDebugRecords(pageNo.value, PAGE_SIZE, keyword.value.trim() || undefined)
    records.value = page.list
    total.value = page.total
  } catch {
    // 拦截器已统一提示错误信息
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  pageNo.value = page
  void loadList()
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
    const time = new Date(record.executedAt).getTime()
    if (time >= startOfToday) groups[0].items.push(record)
    else if (time >= startOfYesterday) groups[1].items.push(record)
    else groups[2].items.push(record)
  }
  return groups.filter((group) => group.items.length)
})

// ==================== 行操作 ====================

const STATUS_TAG_TYPES: Record<string, string> = {
  success: 'success',
  failed: 'warning',
  error: 'danger',
}

async function handleDelete(record: ApiDebugRecordItem) {
  try {
    await ElMessageBox.confirm(`确定删除调试记录「${record.name ?? record.url ?? ''}」？`, '删除记录', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteDebugRecord(record.id)
    // 末页删空时回退一页，避免停留在空页
    if (records.value.length === 1 && pageNo.value > 1) pageNo.value -= 1
    await loadList()
    ElMessage.success('已删除')
  } catch {
    // 拦截器已统一提示错误信息
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
  } catch {
    // 拦截器已统一提示错误信息
  }
}

function handleRestore(record: ApiDebugRecordItem) {
  emit('restore', record)
}
</script>

<template>
  <div v-loading="loading" class="history">
    <div class="history__toolbar">
      <el-input
        v-model="keyword"
        size="small"
        clearable
        placeholder="搜索名称或 URL"
        class="history__search"
        @input="handleSearchInput"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <span class="history__total">共 {{ total }} 条</span>
    </div>

    <div class="history__list">
      <section v-for="group in groupedRecords" :key="group.label" class="history__group">
        <h4 class="history__group-title">{{ group.label }}</h4>
        <div v-for="record in group.items" :key="record.id" class="history__item">
          <el-tag size="small" :type="(STATUS_TAG_TYPES[record.status] ?? 'info') as never" effect="plain">
            {{ record.method }}
          </el-tag>
          <el-tag v-if="record.responseStatus" size="small" effect="plain">{{ record.responseStatus }}</el-tag>

          <template v-if="renamingId === record.id">
            <el-input
              v-model="renamingName"
              size="small"
              autofocus
              @keyup.enter="commitRename(record)"
              @blur="commitRename(record)"
            />
          </template>
          <button v-else class="history__item-main" @click="handleRestore(record)" @dblclick="startRename(record)">
            <span class="history__item-name">{{ record.name || record.url || '(未命名)' }}</span>
            <span class="history__item-url">{{ record.url }}</span>
          </button>

          <span class="history__item-meta">
            {{ formatDateTime(record.executedAt) }}
            <template v-if="record.durationMs != null"> · {{ record.durationMs }}ms</template>
          </span>

          <el-tooltip content="恢复到新标签" placement="top">
            <el-button link type="primary" size="small" @click="handleRestore(record)">恢复</el-button>
          </el-tooltip>
          <el-dropdown trigger="click">
            <el-button link size="small"><el-icon><MoreFilled /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="startRename(record)">重命名</el-dropdown-item>
                <el-dropdown-item divided @click="handleDelete(record)">删除记录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </section>

      <div v-if="!loading && !records.length" class="history__empty">暂无调试记录</div>
    </div>

    <el-pagination
      v-if="total > PAGE_SIZE"
      v-model:current-page="pageNo"
      layout="prev, pager, next"
      :page-size="PAGE_SIZE"
      :total="total"
      class="history__pager"
      @current-change="handlePageChange"
    />
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

  &__group-title {
    margin: var(--space-sm) 0;
    font-size: var(--font-size-xs);
    font-weight: 600;
    color: var(--color-neutral-400);
  }

  &__item {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    padding: 6px var(--space-sm);
    border-radius: var(--radius-sm, 4px);

    &:hover {
      background: var(--color-neutral-50, #fafafa);
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

  &__item-meta {
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

  &__pager {
    justify-content: center;
  }
}
</style>
