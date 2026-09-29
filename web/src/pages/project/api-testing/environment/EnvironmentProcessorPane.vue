<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ApiProcessor, ApiProcessorType } from '@/types'
import type { HttpConfigForm, DsForm } from '@/composables/project/api-testing/environment/useEnvironmentConfig'
import type { ProcDetail, ProcDraftMode } from '@/composables/project/api-testing/environment/useEnvironmentProcessors'
import ProcessorForm from '@/components/project/api-testing/ProcessorForm.vue'

// 行数据：列表行与末尾新增草稿行同构渲染，避免表单/明细模板重复
interface PaneRow { key: string; processor: ApiProcessor; number: number }

const props = defineProps<{
  processorType: ApiProcessorType
  emptyDescription: string
  count: number
  canEdit: boolean
  processors: ApiProcessor[]
  draft: ApiProcessor | null
  draftMode: ProcDraftMode
  expandedId: string
  configForms: HttpConfigForm[]
  dsForms: DsForm[]
  procTags: (processor: ApiProcessor) => { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[]
  procDisplayName: (processor: ApiProcessor, index: number) => string
  procDetail: (processor: ApiProcessor) => ProcDetail
}>()

const emit = defineEmits<{
  (e: 'toggle-detail', processor: ApiProcessor): void
  (e: 'add'): void
  (e: 'edit', processor: ApiProcessor): void
  (e: 'cancel'): void
  (e: 'save'): void
  (e: 'remove', processor: ApiProcessor): void
  (e: 'move', index: number, direction: -1 | 1): void
  (e: 'copy', processor: ApiProcessor): void
  (e: 'import'): void
  (e: 'import-extractors'): void
}>()

const nameError = ref('')

const showDraftRow = computed(() =>
  props.draftMode === 'add' && props.draft !== null && props.draft.processorType === props.processorType,
)

const rows = computed<PaneRow[]>(() => {
  const list: PaneRow[] = props.processors.map((processor, index) => ({
    key: processor.id ?? `row-${index}`,
    processor,
    number: index + 1,
  }))
  if (showDraftRow.value && props.draft) {
    list.push({ key: 'draft', processor: props.draft, number: props.count + 1 })
  }
  return list
})

function isDraftRow(row: PaneRow): boolean {
  return row.key === 'draft' && props.draftMode === 'add'
}

function isEditingRow(row: PaneRow): boolean {
  if (props.draftMode === 'add') return isDraftRow(row)
  return props.draftMode === 'edit' && props.draft !== null && props.draft.id === row.processor.id
}

function isExpandedRow(row: PaneRow): boolean {
  return !!row.processor.id && props.expandedId === row.processor.id
}

const expandedDetail = computed<ProcDetail | null>(() => {
  const processor = props.processors.find((item) => item.id === props.expandedId)
  return processor ? props.procDetail(processor) : null
})

const draftName = computed({
  get: () => props.draft?.name ?? '',
  set: (value: string) => {
    if (props.draft) props.draft.name = value
  },
})

const draftConfig = computed(() => props.draft?.config ?? {})

function onDraftConfig(value: Record<string, unknown>) {
  if (props.draft) props.draft.config = value
}

function onRowClick(row: PaneRow) {
  // 新增草稿行不参与展开/收起，避免未保存内容被误关（docs34 §1.3）
  if (isDraftRow(row)) return
  emit('toggle-detail', row.processor)
}

function onRowKey(row: PaneRow, event: KeyboardEvent) {
  if (event.target !== event.currentTarget) return
  onRowClick(row)
}

function onSave() {
  if (!props.draft?.name.trim()) {
    nameError.value = '请输入处理器名称'
    return
  }
  nameError.value = ''
  emit('save')
}

watch(() => props.draft, () => { nameError.value = '' })
watch(() => props.draft?.name, () => { nameError.value = '' })
</script>

<template>
  <div class="env-proc-pane">
    <div class="env-proc-pane__head">
      <el-button link type="primary" size="small" :disabled="!canEdit" @click="emit('add')">＋ 添加处理器</el-button>
      <el-button link type="primary" size="small" :disabled="!canEdit" @click="emit('import')">从公共组件引入</el-button>
    </div>

    <div class="env-proc-pane__list">
      <div
        v-for="row in rows"
        :key="row.key"
        class="env-proc-pane__item"
        :class="{ 'is-open': isEditingRow(row) || isExpandedRow(row), 'is-editing': isEditingRow(row) }"
      >
        <div
          class="env-proc-pane__row"
          role="button"
          tabindex="0"
          :aria-expanded="isExpandedRow(row)"
          @click="onRowClick(row)"
          @keydown.enter.prevent="onRowKey(row, $event)"
          @keydown.space.prevent="onRowKey(row, $event)"
        >
          <span class="env-proc-pane__num">{{ row.number }}</span>
          <el-tag v-for="tag in procTags(row.processor)" :key="tag.text" size="small" :type="tag.type">{{ tag.text }}</el-tag>
          <span class="env-proc-pane__name">{{ procDisplayName(row.processor, row.number - 1) }}</span>
          <div v-if="isEditingRow(row) || canEdit" class="env-proc-pane__ops" @click.stop>
            <template v-if="isEditingRow(row)">
              <el-button size="small" @click="emit('cancel')">取消</el-button>
              <el-button size="small" type="primary" @click="onSave">保存</el-button>
            </template>
            <template v-else>
              <el-button link size="small" :disabled="row.number === 1" @click="emit('move', row.number - 1, -1)">上移</el-button>
              <el-button link size="small" :disabled="row.number === count" @click="emit('move', row.number - 1, 1)">下移</el-button>
              <el-button link size="small" @click="emit('edit', row.processor)">编辑</el-button>
              <el-button link size="small" @click="emit('copy', row.processor)">复制</el-button>
              <el-button link size="small" type="danger" @click="emit('remove', row.processor)">删除</el-button>
            </template>
          </div>
        </div>

        <!-- 表单（编辑/新增）展开期间行内操作替换为常显 [取消]/[保存]，且不显示表单标题（docs34 §1.3） -->
        <div v-if="isEditingRow(row)" class="env-proc-pane__body">
          <el-form label-position="top" @submit.prevent>
            <el-form-item class="env-proc-pane__name-item" label="名称" required :error="nameError">
              <el-input v-model="draftName" maxlength="100" placeholder="如：Token 预置" />
            </el-form-item>
          </el-form>
          <span class="env-proc-pane__form-label">配置（随类型切换）</span>
          <ProcessorForm
            :model-value="draftConfig"
            :http-options="configForms"
            :ds-options="dsForms"
            :show-type-select="true"
            :show-ref-select="false"
            @update:model-value="onDraftConfig"
            @import-extractors="emit('import-extractors')"
          />
        </div>

        <!-- 只读明细：结构与公共组件明细一致（44 §2.2），基本信息无数据不展示 -->
        <div v-else-if="isExpandedRow(row) && expandedDetail" class="env-proc-pane__body">
          <div class="env-proc-pane__detail-head">
            <span class="env-proc-pane__detail-name">{{ procDisplayName(row.processor, row.number - 1) }}</span>
            <el-tag v-for="tag in procTags(row.processor)" :key="tag.text" size="small" :type="tag.type">{{ tag.text }}</el-tag>
          </div>

          <div class="env-proc-pane__section">
            <span class="env-proc-pane__section-label">配置</span>
            <div class="env-proc-pane__meta">
              <div
                v-for="item in expandedDetail.config"
                :key="item.label"
                class="env-proc-pane__meta-item"
                :class="{ 'is-wide': item.wide }"
              >
                <span class="env-proc-pane__meta-k">{{ item.label }}</span>
                <span class="env-proc-pane__meta-v">{{ item.value }}</span>
              </div>
            </div>
          </div>

          <div v-if="expandedDetail.extractors.length" class="env-proc-pane__section">
            <span class="env-proc-pane__section-label">提取器</span>
            <el-table :data="expandedDetail.extractors" size="small">
              <el-table-column label="来源" prop="source" min-width="110" />
              <el-table-column label="表达式" prop="expression" min-width="180" show-overflow-tooltip />
              <el-table-column label="目标变量名" prop="variableName" min-width="130" />
              <el-table-column label="描述" prop="description" min-width="120" show-overflow-tooltip />
            </el-table>
          </div>
        </div>
      </div>
    </div>

    <div v-if="rows.length === 0" class="env-proc-pane__empty">{{ emptyDescription }}</div>
  </div>
</template>

<style scoped lang="scss">
.env-proc-pane {
  display: flex;
  flex-direction: column;
}

.env-proc-pane__head {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-100);
}

.env-proc-pane__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.env-proc-pane__item {
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);

  &.is-open {
    border-color: var(--color-neutral-200);
  }
}

.env-proc-pane__row {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 9px var(--space-md);
  border-radius: var(--radius-md);
  cursor: pointer;

  &:hover,
  &:focus-within {
    background: var(--color-neutral-50);
  }

  &:focus-visible {
    outline: 2px solid var(--color-primary-500);
    outline-offset: -2px;
  }

  .is-open > & {
    background: var(--color-neutral-50);
  }
}

.env-proc-pane__num {
  flex: none;
  width: 18px;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.env-proc-pane__name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 行内操作悬浮展示并浮在行右端：浮层自带底色，窄面板下不挤压名称 */
.env-proc-pane__ops {
  position: absolute;
  right: var(--space-md);
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding-left: var(--space-lg);
  background: var(--color-neutral-50);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--transition-fast);

  .env-proc-pane__row:hover &,
  .env-proc-pane__row:focus-within &,
  .is-editing & {
    opacity: 1;
    pointer-events: auto;
  }
}

.env-proc-pane__body {
  border-top: 1px solid var(--color-neutral-100);
  padding: var(--space-md);
}

.env-proc-pane__detail-head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.env-proc-pane__detail-name {
  font-size: var(--font-size-lg);
  color: var(--color-neutral-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.env-proc-pane__section {
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

.env-proc-pane__section-label {
  display: block;
  margin: 0;
  padding-top: 7px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-400);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.env-proc-pane__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: var(--space-sm) var(--space-md);

  .is-wide {
    grid-column: 1 / -1;
  }
}

.env-proc-pane__meta-k {
  display: block;
  margin-bottom: 3px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-proc-pane__meta-v {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  word-break: break-all;
}

.env-proc-pane__name-item {
  max-width: 360px;
}

.env-proc-pane__form-label {
  display: block;
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-400);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.env-proc-pane__empty {
  padding: 36px 0;
  text-align: center;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}
</style>
