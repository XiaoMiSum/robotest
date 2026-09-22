<script setup lang="ts">
import { useComponentPage } from '@/composables/project/api-testing/component/useComponentPage'
import type { ApiComponentListItem } from '@/types'
import ProcessorForm from '@/components/api-testing/ProcessorForm.vue'
import ValidatorForm from '@/components/api-testing/ValidatorForm.vue'
import ExtractorForm from '@/components/api-testing/ExtractorForm.vue'
import ExtractorAssetPicker from '@/components/api-testing/ExtractorAssetPicker.vue'

const {
  canEdit,
  listLoading,
  loadError,
  list,
  total,
  keyword,
  keywordDraft,
  filterType,
  filterScope,
  filterEnabled,
  pageNo,
  pageSize,
  hasSelection,
  drawerVisible,
  editingId,
  saving,
  form,
  basicConfigEnabled,
  extractorPickerVisible,
  extractorPickerLoading,
  extractorPickerItems,
  extractorPickerKeyword,
  httpRefOptions,
  dsRefOptions,
  loadList,
  handlePageChange,
  handleSizeChange,
  handleSearch,
  handleReset,
  handleSelectionChange,
  handleToggle,
  handleBatchToggle,
  handleBatchDelete,
  handleDelete,
  handleCopy,
  openExtractorPicker,
  handleExtractorPicked,
  loadExtractorAssets,
  openCreateDrawer,
  openEditDrawer,
  handleSave,
  COMPONENT_TYPE_OPTIONS,
  COMPONENT_SCOPE_OPTIONS,
  SCOPE_TAG_TYPE,
  componentTypeLabel,
  componentScopeLabel,
} = useComponentPage()
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
        <el-button type="primary" :disabled="!canEdit" @click="openCreateDrawer">
          <el-icon><Plus /></el-icon>新建组件
        </el-button>
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
        <div class="component-page__form-grid">
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
          <el-form-item label="启用">
            <el-switch v-model="basicConfigEnabled" />
          </el-form-item>
        </div>

        <!-- 按类型渲染配置表单 -->
        <el-divider v-if="form.type === 'preprocessor' || form.type === 'postprocessor'" content-position="left">处理器配置</el-divider>
        <ProcessorForm
          v-if="form.type === 'preprocessor' || form.type === 'postprocessor'"
          v-model="form.config"
          :http-options="httpRefOptions"
          :ds-options="dsRefOptions"
          @import-extractors="openExtractorPicker"
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

    <!-- 从公共组件引入提取器 -->
    <ExtractorAssetPicker
      v-model="extractorPickerVisible"
      :loading="extractorPickerLoading"
      :items="extractorPickerItems"
      :keyword="extractorPickerKeyword"
      @update:keyword="extractorPickerKeyword = $event"
      @search="loadExtractorAssets"
      @confirm="handleExtractorPicked"
    />
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

  .component-page__form-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    column-gap: var(--space-md);
  }
}
</style>
