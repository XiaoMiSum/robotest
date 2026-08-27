<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiBuiltinFunctionGroup, ApiCustomFunctionListItem, ApiFunctionScope, ApiCustomFunctionDetail } from '@/types'
import { useAuthStore } from '@/stores/auth'
import {
  fetchBuiltinCatalog,
  fetchCustomFunctions,
  fetchCustomFunctionDetail,
  createCustomFunction,
  updateCustomFunction,
  toggleCustomFunction,
  deleteCustomFunction,
} from '@/services/apiFunction'
import {
  filterFunctions,
  formatScopeLabel,
  resolveFunctionError,
  SCOPE_OPTIONS,
  FUNCTION_TAB_OPTIONS,
  type FunctionTab,
} from './functionModel'
import FunctionHelperDialog from './FunctionHelperDialog.vue'

/** 列表项统一类型 */
interface DisplayListItem {
  type: 'builtin' | 'custom'
  name: string
  description: string
  scope?: ApiFunctionScope
  id?: string
  enabled?: boolean
}

const authStore = useAuthStore()
const canEdit = computed(
  () =>
    authStore.hasPermission('api-func:edit') ||
    authStore.hasPermission('api-func:edit-space') ||
    authStore.hasPermission('api-func:edit-global'),
)

// ==================== 列表状态 ====================

const listLoading = ref(false)
const loadError = ref(false)
const builtinGroups = ref<ApiBuiltinFunctionGroup[]>([])
const customList = ref<ApiCustomFunctionListItem[]>([])
const keyword = ref('')
const activeTab = ref<FunctionTab>('all')

let searchTimer: ReturnType<typeof setTimeout> | undefined
function handleSearchInput(): void {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void loadCustomList(), 300)
}
onBeforeUnmount(() => clearTimeout(searchTimer))

const filtered = computed(() => filterFunctions(builtinGroups.value, customList.value, keyword.value))

const displayItems = computed(() => {
  const { builtin, custom } = filtered.value
  const items: DisplayListItem[] = []
  if (activeTab.value === 'all' || activeTab.value === 'builtin') {
    for (const group of builtin) {
      for (const fn of group.functions) {
        items.push({ type: 'builtin', name: fn.name, description: fn.description })
      }
    }
  }
  if (activeTab.value === 'all' || activeTab.value === 'custom') {
    for (const fn of custom) {
      items.push({ type: 'custom', name: fn.name, description: fn.description ?? '', scope: fn.scope, id: fn.id, enabled: fn.enabled })
    }
  }
  return items
})

const selectedType = ref<'builtin' | 'custom' | null>(null)
const selectedName = ref('')
const selectedCustomId = ref('')

async function loadBuiltin(): Promise<void> {
  try {
    builtinGroups.value = await fetchBuiltinCatalog()
  } catch (err) {
    loadError.value = true
    ElMessage.error(resolveFunctionError(err))
  }
}

async function loadCustomList(): Promise<void> {
  try {
    customList.value = await fetchCustomFunctions(keyword.value.trim() ? { keyword: keyword.value.trim() } : undefined)
  } catch (err) {
    loadError.value = true
    ElMessage.error(resolveFunctionError(err))
  }
}

async function loadAll(): Promise<void> {
  listLoading.value = true
  loadError.value = false
  try {
    await Promise.all([loadBuiltin(), loadCustomList()])
  } finally {
    listLoading.value = false
  }
}

function selectItem(type: 'builtin' | 'custom', name: string, id?: string): void {
  selectedType.value = type
  selectedName.value = name
  selectedCustomId.value = id ?? ''
}

// 内置函数选中后的详情
const selectedBuiltinFn = computed(() => {
  if (selectedType.value !== 'builtin') return null
  for (const group of builtinGroups.value) {
    const found = group.functions.find((fn) => fn.name === selectedName.value)
    if (found) return found
  }
  return null
})

// 自定义函数详情
const customDetail = ref<ApiCustomFunctionDetail | null>(null)
const detailLoading = ref(false)

watch(selectedCustomId, async (id) => {
  if (!id || selectedType.value !== 'custom') {
    customDetail.value = null
    return
  }
  detailLoading.value = true
  try {
    customDetail.value = await fetchCustomFunctionDetail(id)
  } catch (err) {
    ElMessage.error(resolveFunctionError(err))
  } finally {
    detailLoading.value = false
  }
})

// ==================== 新建 ====================

const createDialogVisible = ref(false)
const createForm = reactive({
  name: '',
  description: '',
  paramsDesc: '',
  script: '',
  scope: 'project' as ApiFunctionScope,
})
const creating = ref(false)

function openCreateDialog(): void {
  createForm.name = ''
  createForm.description = ''
  createForm.paramsDesc = ''
  createForm.script = ''
  createForm.scope = 'project'
  createDialogVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写函数名称')
    return
  }
  if (!createForm.script.trim()) {
    ElMessage.warning('请填写 Groovy 脚本')
    return
  }
  creating.value = true
  try {
    const resp = await createCustomFunction({
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
      paramsDesc: createForm.paramsDesc.trim() || undefined,
      script: createForm.script.trim(),
      scope: createForm.scope,
    })
    createDialogVisible.value = false
    ElMessage.success('函数已创建')
    await loadCustomList()
    selectItem('custom', createForm.name.trim(), resp.id)
  } catch (err) {
    ElMessage.error(resolveFunctionError(err))
  } finally {
    creating.value = false
  }
}

// ==================== 编辑 ====================

const editDialogVisible = ref(false)
const editForm = reactive({
  id: '',
  name: '',
  description: '',
  paramsDesc: '',
  script: '',
  scope: 'project' as ApiFunctionScope,
})
const editing = ref(false)

function openEditDialog(): void {
  if (!customDetail.value) return
  editForm.id = customDetail.value.id
  editForm.name = customDetail.value.name
  editForm.description = customDetail.value.description ?? ''
  editForm.paramsDesc = customDetail.value.paramsDesc ?? ''
  editForm.script = customDetail.value.script
  editForm.scope = customDetail.value.scope
  editDialogVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写函数名称')
    return
  }
  if (!editForm.script.trim()) {
    ElMessage.warning('请填写 Groovy 脚本')
    return
  }
  editing.value = true
  try {
    await updateCustomFunction(editForm.id, {
      name: editForm.name.trim(),
      description: editForm.description.trim() || undefined,
      paramsDesc: editForm.paramsDesc.trim() || undefined,
      script: editForm.script.trim(),
      scope: editForm.scope,
    })
    editDialogVisible.value = false
    ElMessage.success('已保存')
    await loadCustomList()
    if (selectedCustomId.value === editForm.id) {
      customDetail.value = await fetchCustomFunctionDetail(editForm.id)
    }
  } catch (err) {
    ElMessage.error(resolveFunctionError(err))
  } finally {
    editing.value = false
  }
}

// ==================== 启停 ====================

function handleToggleItem(item: DisplayListItem): void {
  void handleToggle(item)
}

async function handleToggle(item: DisplayListItem): Promise<void> {
  if (!item.id) return
  try {
    await toggleCustomFunction(item.id, !item.enabled)
    ElMessage.success(item.enabled ? '已禁用' : '已启用')
    await loadCustomList()
    if (selectedCustomId.value === item.id && customDetail.value) {
      customDetail.value.enabled = !item.enabled
    }
  } catch (err) {
    ElMessage.error(resolveFunctionError(err))
  }
}

// ==================== 删除 ====================

function handleDeleteItem(item: DisplayListItem): void {
  void handleDelete(item as ApiCustomFunctionListItem)
}

async function handleDelete(item: ApiCustomFunctionListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除后函数不可恢复，确认删除「${item.name}」？`, '删除函数', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteCustomFunction(item.id)
    ElMessage.success('已删除')
    if (selectedCustomId.value === item.id) {
      selectedType.value = null
      selectedName.value = ''
      selectedCustomId.value = ''
      customDetail.value = null
    }
    await loadCustomList()
  } catch (err) {
    ElMessage.error(resolveFunctionError(err))
  }
}

// ==================== 函数助手 ====================

const helperVisible = ref(false)

function openHelper(): void {
  helperVisible.value = true
}

/** 函数助手「插入表达式」回调：函数管理页无目标输入框，复制到剪贴板 */
function handleInsertExpression(expression: string): void {
  void navigator.clipboard.writeText(expression).then(
    () => ElMessage.success(`表达式已复制：${expression}`),
    () => ElMessage.error('复制失败，请手动复制'),
  )
}

onMounted(() => void loadAll())
</script>

<template>
  <div class="fn-page">
    <header class="fn-page__header">
      <div>
        <h3 class="fn-page__title">函数管理</h3>
        <p class="fn-page__subtitle">管理内置函数与自定义 Groovy 函数；函数助手可快速生成调用表达式</p>
      </div>
      <div class="fn-page__actions">
        <el-button @click="openHelper">函数助手</el-button>
        <el-button type="primary" :disabled="!canEdit" @click="openCreateDialog">新建函数</el-button>
      </div>
    </header>

    <div class="fn-page__body">
      <aside class="fn-page__list">
        <!-- 搜索 -->
        <el-input
          v-model="keyword"
          placeholder="搜索函数名称..."
          clearable
          class="fn-page__search"
          @input="handleSearchInput"
        />

        <!-- 标签页筛选 -->
        <el-radio-group v-model="activeTab" class="fn-page__tabs">
          <el-radio-button v-for="tab in FUNCTION_TAB_OPTIONS" :key="tab.value" :value="tab.value">
            {{ tab.label }}
          </el-radio-button>
        </el-radio-group>

        <!-- 加载/错误/空态 -->
        <div v-if="loadError" class="fn-page__empty">
          <p>函数列表加载失败</p>
          <el-button size="small" @click="loadAll()">重试</el-button>
        </div>

        <el-skeleton v-else-if="listLoading" :rows="8" animated class="fn-page__skeleton" />

        <div v-else-if="displayItems.length === 0" class="fn-page__empty">
          <p>{{ keyword ? '无匹配函数' : '暂无自定义函数，点击新建' }}</p>
          <el-button v-if="keyword" size="small" @click="keyword = ''; loadCustomList()">清除搜索</el-button>
        </div>

        <!-- 函数列表 -->
        <ul v-else class="fn-page__items">
          <li
            v-for="item in displayItems"
            :key="`${item.type}-${item.name}`"
            class="fn-page__item"
            :class="{
              'is-active': item.type === selectedType && item.name === selectedName,
              'is-disabled': item.type === 'custom' && item.enabled === false,
            }"
            @click="selectItem(item.type, item.name, item.id)"
          >
            <div class="fn-page__item-main">
              <span class="fn-page__item-name">{{ item.name }}</span>
              <el-tag v-if="item.type === 'builtin'" size="small" effect="plain">内置</el-tag>
              <el-tag v-else size="small" type="success" effect="light">自定义</el-tag>
              <el-tag v-if="item.scope" size="small" effect="plain">{{ formatScopeLabel(item.scope) }}</el-tag>
            </div>
            <div class="fn-page__item-meta">{{ item.description }}</div>
            <div v-if="item.type === 'custom' && canEdit" class="fn-page__item-actions" @click.stop>
              <el-button link size="small" @click="handleToggleItem(item)">
                {{ item.enabled ? '禁用' : '启用' }}
              </el-button>
              <el-button link size="small" type="danger" @click="handleDeleteItem(item)">删除</el-button>
            </div>
          </li>
        </ul>
      </aside>

      <!-- 右侧详情面板 -->
      <section class="fn-page__detail">
        <!-- 内置函数详情 -->
        <div v-if="selectedType === 'builtin' && selectedBuiltinFn" class="fn-detail">
          <div class="fn-detail__header">
            <h4 class="fn-detail__name">{{ selectedBuiltinFn.name }}</h4>
            <el-tag effect="plain">内置</el-tag>
          </div>
          <div class="fn-detail__section">
            <label class="fn-detail__label">签名</label>
            <code class="fn-detail__code">{{ selectedBuiltinFn.signature }}</code>
          </div>
          <div class="fn-detail__section">
            <label class="fn-detail__label">描述</label>
            <p class="fn-detail__text">{{ selectedBuiltinFn.description }}</p>
          </div>
          <div v-if="selectedBuiltinFn.params.length > 0" class="fn-detail__section">
            <label class="fn-detail__label">参数</label>
            <div class="fn-detail__params">
              <div v-for="p in selectedBuiltinFn.params" :key="p.name" class="fn-detail__param">
                <code class="fn-detail__code">{{ p.name }}</code>
                <el-tag v-if="p.required" size="small" type="warning" effect="light">必填</el-tag>
                <span class="fn-detail__param-desc">{{ p.description }}</span>
              </div>
            </div>
          </div>
          <div class="fn-detail__section">
            <label class="fn-detail__label">示例</label>
            <code class="fn-detail__code">{{ selectedBuiltinFn.example }}</code>
          </div>
        </div>

        <!-- 自定义函数详情/编辑 -->
        <div v-else-if="selectedType === 'custom' && !detailLoading" class="fn-detail">
          <template v-if="customDetail">
            <div class="fn-detail__header">
              <h4 class="fn-detail__name">{{ customDetail.name }}</h4>
              <div class="fn-detail__header-actions">
                <el-tag :type="customDetail.enabled ? 'success' : 'info'" effect="light">
                  {{ customDetail.enabled ? '已启用' : '已禁用' }}
                </el-tag>
                <el-tag effect="plain">{{ formatScopeLabel(customDetail.scope) }}</el-tag>
                <el-button v-if="canEdit" size="small" @click="openEditDialog">编辑</el-button>
              </div>
            </div>
            <div v-if="customDetail.description" class="fn-detail__section">
              <label class="fn-detail__label">描述</label>
              <p class="fn-detail__text">{{ customDetail.description }}</p>
            </div>
            <div v-if="customDetail.paramsDesc" class="fn-detail__section">
              <label class="fn-detail__label">参数说明</label>
              <p class="fn-detail__text">{{ customDetail.paramsDesc }}</p>
            </div>
            <div class="fn-detail__section">
              <label class="fn-detail__label">Groovy 脚本</label>
              <pre class="fn-detail__script">{{ customDetail.script }}</pre>
            </div>
          </template>
          <div v-else class="fn-page__empty fn-page__empty--wide">
            <p>加载中...</p>
          </div>
        </div>

        <!-- 空态 -->
        <div v-else class="fn-page__empty fn-page__empty--wide">
          <p>{{ listLoading ? '加载中...' : '选择函数查看详情' }}</p>
        </div>
      </section>
    </div>

    <footer class="fn-page__footer">
      函数助手：可在任意需要输入表达式的位置使用，快速生成 ${} 调用语法
    </footer>

    <!-- 新建函数弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建自定义函数" width="600px">
      <el-form label-width="90px">
        <el-form-item label="函数名称" required>
          <el-input v-model="createForm.name" maxlength="100" placeholder="如：myFunc" />
        </el-form-item>
        <el-form-item label="作用域">
          <el-select v-model="createForm.scope">
            <el-option v-for="opt in SCOPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="参数说明">
          <el-input v-model="createForm.paramsDesc" placeholder="如：id:用户ID, name:名称" maxlength="500" />
        </el-form-item>
        <el-form-item label="Groovy 脚本" required>
          <el-input
            v-model="createForm.script"
            type="textarea"
            :rows="6"
            maxlength="20000"
            placeholder="// args 数组承接调用参数，返回值即求值结果&#10;return args[0]"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑函数弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑自定义函数" width="600px">
      <el-form label-width="90px">
        <el-form-item label="函数名称" required>
          <el-input v-model="editForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="作用域">
          <el-select v-model="editForm.scope">
            <el-option v-for="opt in SCOPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="参数说明">
          <el-input v-model="editForm.paramsDesc" maxlength="500" />
        </el-form-item>
        <el-form-item label="Groovy 脚本" required>
          <el-input v-model="editForm.script" type="textarea" :rows="6" maxlength="20000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 函数助手弹窗 -->
    <FunctionHelperDialog v-model="helperVisible" @insert="handleInsertExpression" />
  </div>
</template>

<style scoped lang="scss">
.fn-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--space-md);
}

.fn-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fn-page__title {
  margin: 0;
  font-size: var(--font-size-lg);
}

.fn-page__subtitle {
  margin: 4px 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.fn-page__body {
  flex: 1;
  display: flex;
  gap: var(--space-lg);
  min-height: 0;
}

.fn-page__list {
  width: 340px;
  flex-shrink: 0;
  overflow-y: auto;
  background: var(--color-neutral-0, #fff);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.fn-page__search {
  flex-shrink: 0;
}

.fn-page__tabs {
  flex-shrink: 0;
}

.fn-page__skeleton {
  padding: var(--space-sm);
}

.fn-page__items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fn-page__item {
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
  cursor: pointer;
  transition: all var(--transition-fast);

  &:hover {
    background: var(--color-neutral-50);
  }

  &.is-active {
    border-color: var(--color-primary-200, #bfdbfe);
    background: rgba(59, 130, 246, 0.06);
  }

  &.is-disabled {
    opacity: 0.55;
  }
}

.fn-page__item-main {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.fn-page__item-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  font-family: monospace;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fn-page__item-meta {
  margin-top: 2px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fn-page__item-actions {
  margin-top: 4px;

  .el-button + .el-button {
    margin-left: 8px;
  }
}

.fn-page__detail {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

.fn-page__empty {
  text-align: center;
  padding: var(--space-xl) 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);

  p {
    margin-bottom: var(--space-sm);
  }

  &--wide {
    padding-top: var(--space-xxl, 64px);
  }
}

.fn-page__footer {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

// ========== 详情面板 ==========

.fn-detail {
  background: var(--color-neutral-0, #fff);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
}

.fn-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}

.fn-detail__header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.fn-detail__name {
  margin: 0;
  font-size: var(--font-size-md);
  font-family: monospace;
}

.fn-detail__section {
  & + & {
    margin-top: var(--space-md);
    padding-top: var(--space-md);
    border-top: 1px solid var(--color-neutral-100, #f3f4f6);
  }
}

.fn-detail__label {
  display: block;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-400);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.fn-detail__text {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
}

.fn-detail__code {
  display: inline-block;
  background: var(--color-neutral-50, #f9fafb);
  border: 1px solid var(--color-neutral-100, #f3f4f6);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  font-family: monospace;
  font-size: 13px;
  color: var(--color-primary-600, #2563eb);
}

.fn-detail__params {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fn-detail__param {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.fn-detail__param-desc {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.fn-detail__script {
  background: var(--color-neutral-50, #f9fafb);
  border: 1px solid var(--color-neutral-100, #f3f4f6);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  font-family: monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
