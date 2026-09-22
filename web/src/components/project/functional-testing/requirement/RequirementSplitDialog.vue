<script setup lang="ts">
import { useRequirementSplit } from '@/composables/project/functional-testing/requirement/useRequirementSplit'
import AiModelSelect from '@/components/common/AiModelSelect.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'

const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ imported: [count: number] }>()

const {
  phase,
  inputExpanded,
  previewExpanded,
  text,
  warnings,
  importing,
  totalCount,
  checkedCount,
  allChecked,
  partialChecked,
  groups,
  startSplit,
  stop,
  expandInput,
  toggleAll,
  removeEntry,
  saveEdit,
  importChecked,
  reset,
} = useRequirementSplit(visible, emit)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="AI 拆分需求文档"
    width="720px"
    class="split-dialog"
    :close-on-click-modal="false"
    @closed="reset"
  >
    <div v-if="phase !== 'preview' || inputExpanded" class="split-dialog__input">
      <MarkdownEditor
        v-model="text"
        height="280px"
        :disabled="phase === 'streaming'"
        placeholder="粘贴整份需求文档，AI 将按模块/功能拆分为细粒度需求条目"
      />
      <div class="split-dialog__actions">
        <span class="split-dialog__hint">
          需求文档（Markdown，上限 20000 字符，超出部分将被忽略）
        </span>
        <el-progress
          v-if="phase === 'streaming'"
          :percentage="100"
          :indeterminate="true"
          :duration="1.2"
          class="split-dialog__progress"
        />
        <template v-else>
          <AiModelSelect />
          <el-button type="primary" :disabled="!text.trim()" @click="startSplit">
            <el-icon><MagicStick /></el-icon>AI 拆分
          </el-button>
        </template>
        <el-button v-if="phase === 'streaming'" @click="stop">停止</el-button>
      </div>
    </div>
    <div v-else class="split-dialog__input">
      <div class="split-dialog__collapsed">
        <span class="split-dialog__hint">
          已生成 {{ totalCount }} 条拆分结果，可重新拆分或确认入库
        </span>
        <el-link type="primary" :underline="false" @click="expandInput">重新拆分</el-link>
      </div>
    </div>

    <el-alert
      v-for="(w, i) in warnings"
      :key="i"
      :title="w"
      type="warning"
      :closable="false"
      show-icon
      class="split-dialog__warning"
    />

    <div
      v-if="phase === 'preview' && inputExpanded"
      class="split-dialog__divider"
      @click="previewExpanded = !previewExpanded"
    >
      <span class="split-dialog__divider-line" />
      <span class="split-dialog__divider-label">
        <el-icon><ArrowUp v-if="previewExpanded" /><ArrowDown v-else /></el-icon>
        {{ previewExpanded ? '收起拆分结果预览' : '展开拆分结果预览' }}
      </span>
      <span class="split-dialog__divider-line" />
    </div>

    <template v-if="phase === 'preview' && (previewExpanded || !inputExpanded)">
      <div v-if="groups.length" class="split-dialog__preview-header">
        <span class="split-dialog__preview-title">拆分预览（共 {{ totalCount }} 条）</span>
        <el-checkbox :model-value="allChecked" :indeterminate="partialChecked" @change="toggleAll">
          全选
        </el-checkbox>
      </div>
      <div class="split-dialog__preview">
        <div v-for="g in groups" :key="g.module" class="split-dialog__group">
          <div class="split-dialog__group-header">
            <span class="split-dialog__group-name">{{ g.module }}</span>
            <span class="split-dialog__group-count">已选 {{ g.checked }}/{{ g.items.length }}</span>
          </div>
          <div v-for="entry in g.items" :key="entry.key" class="split-dialog__entry">
            <el-checkbox v-model="entry.checked" class="split-dialog__entry-check" />
            <template v-if="entry.editing">
              <div class="split-dialog__entry-edit">
                <el-input v-model="entry.title" maxlength="200" size="small" placeholder="需求点标题" />
                <MarkdownEditor v-model="entry.content" height="160px" />
                <div class="split-dialog__entry-edit-actions">
                  <el-button link size="small" type="primary" @click="saveEdit(entry)">保存</el-button>
                  <el-button link size="small" @click="entry.editing = false">取消</el-button>
                </div>
              </div>
            </template>
            <template v-else>
              <span class="split-dialog__entry-title">{{ entry.title }}</span>
              <span class="split-dialog__entry-actions">
                <el-button
                  link
                  type="primary"
                  size="small"
                  :aria-label="`编辑 ${entry.title}`"
                  @click="entry.editing = true"
                >
                  <el-icon><EditPen /></el-icon>
                </el-button>
                <el-button
                  link
                  type="danger"
                  size="small"
                  :aria-label="`删除 ${entry.title}`"
                  @click="removeEntry(entry)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </span>
            </template>
          </div>
        </div>
        <el-empty
          v-if="!groups.length"
          description="预览为空，可关闭后重新拆分"
          :image-size="64"
        />
      </div>
    </template>

    <template v-if="phase === 'preview' && (previewExpanded || !inputExpanded)" #footer>
      <span class="split-dialog__selected">已选 {{ checkedCount }} / {{ totalCount }} 条</span>
      <el-button :loading="importing" :disabled="checkedCount === 0" type="primary" @click="importChecked">
        批量入库
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.split-dialog__actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-top: var(--space-md);
}

.split-dialog__hint {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.split-dialog__collapsed {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.split-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 180px);
  overflow-y: auto;
}

.split-dialog__progress {
  flex: 1;
}

.split-dialog__selected {
  float: left;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 32px;
}

.split-dialog__warning {
  margin-top: var(--space-md);
}

.split-dialog__divider {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin: var(--space-md) 0;
  cursor: pointer;
  user-select: none;

  &:hover .split-dialog__divider-label {
    color: var(--el-color-primary);
  }
}

.split-dialog__divider-line {
  flex: 1;
  height: 1px;
  background: var(--el-border-color-lighter);
}

.split-dialog__divider-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  transition: color 0.2s;
}

.split-dialog__preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--space-lg) 0 var(--space-sm);
}

.split-dialog__preview-title {
  font-weight: 600;
}

.split-dialog__preview {
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.split-dialog__group {
  padding: var(--space-md);

  & + & {
    border-top: 1px dashed var(--el-border-color-lighter);
  }
}

.split-dialog__group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-sm);
}

.split-dialog__group-name {
  font-weight: 600;
}

.split-dialog__group-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.split-dialog__entry {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;

  & + & {
    border-top: 1px solid var(--el-border-color-extra-light);
  }
}

.split-dialog__entry-actions {
  visibility: hidden;
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
}

.split-dialog__entry:hover .split-dialog__entry-actions,
.split-dialog__entry:focus-within .split-dialog__entry-actions {
  visibility: visible;
}

.split-dialog__entry-check {
  margin-right: var(--space-xs);
}

.split-dialog__entry-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.split-dialog__entry-edit {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.split-dialog__entry-edit-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
