<script setup lang="ts">
import { Codemirror } from 'vue-codemirror'
import { java } from '@codemirror/lang-java'
import { useFunctionalTesting } from '@/composables/project/functional-testing/useFunctionalTesting'
import { formatScopeLabel } from '@/composables/project/api-testing/function/functionModel'

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
  formErrors,
  paramErrors,
  serializedParams,
  addParamRow,
  removeParamRow,
  moveParamRow,
  saving,
  loadAll,
  selectItem,
  startCreate,
  startEdit,
  cancelEdit,
  submitForm,
  handleToggle,
  handleDeleteItem,
  FUNCTION_TAB_OPTIONS,
  SCOPE_OPTIONS,
} = useFunctionalTesting()

function clearParamErrors(): void {
  paramErrors.value = {}
}
</script>

<template>
  <div class="fn-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">函数管理</h1>
        <p class="page-head__desc">项目内可复用的自定义函数，内置函数由平台统一提供</p>
      </div>
    </header>

    <div class="fn-body">
      <aside class="fn-list">
        <div class="fn-list__head">
          <el-input
            v-model="keyword"
            class="fn-list__search"
            placeholder="搜索函数名称..."
            clearable
            @input="handleSearchInput"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" :disabled="!canEdit" class="fn-list__add" @click="startCreate">
            <el-icon><Plus /></el-icon>新增
          </el-button>
        </div>

        <div class="fn-list__body">
          <el-radio-group v-model="activeTab" class="fn-list__tabs">
            <el-radio-button v-for="tab in FUNCTION_TAB_OPTIONS" :key="tab.value" :value="tab.value">
              {{ tab.label }}
            </el-radio-button>
          </el-radio-group>

          <div v-if="loadError" class="fn-empty">
            <p>函数列表加载失败</p>
            <el-button size="small" @click="loadAll()">重试</el-button>
          </div>

          <el-skeleton v-else-if="listLoading" :rows="8" animated class="fn-skeleton" />

          <div v-else-if="displayItems.length === 0" class="fn-empty">
            <p>{{ keyword ? '无匹配函数' : '暂无自定义函数，点击新增' }}</p>
            <el-button v-if="keyword" size="small" @click="keyword = ''; loadAll()">清除搜索</el-button>
          </div>

          <ul v-else class="fn-items">
            <li
              v-for="item in displayItems"
              :key="`${item.type}-${item.name}`"
              class="fn-item"
              :class="{
                'is-active': item.type === selectedType && item.name === selectedName,
                'is-disabled': item.type === 'custom' && item.enabled === false,
              }"
              @click="selectItem(item.type, item.name, item.id)"
            >
              <div class="fn-item__main">
                <span class="fn-item__name">{{ item.name }}</span>
                <el-tag v-if="item.type === 'builtin'" size="small" effect="plain">内置</el-tag>
                <template v-else>
                  <el-tag size="small" type="success" effect="light">自定义</el-tag>
                  <el-tag v-if="item.scope" size="small" effect="plain">{{ formatScopeLabel(item.scope) }}</el-tag>
                  <el-tag
                    size="small"
                    :type="item.enabled ? 'success' : 'info'"
                    :effect="item.enabled ? 'light' : 'plain'"
                  >
                    {{ item.enabled ? '已启用' : '已停用' }}
                  </el-tag>
                </template>
              </div>
              <div class="fn-item__meta">{{ item.description }}</div>
              <div v-if="item.type === 'custom' && canEdit" class="fn-item__acts" @click.stop>
                <el-button link size="small" type="danger" @click="handleDeleteItem(item)">删除</el-button>
              </div>
            </li>
          </ul>
        </div>
      </aside>

      <!-- 内置函数详情 -->
      <section v-if="selectedType === 'builtin' && selectedBuiltinFn" class="fn-detail">
        <div class="fn-detail__header">
          <div class="fn-detail__id">
            <h4 class="fn-detail__name">{{ selectedBuiltinFn.name }}</h4>
          </div>
          <div class="fn-detail__header-actions">
            <el-tag effect="plain">内置</el-tag>
          </div>
        </div>

        <div class="fn-detail__section">
          <label class="fn-detail__label">签名</label>
          <code class="fn-detail__code fn-detail__code--block">{{ selectedBuiltinFn.signature }}</code>
        </div>

        <div class="fn-detail__section">
          <label class="fn-detail__label">描述</label>
          <p class="fn-detail__text">{{ selectedBuiltinFn.description }}</p>
        </div>

        <div v-if="selectedBuiltinFn.params.length > 0" class="fn-detail__section">
          <label class="fn-detail__label">参数</label>
          <div class="fn-params">
            <div v-for="p in selectedBuiltinFn.params" :key="p.name" class="fn-param">
              <code class="fn-detail__code">{{ p.name }}</code>
              <el-tag v-if="p.required" size="small" type="warning" effect="light">必填</el-tag>
              <span class="fn-param__desc">{{ p.description }}</span>
            </div>
          </div>
        </div>

        <div class="fn-detail__section">
          <label class="fn-detail__label">示例</label>
          <code class="fn-detail__code fn-detail__code--block">{{ selectedBuiltinFn.example }}</code>
        </div>
      </section>

      <!-- 新建 / 编辑自定义函数 -->
      <section
        v-else-if="selectedType === 'custom' && panelMode !== 'view' && !detailLoading"
        class="fn-detail"
      >
        <div class="fn-detail__header">
          <div class="fn-detail__id">
            <h4 class="fn-detail__name">
              {{ panelMode === 'create' ? '新建自定义函数' : `编辑：${customDetail?.name ?? ''}` }}
            </h4>
          </div>
          <div class="fn-detail__header-actions">
            <el-tag effect="plain">自定义</el-tag>
          </div>
        </div>

        <el-form label-position="top">
          <el-form-item label="函数名称" required :error="formErrors.name || undefined">
            <el-input
              v-model="form.name"
              maxlength="100"
              placeholder="如：myFunc"
              @input="formErrors.name = ''"
            />
          </el-form-item>

          <el-form-item label="作用域">
            <el-select v-model="form.scope">
              <el-option
                v-for="opt in SCOPE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>

          <el-form-item label="参数配置">
            <div class="param-config">
              <table class="param-table">
                <thead>
                  <tr>
                    <th>参数名</th>
                    <th>必填</th>
                    <th>描述</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, i) in form.params" :key="i">
                    <td>
                      <el-input
                        v-model="row.name"
                        size="small"
                        maxlength="50"
                        placeholder="参数名"
                        @input="clearParamErrors"
                      />
                    </td>
                    <td class="param-table__req">
                      <el-checkbox v-model="row.required" @change="clearParamErrors" />
                    </td>
                    <td>
                      <el-input
                        v-model="row.description"
                        size="small"
                        maxlength="200"
                        placeholder="描述（可选）"
                        @input="clearParamErrors"
                      />
                    </td>
                    <td class="param-table__ops">
                      <el-button
                        link
                        size="small"
                        :disabled="i === 0"
                        title="上移"
                        @click="moveParamRow(i, -1)"
                      >↑</el-button>
                      <el-button
                        link
                        size="small"
                        :disabled="i === form.params.length - 1"
                        title="下移"
                        @click="moveParamRow(i, 1)"
                      >↓</el-button>
                      <el-button link size="small" type="danger" @click="removeParamRow(i)">删除</el-button>
                    </td>
                  </tr>
                  <tr v-if="form.params.length === 0">
                    <td colspan="4" class="param-table__empty">暂无参数，点击「添加参数」新增</td>
                  </tr>
                </tbody>
              </table>
              <div class="param-config__add">
                <el-button size="small" @click="addParamRow">＋ 添加参数</el-button>
              </div>
              <div class="param-config__preview">
                保存为参数说明：<code>{{ serializedParams || '（无参数）' }}</code>
              </div>
              <p v-for="(msg, idx) in paramErrors" :key="idx" class="param-config__error">{{ msg }}</p>
            </div>
          </el-form-item>

          <el-form-item label="Groovy 脚本" required :error="formErrors.script || undefined">
            <div class="code-block">
              <div class="code-block__bar"><el-icon><Terminal /></el-icon>Groovy</div>
              <Codemirror
                v-model="form.script"
                :extensions="editorExtensions"
                class="code-block__editor code-block__editor--edit"
                placeholder="// args 数组承接调用参数，返回值即求值结果&#10;return args[0]"
                @update:model-value="formErrors.script = ''"
              />
            </div>
          </el-form-item>
        </el-form>

        <div class="fn-detail__footer">
          <el-button size="small" @click="cancelEdit">取消</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="submitForm">保存</el-button>
        </div>
      </section>

      <!-- 自定义函数详情 -->
      <section v-else-if="selectedType === 'custom' && !detailLoading" class="fn-detail">
        <template v-if="customDetail">
          <div class="fn-detail__header">
            <div class="fn-detail__id">
              <h4 class="fn-detail__name">{{ customDetail.name }}</h4>
              <el-checkbox
                class="fn-detail__enable"
                :model-value="customDetail.enabled"
                :disabled="!canEdit"
                @change="handleToggle(customDetail)"
              >启用</el-checkbox>
            </div>
            <div class="fn-detail__header-actions">
              <el-tag effect="plain">{{ formatScopeLabel(customDetail.scope) }}</el-tag>
              <el-button v-if="canEdit" size="small" @click="startEdit">编辑</el-button>
            </div>
          </div>

          <div class="fn-detail__section">
            <label class="fn-detail__label">签名</label>
            <code class="fn-detail__code fn-detail__code--block">{{ customSignature }}</code>
          </div>

          <div class="fn-detail__section">
            <label class="fn-detail__label">描述</label>
            <p class="fn-detail__text">{{ customDetail.description || '—' }}</p>
          </div>

          <div class="fn-detail__section">
            <label class="fn-detail__label">参数</label>
            <div v-if="customParams.length > 0" class="fn-params">
              <div v-for="p in customParams" :key="p.name" class="fn-param">
                <code class="fn-detail__code">{{ p.name }}</code>
                <el-tag v-if="p.required" size="small" type="warning" effect="light">必填</el-tag>
                <span class="fn-param__desc">{{ p.description }}</span>
              </div>
            </div>
            <p v-else class="fn-detail__text">—</p>
          </div>

          <div class="fn-detail__section">
            <label class="fn-detail__label">示例</label>
            <code class="fn-detail__code fn-detail__code--block">{{ customSignature }}</code>
          </div>

          <div class="fn-detail__section">
            <label class="fn-detail__label">Groovy 脚本</label>
            <div class="code-block">
              <div class="code-block__bar"><el-icon><Terminal /></el-icon>Groovy</div>
              <Codemirror
                :model-value="customDetail.script"
                :extensions="editorExtensions"
                :disabled="true"
                class="code-block__editor code-block__editor--view"
              />
            </div>
          </div>
        </template>
        <div v-else class="fn-empty fn-empty--wide">
          <p>加载中...</p>
        </div>
      </section>

      <!-- 未选中 -->
      <section v-else class="fn-detail">
        <div class="fn-empty fn-empty--wide">
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

.fn-body {
  flex: 1;
  display: flex;
  gap: var(--space-lg);
  min-height: 0;
}

/* 左：函数列表（首行搜索 + 新增，其下为唯一滚动区） */
.fn-list {
  width: 340px;
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

.fn-list__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.fn-list__search {
  flex: 1;
  min-width: 0;
}

.fn-list__add {
  flex-shrink: 0;
}

.fn-list__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.fn-list__tabs {
  display: flex;
  width: 100%;
  flex-shrink: 0;

  :deep(.el-radio-button) {
    flex: 1;

    .el-radio-button__inner {
      width: 100%;
      padding: 7px 0;
      font-size: var(--font-size-xs);
    }
  }
}

.fn-skeleton {
  padding: var(--space-sm);
}

.fn-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fn-item {
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

  &.is-disabled {
    opacity: 0.55;
  }
}

.fn-item__main {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.fn-item__name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  font-family: var(--font-mono);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fn-item__meta {
  margin-top: 2px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fn-item__acts {
  margin-top: 4px;
}

.fn-empty {
  text-align: center;
  padding: var(--space-lg) var(--space-sm);
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);

  p {
    margin: 0 0 var(--space-sm);
  }

  &--wide {
    padding-top: 72px;
  }
}

/* 右：详情（滚动卡片，头部吸顶，分区标签左置两栏对齐） */
.fn-detail {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: 0 var(--space-lg) var(--space-lg);
}

.fn-detail__header {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin: 0 calc(0px - var(--space-lg)) var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: var(--color-neutral-0);
  border-bottom: 1px solid var(--color-neutral-100);
}

.fn-detail__id {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex: 1 1 auto;
  min-width: 0;
}

.fn-detail__name {
  margin: 0;
  font-size: var(--font-size-lg);
  font-family: var(--font-mono);
  color: var(--color-neutral-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.fn-detail__enable {
  flex-shrink: 0;
}

.fn-detail__header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.fn-detail__section {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: var(--space-sm) var(--space-md);
  align-items: start;

  & + & {
    margin-top: var(--space-md);
    padding-top: var(--space-md);
    border-top: 1px solid var(--color-neutral-100);
  }
}

.fn-detail__label {
  display: block;
  margin: 0;
  padding-top: 7px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-400);
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
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  padding: 5px 9px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-primary-600);
  word-break: break-all;
}

.fn-detail__code--block {
  display: block;
  width: 100%;
  padding: 9px 12px;
  border-left: 3px solid var(--color-primary-500);
  border-radius: var(--radius-md);
}

.fn-params {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fn-param {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 7px 10px;
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);

  .fn-detail__code {
    flex-shrink: 0;
    background: var(--color-neutral-0);
  }
}

.fn-param__desc {
  flex: 1;
  min-width: 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.fn-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-100);
}

/* 参数配置：行编辑表格 + 实时序列化预览（docs23 §1.3） */
.param-config {
  width: 100%;
}

.param-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;

  th {
    padding: 6px 8px;
    font-size: var(--font-size-xs);
    font-weight: 600;
    color: var(--color-neutral-400);
    text-align: left;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    border-bottom: 1px solid var(--color-neutral-100);
  }

  td {
    padding: 5px 8px;
    border-bottom: 1px solid var(--color-neutral-100);
    vertical-align: middle;
  }

  th:nth-child(1),
  td:nth-child(1) {
    width: 30%;
  }

  th:nth-child(2),
  td:nth-child(2) {
    width: 56px;
  }

  th:nth-child(4),
  td:nth-child(4) {
    width: 132px;
  }
}

.param-table__req {
  text-align: center;
}

.param-table__ops {
  text-align: right;
  white-space: nowrap;
}

.param-table__empty {
  padding: 12px 8px;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
  text-align: left;
}

.param-config__add {
  margin-top: 8px;
}

.param-config__preview {
  margin-top: 8px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  word-break: break-all;

  code {
    font-family: var(--font-mono);
    color: var(--color-primary-600);
  }
}

.param-config__error {
  margin: 6px 0 0;
  font-size: var(--font-size-xs);
  color: var(--el-color-danger);
}

/* Groovy 脚本：深色标题条 + 代码区（对齐 demo 的 code-block 视觉） */
.code-block {
  width: 100%;
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.code-block__bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: #2d2d2d;
  color: #9aa0a6;
  font-family: var(--font-mono);
  font-size: var(--font-size-2xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.code-block__editor {
  width: 100%;
  background: #1e1e1e;

  :deep(.cm-editor) {
    height: auto;
    background: #1e1e1e;
    color: #d4d4d4;
    font-size: var(--font-size-xs);
  }

  :deep(.cm-editor.cm-focused) {
    outline: none;
  }

  :deep(.cm-scroller) {
    width: 100%;
    overflow-x: auto;
    font-family: var(--font-mono);
    line-height: 1.6;
    color: #d4d4d4;
  }

  :deep(.cm-content) {
    color: #d4d4d4;
    caret-color: #d4d4d4;
    padding: 10px 12px;
  }

  :deep(.cm-placeholder) {
    color: #858585;
  }

  :deep(.cm-gutters) {
    background: #1e1e1e;
    color: #858585;
    border-right: 1px solid #2d2d2d;
  }

  :deep(.cm-activeLine) {
    background: #2a2d2e;
  }

  :deep(.cm-activeLineGutter) {
    background: #1e1e1e;
    color: #c6c6c6;
  }

  :deep(.cm-cursor),
  :deep(.cm-dropCursor) {
    border-left-color: #d4d4d4;
  }

  :deep(.cm-panels) {
    background: #2d2d2d;
    color: #cccccc;
  }

  :deep(.cm-tooltip) {
    background: #2d2d2d;
    border: 1px solid #454545;
  }

  &--view :deep(.cm-scroller) {
    min-height: 160px;
  }

  &--edit :deep(.cm-scroller) {
    min-height: 180px;
  }
}

// 重复类名提权，压过 Codemirror 基础主题的选区底色（CODE-008 禁止强制优先级声明）
.code-block__editor.code-block__editor {
  :deep(.cm-selectionBackground),
  :deep(.cm-content ::selection) {
    background: #264f78;
  }
}
</style>
