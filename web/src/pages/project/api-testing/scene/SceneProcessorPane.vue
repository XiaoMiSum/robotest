<script setup lang="ts">
import { Rank, Plus } from '@element-plus/icons-vue'
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
              <el-button link size="small">操作</el-button>
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
        <p>选中左侧处理器后在右侧编辑</p>
      </div>
    </div>
  </div>
</template>
