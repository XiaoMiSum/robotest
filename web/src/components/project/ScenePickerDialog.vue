<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiScenePageItem, ProjectModule } from '@/types'
import { fetchScenePage } from '@/services/project/scene'
import { fetchProjectModuleTree } from '@/services/project'
import { flattenModuleNames } from '@/pages/project/interfacesModel'
import { formatDateTime } from '@/utils/format'

/**
 * 场景多选选择器（定时任务「指定场景」执行方式）：
 * 关键字搜索 + 模块筛选 + 分页列表勾选，跨页保留选择，已选场景标签回填。
 * 场景可能在几十上百个，搜索定位优先于一次性铺开全部列表。
 */
const props = defineProps<{
  selectedIds?: string[]
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  confirm: [selected: { id: string; name: string }[]]
}>()

const loading = ref(false)
const items = ref<ApiScenePageItem[]>([])
const total = ref(0)
const keyword = ref('')
const moduleId = ref<string | undefined>()
const pageNo = ref(1)
const pageSize = 20
const selectedRows = ref<ApiScenePageItem[]>([])
const tableRef = ref<{
  clearSelection(): void
  toggleRowSelection(row: ApiScenePageItem, selected?: boolean): void
}>()

// 模块名映射与筛选选项
const moduleOptions = ref<{ value: string; label: string }[]>([])
const moduleNames = ref<Map<string, string>>(new Map())

async function loadModules() {
  try {
    const tree: ProjectModule[] = await fetchProjectModuleTree('scene')
    moduleNames.value = flattenModuleNames(tree)
    moduleOptions.value = Array.from(moduleNames.value, ([value, label]) => ({ value, label }))
  } catch (error) {
    moduleNames.value = new Map()
    moduleOptions.value = []
    if (visible.value) ElMessage.error(error instanceof Error ? `模块加载失败：${error.message}` : '模块加载失败')
  }
}

async function load() {
  loading.value = true
  try {
    const params: { pageNo: number; pageSize: number; moduleId?: string; search?: string } = {
      pageNo: pageNo.value,
      pageSize,
    }
    if (keyword.value.trim()) params.search = keyword.value.trim()
    if (moduleId.value) params.moduleId = moduleId.value
    const page = await fetchScenePage(params)
    items.value = page.list
    total.value = page.total
    // 回勾已选：列表就绪后补勾，配合 reserve-selection 跨页保留
    const preselect = new Set(props.selectedIds ?? [])
    requestAnimationFrame(() => {
      for (const row of items.value) {
        if (preselect.has(row.id)) tableRef.value?.toggleRowSelection(row, true)
      }
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '场景加载失败')
  } finally {
    loading.value = false
  }
}

function handleSelectionChange(rows: ApiScenePageItem[]): void {
  selectedRows.value = rows
}

function search(): void {
  pageNo.value = 1
  void load()
}

function handleModuleChange(): void {
  search()
}

function handlePageChange(page: number): void {
  pageNo.value = page
  void load()
}

function close(): void {
  visible.value = false
}

function confirm(): void {
  emit('confirm', selectedRows.value.map((r) => ({ id: r.id, name: r.name })))
  close()
}

// 每次打开重置筛选并加载首页；clearSelection 需在表格就绪后执行，故放到下一帧
watch(
  visible,
  (open) => {
    if (!open) return
    keyword.value = ''
    moduleId.value = undefined
    pageNo.value = 1
    selectedRows.value = []
    void loadModules()
    requestAnimationFrame(() => {
      tableRef.value?.clearSelection()
      void load()
    })
  },
  { immediate: true },
)

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
</script>

<template>
  <el-dialog v-model="visible" title="选择场景" width="820px" append-to-body>
    <div class="scene-picker">
      <div class="scene-picker__search">
        <el-input
          v-model="keyword"
          placeholder="按场景名称搜索"
          clearable
          class="scene-picker__keyword"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select
          v-model="moduleId"
          placeholder="全部模块"
          clearable
          class="scene-picker__module"
          @change="handleModuleChange"
        >
          <el-option v-for="m in moduleOptions" :key="m.value" :value="m.value" :label="m.label" />
        </el-select>
        <el-button @click="search">过滤</el-button>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="items"
        row-key="id"
        :reserve-selection="true"
        height="460"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="40" :reserve-selection="true" />
        <el-table-column prop="name" label="场景名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="所属模块" width="140">
          <template #default="{ row }">{{ row.moduleId ? moduleNames.get(row.moduleId) ?? '—' : '未分组' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.lastStatus" size="small" :type="statusType(row.lastStatus)">{{ statusLabel(row.lastStatus) }}</el-tag>
            <span v-else class="scene-picker__muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="stepCount" label="步骤数" width="80" align="center" />
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无匹配场景" :image-size="60" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="scene-picker__pagination"
        @current-change="handlePageChange"
      />
    </div>

    <template #footer>
      <div class="scene-picker__footer">
        <span class="scene-picker__count">已选 {{ selectedRows.length }} 个</span>
        <el-button @click="close">取消</el-button>
        <el-button type="primary" @click="confirm">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.scene-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.scene-picker__search {
  display: flex;
  gap: 8px;
}

.scene-picker__keyword {
  flex: 1;
}

.scene-picker__module {
  width: 220px;
}

.scene-picker__pagination {
  justify-content: flex-end;
}

.scene-picker__footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scene-picker__count {
  flex: 1;
  text-align: right;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.scene-picker__muted {
  color: var(--el-text-color-placeholder);
}
</style>