<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiInterfaceItem, ApiInterfaceStatus, ApiInterfaceView, ProjectModule } from '@/types'
import { fetchProjectModuleTree } from '@/services/project'
import {
  batchDeleteInterfaces,
  batchMoveInterfaces,
  copyInterface,
  deleteInterface,
  fetchInterfacePage,
  followInterface,
  unfollowInterface,
  updateInterfaceStatus,
} from '@/services/apiInterface'
import { useApiTestingUiStore } from '@/stores/apiTestingUi'
import { formatDateTime } from '@/utils/format'
import {
  buildInterfaceListQuery,
  flattenModuleNames,
  methodTagType,
} from './interfacesModel'
import type { PendingDebugRequest } from '@/stores/apiTestingUi'
import ImportDialog from './interfaces/ImportDialog.vue'
import ProjectModuleTree from '@/components/project/ProjectModuleTree.vue'

const router = useRouter()
const uiStore = useApiTestingUiStore()

const emit = defineEmits<{
  (e: 'create', moduleId?: string): void
  (e: 'edit', interfaceId: string): void
}>()

// ==================== 模块树 ====================
// 交互树由 ProjectModuleTree 组件管理；此处仅保留模块名映射供列表「模块」列展示
const moduleTree = ref<ProjectModule[]>([])
const moduleNames = computed(() => flattenModuleNames(moduleTree.value))
const selectedModuleId = ref<string | null>(null)

async function loadModules() {
  try {
    moduleTree.value = await fetchProjectModuleTree('interface')
  } catch {
    // 模块服务不可用时列名退化为空，不阻塞主流程
    moduleTree.value = []
  }
}

function handleSelectModule(moduleId: string) {
  selectedModuleId.value = moduleId
}

// ==================== 列表 ====================
const rows = ref<ApiInterfaceItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20
const loading = ref(false)
const searchText = ref('')
const statusFilter = ref<'' | ApiInterfaceStatus>('')
const viewFilter = ref<ApiInterfaceView>('all')
const selectedRows = ref<ApiInterfaceItem[]>([])

async function loadPage() {
  loading.value = true
  try {
    const page = await fetchInterfacePage(
      buildInterfaceListQuery({
        pageNo: pageNo.value,
        pageSize,
        moduleId: selectedModuleId.value,
        search: searchText.value.trim() || undefined,
        status: statusFilter.value || undefined,
        view: viewFilter.value,
      }) as { pageNo: number; pageSize: number; moduleId?: string; search?: string; status?: string; view?: ApiInterfaceView },
    )
    rows.value = page.list
    total.value = page.total
    // 模块名映射随每次列表加载刷新，保证重命名/删除/拖拽后「模块」列显示最新名称
    void loadModules()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '接口列表加载失败')
  } finally {
    loading.value = false
  }
}

watch([selectedModuleId, statusFilter, viewFilter], () => {
  pageNo.value = 1
  void loadPage()
})

function handleSearch() {
  pageNo.value = 1
  void loadPage()
}

function handleReset() {
  searchText.value = ''
  statusFilter.value = ''
  viewFilter.value = 'all'
  pageNo.value = 1
  void loadPage()
}

const VIEW_OPTIONS: { value: ApiInterfaceView; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'followed', label: '我关注的' },
  { value: 'created', label: '我创建的' },
]

// ==================== 行操作 ====================
function openEditor(item: Pick<ApiInterfaceItem, 'id'>) {
  emit('edit', item.id)
}

function openCreate() {
  emit('create', selectedModuleId.value ?? undefined)
}

/** 调试联动：预填快照交给 store，切到快速调试子页由其消费 */
function debugFromRow(item: ApiInterfaceItem) {
  const pending: PendingDebugRequest = {
    name: item.name,
    method: item.method,
    path: item.path,
    source: { id: item.id, name: item.name },
  }
  uiStore.pendingDebugRequest = pending
  void router.replace({ query: { ...router.currentRoute.value.query, tab: 'debug' } })
}

async function toggleFollow(item: ApiInterfaceItem) {
  try {
    if (item.followed) {
      await unfollowInterface(item.id)
      item.followed = false
    } else {
      await followInterface(item.id)
      item.followed = true
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function handleStatusChange(item: ApiInterfaceItem, next: ApiInterfaceStatus) {
  try {
    await updateInterfaceStatus(item.id, next)
    item.status = next
  } catch (error) {
    item.status = item.status === 'enabled' ? 'disabled' : 'enabled'
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  }
}

async function handleCopy(item: ApiInterfaceItem) {
  try {
    const newId = await copyInterface(item.id)
    ElMessage.success('已复制，正在打开副本')
    await loadPage()
    emit('edit', newId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复制失败')
  }
}

async function handleDelete(item: ApiInterfaceItem) {
  await ElMessageBox.confirm(`删除接口「${item.name}」？删除后不可恢复。`, '删除接口', { type: 'warning' })
  try {
    await deleteInterface(item.id)
    ElMessage.success('已删除')
    if (!rows.value.length && pageNo.value > 1) pageNo.value -= 1
    else void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

// ==================== 批量操作 ====================
const showBatchMove = ref(false)
const batchMoveTarget = ref<string | null>(null)

const hasSelection = computed(() => selectedRows.value.length > 0)

async function confirmBatchMove() {
  if (!batchMoveTarget.value) {
    ElMessage.warning('请选择目标模块')
    return
  }
  try {
    await batchMoveInterfaces(selectedRows.value.map((row) => row.id), batchMoveTarget.value)
    ElMessage.success(`已移动 ${selectedRows.value.length} 个接口`)
    showBatchMove.value = false
    void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量移动失败')
  }
}

async function confirmBatchDelete() {
  const ids = selectedRows.value.map((row) => row.id)
  await ElMessageBox.confirm(`删除选中的 ${ids.length} 个接口？任一被场景引用将整体拒绝。`, '批量删除', { type: 'warning' })
  try {
    await batchDeleteInterfaces(ids)
    ElMessage.success('已删除')
    selectedRows.value = []
    if (!rows.value.length && pageNo.value > 1) pageNo.value -= 1
    else void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败')
  }
}

// ==================== 导入 ====================
const showImport = ref(false)

/** 供宿主 workspace 头部「导入」按钮调用：始终优先切回列表后再弹出对话框 */
function openImport() {
  showImport.value = true
}

function handleImported() {
  pageNo.value = 1
  void loadPage()
}

onMounted(async () => {
  await loadPage()
})

// 头部「新建/导入」按钮通过 ref 委托到列表页，以复用当前模块上下文
defineExpose({ openCreate, openImport })
</script>

<template>
  <div class="interfaces-page">
    <el-card shadow="never" class="interfaces-page__search-card">
      <div class="interfaces-page__toolbar">
        <el-segmented
          v-model="viewFilter"
          :options="VIEW_OPTIONS"
          data-test="interface-view-switch"
        />
        <el-input
          v-model="searchText"
          placeholder="搜索名称 / 路径"
          clearable
          style="width: 240px"
          data-test="interface-search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 120px" data-test="interface-status-filter">
          <el-option value="enabled" label="启用" />
          <el-option value="disabled" label="停用" />
        </el-select>
        <el-button type="primary" data-test="interface-search-btn" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button data-test="interface-reset-btn" @click="handleReset">重置</el-button>
        <div class="interfaces-page__spacer" />
        <template v-if="hasSelection">
          <span class="interfaces-page__selected-count">已选 {{ selectedRows.length }} 项</span>
          <el-button size="small" data-test="batch-move-btn" @click="showBatchMove = true">批量移动</el-button>
          <el-button size="small" type="danger" plain data-test="batch-delete-btn" @click="confirmBatchDelete">批量删除</el-button>
          <el-divider direction="vertical" />
        </template>
      </div>
    </el-card>

    <div class="interfaces-page__body">
      <el-card shadow="never" class="interfaces-page__module-card">
        <div class="interfaces-page__modules">
          <ProjectModuleTree
            asset-type="interface"
            :current-module-id="selectedModuleId || undefined"
            @select-module="handleSelectModule"
            @select-document="handleSelectModule"
          />
        </div>
      </el-card>

      <el-card v-loading="loading" shadow="never" class="interfaces-page__data-card">
        <el-table
            :data="rows"
            data-test="interface-table"
            @selection-change="(selection: ApiInterfaceItem[]) => (selectedRows = selection)"
          >
            <el-table-column type="selection" width="42" />
            <el-table-column width="44">
              <template #default="{ row }">
                <el-icon
                  class="interfaces-page__star"
                  :class="{ 'is-active': row.followed }"
                  :data-test="'interface-star-' + row.id"
                  @click="toggleFollow(row as ApiInterfaceItem)"
                ><StarFilled v-if="row.followed" /><Star v-else /></el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link type="primary" :underline="false" @click="openEditor(row as Pick<ApiInterfaceItem, 'id'>)">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column label="方法" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="methodTagType(row.method)">{{ row.method }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip />
            <el-table-column label="模块" width="140">
              <template #default="{ row }">{{ row.moduleId ? moduleNames.get(row.moduleId) ?? '—' : '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status === 'enabled'"
                  size="small"
                  @change="(value: string | number | boolean) => handleStatusChange(row as ApiInterfaceItem, value ? 'enabled' : 'disabled')"
                />
              </template>
            </el-table-column>
            <el-table-column prop="referenceCount" label="引用" width="70" align="center" />
            <el-table-column prop="updatedAt" label="更新时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" type="primary" :data-test="'interface-debug-' + row.id" @click="debugFromRow(row as ApiInterfaceItem)">调试</el-button>
                <el-button link size="small" @click="openEditor(row as Pick<ApiInterfaceItem, 'id'>)">编辑</el-button>
                <el-dropdown trigger="click" class="interfaces-page__more" @command="(cmd: string) => cmd === 'copy' ? handleCopy(row as ApiInterfaceItem) : handleDelete(row as ApiInterfaceItem)">
                  <el-button link size="small">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="copy">复制</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无接口，点击右上角「新建接口」或导入现有定义" />
            </template>
          </el-table>
          <el-pagination
            v-model:current-page="pageNo"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            class="interfaces-page__pagination"
            @current-change="loadPage"
          />
        </el-card>
      </div>

    <ImportDialog v-model="showImport" @imported="handleImported" />

    <el-dialog v-model="showBatchMove" title="批量移动到模块" width="420px">
      <el-tree
        :data="moduleTree"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        :expand-on-click-node="false"
        highlight-current
        @node-click="(node: ProjectModule) => (batchMoveTarget = node.id)"
      />
      <template #footer>
        <el-button @click="showBatchMove = false">取消</el-button>
        <el-button type="primary" data-test="batch-move-confirm-btn" @click="confirmBatchMove">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.interfaces-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  height: 100%;
}

.interfaces-page__search-card {
  flex-shrink: 0;
}

.interfaces-page__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.interfaces-page__spacer {
  flex: 1;
}

// 与前面的 link 操作按钮保持同基线、同间距
.interfaces-page__more {
  margin-left: 12px;
  vertical-align: middle;
}

.interfaces-page__selected-count {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.interfaces-page__body {
  display: flex;
  gap: var(--space-lg);
  min-height: 0;
  flex: 1;
}

.interfaces-page__module-card {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.interfaces-page__modules {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;

  :deep(.module-tree) {
    min-height: 0;
  }
}

.interfaces-page__data-card {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;

  :deep(.el-card__body) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
}

.interfaces-page__star {
  cursor: pointer;
  color: var(--color-neutral-300);

  &.is-active {
    color: #f59e0b;
  }
}

.interfaces-page__pagination {
  justify-content: flex-end;
  margin-top: var(--space-md);
}

</style>
