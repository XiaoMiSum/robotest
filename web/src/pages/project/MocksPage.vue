<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiMockItem } from '@/types'
import {
  fetchMockPage,
  toggleMock,
  batchToggleMocks,
  deleteMock,
  duplicateMock,
  resetMockHitCount,
  moveMockUp,
  moveMockDown,
  fetchMockAddress,
} from '@/services/apiMock'
import { methodTagType } from './mocksModel'
import { formatDateTime } from '@/utils/format'
import MockEditorDrawer from './mocks/MockEditorDrawer.vue'
import MockDebugPanel from './mocks/MockDebugPanel.vue'
import MockAddressDialog from './mocks/MockAddressDialog.vue'

const props = defineProps<{
  interfaceId?: string
}>()

const loading = ref(false)
const list = ref<ApiMockItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const search = ref('')
const enabledFilter = ref<boolean | undefined>(undefined)
const selectedIds = ref<string[]>([])

// 编辑器
const editorVisible = ref(false)
const editorMockId = ref<string | null>(null)
const editorInterfaceId = ref<string | null>(null)

// 调试面板
const debugVisible = ref(false)
const debugMockId = ref<string | null>(null)

// 地址弹窗
const addressVisible = ref(false)
const addressData = ref<{ mockUrl: string; method: string; name: string; headers?: Record<string, unknown> } | null>(null)

async function loadList() {
  loading.value = true
  try {
    const result = await fetchMockPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      interfaceId: props.interfaceId,
      search: search.value || undefined,
      enabled: enabledFilter.value,
    })
    list.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

onMounted(loadList)

watch(() => props.interfaceId, () => {
  pageNo.value = 1
  loadList()
})

function handleSearch() {
  pageNo.value = 1
  loadList()
}

function handlePageChange(newPage: number) {
  pageNo.value = newPage
  loadList()
}

function handleSizeChange(newSize: number) {
  pageSize.value = newSize
  pageNo.value = 1
  loadList()
}

async function handleToggle(row: ApiMockItem, enabled: boolean) {
  try {
    await toggleMock(row.id, enabled)
    row.enabled = enabled
    ElMessage.success(enabled ? '已启用' : '已停用')
  } catch {
    loadList()
  }
}

async function handleBatchToggle(enabled: boolean) {
  if (!selectedIds.value.length) return
  try {
    const result = await batchToggleMocks({ ids: selectedIds.value, enabled })
    ElMessage.success(`已${enabled ? '启用' : '停用'} ${result.updatedCount} 条规则`)
    selectedIds.value = []
    loadList()
  } catch {
    loadList()
  }
}

async function handleDelete(row: ApiMockItem) {
  await ElMessageBox.confirm(`确定删除 Mock「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteMock(row.id)
  ElMessage.success('已删除')
  loadList()
}

async function handleDuplicate(row: ApiMockItem) {
  await duplicateMock(row.id)
  ElMessage.success('已复制')
  loadList()
}

async function handleResetHit(row: ApiMockItem) {
  await resetMockHitCount(row.id)
  row.hitCount = 0
  row.lastHitAt = null
  ElMessage.success('已重置')
}

async function handleMoveUp(row: ApiMockItem) {
  const result = await moveMockUp(row.id)
  if (!result.success) {
    ElMessage.warning('已到顶部，无法上移')
    return
  }
  loadList()
}

async function handleMoveDown(row: ApiMockItem) {
  const result = await moveMockDown(row.id)
  if (!result.success) {
    ElMessage.warning('已到底部，无法下移')
    return
  }
  loadList()
}

async function handleShowAddress(row: ApiMockItem) {
  const addr = await fetchMockAddress(row.id)
  addressData.value = { mockUrl: addr.mockUrl, method: addr.method, name: row.name, headers: addr.headers }
  addressVisible.value = true
}

function openEditor(mockId?: string, interfaceId?: string) {
  editorMockId.value = mockId ?? null
  editorInterfaceId.value = interfaceId ?? null
  editorVisible.value = true
}

function openDebug(mockId: string) {
  debugMockId.value = mockId
  debugVisible.value = true
}

function handleEditorSaved() {
  editorVisible.value = false
  loadList()
}

function handleSelectionChange(rows: ApiMockItem[]) {
  selectedIds.value = rows.map((r) => r.id)
}

function formatHitTime(val: string | null): string {
  if (!val) return '-'
  return formatDateTime(val)
}
</script>

<template>
  <div class="mocks-page">
    <div class="mocks-page__toolbar">
      <div class="mocks-page__toolbar-left">
        <el-button type="primary" @click="openEditor(undefined, props.interfaceId)">
          <el-icon><Plus /></el-icon>
          新建 Mock
        </el-button>
        <el-button
          v-if="selectedIds.length"
          @click="handleBatchToggle(true)"
        >
          批量启用 ({{ selectedIds.length }})
        </el-button>
        <el-button
          v-if="selectedIds.length"
          @click="handleBatchToggle(false)"
        >
          批量停用 ({{ selectedIds.length }})
        </el-button>
      </div>
      <div class="mocks-page__toolbar-right">
        <el-select
          v-model="enabledFilter"
          clearable
          placeholder="启用状态"
          style="width: 120px"
          @change="handleSearch"
        >
          <el-option label="已启用" :value="true" />
          <el-option label="已停用" :value="false" />
        </el-select>
        <el-input
          v-model="search"
          clearable
          placeholder="搜索名称或路径"
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      row-key="id"
      class="mocks-page__table"
      @selection-change="(rows: ApiMockItem[]) => handleSelectionChange(rows)"
    >
      <el-table-column type="selection" width="40" />
      <el-table-column label="名称" prop="name" min-width="180" show-overflow-tooltip />
      <el-table-column label="方法" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="methodTagType((row as ApiMockItem).method)" disable-transitions>
            {{ (row as ApiMockItem).method }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="路径" prop="path" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态码" prop="responseStatus" width="80" align="center" />
      <el-table-column label="优先级" prop="priority" width="70" align="center" />
      <el-table-column label="启停" width="80" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="(row as ApiMockItem).enabled"
            @change="(val: string | number | boolean) => handleToggle(row as ApiMockItem, val === true)"
          />
        </template>
      </el-table-column>
      <el-table-column label="命中次数" prop="hitCount" width="90" align="center" />
      <el-table-column label="最后命中" width="160" align="center">
        <template #default="{ row }">
          {{ formatHitTime((row as ApiMockItem).lastHitAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEditor((row as ApiMockItem).id)">编辑</el-button>
          <el-button link type="primary" size="small" @click="openDebug((row as ApiMockItem).id)">调试</el-button>
          <el-button link type="primary" size="small" @click="handleShowAddress(row as ApiMockItem)">地址</el-button>
          <el-button link type="primary" size="small" @click="handleDuplicate(row as ApiMockItem)">复制</el-button>
          <el-dropdown trigger="click">
            <el-button link type="primary" size="small">更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleMoveUp(row as ApiMockItem)">上移</el-dropdown-item>
                <el-dropdown-item @click="handleMoveDown(row as ApiMockItem)">下移</el-dropdown-item>
                <el-dropdown-item @click="handleResetHit(row as ApiMockItem)">重置命中</el-dropdown-item>
                <el-dropdown-item divided @click="handleDelete(row as ApiMockItem)">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <div class="mocks-page__pagination">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <MockEditorDrawer
      v-model="editorVisible"
      :mock-id="editorMockId"
      :interface-id="editorInterfaceId"
      @saved="handleEditorSaved"
    />

    <MockDebugPanel
      v-model="debugVisible"
      :mock-id="debugMockId"
    />

    <MockAddressDialog
      v-model="addressVisible"
      :data="addressData"
    />
  </div>
</template>

<style scoped lang="scss">
.mocks-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.mocks-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
  flex-shrink: 0;
}

.mocks-page__toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mocks-page__toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mocks-page__table {
  flex: 1;
}

.mocks-page__pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--space-md);
  flex-shrink: 0;
}
</style>
