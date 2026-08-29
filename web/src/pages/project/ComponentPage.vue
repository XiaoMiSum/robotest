<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiComponentListItem, ApiComponentSaveReq, ApiComponentScope, ApiComponentType } from '@/types'
import { useAuthStore } from '@/stores/auth'
import {
  batchDeleteComponents,
  batchToggleComponents,
  copyComponent,
  createComponent,
  deleteComponent,
  fetchComponents,
  toggleComponent,
  updateComponent,
} from '@/services/apiComponent'
import {
  COMPONENT_SCOPE_OPTIONS,
  COMPONENT_TYPE_OPTIONS,
  SCOPE_TAG_TYPE,
  componentScopeLabel,
  componentTypeLabel,
  resolveComponentError,
} from './componentModel'
import ProcessorForm from '@/components/api-testing/ProcessorForm.vue'
import ValidatorForm from '@/components/api-testing/ValidatorForm.vue'
import ExtractorForm from '@/components/api-testing/ExtractorForm.vue'

const authStore = useAuthStore()
const canEdit = computed(() =>
  authStore.hasPermission('api-component:edit')
  || authStore.hasPermission('api-component:edit-space')
  || authStore.hasPermission('api-component:edit-global'),
)

// ==================== 列表状态 ====================

const listLoading = ref(false)
const loadError = ref(false)
const list = ref<ApiComponentListItem[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const keyword = ref('')
const keywordDraft = ref('')
const filterType = ref<ApiComponentType | ''>('')
const filterScope = ref<ApiComponentScope | ''>('')
const filterEnabled = ref<boolean | ''>('')
const pageNo = ref(1)
const pageSize = ref(20)

async function loadList(): Promise<void> {
  listLoading.value = true
  loadError.value = false
  try {
    const result = await fetchComponents({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      type: filterType.value || undefined,
      scope: filterScope.value || undefined,
      enabled: filterEnabled.value !== '' ? filterEnabled.value === true : undefined,
      keyword: keyword.value.trim() || undefined,
    })
    list.value = result.list
    total.value = result.total
    selectedIds.value = []
  } catch (err) {
    loadError.value = true
    ElMessage.error(resolveComponentError(err))
  } finally {
    listLoading.value = false
  }
}

function handlePageChange(page: number) {
  pageNo.value = page
  void loadList()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  pageNo.value = 1
  void loadList()
}

function handleSearch() {
  keyword.value = keywordDraft.value
  pageNo.value = 1
  void loadList()
}

function handleReset() {
  keywordDraft.value = ''
  keyword.value = ''
  filterType.value = ''
  filterScope.value = ''
  filterEnabled.value = ''
  pageNo.value = 1
  void loadList()
}

function handleSelectionChange(rows: ApiComponentListItem[]) {
  selectedIds.value = rows.map((r) => r.id)
}

const hasSelection = computed(() => selectedIds.value.length > 0)

// ==================== 启停 ====================

async function handleToggle(row: ApiComponentListItem) {
  try {
    await toggleComponent(row.id, !row.enabled)
    await loadList()
  } catch (err) {
    ElMessage.error(resolveComponentError(err))
  }
}

// ==================== 批量启停 ====================

async function handleBatchToggle(enabled: boolean) {
  if (!hasSelection.value) return
  const action = enabled ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${action}选中的 ${selectedIds.value.length} 个组件？`, `批量${action}`, {
      type: 'warning',
      confirmButtonText: action,
    })
  } catch {
    return
  }
  try {
    await batchToggleComponents(selectedIds.value, enabled)
    ElMessage.success(`已${action}`)
    await loadList()
  } catch (err) {
    ElMessage.error(resolveComponentError(err))
  }
}

// ==================== 批量删除 ====================

async function handleBatchDelete() {
  if (!hasSelection.value) return
  try {
    await ElMessageBox.confirm(
      `删除后不可恢复，确认删除选中的 ${selectedIds.value.length} 个组件？已引入的副本不受影响`,
      '批量删除',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await batchDeleteComponents(selectedIds.value)
    ElMessage.success('已删除')
    await loadList()
  } catch (err) {
    ElMessage.error(resolveComponentError(err))
  }
}

// ==================== 单行删除 ====================

async function handleDelete(row: ApiComponentListItem) {
  try {
    await ElMessageBox.confirm(
      `删除后不可恢复，确认删除「${row.name}」？已引入的副本不受影响`,
      '删除组件',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteComponent(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch (err) {
    ElMessage.error(resolveComponentError(err))
  }
}

// ==================== 复制 ====================

async function handleCopy(row: ApiComponentListItem) {
  try {
    await copyComponent(row.id)
    ElMessage.success('已复制')
    await loadList()
  } catch (err) {
    ElMessage.error(resolveComponentError(err))
  }
}

// ==================== 新建/编辑抽屉 ====================

const drawerVisible = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
const form = reactive<{
  type: ApiComponentType
  name: string
  description: string
  scope: ApiComponentScope
  config: Record<string, unknown>
}>({
  type: 'preprocessor',
  name: '',
  description: '',
  scope: 'project',
  config: {},
})

watch(() => form.type, () => {
  if (!editingId.value) {
    form.config = {}
  }
})

function openCreateDrawer() {
  editingId.value = null
  form.type = 'preprocessor'
  form.name = ''
  form.description = ''
  form.scope = 'project'
  form.config = {}
  drawerVisible.value = true
}

function openEditDrawer(row: ApiComponentListItem) {
  editingId.value = row.id
  form.type = row.type
  form.name = row.name
  form.description = row.description ?? ''
  form.scope = row.scope
  try {
    form.config = row.config ? JSON.parse(row.config) : {}
  } catch {
    form.config = {}
  }
  drawerVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写组件名称')
    return
  }
  saving.value = true
  try {
    const payload: ApiComponentSaveReq = {
      type: form.type,
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      config: Object.keys(form.config).length > 0 ? form.config : undefined,
    }
    if (editingId.value) {
      await updateComponent(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      payload.scope = form.scope
      await createComponent(payload)
      ElMessage.success('已创建')
    }
    drawerVisible.value = false
    await loadList()
  } catch (err) {
    ElMessage.error(resolveComponentError(err))
  } finally {
    saving.value = false
  }
}

// ==================== 初始化 ====================

onMounted(() => void loadList())
</script>

<template>
  <div class="component-page">
    <el-card shadow="never" class="component-page__card">
      <div class="component-page__toolbar">
        <el-select v-model="filterType" clearable placeholder="组件类型" style="width: 140px">
          <el-option
            v-for="opt in COMPONENT_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select v-model="filterScope" clearable placeholder="作用域" style="width: 120px">
          <el-option
            v-for="opt in COMPONENT_SCOPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select v-model="filterEnabled" clearable placeholder="状态" style="width: 100px">
          <el-option label="已启用" :value="true" />
          <el-option label="已停用" :value="false" />
        </el-select>
        <el-input
          v-model="keywordDraft"
          placeholder="搜索组件名称..."
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="component-page__toolbar-spacer" />
        <template v-if="hasSelection">
          <el-button :disabled="!canEdit" @click="handleBatchToggle(true)">批量启用</el-button>
          <el-button :disabled="!canEdit" @click="handleBatchToggle(false)">批量停用</el-button>
          <el-button type="danger" :disabled="!canEdit" @click="handleBatchDelete">批量删除</el-button>
        </template>
        <el-button type="primary" :disabled="!canEdit" @click="openCreateDrawer">新建组件</el-button>
      </div>

      <div v-if="loadError" class="component-page__empty">
        <p>组件列表加载失败</p>
        <el-button size="small" @click="loadList()">重试</el-button>
      </div>

      <el-skeleton v-else-if="listLoading" :rows="6" animated style="flex: 1" />

      <div v-else-if="list.length === 0" class="component-page__empty">
        <el-icon :size="48" class="component-page__empty-icon"><Box /></el-icon>
        <p>{{ keyword || filterType || filterScope || filterEnabled !== '' ? '无匹配结果' : '暂无公共组件' }}</p>
        <el-button v-if="keyword || filterType || filterScope || filterEnabled !== ''" size="small" @click="keyword = ''; filterType = ''; filterScope = ''; filterEnabled = ''">清除筛选</el-button>
      </div>

      <el-table
        v-else
        :data="list"
        row-key="id"
        class="component-page__table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="40" :selectable="() => canEdit" />
        <el-table-column label="名称" prop="name" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            {{ componentTypeLabel((row as ApiComponentListItem).type) }}
          </template>
        </el-table-column>
        <el-table-column label="作用域" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="SCOPE_TAG_TYPE[(row as ApiComponentListItem).scope]">{{ componentScopeLabel((row as ApiComponentListItem).scope) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="(row as ApiComponentListItem).enabled"
              :disabled="!canEdit"
              @change="handleToggle(row as ApiComponentListItem)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updatedAt" width="170" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :disabled="!canEdit" @click="openEditDrawer(row as ApiComponentListItem)">编辑</el-button>
            <el-button link type="primary" size="small" @click="handleCopy(row as ApiComponentListItem)">复制</el-button>
            <el-button link type="danger" size="small" :disabled="!canEdit" @click="handleDelete(row as ApiComponentListItem)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="component-page__pagination">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 新建/编辑抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="editingId ? '编辑组件' : '新建组件'"
      size="640px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="component-page__form">
        <el-form-item label="组件名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="如：Token 预置" />
        </el-form-item>
        <el-form-item label="组件类型" required>
          <el-select v-model="form.type" :disabled="!!editingId" style="width: 100%">
            <el-option
              v-for="opt in COMPONENT_TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingId" label="作用域" required>
          <el-select v-model="form.scope" style="width: 100%">
            <el-option
              v-for="opt in COMPONENT_SCOPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" placeholder="组件用途说明" />
        </el-form-item>

        <!-- 按类型渲染配置表单 -->
        <el-divider v-if="form.type === 'preprocessor' || form.type === 'postprocessor'" content-position="left">处理器配置</el-divider>
        <ProcessorForm
          v-if="form.type === 'preprocessor' || form.type === 'postprocessor'"
          v-model="form.config"
        />

        <el-divider v-if="form.type === 'validator'" content-position="left">验证器配置</el-divider>
        <ValidatorForm
          v-if="form.type === 'validator'"
          v-model="form.config"
        />

        <el-divider v-if="form.type === 'extractor'" content-position="left">提取器配置</el-divider>
        <ExtractorForm
          v-if="form.type === 'extractor'"
          v-model="form.config"
        />
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.component-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.component-page__card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: var(--radius-lg);

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
}

.component-page__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.component-page__toolbar-spacer {
  flex: 1;
}

.component-page__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);

  p {
    margin: var(--space-sm) 0;
  }
}

.component-page__empty-icon {
  color: var(--color-neutral-300);
}

.component-page__table {
  flex: 1;
  margin-top: var(--space-md);
}

.component-page__pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-sm) 0 0;
  flex-shrink: 0;
}

.component-page__form {
  padding: 0 var(--space-sm);
}
</style>
