<script setup lang="ts">
import { ref } from 'vue'
import type { ApiSceneStepItem } from '@/types'
import { methodTagType, stepMethod, stepPath, SOURCE_TYPE_LABELS } from '../scenesModel'

const props = defineProps<{ steps: ApiSceneStepItem[]; isExecuting?: boolean }>()
const emit = defineEmits<{
  (e: 'add'): void
  (e: 'edit', step: ApiSceneStepItem): void
  (e: 'delete', step: ApiSceneStepItem): void
  (e: 'toggle', step: ApiSceneStepItem): void
  (e: 'copy', step: ApiSceneStepItem): void
  (e: 'reorder', steps: ApiSceneStepItem[]): void
  (e: 'debug', step: ApiSceneStepItem): void
  (e: 'insertBefore', index: number): void
}>()

const expandedIds = ref<Set<string>>(new Set())
const dragIndex = ref<number | null>(null)

function toggleExpand(id: string) {
  if (expandedIds.value.has(id)) expandedIds.value.delete(id)
  else expandedIds.value.add(id)
}

function onDragStart(index: number, e: DragEvent) {
  dragIndex.value = index
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}

function onDragOver(_index: number, e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
}

function onDrop(index: number) {
  if (dragIndex.value === null || dragIndex.value === index) { dragIndex.value = null; return }
  const next = [...props.steps]
  const [moved] = next.splice(dragIndex.value, 1)
  next.splice(index, 0, moved)
  dragIndex.value = null
  emit('reorder', next.map((s, i) => ({ ...s, sortOrder: i })))
}

function moveUp(index: number) {
  if (index <= 0) return
  const next = [...props.steps]
  ;[next[index - 1], next[index]] = [next[index], next[index - 1]]
  emit('reorder', next.map((s, i) => ({ ...s, sortOrder: i })))
}

function moveDown(index: number) {
  if (index >= props.steps.length - 1) return
  const next = [...props.steps]
  ;[next[index], next[index + 1]] = [next[index + 1], next[index]]
  emit('reorder', next.map((s, i) => ({ ...s, sortOrder: i })))
}
</script>

<template>
  <div class="step-canvas">
    <div class="step-canvas__list">
      <template v-for="(step, index) in steps" :key="step.id">
        <!-- 插入指示线 -->
        <div
          class="step-canvas__insert-zone"
          @dragover.prevent="(e: DragEvent) => onDragOver(index, e)"
          @drop="() => onDrop(index)"
        >
          <div class="step-canvas__insert-line" />
        </div>

        <div
          class="step-canvas__card"
          :class="{ 'is-disabled': !step.enabled, 'is-missing': step.sourceMissing, 'is-expanded': expandedIds.has(step.id) }"
          draggable="true"
          data-test="step-card"
          @dragstart="(e: DragEvent) => onDragStart(index, e)"
          @dragover.prevent="(e: DragEvent) => onDragOver(index, e)"
          @drop="() => onDrop(index)"
        >
          <div class="step-canvas__card-header">
            <el-icon class="step-canvas__drag-handle" title="拖拽排序"><Rank /></el-icon>
            <span class="step-canvas__index">{{ index + 1 }}</span>
            <el-tag v-if="step.stepType" size="small" type="info">{{ step.stepType.toUpperCase() }}</el-tag>
            <el-tag v-if="step.sourceType && step.sourceType !== 'custom'" size="small">
              {{ SOURCE_TYPE_LABELS[step.sourceType] ?? step.sourceType }}
            </el-tag>
            <el-tag v-if="step.sourceInterfaceName" size="small" type="warning" class="step-canvas__source-tag">
              {{ step.sourceInterfaceName }}
            </el-tag>
            <span v-if="step.sourceMissing" class="step-canvas__missing-badge">源已删除</span>
            <div class="step-canvas__header-spacer" />
            <el-button v-if="step.sourceType === 'link'" link size="small" class="step-canvas__expand-btn" @click.stop="toggleExpand(step.id)">
              <el-icon><ArrowDown v-if="!expandedIds.has(step.id)" /><ArrowUp v-else /></el-icon>
            </el-button>
            <el-switch :model-value="step.enabled" size="small" @change="() => emit('toggle', step)" @click.stop />
            <el-dropdown trigger="click" @click.stop>
              <el-button link size="small">操作</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="emit('edit', step)">编辑</el-dropdown-item>
                  <el-dropdown-item :disabled="index === 0" @click="moveUp(index)">上移</el-dropdown-item>
                  <el-dropdown-item :disabled="index === steps.length - 1" @click="moveDown(index)">下移</el-dropdown-item>
                  <el-dropdown-item :disabled="isExecuting" @click="emit('debug', step)">调试</el-dropdown-item>
                  <el-dropdown-item divided @click="emit('copy', step)">复制</el-dropdown-item>
                  <el-dropdown-item divided style="color: var(--el-color-danger)" @click="emit('delete', step)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div class="step-canvas__card-body" @click="emit('edit', step)">
            <div class="step-canvas__card-name">{{ step.name }}</div>
            <div v-if="stepMethod(step)" class="step-canvas__card-meta">
              <el-tag size="small" :type="methodTagType(stepMethod(step) ?? '')">{{ stepMethod(step) }}</el-tag>
              <span class="step-canvas__card-path">{{ stepPath(step) }}</span>
            </div>
          </div>

          <!-- 展开详情（链接引用的源已删除时显示快照信息） -->
          <div v-if="expandedIds.has(step.id)" class="step-canvas__expanded-detail">
            <div v-if="step.sourceMissing" class="step-canvas__snapshot-hint">
              <el-icon><WarningFilled /></el-icon> 源已删除，使用创建时的快照执行
            </div>
            <div v-if="step.validators?.length" class="step-canvas__detail-item">
              断言：{{ step.validators.length }} 条
            </div>
            <div v-if="step.extractors?.length" class="step-canvas__detail-item">
              提取器：{{ step.extractors.length }} 条
            </div>
          </div>
        </div>
      </template>

      <!-- 末尾插入区 -->
      <div
        class="step-canvas__insert-zone"
        @dragover.prevent
        @drop="() => onDrop(steps.length)"
      />
    </div>

    <el-button class="step-canvas__add" data-test="step-add-btn" @click="emit('add')">
      <el-icon><Plus /></el-icon> 添加步骤
    </el-button>
  </div>
</template>

<style scoped lang="scss">
.step-canvas {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.step-canvas__list {
  display: flex;
  flex-direction: column;
}

.step-canvas__insert-zone {
  height: 4px;
  transition: height var(--transition-fast);

  &:hover {
    height: 24px;
    background: var(--color-primary-50, #eff6ff);
    border-radius: var(--radius-sm);
  }
}

.step-canvas__insert-line {
  display: none;
}

.step-canvas__card {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  cursor: default;
  margin: 2px 0;

  &:hover {
    border-color: var(--color-primary-300);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }

  &.is-disabled {
    opacity: 0.5;
  }

  &.is-missing {
    border-color: var(--color-warning-300);
    background: var(--color-warning-50, #fffbeb);
  }
}

.step-canvas__card-header {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.step-canvas__drag-handle {
  cursor: grab;
  color: var(--color-neutral-400);
  font-size: 16px;
  flex-shrink: 0;

  &:active { cursor: grabbing; }
}

.step-canvas__index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-neutral-100);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-neutral-600);
  flex-shrink: 0;
}

.step-canvas__header-spacer {
  flex: 1;
}

.step-canvas__expand-btn {
  color: var(--color-neutral-400);
}

.step-canvas__source-tag {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-canvas__missing-badge {
  font-size: 11px;
  color: var(--color-warning-600, #d97706);
}

.step-canvas__card-body {
  padding: var(--space-xs) 0 0 0;
  cursor: pointer;
}

.step-canvas__card-name {
  font-weight: 500;
  margin-bottom: 2px;
}

.step-canvas__card-meta {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.step-canvas__card-path {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-canvas__expanded-detail {
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.step-canvas__snapshot-hint {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-warning-600, #d97706);
  margin-bottom: var(--space-xs);
}

.step-canvas__detail-item {
  margin-top: 2px;
}

.step-canvas__add {
  border-style: dashed;
  width: 100%;
  margin-top: var(--space-sm);
}
</style>
