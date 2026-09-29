<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useComponentPage } from '@/composables/project/api-testing/component/useComponentPage'
import type { ComponentTab } from '@/composables/project/api-testing/component/componentModel'
import ComponentDetailPanel from './ComponentDetailPanel.vue'
import ComponentEditPanel from './ComponentEditPanel.vue'
import ExtractorAssetPicker from '@/components/project/api-testing/ExtractorAssetPicker.vue'

/** 触发上一页 / 下一页加载的滚动边距（对齐项目列表页的预取量） */
const SCROLL_THRESHOLD = 240

const itemsEl = ref<HTMLElement | null>(null)
/** 位移补偿基线：窗口前插或丢首部前的 scrollHeight */
let shiftBaseline = 0

const {
  canEdit,
  listLoading,
  loadError,
  list,
  loadingMore,
  loadingPrev,
  moreError,
  prevError,
  keyword,
  filterType,
  hasFilter,
  selectedId,
  selectedItem,
  panelMode,
  saving,
  editingId,
  form,
  httpRefOptions,
  dsRefOptions,
  extractorPickerVisible,
  extractorPickerLoading,
  extractorPickerItems,
  extractorPickerKeyword,
  loadList,
  loadMore,
  loadPrev,
  handleSearchInput,
  handleTabChange,
  clearFilters,
  selectComponent,
  startCreate,
  startEdit,
  cancelEdit,
  handleSave,
  handleEnableToggle,
  handleDelete,
  handleCopy,
  openExtractorPicker,
  handleExtractorPicked,
  loadExtractorAssets,
  COMPONENT_TAB_OPTIONS,
  SCOPE_TAG_TYPE,
  componentTypeLabel,
  componentScopeLabel,
} = useComponentPage({
  // 骨架与加载提示渲染在滚动容器外，补偿窗口内 scrollHeight 差值只含条目变化
  scroll: {
    beforeShift: () => {
      shiftBaseline = itemsEl.value?.scrollHeight ?? 0
    },
    afterShift: () => {
      const el = itemsEl.value
      if (!el) return
      el.scrollTop += el.scrollHeight - shiftBaseline
    },
  },
})

function handleListScroll(): void {
  const el = itemsEl.value
  if (!el) return
  // 失败态交给行内 [重试] 恢复，避免「仍贴近边界 → 自动重试 → 再失败」的循环
  const canPrev = !prevError.value
  const canMore = !moreError.value
  const distanceBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  // 内容未撑满视口时只补下一页，避免上下两个方向同时发起请求
  if (canPrev && el.scrollHeight > el.clientHeight && el.scrollTop < SCROLL_THRESHOLD) void loadPrev()
  if (canMore && distanceBottom < SCROLL_THRESHOLD) void loadMore()
}

watch([list, listLoading, loadingMore, loadingPrev], async () => {
  if (loadError.value) return
  // 首屏与每次窗口变更后按需续加载，撑满视口或到达窗口边界为止
  await nextTick()
  handleListScroll()
})
</script>

<template>
  <div class="cp-page">
    <div class="cp-body">
      <!-- 左：组件列表（搜索与新建位于列表首行，其下为类型页签） -->
      <aside class="cp-list">
        <div class="cp-list__head">
          <el-input
            v-model="keyword"
            placeholder="搜索组件名称..."
            clearable
            @input="handleSearchInput"
          />
          <el-button type="primary" :disabled="!canEdit" @click="startCreate">
            <el-icon><Plus /></el-icon>新建组件
          </el-button>
        </div>

        <el-segmented
          :model-value="filterType"
          :options="COMPONENT_TAB_OPTIONS"
          size="small"
          class="cp-list__tabs"
          @update:model-value="handleTabChange($event as ComponentTab)"
        />

        <div v-if="loadError" class="cp-empty">
          <p>组件列表加载失败</p>
          <el-button size="small" @click="loadList()">重试</el-button>
        </div>

        <el-skeleton v-else-if="listLoading" :rows="6" animated class="cp-list__skeleton" />

        <div v-else-if="list.length === 0" class="cp-empty">
          <p>{{ hasFilter ? '无匹配结果' : '暂无公共组件' }}</p>
          <el-button v-if="hasFilter" size="small" @click="clearFilters">清除筛选</el-button>
        </div>

        <template v-else>
          <div class="cp-list__state">
            <template v-if="loadingPrev">加载中…</template>
            <template v-else-if="prevError">
              <span>加载上一页失败</span>
              <el-button size="small" @click="loadPrev">重试</el-button>
            </template>
          </div>

          <ul ref="itemsEl" class="cp-items" @scroll.passive="handleListScroll">
            <li
              v-for="item in list"
              :key="item.id"
              class="cp-item"
              :class="{ 'is-active': item.id === selectedId, 'is-disabled': !item.enabled }"
              @click="selectComponent(item.id)"
            >
              <div class="cp-item__main">
                <span class="cp-item__name">{{ item.name }}</span>
                <el-tag size="small" :type="SCOPE_TAG_TYPE[item.scope]">{{ componentScopeLabel(item.scope) }}</el-tag>
                <el-tag size="small" :type="item.enabled ? 'success' : 'info'" :effect="item.enabled ? 'light' : 'plain'">
                  {{ item.enabled ? '已启用' : '已停用' }}
                </el-tag>
              </div>
              <div class="cp-item__meta">{{ componentTypeLabel(item.type) }} · {{ item.description || '—' }}</div>
            </li>
          </ul>

          <div class="cp-list__state">
            <template v-if="loadingMore">加载中…</template>
            <template v-else-if="moreError">
              <span>加载下一页失败</span>
              <el-button size="small" @click="loadMore">重试</el-button>
            </template>
          </div>
        </template>
      </aside>

      <!-- 右：查看 / 新建编辑 / 未选中三态 -->
      <section class="cp-detail-host">
        <ComponentDetailPanel
          v-if="panelMode === 'view' && selectedItem"
          :item="selectedItem"
          :can-edit="canEdit"
          @edit="startEdit()"
          @copy="handleCopy(selectedItem)"
          @delete="handleDelete(selectedItem)"
          @toggle="handleEnableToggle"
        />

        <ComponentEditPanel
          v-else-if="panelMode !== 'view'"
          :form="form"
          :editing="!!editingId"
          :saving="saving"
          :http-options="httpRefOptions"
          :ds-options="dsRefOptions"
          @save="handleSave"
          @cancel="cancelEdit"
          @import-extractors="openExtractorPicker"
        />

        <div v-else class="cp-empty cp-empty--wide">
          <p>{{ listLoading ? '加载中...' : '选择组件查看详情' }}</p>
        </div>
      </section>
    </div>

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
.cp-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  height: 100%;
}

.cp-body {
  flex: 1;
  display: flex;
  gap: var(--space-lg);
  min-height: 0;
}

/* 左：组件列表（与环境管理 / 函数管理同构） */
.cp-list {
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

.cp-list__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;

  .el-input {
    flex: 1;
    min-width: 0;
  }

  .el-button {
    flex-shrink: 0;
  }
}

.cp-list__tabs {
  flex-shrink: 0;
  width: 100%;
}

.cp-list__skeleton {
  padding: var(--space-sm);
}

/* 首尾加载提示固定占位，出现与消失不改变列表可视区高度（避免滚动跳动） */
.cp-list__state {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  min-height: 24px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.cp-items {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cp-item {
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

.cp-item__main {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 名称过长时让位给作用域与状态标签 */
.cp-item__name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cp-item__main .el-tag {
  flex: none;
}

.cp-item__meta {
  margin-top: 2px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右：详情 / 编辑（头部吸顶，分区标签左置两栏对齐） */
.cp-detail-host {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: 0 var(--space-lg) var(--space-lg);
}

:deep(.cp-detail__header) {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin: 0 calc(0px - var(--space-lg)) var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: var(--color-neutral-0);
  border-bottom: 1px solid var(--color-neutral-100);
}

:deep(.cp-detail__id) {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

:deep(.cp-detail__name) {
  margin: 0;
  font-size: var(--font-size-lg);
  color: var(--color-neutral-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.cp-detail__header-actions) {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

:deep(.cp-detail__section) {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: var(--space-sm) var(--space-md);
  align-items: start;
}

:deep(.cp-detail__section + .cp-detail__section) {
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-100);
}

:deep(.cp-detail__label) {
  display: block;
  margin: 0;
  padding-top: 7px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-400);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

:deep(.cp-meta) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: var(--space-md);
}

:deep(.cp-meta__item--wide) {
  grid-column: 1 / -1;
}

:deep(.cp-meta__k) {
  display: block;
  margin-bottom: 3px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

:deep(.cp-meta__v) {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  word-break: break-all;
}

:deep(.cp-code) {
  display: block;
  padding: 9px 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

:deep(.cp-kv) {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

:deep(.cp-kv + .cp-kv) {
  margin-top: 5px;
}

:deep(.cp-kv__k) {
  flex-shrink: 0;
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-primary-600);
}

:deep(.cp-kv__v) {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  word-break: break-all;
}

/* 提取器只读表格：表头沿用全局表格变量，仅去掉行间重边框 */
:deep(.cp-ext-table) {
  --el-table-border-color: var(--color-neutral-100);
}

/* 编辑面板表单 */
:deep(.cp-form__grid) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--space-md);
}

:deep(.cp-form__control) {
  width: 100%;
}

:deep(.cp-form__desc) {
  grid-column: 1 / -1;
}

:deep(.cp-form__hint) {
  grid-column: 1 / -1;
  margin: calc(var(--space-xs) * -1) 0 var(--space-md);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

:deep(.cp-form__label) {
  display: block;
  margin: var(--space-md) 0 var(--space-sm);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-400);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.cp-empty {
  text-align: center;
  padding: var(--space-lg) var(--space-sm);
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);

  p {
    margin: 0 0 var(--space-sm);
  }
}

.cp-list > .cp-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.cp-empty--wide {
  padding-top: 72px;
}
</style>
