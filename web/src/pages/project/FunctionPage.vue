<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Codemirror } from 'vue-codemirror'
import { java } from '@codemirror/lang-java'
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

/** 列表项统一类型 */
interface DisplayListItem {
  type: 'builtin' | 'custom'
  name: string
  description: string
  scope?: ApiFunctionScope
  id?: string
  enabled?: boolean
}

// Groovy 与 Java 语法同构，复用 Java 语言包提供高亮
const editorExtensions = [java()]

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
  panelMode.value = 'view'
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

/** 自定义函数参数：从 paramsDesc（`id:用户ID, name:名称`）解析为与内置函数参数一致的结构 */
const customParams = computed(() => {
  const desc = customDetail.value?.paramsDesc
  if (!desc) return []
  return desc.split(',').map((p) => {
    const seg = p.trim()
    return {
      name: seg.split(':')[0]?.trim() ?? '',
      required: true,
      description: seg,
    }
  })
})

/** 自定义函数签名与示例：按实际参数名拼接（无参数时去掉占位参数） */
const customSignature = computed(() => {
  const name = customDetail.value?.name ?? ''
  const args = customParams.value.map((p) => p.name).join(', ')
  return `\${${name}${args ? `(${args})` : '()'}}`
})

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

// ==================== 自定义函数详情面板 ====================

type CustomPanelMode = 'view' | 'create' | 'edit'

const panelMode = ref<CustomPanelMode>('view')
const form = reactive({
  id: '',
  name: '',
  description: '',
  paramsDesc: '',
  script: '',
  scope: 'project' as ApiFunctionScope,
})
const saving = ref(false)

function resetForm(): void {
  form.id = ''
  form.name = ''
  form.description = ''
  form.paramsDesc = ''
  form.script = ''
  form.scope = 'project'
}

function startCreate(): void {
  selectedType.value = 'custom'
  selectedName.value = ''
  selectedCustomId.value = ''
  customDetail.value = null
  resetForm()
  panelMode.value = 'create'
}

function startEdit(): void {
  if (!customDetail.value) return
  form.id = customDetail.value.id
  form.name = customDetail.value.name
  form.description = customDetail.value.description ?? ''
  form.paramsDesc = customDetail.value.paramsDesc ?? ''
  form.script = customDetail.value.script
  form.scope = customDetail.value.scope
  panelMode.value = 'edit'
}

function cancelEdit(): void {
  panelMode.value = 'view'
  // 取消新增时若未选中任何函数则清空选择回到空态
  if (selectedCustomId.value === '') {
    selectedType.value = null
    selectedName.value = ''
  }
}

function validateForm(): boolean {
  if (!form.name.trim()) {
    ElMessage.warning('请填写函数名称')
    return false
  }
  if (!form.script.trim()) {
    ElMessage.warning('请填写 Groovy 脚本')
    return false
  }
  return true
}

async function submitForm(): Promise<void> {
  if (!validateForm()) return
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      paramsDesc: form.paramsDesc.trim() || undefined,
      script: form.script.trim(),
      scope: form.scope,
    }
    if (panelMode.value === 'create') {
      const resp = await createCustomFunction(payload)
      ElMessage.success('函数已创建')
      await loadCustomList()
      panelMode.value = 'view'
      selectItem('custom', form.name.trim(), resp.id)
    } else {
      await updateCustomFunction(form.id, payload)
      ElMessage.success('已保存')
      await loadCustomList()
      panelMode.value = 'view'
      if (selectedCustomId.value === form.id) {
        customDetail.value = await fetchCustomFunctionDetail(form.id)
      }
    }
  } catch (err) {
    ElMessage.error(resolveFunctionError(err))
  } finally {
    saving.value = false
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

onMounted(() => void loadAll())
</script>

<template>
  <div class="fn-page">
    <div class="fn-page__body">
      <aside class="fn-page__list">
        <!-- 搜索 + 新增 -->
        <div class="fn-page__search-row">
          <el-input
            v-model="keyword"
            placeholder="搜索函数名称..."
            clearable
            class="fn-page__search"
            @input="handleSearchInput"
          />
          <el-button
            type="primary"
            :disabled="!canEdit"
            class="fn-page__add-btn"
            @click="startCreate"
          >
            <el-icon><Plus /></el-icon>新增
          </el-button>
        </div>

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
          <p>{{ keyword ? '无匹配函数' : '暂无自定义函数，点击新增' }}</p>
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

        <!-- 自定义函数详情/编辑/新建 -->
        <div v-else-if="selectedType === 'custom' && panelMode !== 'view' && !detailLoading" class="fn-detail">
          <div class="fn-detail__header">
            <h4 class="fn-detail__name">{{ panelMode === 'create' ? '新建自定义函数' : `编辑：${customDetail?.name ?? ''}` }}</h4>
            <div class="fn-detail__header-actions">
              <el-tag effect="plain">自定义</el-tag>
            </div>
          </div>
          <el-form label-width="90px">
            <el-form-item label="函数名称" required>
              <el-input v-model="form.name" maxlength="100" placeholder="如：myFunc" />
            </el-form-item>
            <el-form-item label="作用域">
              <el-select v-model="form.scope">
                <el-option v-for="opt in SCOPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
            <el-form-item label="参数说明">
              <el-input v-model="form.paramsDesc" placeholder="如：id:用户ID, name:名称" maxlength="500" />
            </el-form-item>
            <el-form-item label="Groovy 脚本" required>
              <Codemirror
                v-model="form.script"
                :extensions="editorExtensions"
                placeholder="// args 数组承接调用参数，返回值即求值结果&#10;return args[0]"
                :style="{ width: '100%', height: '260px', border: '1px solid var(--color-neutral-100)', borderRadius: 'var(--radius-md)' }"
              />
            </el-form-item>
          </el-form>
          <div class="fn-detail__footer-actions">
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
          </div>
        </div>

        <!-- 自定义函数详情（查看） -->
        <div v-else-if="selectedType === 'custom' && !detailLoading" class="fn-detail">
          <template v-if="customDetail">
            <div class="fn-detail__header">
              <h4 class="fn-detail__name">{{ customDetail.name }}</h4>
              <div class="fn-detail__header-actions">
                <el-tag :type="customDetail.enabled ? 'success' : 'info'" effect="light">
                  {{ customDetail.enabled ? '已启用' : '已禁用' }}
                </el-tag>
                <el-tag effect="plain">{{ formatScopeLabel(customDetail.scope) }}</el-tag>
                <el-button v-if="canEdit" size="small" @click="startEdit">编辑</el-button>
              </div>
            </div>
            <div class="fn-detail__section">
              <label class="fn-detail__label">签名</label>
              <code class="fn-detail__code">{{ customSignature }}</code>
            </div>
            <div class="fn-detail__section">
              <label class="fn-detail__label">描述</label>
              <p class="fn-detail__text">{{ customDetail.description || '—' }}</p>
            </div>
            <div class="fn-detail__section">
              <label class="fn-detail__label">参数</label>
              <div v-if="customParams.length > 0" class="fn-detail__params">
                <div v-for="p in customParams" :key="p.name" class="fn-detail__param">
                  <code class="fn-detail__code">{{ p.name }}</code>
                  <el-tag v-if="p.required" size="small" type="warning" effect="light">必填</el-tag>
                  <span class="fn-detail__param-desc">{{ p.description }}</span>
                </div>
              </div>
              <p v-else class="fn-detail__text">—</p>
            </div>
            <div class="fn-detail__section">
              <label class="fn-detail__label">示例</label>
              <code class="fn-detail__code">{{ customSignature }}</code>
            </div>
            <div class="fn-detail__section">
              <label class="fn-detail__label">Groovy 脚本</label>
              <Codemirror
                :model-value="customDetail.script"
                :extensions="editorExtensions"
                :disabled="true"
                :style="{ width: '100%', height: 'auto', border: '1px solid var(--color-neutral-100)', borderRadius: 'var(--radius-md)' }"
              />
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
  </div>
</template>

<style scoped lang="scss">
.fn-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--space-md);
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

.fn-page__search-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.fn-page__search {
  flex: 1;
}

.fn-page__add-btn {
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

.fn-detail__footer-actions {
  margin-top: var(--space-lg);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-100, #f3f4f6);
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
}

// CodeMirror 容器 display: contents，需强制编辑器与滚动区充满可用宽度，
// 避免随最长行内容宽度自适应撑开（与其他输入框保持一致）
:deep(.v-codemirror .cm-editor) {
  width: 100%;
}

:deep(.v-codemirror .cm-scroller) {
  width: 100%;
  overflow-x: auto;
}
</style>
