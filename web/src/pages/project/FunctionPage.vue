<script setup lang="ts">
import { Codemirror } from 'vue-codemirror'
import { java } from '@codemirror/lang-java'
import { useFunctionalTesting } from '@/composables/project/functional-testing/useFunctionalTesting'
import { formatScopeLabel } from './functionModel'

const editorExtensions = [java()]

const {
  canEdit,
  listLoading,
  loadError,
  keyword,
  activeTab,
  handleSearchInput,
  displayItems,
  selectedType,
  selectedName,
  selectedBuiltinFn,
  detailLoading,
  customDetail,
  customParams,
  customSignature,
  panelMode,
  form,
  saving,
  loadAll,
  selectItem,
  startCreate,
  startEdit,
  cancelEdit,
  submitForm,
  handleToggleItem,
  handleDeleteItem,
  FUNCTION_TAB_OPTIONS,
  SCOPE_OPTIONS,
} = useFunctionalTesting()
</script>

<template>
  <div class="fn-page">
    <div class="fn-page__body">
      <aside class="fn-page__list">
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

        <el-radio-group v-model="activeTab" class="fn-page__tabs">
          <el-radio-button v-for="tab in FUNCTION_TAB_OPTIONS" :key="tab.value" :value="tab.value">
            {{ tab.label }}
          </el-radio-button>
        </el-radio-group>

        <div v-if="loadError" class="fn-page__empty">
          <p>函数列表加载失败</p>
          <el-button size="small" @click="loadAll()">重试</el-button>
        </div>

        <el-skeleton v-else-if="listLoading" :rows="8" animated class="fn-page__skeleton" />

        <div v-else-if="displayItems.length === 0" class="fn-page__empty">
          <p>{{ keyword ? '无匹配函数' : '暂无自定义函数，点击新增' }}</p>
          <el-button v-if="keyword" size="small" @click="keyword = ''; loadAll()">清除搜索</el-button>
        </div>

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

      <section class="fn-page__detail">
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

:deep(.v-codemirror .cm-editor) {
  width: 100%;
}

:deep(.v-codemirror .cm-scroller) {
  width: 100%;
  overflow-x: auto;
}
</style>
