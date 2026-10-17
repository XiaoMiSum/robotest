<script setup lang="ts">
import { ArrowDown, Rank, Plus, EditPen } from '@element-plus/icons-vue'
import type { ApiHttpConfig, ApiDataSource } from '@/types'
import ProcessorConfigEditor from '@/components/project/api-testing/ProcessorConfigEditor.vue'
import type { SceneProcessorElement } from '@/composables/project/api-testing/scene/useSceneProcessors'
import type { useEditorSplit } from '@/composables/project/api-testing/scene/useEditorSplit'

const props = defineProps<{
  type: 'pre' | 'post'
  split: ReturnType<typeof useEditorSplit>
  processors: SceneProcessorElement[]
  selectedIdx: number | null
  indexes: number[]
  tags: (idx: number) => { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }[]
  displayName: (idx: number) => string
  drag: { type: 'pre' | 'post'; flat: number } | null
  httpRefOptions: ApiHttpConfig[]
  dsRefOptions: ApiDataSource[]
}>()

const emit = defineEmits<{
  select: [idx: number]
  add: []
  remove: [position: number]
  move: [position: number, dir: -1 | 1]
  copy: [idx: number]
  update: [idx: number, value: Record<string, unknown>]
  openAssetPicker: []
  openExtractorPicker: []
  onDragStart: [flat: number, e: DragEvent]
  onDragOver: [e: DragEvent]
  onDrop: [position: number]
}>()

const label = props.type === 'pre' ? '前置处理器' : '后置处理器'
const emptyDesc = props.type === 'pre' ? '暂无前置处理器' : '暂无后置处理器'
</script>

<!-- eslint-disable vue/no-mutating-props -->
<template>
  <div
    :ref="split.register"
    class="scene-editor__proc-layout"
    :class="{ 'is-dragging': split.dragging.value }"
  >
    <div class="scene-editor__proc-left" :style="{ flexBasis: `${split.ratio.value * 100}%` }">
      <div class="scene-editor__section-head">
        <span>{{ label }}（{{ indexes.length }}）</span>
        <div class="scene-editor__section-actions">
          <el-button link type="primary" size="small" @click="emit('openAssetPicker')">从公共组件引入</el-button>
          <el-button link type="primary" size="small" @click="emit('add')">+ 添加{{ label }}</el-button>
        </div>
      </div>
      <template v-for="(idx, i) in indexes" :key="idx">
        <div class="scene-editor__proc-insert-zone" @dragover.prevent="emit('onDragOver', $event)" @drop="emit('onDrop', i)">
          <div class="scene-editor__proc-insert-line" />
        </div>
        <div
          class="scene-editor__proc-item"
          :class="{ 'is-selected': selectedIdx === idx, 'is-disabled': !processors[idx]?.enabled, 'is-dragging': drag?.flat === idx }"
          draggable="true"
          @click="emit('select', idx)"
          @dragstart="emit('onDragStart', idx, $event)"
          @dragover.prevent="emit('onDragOver', $event)"
          @drop="emit('onDrop', i)"
        >
          <div class="scene-editor__proc-item-header">
            <el-icon class="scene-editor__proc-drag-handle" title="拖拽排序"><Rank /></el-icon>
            <span class="scene-editor__proc-index">{{ i + 1 }}</span>
            <el-tag v-for="t in tags(idx)" :key="t.text" size="small" :type="t.type">{{ t.text }}</el-tag>
            <div class="scene-editor__proc-header-spacer" />
            <el-switch v-model="processors[idx].enabled" size="small" @click.stop />
            <el-dropdown trigger="click" @click.stop>
              <el-button link size="small" @click.stop>
                操作
                <el-icon class="scene-editor__proc-caret"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="emit('select', idx)">编辑</el-dropdown-item>
                  <el-dropdown-item :disabled="i === 0" @click="emit('move', i, -1)">上移</el-dropdown-item>
                  <el-dropdown-item :disabled="i === indexes.length - 1" @click="emit('move', i, 1)">下移</el-dropdown-item>
                  <el-dropdown-item divided @click="emit('copy', idx)">复制</el-dropdown-item>
                  <el-dropdown-item divided style="color: var(--el-color-danger)" @click="emit('remove', i)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="scene-editor__proc-item-name">{{ displayName(idx) }}</div>
        </div>
      </template>
      <div class="scene-editor__proc-insert-zone" @dragover.prevent="emit('onDragOver', $event)" @drop="emit('onDrop', indexes.length)">
        <div class="scene-editor__proc-insert-line" />
      </div>
      <el-empty v-if="indexes.length === 0" :description="emptyDesc" :image-size="60" />
      <el-button v-if="indexes.length === 0" size="small" class="scene-editor__proc-add" @click="emit('add')">
        <el-icon><Plus /></el-icon> 添加{{ label }}
      </el-button>
    </div>

    <div
      class="scene-editor__splitter"
      :class="{ 'is-dragging': split.dragging.value }"
      @mousedown.prevent="split.start"
    >
      <div class="scene-editor__splitter-line" />
    </div>

    <div class="scene-editor__proc-right">
      <template v-if="selectedIdx !== null">
        <!-- 名称/启用与类型切换同排：类型、引用与配置体由共享组件承载 -->
        <div class="scene-editor__proc-inline">
          <ProcessorConfigEditor
            :model-value="processors[selectedIdx]"
            :http-options="httpRefOptions"
            :ds-options="dsRefOptions"
            @update:model-value="(v: Record<string, unknown>) => emit('update', selectedIdx!, v)"
            @import-extractors="emit('openExtractorPicker')"
          >
            <template #header>
              <el-input v-model="processors[selectedIdx].name" placeholder="处理器名称" class="scene-editor__proc-inline-name" />
              <el-switch v-model="processors[selectedIdx].enabled" active-text="启用" />
              <el-divider direction="vertical" />
            </template>
          </ProcessorConfigEditor>
        </div>
      </template>
      <div v-else class="scene-editor__right-empty">
        <el-icon :size="28"><EditPen /></el-icon>
        <p>暂无{{ label }}，请先新增</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
// 处理器面板与步骤编排同一分栏语言（交互 10 §1.5）：左列表卡片 + 分割条 + 右内联编辑
// 样式必须落在本组件作用域内——类名虽与编排页同名，页面 scoped 样式覆盖不到子组件内部
.scene-editor__proc-layout {
  display: flex;
  align-items: stretch;
  height: 100%;
}

.scene-editor__proc-left {
  flex: 0 1 auto;
  min-width: 240px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: var(--space-md);
  overflow-y: auto;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
}

.scene-editor__proc-right {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--space-md);
  overflow: auto;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
}

// 区块标题与操作行：与编排页 section-head 同口径（字号字重 + 底部分隔线）
.scene-editor__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--color-neutral-100);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-800);
}

.scene-editor__section-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

// 处理器列表项：与步骤卡片同构（描边卡片 + 选中高亮 + 禁用 / 拖拽置灰）
.scene-editor__proc-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
  cursor: pointer;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);

  &:hover {
    border-color: var(--color-primary-300);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }

  &.is-selected {
    border-color: var(--color-primary-400);
    background: var(--color-primary-50);
    box-shadow: 0 0 0 1px var(--color-primary-300);
  }

  &.is-disabled {
    opacity: 0.5;
  }

  &.is-dragging {
    opacity: 0.4;
  }
}

.scene-editor__proc-item-header {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.scene-editor__proc-drag-handle {
  flex-shrink: 0;
  font-size: 15px;
  color: var(--color-neutral-400);
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.scene-editor__proc-index {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--color-neutral-100);
  font-size: 11px;
  font-weight: 600;
  color: var(--color-neutral-600);
}

.scene-editor__proc-header-spacer {
  flex: 1;
}

// 「操作」后的下拉箭头：提示其为下拉菜单入口
.scene-editor__proc-caret {
  margin-left: 2px;
  font-size: 12px;
}

.scene-editor__proc-item-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

// 拖拽落点：常态收成 4px 细线，悬停展开为浅主色提示带
.scene-editor__proc-insert-zone {
  height: 4px;
  transition: height var(--transition-fast);

  &:hover {
    height: 20px;
    background: var(--color-primary-50);
    border-radius: var(--radius-sm);
  }
}

.scene-editor__proc-insert-line {
  display: none;
}

.scene-editor__proc-add {
  align-self: center;
  margin-top: var(--space-sm);
}

// 分割条只作拖拽热区：平时不显示灰条与竖线，拖拽过程中给出反馈（交互 10 §1.1）
.scene-editor__splitter {
  flex: 0 0 var(--space-md);
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;

  .scene-editor__splitter-line {
    display: none;
    width: 1px;
    height: 100%;
    background: var(--color-primary-400);
  }

  &.is-dragging .scene-editor__splitter-line {
    display: block;
  }
}

// 右侧空态：与步骤页签同构——居中图标 + 文案；不设外边距使其与左侧列表等高，白底实线框、不设背景色
.scene-editor__right-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  padding: var(--space-xl) var(--space-lg);
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
  text-align: center;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);

  p {
    margin: 0;
  }
}

// 右侧内联编辑：名称定宽与开关同行，配置体随宽度自适应
.scene-editor__proc-inline {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.scene-editor__proc-inline-name {
  width: 240px;
}
</style>
