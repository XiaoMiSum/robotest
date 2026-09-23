<script setup lang="ts">
import { useEnvironmentPage } from '@/composables/project/api-testing/environment/useEnvironmentPage'
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
</script>

<template>
  <div class="env-page">
    <div class="env-page__body">
      <aside class="env-page__list">
        <div class="env-page__search-row">
          <el-input
            v-model="keyword"
            placeholder="搜索环境名称..."
            clearable
            class="env-page__search"
            @input="handleSearchInput"
          />
          <el-dropdown type="primary" split-button trigger="click" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>新增
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :disabled="!canEdit" @click="openImportDialog">导入环境</el-dropdown-item>
                <el-dropdown-item :disabled="!selectedId" @click="handleExport">导出当前环境</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

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
              <el-button
                v-else-if="canEdit && !item.isDefault"
                link
                size="small"
                class="env-page__item-set-default"
                @click.stop="handleSetDefault(item)"
              >
                设为默认
              </el-button>
              <el-tag v-if="item.scope === 'project'" size="small" effect="plain">项目</el-tag>
            </div>
            <div class="env-page__item-meta">
              {{ item.httpConfigCount }} HTTP · {{ item.variableCount }} 变量 · {{ item.dataSourceCount }} 数据源 ·
              {{ item.processorCount }} 处理器
            </div>
            <div v-if="canEdit" class="env-page__item-actions">
              <el-button link size="small" :disabled="!canMove(item, -1)" @click.stop="handleMoveItem(item, -1)">
                上移
              </el-button>
              <el-button link size="small" :disabled="!canMove(item, 1)" @click.stop="handleMoveItem(item, 1)">
                下移
              </el-button>
              <el-button link size="small" @click.stop="openEditDialog(item)">编辑</el-button>
              <el-button link size="small" @click.stop="openCopyDialog(item)">复制</el-button>
              <el-button link size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
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
          @changed="loadList()"
        />
        <div v-else class="env-page__empty env-page__empty--wide">
          <p>{{ listLoading ? '加载中...' : '暂无环境，点击「新建环境」创建第一个环境' }}</p>
        </div>
      </section>
    </div>

    <footer class="env-page__footer">默认环境说明：场景执行未指定环境时使用默认环境</footer>

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

    <el-dialog v-model="editDialogVisible" title="编辑环境" width="440px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" maxlength="100" placeholder="如：测试环境" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="editForm.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="copyDialogVisible" title="复制环境" width="440px">
      <el-form label-width="90px">
        <el-form-item label="副本名称" required>
          <el-input v-model="copyForm.name" maxlength="100" />
        </el-form-item>
      </el-form>
      <p class="env-page__dialog-tip">复制内容含 HTTP 配置、变量（含取值）与处理器；数据源不复制，需重新填写</p>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCopy">确定</el-button>
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
  gap: var(--space-md);
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
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.env-page__search-row {
  display: flex;
  gap: var(--space-sm);

  .env-page__search {
    flex: 1;
  }
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
    border-color: var(--color-primary-200);
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

.env-page__item-set-default {
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.env-page__item:hover .env-page__item-set-default,
.env-page__item:focus-within .env-page__item-set-default {
  opacity: 1;
}

.env-page__item-meta {
  margin-top: 2px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-page__item-actions {
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

.env-page__item:hover .env-page__item-actions,
.env-page__item:focus-within .env-page__item-actions {
  opacity: 1;
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

.env-page__footer {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}
</style>
