<script setup lang="ts">
import { computed, onBeforeUnmount, ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiMockItem } from '@/types'
import {
  fetchMockPage,
  toggleMock,
  batchToggleMocks,
  deleteMock,
  resetMockHitCount,
  fetchMockAddress,
} from '@/services/project/api-testing/mock'
import { methodTagType } from '@/composables/project/api-testing/mock/mocksModel'
import { formatDateTime } from '@/utils/format'
import MockEditorDrawer from './MockEditorDrawer.vue'
import MockDebugPanel from './MockDebugPanel.vue'
import MockAddressDialog from './MockAddressDialog.vue'
import BatchActionBar from '@/components/common/BatchActionBar.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import ListWorkspace from '@/components/common/ListWorkspace.vue'
import MoreDropdown from '@/components/common/MoreDropdown.vue'
import ListTitleLink from '@/components/common/ListTitleLink.vue'
import ListLoadFeedback from '@/components/common/ListLoadFeedback.vue'
import TableColumnSetting from '@/components/common/TableColumnSetting.vue'
import { useTableColumns, type TableColumnMeta } from '@/composables/common/useTableColumns'

const MOCK_COLUMNS: TableColumnMeta[] = [
  { key: 'selection', label: '选择', pinned: true },
  { key: 'name', label: '名称' },
  { key: 'method', label: '方法' },
  { key: 'path', label: '路径' },
  { key: 'statusCode', label: '状态码' },
  { key: 'priority', label: '优先级' },
  { key: 'enabled', label: '启停' },
  { key: 'hitCount', label: '命中次数' },
  { key: 'lastHit', label: '最后命中' },
  { key: 'actions', label: '操作', pinned: true },
]
const { visibleKeys, setVisible, selectAll, reset } = useTableColumns({
  pageKey: 'mock-list',
  columns: MOCK_COLUMNS,
})

const props = defineProps<{
  interfaceId?: string
}>()

const loading = ref(false)
const loadError = ref('')
const hasLoaded = ref(false)
const list = ref<ApiMockItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const search = ref('')
const searchDraft = ref('')
const enabledFilter = ref<boolean | undefined>(undefined)
const selectedIds = ref<string[]>([])
// 批量条清空需直接驱动表格勾选态，避免只清 ids 导致复选框残留
const tableRef = ref<{ clearSelection: () => void } | null>(null)

// 编辑器
const editorVisible = ref(false)
const editorMockId = ref<string | null>(null)
const editorInterfaceId = ref<string | null>(null)
// 复制模式：携带源 Mock id 供抽屉预填原配置
const editorCopyFromId = ref<string | null>(null)

// 调试面板
const debugVisible = ref(false)
const debugMockId = ref<string | null>(null)

// 地址弹窗
const addressVisible = ref(false)
const addressData = ref<{ mockUrl: string; method: string; name: string; headers?: Record<string, unknown> } | null>(null)

let listRequestId = 0

const hasFilters = computed(() => Boolean(search.value || enabledFilter.value !== undefined))
const emptyMode = computed(() =>
  loading.value ? 'loading' : loadError.value ? 'error' : hasFilters.value ? 'filtered' : 'empty',
)
const emptyTitle = computed(() => {
  if (loading.value) return '正在加载 Mock 规则…'
  if (loadError.value) return '加载 Mock 列表失败'
  if (hasFilters.value) return '无匹配结果'
  return '暂无 Mock 规则'
})

function errorMessage(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback
}

async function loadList(): Promise<void> {
  const requestId = ++listRequestId
  loading.value = true
  loadError.value = ''
  try {
    const result = await fetchMockPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      interfaceId: props.interfaceId,
      search: search.value || undefined,
      enabled: enabledFilter.value,
    })
    if (requestId !== listRequestId) return
    list.value = result.list
    total.value = result.total
    hasLoaded.value = true
  } catch (err) {
    if (requestId !== listRequestId) return
    const message = errorMessage(err, '加载 Mock 列表失败')
    loadError.value = message
    ElMessage.error(message)
  } finally {
    if (requestId === listRequestId) {
      loading.value = false
    }
  }
}

function retry(): void {
  void loadList()
}

onMounted(() => {
  void loadList()
})

onBeforeUnmount(() => {
  listRequestId += 1
})

watch(() => props.interfaceId, () => {
  pageNo.value = 1
  void loadList()
})

function handleSearch(): void {
  search.value = searchDraft.value.trim()
  pageNo.value = 1
  void loadList()
}

function handleReset(): void {
  searchDraft.value = ''
  search.value = ''
  enabledFilter.value = undefined
  pageNo.value = 1
  void loadList()
}

function handlePageChange(newPage: number): void {
  pageNo.value = newPage
  void loadList()
}

function handleSizeChange(newSize: number): void {
  pageSize.value = newSize
  pageNo.value = 1
  void loadList()
}

async function handleToggle(row: ApiMockItem, enabled: boolean) {
  try {
    await toggleMock(row.id, enabled)
    row.enabled = enabled
    ElMessage.success(enabled ? '已启用' : '已停用')
  } catch {
    void loadList()
  }
}

async function handleBatchToggle(enabled: boolean) {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(
    enabled
      ? `启用 ${selectedIds.value.length} 条 Mock 规则？`
      : `停用 ${selectedIds.value.length} 条 Mock 规则？停用后依赖方将无法访问这些 Mock。`,
    enabled ? '启用' : '停用',
    { type: enabled ? 'info' : 'warning' },
  )
  try {
    const result = await batchToggleMocks({ ids: selectedIds.value, enabled })
    ElMessage.success(`已${enabled ? '启用' : '停用'} ${result.updatedCount} 条规则`)
    tableRef.value?.clearSelection()
    void loadList()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${enabled ? '启用' : '停用'}失败`)
    void loadList()
  }
}

async function handleDelete(row: ApiMockItem) {
  await ElMessageBox.confirm(`确定删除 Mock「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteMock(row.id)
  ElMessage.success('已删除')
  void loadList()
}

function handleDuplicate(row: ApiMockItem) {
  // 复制 = 用源规则预填新增表单，由用户调整后保存提交，而非后端直接复制落库
  openEditorWithCopy(row.id)
}

async function handleResetHit(row: ApiMockItem) {
  await resetMockHitCount(row.id)
  row.hitCount = 0
  row.lastHitAt = null
  ElMessage.success('已重置')
}

async function handleShowAddress(row: ApiMockItem) {
  const addr = await fetchMockAddress(row.id)
  addressData.value = { mockUrl: addr.mockUrl, method: addr.method, name: row.name, headers: addr.headers }
  addressVisible.value = true
}

function openEditor(mockId?: string, interfaceId?: string) {
  editorMockId.value = mockId ?? null
  editorInterfaceId.value = interfaceId ?? null
  editorCopyFromId.value = null
  editorVisible.value = true
}

function openEditorWithCopy(copyFromId: string) {
  editorMockId.value = null
  editorInterfaceId.value = null
  editorCopyFromId.value = copyFromId
  editorVisible.value = true
}

function openDebug(mockId: string) {
  debugMockId.value = mockId
  debugVisible.value = true
}

function handleEditorSaved() {
  editorVisible.value = false
  void loadList()
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
  <main class="mocks-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">Mock 服务</h1>
        <p class="page-head__desc">为接口配置模拟响应，解除前后端联调依赖</p>
      </div>
      <el-button type="primary" @click="openEditor(undefined, props.interfaceId)">
        <el-icon><Plus /></el-icon>
        新建 Mock
      </el-button>
    </header>

    <ListWorkspace label="Mock 规则列表" :loading="loading">
      <template #toolbar>
        <div class="mocks-page__filters">
          <el-input
            v-model="searchDraft"
            clearable
            placeholder="请输入名称或路径"
            style="width: 220px"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select
            v-model="enabledFilter"
            clearable
            placeholder="状态"
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option label="已启用" :value="true" />
            <el-option label="已停用" :value="false" />
          </el-select>
          <el-button @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <span class="mocks-page__count">共 {{ total }} 条</span>
      </template>
      <template v-if="hasLoaded && loadError" #feedback>
        <ListLoadFeedback
          mode="refresh-error"
          title="刷新失败，当前显示上次加载的数据"
          :description="loadError"
          :retrying="loading"
          @retry="retry"
        />
      </template>

    <!-- 表体区是页面内唯一滚动拥有者：表头固定，数据超出固定高度时表体内滚动（UI-SC-02 / UI-SC-05） -->
    <div v-if="hasLoaded || !loadError" class="mocks-page__table">
      <el-table
        ref="tableRef"
        :data="list"
        row-key="id"
        height="100%"
        @selection-change="(rows: ApiMockItem[]) => handleSelectionChange(rows)"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column v-if="visibleKeys.includes('name')" label="名称" min-width="180">
          <template #default="{ row }">
            <ListTitleLink :title="(row as ApiMockItem).name" @open="openEditor((row as ApiMockItem).id)" />
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('method')" label="方法" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="methodTagType((row as ApiMockItem).method)" disable-transitions>
              {{ (row as ApiMockItem).method }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('path')" label="路径" prop="path" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="visibleKeys.includes('statusCode')" label="状态码" prop="responseStatus" width="80" align="center" />
        <el-table-column v-if="visibleKeys.includes('priority')" label="优先级" prop="priority" width="70" align="center" />
        <el-table-column v-if="visibleKeys.includes('enabled')" label="启停" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="(row as ApiMockItem).enabled"
              @change="(val: string | number | boolean) => handleToggle(row as ApiMockItem, val === true)"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="visibleKeys.includes('hitCount')" label="命中次数" prop="hitCount" width="90" align="center" />
        <el-table-column v-if="visibleKeys.includes('lastHit')" label="最后命中" width="160" align="center">
          <template #default="{ row }">
            {{ formatHitTime((row as ApiMockItem).lastHitAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #header>
            <div class="mocks-page__action-header">
              <span>操作</span>
              <TableColumnSetting
                icon-only
                :columns="MOCK_COLUMNS"
                :visible-keys="visibleKeys"
                @change="(key, visible) => setVisible(key, visible)"
                @select-all="selectAll"
                @reset="reset"
              />
            </div>
          </template>
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditor((row as ApiMockItem).id)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openDebug((row as ApiMockItem).id)">调试</el-button>
            <el-button link type="primary" size="small" @click="handleShowAddress(row as ApiMockItem)">地址</el-button>
            <el-button link type="primary" size="small" @click="handleDuplicate(row as ApiMockItem)">复制</el-button>
            <MoreDropdown trigger="click" class="mocks-page__more">
              <template #trigger>
                <el-button link type="primary" size="small">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              </template>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleResetHit(row as ApiMockItem)">重置命中</el-dropdown-item>
                  <el-dropdown-item divided @click="handleDelete(row as ApiMockItem)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </MoreDropdown>
          </template>
        </el-table-column>
        <template #empty>
          <ListLoadFeedback
            v-if="loading || !hasLoaded || !loadError"
            :mode="emptyMode"
            :title="emptyTitle"
            :description="loadError"
            @retry="retry"
            @reset="handleReset"
          />
        </template>
      </el-table>
    </div>

    <div v-else class="mocks-page__first-error">
      <ListLoadFeedback mode="error" :title="emptyTitle" :description="loadError" :retrying="loading" @retry="retry" />
    </div>

      <template v-if="hasLoaded || !loadError" #pagination>
        <ListPagination
          v-model:page-no="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          @page-change="({ pageNo: page }) => handlePageChange(page)"
          @size-change="({ pageSize: size }) => handleSizeChange(size)"
        />
      </template>
    </ListWorkspace>

    <BatchActionBar :count="selectedIds.length" @clear="tableRef?.clearSelection()">
      <el-button size="small" @click="handleBatchToggle(true)">批量启用</el-button>
      <el-button size="small" type="danger" plain @click="handleBatchToggle(false)">批量停用</el-button>
    </BatchActionBar>

    <MockEditorDrawer
      v-model="editorVisible"
      :mock-id="editorMockId"
      :interface-id="editorInterfaceId"
      :copy-from-id="editorCopyFromId"
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
  </main>
</template>

<style scoped lang="scss">
/* 高度链路：接口测试主区 → 页面 → 表体容器，滚动只发生在表体（UI-SC-01 / UI-SC-02） */
.mocks-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.page-head {
  display: flex;
  flex-wrap: wrap;
  flex-shrink: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.page-head__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
}

.page-head__desc {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}

.mocks-page__filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md);
}

.mocks-page__count {
  margin-left: auto;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.mocks-page__first-error {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: flex-start;
  padding: 14px 0;
}

/* 表体容器：flex:1 承接剩余高度并允许收缩（min-height:0），表格外层不再叠加 overflow 滚动（UI-SC-02 / UI-SC-06） */
.mocks-page__table {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

// 与前面的 link 操作按钮保持同基线、同间距
.mocks-page__more {
  margin-left: 12px;
  vertical-align: middle;
}

.mocks-page__action-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
  width: 100%;
}
</style>
