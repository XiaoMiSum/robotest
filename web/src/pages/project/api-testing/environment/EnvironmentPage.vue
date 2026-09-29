<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useEnvironmentPage } from '@/composables/project/api-testing/environment/useEnvironmentPage'
import type { ApiEnvironmentListItem } from '@/types'
import EnvironmentDetailPanel from './EnvironmentDetailPanel.vue'

const {
  canEdit,
  listLoading,
  loadError,
  selectedId,
  keyword,
  sortedList,
  loadList,
  selectEnvironment,
  canMove,
  handleMoveItem,
  handleExport,
  handleSearchInput,
  createDialogVisible,
  createForm,
  creating,
  openCreateDialog,
  submitCreate,
  copyDialogVisible,
  copyForm,
  openCopyDialog,
  submitCopy,
  editDialogVisible,
  editForm,
  editing,
  openEditDialog,
  submitEdit,
  importDialogVisible,
  importFileList,
  importOverwrite,
  importing,
  openImportDialog,
  handleImportFileChange,
  handleImportFileRemove,
  submitImport,
  handleDelete,
  handleSetDefault,
} = useEnvironmentPage()

// 新建/编辑/复制共用一个弹窗：composable 仍按模式各自持有表单与提交逻辑，这里只聚合表现层
type EnvDialogMode = 'create' | 'edit' | 'copy'

const dialogMode = ref<EnvDialogMode>('create')
const nameError = ref('')
const copySubmitting = ref(false)

const dialogVisible = computed({
  get: () => createDialogVisible.value || editDialogVisible.value || copyDialogVisible.value,
  set: (visible: boolean) => {
    if (visible) return
    createDialogVisible.value = false
    editDialogVisible.value = false
    copyDialogVisible.value = false
    nameError.value = ''
  },
})

const isCopyMode = computed(() => dialogMode.value === 'copy')
const dialogTitle = computed(
  () => ({ create: '新建环境', edit: '编辑环境', copy: '复制环境' })[dialogMode.value],
)
const dialogOkText = computed(() => ({ create: '创建', edit: '确定', copy: '复制' })[dialogMode.value])

const dialogName = computed({
  get: () =>
    dialogMode.value === 'create' ? createForm.name : dialogMode.value === 'edit' ? editForm.name : copyForm.name,
  set: (value: string) => {
    if (dialogMode.value === 'create') createForm.name = value
    else if (dialogMode.value === 'edit') editForm.name = value
    else copyForm.name = value
  },
})

const dialogDescription = computed({
  get: () => (dialogMode.value === 'edit' ? editForm.description : createForm.description),
  set: (value: string) => {
    if (dialogMode.value === 'edit') editForm.description = value
    else createForm.description = value
  },
})

const dialogIsDefault = computed({
  get: () => (dialogMode.value === 'edit' ? editForm.isDefault : createForm.isDefault),
  set: (value: boolean) => {
    if (dialogMode.value === 'edit') editForm.isDefault = value
    else createForm.isDefault = value
  },
})

const dialogSubmitting = computed(() => {
  if (dialogMode.value === 'create') return creating.value
  if (dialogMode.value === 'edit') return editing.value
  return copySubmitting.value
})

function openDialog(mode: EnvDialogMode, item?: ApiEnvironmentListItem) {
  dialogMode.value = mode
  nameError.value = ''
  if (mode === 'create') {
    openCreateDialog()
    return
  }
  if (!item) return
  if (mode === 'edit') openEditDialog(item)
  else openCopyDialog(item)
}

function handleDialogNameInput() {
  nameError.value = ''
}

async function submitDialog() {
  if (!dialogName.value.trim()) {
    nameError.value = '请填写环境名称'
    ElMessage.warning('请填写环境名称')
    return
  }
  nameError.value = ''
  if (dialogMode.value === 'create') {
    await submitCreate()
    return
  }
  if (dialogMode.value === 'edit') {
    await submitEdit()
    return
  }
  copySubmitting.value = true
  try {
    await submitCopy()
  } finally {
    copySubmitting.value = false
  }
}
</script>

<template>
  <div class="env-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">环境管理</h1>
        <p class="page-head__desc">
          场景与接口执行的环境配置，含 HTTP 配置、变量、数据源与处理器；场景执行未指定环境时，自动使用列表中标记「默认」的环境
        </p>
      </div>
    </header>

    <div class="env-body">
      <!-- 左：环境列表（搜索与 [新建▾] 位于列表首行，其下为唯一滚动区） -->
      <aside class="env-list">
        <div class="env-list__head">
          <el-input
            v-model="keyword"
            placeholder="搜索环境名称..."
            clearable
            class="env-list__search"
            @input="handleSearchInput"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-dropdown type="primary" split-button trigger="click" class="env-list__create" @click="openDialog('create')">
            <el-icon><Plus /></el-icon>新建
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :disabled="!canEdit" @click="openImportDialog">导入环境</el-dropdown-item>
                <el-dropdown-item :disabled="!selectedId" @click="handleExport">导出当前环境</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <div class="env-list__body">
          <div v-if="loadError" class="env-empty">
            <p>环境列表加载失败</p>
            <el-button size="small" @click="loadList()">重试</el-button>
          </div>

          <el-skeleton v-else-if="listLoading" :rows="5" animated />

          <div v-else-if="sortedList.length === 0" class="env-empty">
            <p>{{ keyword ? '无匹配环境' : '暂无环境，点击新建' }}</p>
            <el-button v-if="keyword" size="small" @click="keyword = ''; loadList()">清除搜索</el-button>
          </div>

          <ul v-else class="env-items">
            <li
              v-for="item in sortedList"
              :key="item.id"
              class="env-item"
              :class="{ 'is-active': item.id === selectedId }"
              @click="selectEnvironment(item.id)"
            >
              <div class="env-item__main">
                <span class="env-item__name">{{ item.name }}</span>
                <el-tag v-if="item.isDefault" size="small" type="warning" effect="light">默认</el-tag>
                <el-button
                  v-else-if="canEdit"
                  link
                  size="small"
                  class="env-item__set-default"
                  @click.stop="handleSetDefault(item)"
                >
                  设为默认
                </el-button>
              </div>
              <div class="env-item__meta">
                {{ item.httpConfigCount }} HTTP · {{ item.variableCount }} 变量 · {{ item.dataSourceCount }} 数据源 ·
                {{ item.processorCount }} 处理器
              </div>
              <div v-if="canEdit" class="env-item__acts">
                <el-button link size="small" :disabled="!canMove(item, -1)" @click.stop="handleMoveItem(item, -1)">
                  上移
                </el-button>
                <el-button link size="small" :disabled="!canMove(item, 1)" @click.stop="handleMoveItem(item, 1)">
                  下移
                </el-button>
                <el-button link size="small" @click.stop="openDialog('edit', item)">编辑</el-button>
                <el-button link size="small" @click.stop="openDialog('copy', item)">复制</el-button>
                <el-button link size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
              </div>
            </li>
          </ul>
        </div>
      </aside>

      <!-- 右：环境详情 -->
      <section class="env-page__detail">
        <EnvironmentDetailPanel
          v-if="selectedId"
          :key="selectedId"
          :environment-id="selectedId"
          :can-edit="canEdit"
          @changed="loadList()"
        />
        <div v-else class="env-empty env-empty--wide">
          <p>{{ listLoading ? '加载中...' : '暂无环境，点击「新建」创建第一个环境' }}</p>
        </div>
      </section>
    </div>

    <!-- 新建 / 编辑 / 复制环境（单弹窗按模式切换标题、字段与主按钮） -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="440px">
      <el-form label-width="90px">
        <el-form-item :label="isCopyMode ? '副本名称' : '名称'" :error="nameError" required>
          <el-input
            v-model="dialogName"
            maxlength="100"
            :placeholder="isCopyMode ? undefined : '如：测试环境'"
            @input="handleDialogNameInput"
          />
        </el-form-item>
        <template v-if="!isCopyMode">
          <el-form-item label="描述">
            <el-input
              v-model="dialogDescription"
              type="textarea"
              :rows="2"
              maxlength="500"
              placeholder="可选，环境描述"
            />
          </el-form-item>
          <el-form-item label="设为默认">
            <el-switch v-model="dialogIsDefault" />
          </el-form-item>
        </template>
        <p v-else class="env-page__dialog-tip">
          复制内容含 HTTP 配置、变量（含取值）与处理器；数据源不复制，需重新填写
        </p>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogSubmitting" @click="submitDialog">{{ dialogOkText }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" title="导入环境" width="480px">
      <el-upload
        v-model:file-list="importFileList"
        drag
        :auto-upload="false"
        :show-file-list="true"
        :limit="1"
        accept=".json,application/json"
        :on-change="handleImportFileChange"
        :on-remove="handleImportFileRemove"
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
}

.page-head {
  display: flex;
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
  letter-spacing: -0.01em;
}

.page-head__desc {
  margin: 4px 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.env-body {
  flex: 1;
  display: flex;
  gap: var(--space-lg);
  min-height: 0;
}

/* 左：环境列表（首行搜索 + [新建▾]，其下为唯一滚动区） */
.env-list {
  width: 300px;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.env-list__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;

  .env-list__search {
    flex: 1;
    min-width: 0;
  }

  .env-list__create {
    flex-shrink: 0;
  }
}

.env-list__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.env-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.env-item {
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
  cursor: pointer;
  transition: all var(--transition-fast);

  &:hover {
    background: var(--color-neutral-50);
  }

  &.is-active {
    border-color: var(--color-primary-200);
    background: rgba(59, 130, 246, 0.06);
  }
}

.env-item__main {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.env-item__name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.env-item__set-default {
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.env-item:hover .env-item__set-default,
.env-item:focus-within .env-item__set-default {
  opacity: 1;
}

.env-item__meta {
  margin-top: 2px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-item__acts {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 2px 6px;
  opacity: 0;
  transition: opacity var(--transition-fast);

  .el-button + .el-button {
    margin-left: 0;
  }
}

.env-item:hover .env-item__acts,
.env-item:focus-within .env-item__acts {
  opacity: 1;
}

/* 右：详情（滚动卡片由详情面板自身承载） */
.env-page__detail {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.env-empty {
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

.env-page__dialog-tip {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-page__import-overwrite {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
}
</style>
