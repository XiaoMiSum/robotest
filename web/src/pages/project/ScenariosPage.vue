<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiScenePageItem, ProjectModule } from '@/types'
import { fetchProjectModuleTree } from '@/services/project'
import { copyScene, deleteScene, fetchScenePage, executeScene, followScene, unfollowScene, batchDeleteScenes } from '@/services/apiScene'
import { flattenModuleNames } from './interfacesModel'
import { formatDateTime } from '@/utils/format'
import ProjectModuleTree from '@/components/project/ProjectModuleTree.vue'

const emit = defineEmits<{
  (e: 'edit', sceneId: string): void
  (e: 'create', moduleId?: string): void
}>()

// ==================== 模块树 ====================
// 交互树由 ProjectModuleTree 组件管理；此处仅保留模块名映射供列表「所属模块」列展示
const moduleTree = ref<ProjectModule[]>([])
const moduleNames = computed(() => flattenModuleNames(moduleTree.value))
const selectedModuleId = ref<string | null>(null)

async function loadModules() {
  try {
    moduleTree.value = await fetchProjectModuleTree('scene')
  } catch {
    // 模块服务不可用时列名退化为空，不阻塞主流程
    moduleTree.value = []
  }
}

function handleSelectModule(moduleId: string) {
  selectedModuleId.value = moduleId
}

// ==================== 列表 ====================
const rows = ref<ApiScenePageItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20
const loading = ref(false)
const searchText = ref('')
const statusFilter = ref<string>('')

const STATUS_OPTIONS = [
  { value: 'success', label: '执行成功' },
  { value: 'failed', label: '执行失败' },
  { value: 'not_executed', label: '未执行' },
]
const VIEW_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'followed', label: '我关注的' },
  { value: 'created', label: '我创建的' },
]
const viewFilter = ref<string>('all')
const selectedIds = ref<string[]>([])

async function loadPage() {
  loading.value = true
  try {
    const query: Record<string, unknown> = { pageNo: pageNo.value, pageSize }
    if (selectedModuleId.value) query.moduleId = selectedModuleId.value
    if (searchText.value.trim()) query.search = searchText.value.trim()
    if (statusFilter.value === 'not_executed') query.lastStatus = null
    else if (statusFilter.value) query.lastStatus = statusFilter.value
    if (viewFilter.value && viewFilter.value !== 'all') query.view = viewFilter.value
    const page = await fetchScenePage(query as { pageNo: number; pageSize: number; moduleId?: string; search?: string; view?: string })
    rows.value = page.list
    total.value = page.total
    // 模块名映射随每次列表加载刷新，保证重命名/删除/拖拽后「所属模块」列显示最新名称
    void loadModules()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '场景列表加载失败')
  } finally {
    loading.value = false
  }
}

watch(selectedModuleId, () => {
  pageNo.value = 1
  void loadPage()
})

watch(statusFilter, () => {
  pageNo.value = 1
  void loadPage()
})

watch(viewFilter, () => {
  pageNo.value = 1
  selectedIds.value = []
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

// ==================== 行操作 ====================
function openDetail(item: ApiScenePageItem) {
  emit('edit', item.id)
}

async function handleCopy(item: ApiScenePageItem) {
  try {
    const newId = await copyScene(item.id)
    ElMessage.success('已复制')
    await loadPage()
    emit('edit', newId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复制失败')
  }
}

async function handleDelete(item: ApiScenePageItem) {
  await ElMessageBox.confirm(`删除场景「${item.name}」？删除后不可恢复。`, '删除场景', { type: 'warning' })
  try {
    await deleteScene(item.id)
    ElMessage.success('已删除')
    if (!rows.value.length && pageNo.value > 1) pageNo.value -= 1
    else void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function handleExecute(item: ApiScenePageItem) {
  try {
    await executeScene(item.id)
    ElMessage.success(`场景「${item.name}」执行已启动`)
    void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行失败')
  }
}

function handleRowCommand(cmd: string, item: ApiScenePageItem) {
  if (cmd === 'copy') void handleCopy(item)
  else if (cmd === 'delete') void handleDelete(item)
  else if (cmd === 'execute') void handleExecute(item)
}

async function toggleFollow(item: ApiScenePageItem) {
  try {
    if (item.followed) await unfollowScene(item.id)
    else await followScene(item.id)
    await loadPage()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleBatchDelete() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(`批量删除 ${selectedIds.value.length} 个场景？删除后不可恢复。`, '批量删除', { type: 'warning' })
  try {
    await batchDeleteScenes(selectedIds.value)
    ElMessage.success('已删除')
    selectedIds.value = []
    if (!rows.value.length && pageNo.value > 1) pageNo.value -= 1
    else void loadPage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败')
  }
}

function statusType(status: string | null): 'success' | 'danger' | 'info' | 'warning' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'running') return 'warning'
  return 'info'
}

function statusLabel(status: string | null): string {
  if (!status) return '未执行'
  const map: Record<string, string> = { success: '成功', failed: '失败', running: '执行中', pending: '等待中', error: '异常' }
  return map[status] ?? status
}

onMounted(async () => {
  await loadPage()
})
</script>

<template>
  <div class="scenarios-page">
    <el-card shadow="never" class="scenarios-page__search-card">
      <div class="scenarios-page__toolbar">
        <el-segmented
          v-model="viewFilter"
          :options="VIEW_OPTIONS"
        />
        <el-input
          v-model="searchText"
          placeholder="搜索场景名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 140px">
          <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="scenarios-page__spacer" />
        <template v-if="selectedIds.length > 0">
          <span class="scenarios-page__selected-count">已选 {{ selectedIds.length }} 项</span>
          <el-button type="danger" plain @click="handleBatchDelete">批量删除</el-button>
        </template>
      </div>
    </el-card>

    <div class="scenarios-page__body">
      <el-card shadow="never" class="scenarios-page__module-card">
        <div class="scenarios-page__modules">
          <ProjectModuleTree
            asset-type="scene"
            :current-module-id="selectedModuleId || undefined"
            @select-module="handleSelectModule"
            @select-document="handleSelectModule"
          />
        </div>
      </el-card>

      <el-card v-loading="loading" shadow="never" class="scenarios-page__data-card">
        <el-table :data="rows" @selection-change="(val: ApiScenePageItem[]) => selectedIds = val.map(r => r.id)">
            <el-table-column type="selection" width="40" />
            <el-table-column width="44">
              <template #default="{ row }">
                <el-icon
                  class="scenarios-page__star"
                  :class="{ 'is-active': row.followed }"
                  @click="toggleFollow(row as ApiScenePageItem)"
                ><StarFilled v-if="row.followed" /><Star v-else /></el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="场景名称" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link type="primary" :underline="false" @click="openDetail(row as ApiScenePageItem)">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column label="所属模块" width="140">
              <template #default="{ row }">{{ row.moduleId ? moduleNames.get(row.moduleId) ?? '—' : '未分组' }}</template>
            </el-table-column>
            <el-table-column label="优先级" width="80" align="center">
              <template #default="{ row }">
                <span v-if="row.priority" class="scenarios-page__priority" :class="`scenarios-page__priority--${row.priority.toLowerCase()}`">{{ row.priority }}</span>
                <span v-else class="text-neutral-400">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="stepCount" label="步骤数" width="80" align="center" />
            <el-table-column label="最近执行" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.lastStatus" size="small" :type="statusType(row.lastStatus)">{{ statusLabel(row.lastStatus) }}</el-tag>
                <span v-else class="text-neutral-400">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastExecutedAt" label="最近执行时间" width="170">
              <template #default="{ row }">
                <span v-if="row.lastExecutedAt">{{ formatDateTime(row.lastExecutedAt) }}</span>
                <span v-else class="text-neutral-400">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click="openDetail(row as ApiScenePageItem)">编辑</el-button>
                <el-dropdown trigger="click" class="scenarios-page__more" @command="(cmd: string) => handleRowCommand(cmd, row as ApiScenePageItem)">
                  <el-button link size="small">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="execute">执行</el-dropdown-item>
                      <el-dropdown-item command="copy">复制</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无测试场景，点击右上角「新建场景」开始创建" />
            </template>
          </el-table>
          <el-pagination
            v-model:current-page="pageNo"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            class="scenarios-page__pagination"
            @current-change="loadPage"
          />
        </el-card>
      </div>
  </div>
</template>

<style scoped lang="scss">
.scenarios-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  height: 100%;
}

.scenarios-page__search-card {
  flex-shrink: 0;
}

.scenarios-page__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.scenarios-page__spacer {
  flex: 1;
}

// 与前面的 link 操作按钮保持同基线、同间距
.scenarios-page__more {
  margin-left: 12px;
  vertical-align: middle;
}

.scenarios-page__selected-count {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.scenarios-page__body {
  display: flex;
  gap: var(--space-lg);
  min-height: 0;
  flex: 1;
}

.scenarios-page__module-card {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.scenarios-page__modules {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;

  :deep(.module-tree) {
    min-height: 0;
  }
}

.scenarios-page__data-card {
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

.scenarios-page__pagination {
  justify-content: flex-end;
  margin-top: var(--space-md);
}

.scenarios-page__star {
  cursor: pointer;
  color: var(--color-neutral-300);

  &.is-active {
    color: #f59e0b;
  }
}

.scenarios-page__priority {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.scenarios-page__priority--p0 { background: var(--color-priority-p0); }
.scenarios-page__priority--p1 { background: var(--color-priority-p1); }
.scenarios-page__priority--p2 { background: var(--color-priority-p2); }
.scenarios-page__priority--p3 { background: var(--color-priority-p3); }

.text-neutral-400 {
  color: var(--color-neutral-400);
}
</style>
