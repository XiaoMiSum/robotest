<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiEnvironmentListItem, ApiImportResult } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { WORKSPACE_ROLE } from '@/services/admin'
import {
  copyEnvironment,
  createEnvironment,
  deleteEnvironment,
  fetchEnvironments,
  importEnvironment,
  sortEnvironment,
} from '@/services/apiEnvironment'
import { formatImportResult, resolveEnvironmentError, sortEnvironments } from './environmentsModel'
import EnvironmentDetailPanel from './EnvironmentDetailPanel.vue'

const authStore = useAuthStore()
// 空间管理员≙项目维护者（详细设计 4.3），前端仅作交互提示，后端仍兜底校验
const canEdit = computed(() => authStore.activeWorkspace?.workspaceRole === WORKSPACE_ROLE.ADMIN)

// ==================== 列表状态 ====================

const listLoading = ref(false)
const loadError = ref(false)
const environments = ref<ApiEnvironmentListItem[]>([])
const selectedId = ref('')
const keyword = ref('')

let searchTimer: ReturnType<typeof setTimeout> | undefined
// 交互设计 2.2：搜索防抖 300ms 走服务端过滤
function handleSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void loadList(), 300)
}
onBeforeUnmount(() => clearTimeout(searchTimer))

const sortedList = computed(() => sortEnvironments(environments.value))

async function loadList(keepSelection = true): Promise<void> {
  listLoading.value = true
  loadError.value = false
  try {
    environments.value = await fetchEnvironments(keyword.value.trim() || undefined)
    const stillExists = keepSelection && environments.value.some((item) => item.id === selectedId.value)
    if (!stillExists) selectedId.value = sortedList.value[0]?.id ?? ''
  } catch (err) {
    loadError.value = true
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    listLoading.value = false
  }
}

function selectEnvironment(id: string) {
  if (id !== selectedId.value) selectedId.value = id
}

const selectedIsFirst = computed(() => sortedList.value[0]?.id === selectedId.value)
const selectedIsLast = computed(() => {
  const list = sortedList.value
  return list.length > 0 && list[list.length - 1]?.id === selectedId.value
})

/** 相邻互换 sortOrder，两次 PATCH 后刷新保证列表与服务端一致 */
async function handleMove(direction: -1 | 1) {
  const ordered = sortedList.value
  const index = ordered.findIndex((item) => item.id === selectedId.value)
  const current = ordered[index]
  const neighbor = ordered[index + direction]
  if (!current || !neighbor) return
  try {
    await Promise.all([
      sortEnvironment(current.id, neighbor.sortOrder),
      sortEnvironment(neighbor.id, current.sortOrder),
    ])
    await loadList()
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}

// ==================== 新建 ====================

const createDialogVisible = ref(false)
const createForm = reactive({ name: '', description: '', isDefault: false })
const creating = ref(false)

function openCreateDialog() {
  createForm.name = ''
  createForm.description = ''
  createForm.isDefault = false
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写环境名称')
    return
  }
  creating.value = true
  try {
    // 不传 httpConfigs，后端自动生成默认 HTTP 配置（详细设计归一化规则）
    const created = await createEnvironment({
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
      isDefault: createForm.isDefault,
    })
    createDialogVisible.value = false
    ElMessage.success('环境已创建')
    await loadList()
    selectEnvironment(created.id)
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    creating.value = false
  }
}

// ==================== 复制 ====================

const copyDialogVisible = ref(false)
const copySourceName = ref('')
const copyForm = reactive({ name: '' })

function openCopyDialog(item: ApiEnvironmentListItem) {
  copySourceName.value = item.id
  copyForm.name = `${item.name}（副本）`
  copyDialogVisible.value = true
}

async function submitCopy() {
  if (!copyForm.name.trim()) return
  try {
    // 敏感值与数据源不复制（需求 3.7.1），副本需重新填写
    const copied = await copyEnvironment(copySourceName.value, copyForm.name.trim())
    copyDialogVisible.value = false
    ElMessage.success('复制成功')
    await loadList()
    selectEnvironment(copied.id)
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  }
}

// ==================== 导入 / 导出 / 删除 ====================

const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importOverwrite = ref(false)
const importing = ref(false)

function openImportDialog() {
  importFile.value = null
  importOverwrite.value = false
  importDialogVisible.value = true
}

function handleImportFileChange(uploadFile: unknown) {
  const raw = (uploadFile as { raw?: File }).raw
  if (raw) importFile.value = raw
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请选择环境 JSON 文件')
    return
  }
  importing.value = true
  try {
    const result: ApiImportResult = await importEnvironment(importFile.value, importOverwrite.value)
    importDialogVisible.value = false
    await ElMessageBox.alert(formatImportResult(result), '导入结果', { confirmButtonText: '知道了' })
    await loadList()
  } catch (err) {
    ElMessage.error(resolveEnvironmentError(err))
  } finally {
    importing.value = false
  }
}

async function handleDelete(item: ApiEnvironmentListItem) {
  try {
    await ElMessageBox.confirm(`删除后环境配置不可恢复，确认删除「${item.name}」？`, '删除环境', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteEnvironment(item.id)
    ElMessage.success('已删除')
    if (selectedId.value === item.id) selectedId.value = ''
    await loadList()
  } catch (err) {
    // 7402 场景引用 / 7404 定时任务绑定：文案提示先解除引用
    ElMessage.error(resolveEnvironmentError(err))
  }
}

onMounted(() => void loadList(false))
</script>

<template>
  <div class="env-page">
    <header class="env-page__header">
      <div>
        <h3 class="env-page__title">环境管理</h3>
        <p class="env-page__subtitle">默认环境在场景执行未指定环境时生效；配置项与 Ryze 对应一致</p>
      </div>
      <div class="env-page__actions">
        <el-button :disabled="!canEdit" @click="openImportDialog">导入环境</el-button>
        <el-button type="primary" :disabled="!canEdit" @click="openCreateDialog">新建环境</el-button>
      </div>
    </header>

    <div class="env-page__body">
      <aside class="env-page__list">
        <el-input
          v-model="keyword"
          placeholder="搜索环境名称..."
          clearable
          class="env-page__search"
          @input="handleSearchInput"
        />

        <div v-if="loadError" class="env-page__empty">
          <p>环境列表加载失败</p>
          <el-button size="small" @click="loadList()">重试</el-button>
        </div>

        <el-skeleton v-else-if="listLoading" :rows="5" animated class="env-page__skeleton" />

        <div v-else-if="sortedList.length === 0" class="env-page__empty">
          <p>{{ keyword ? '无匹配环境' : '暂无环境，点击新建' }}</p>
          <el-button v-if="keyword" size="small" @click="keyword = ''; loadList()">清除搜索</el-button>
        </div>

        <ul v-else class="env-page__items">
          <li
            v-for="item in sortedList"
            :key="item.id"
            class="env-page__item"
            :class="{ 'is-active': item.id === selectedId }"
            @click="selectEnvironment(item.id)"
          >
            <div class="env-page__item-main">
              <span class="env-page__item-name">{{ item.name }}</span>
              <el-tag v-if="item.isDefault" size="small" type="warning" effect="light">默认</el-tag>
              <el-tag v-if="item.scope === 'project'" size="small" effect="plain">项目</el-tag>
            </div>
            <div class="env-page__item-meta">
              {{ item.variableCount }} 变量 · {{ item.dataSourceCount }} 数据源 · {{ item.processorCount }} 处理器
            </div>
            <div v-if="canEdit" class="env-page__item-actions" @click.stop>
              <el-button link size="small" @click="openCopyDialog(item)">复制</el-button>
              <el-button link size="small" type="danger" @click="handleDelete(item)">删除</el-button>
            </div>
          </li>
        </ul>
      </aside>

      <section class="env-page__detail">
        <EnvironmentDetailPanel
          v-if="selectedId"
          :key="selectedId"
          :environment-id="selectedId"
          :can-edit="canEdit"
          :is-first="selectedIsFirst"
          :is-last="selectedIsLast"
          @changed="loadList()"
          @move="handleMove"
        />
        <div v-else class="env-page__empty env-page__empty--wide">
          <p>{{ listLoading ? '加载中...' : '暂无环境，点击「新建环境」创建第一个环境' }}</p>
        </div>
      </section>
    </div>

    <!-- 新建弹窗：名称、描述、是否设为默认（交互设计 2.2） -->
    <el-dialog v-model="createDialogVisible" title="新建环境" width="440px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" maxlength="100" placeholder="如：测试环境" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="createForm.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 复制弹窗：默认「原名称（副本）」（交互设计 2.5） -->
    <el-dialog v-model="copyDialogVisible" title="复制环境" width="440px">
      <el-form label-width="90px">
        <el-form-item label="副本名称" required>
          <el-input v-model="copyForm.name" maxlength="100" />
        </el-form-item>
      </el-form>
      <p class="env-page__dialog-tip">复制内容含 HTTP 配置与变量；敏感值与数据源不复制，需重新填写</p>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCopy">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗：JSON 文件 + 重名处理开关（交互设计 2.5） -->
    <el-dialog v-model="importDialogVisible" title="导入环境" width="480px">
      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="true"
        :limit="1"
        accept=".json,application/json"
        :on-change="handleImportFileChange"
      >
        <el-icon :size="32"><UploadFilled /></el-icon>
        <div>拖拽或点击选择环境 JSON 文件</div>
      </el-upload>
      <div class="env-page__import-overwrite">
        <el-switch v-model="importOverwrite" />
        <span>重名时覆盖（关闭则跳过不新增）</span>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.env-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--space-md);
}

.env-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.env-page__title {
  margin: 0;
  font-size: var(--font-size-lg);
}

.env-page__subtitle {
  margin: 4px 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-page__body {
  flex: 1;
  display: flex;
  gap: var(--space-lg);
  min-height: 0;
}

.env-page__list {
  width: 300px;
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

.env-page__skeleton {
  padding: var(--space-sm);
}

.env-page__items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.env-page__item {
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
}

.env-page__item-main {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.env-page__item-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.env-page__item-meta {
  margin-top: 2px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-page__item-actions {
  margin-top: 4px;

  .el-button + .el-button {
    margin-left: 8px;
  }
}

.env-page__detail {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

.env-page__empty {
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
</style>
