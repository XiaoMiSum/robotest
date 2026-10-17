<script setup lang="ts">
import { ref } from 'vue'
import type { ApiSceneStepItem } from '@/types'
import { methodTagType, stepMethod, stepSqlType } from '@/composables/project/api-testing/scene/scenesModel'

const props = defineProps<{ steps: ApiSceneStepItem[]; isExecuting?: boolean; selectedId?: string | null }>()
const emit = defineEmits<{
  (e: 'edit', step: ApiSceneStepItem): void
  (e: 'delete', step: ApiSceneStepItem): void
  (e: 'toggle', step: ApiSceneStepItem): void
  (e: 'copy', step: ApiSceneStepItem): void
  (e: 'reorder', steps: ApiSceneStepItem[]): void
  (e: 'debug', step: ApiSceneStepItem): void
  (e: 'insertBefore', index: number): void
  (e: 'add'): void
}>()

const dragIndex = ref<number | null>(null)

/** 步骤首行标签：HTTP 显示方法，JDBC 显示 SQL 语句类型 */
function stepTag(step: ApiSceneStepItem): { text: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' } | null {
  const method = stepMethod(step)
  if (method) return { text: method, type: methodTagType(method) }
  const sql = stepSqlType(step)
  if (sql) return { text: sql, type: 'primary' }
  return null
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
          :class="{ 'is-disabled': !step.enabled, 'is-missing': step.sourceMissing, 'is-selected': step.id === props.selectedId }"
          draggable="true"
          data-test="step-card"
          @click="emit('edit', step)"
          @dragstart="(e: DragEvent) => onDragStart(index, e)"
          @dragover.prevent="(e: DragEvent) => onDragOver(index, e)"
          @drop="() => onDrop(index)"
        >
          <div class="step-canvas__card-header">
            <!-- 拖拽手柄不触发选中：其余区域点击均展开右侧明细（交互 10 §1.2） -->
            <el-icon class="step-canvas__drag-handle" title="拖拽排序" @click.stop><Rank /></el-icon>
            <span class="step-canvas__index">{{ index + 1 }}</span>
            <el-tag v-if="step.stepType" size="small" type="info">{{ step.stepType.toUpperCase() }}</el-tag>
            <el-tag v-if="stepTag(step)" size="small" :type="stepTag(step)!.type">{{ stepTag(step)!.text }}</el-tag>
            <el-tag v-if="step.sourceInterfaceName" size="small" type="warning" class="step-canvas__source-tag">
              {{ step.sourceInterfaceName }}
            </el-tag>
            <el-tag v-if="step.validators?.length" size="small" type="success" class="step-canvas__count-tag">
              验证器 {{ step.validators.length }}
            </el-tag>
            <el-tag v-if="step.extractors?.length" size="small" type="primary" class="step-canvas__count-tag">
              提取器 {{ step.extractors.length }}
            </el-tag>
            <span v-if="step.sourceMissing" class="step-canvas__missing-badge">源已删除</span>
            <div class="step-canvas__header-spacer" />
            <el-checkbox
              :model-value="step.enabled"
              size="small"
              class="step-canvas__enabled"
              @change="() => emit('toggle', step)"
              @click.stop
            >启用</el-checkbox>
            <el-dropdown trigger="click" @click.stop>
              <el-button link size="small" type="primary" @click.stop>
                操作
                <el-icon class="step-canvas__caret"><ArrowDown /></el-icon>
              </el-button>
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

          <div class="step-canvas__card-body">
            <div class="step-canvas__card-name">{{ step.name }}</div>
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

    <!-- 空态：与前后置处理器列表同构（引导插图 + 新增按钮，交互 10 §1.2） -->
    <el-empty v-if="steps.length === 0" description="暂无步骤" :image-size="60" />
    <el-button v-if="steps.length === 0" size="small" class="step-canvas__add" @click="emit('add')">
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

// 空态新增按钮：与处理器页签同为居中弱样式入口
.step-canvas__add {
  align-self: center;
  margin-top: var(--space-sm);
}

.step-canvas__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.step-canvas__insert-zone {
  height: 4px;
  transition: height var(--transition-fast);

  &:hover {
    height: 24px;
    background: var(--color-primary-50);
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
  // 定高改最小高度：验证器 / 提取器 tag 与展开详情不再被裁掉
  min-height: 88px;
  padding: var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  cursor: pointer;

  &:hover {
    border-color: var(--color-primary-300);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }

  &.is-disabled {
    opacity: 0.5;
  }

  &.is-missing {
    border-color: var(--color-warning-border);
    background: var(--color-warning-light);
  }

  &.is-selected {
    border-color: var(--color-primary-400);
    background: var(--color-primary-50);
    box-shadow: 0 0 0 1px var(--color-primary-300);
  }
}

// 头部 tag 较多（类型 / 方法 / 来源 / 验证器 / 提取器）时折行，配合卡片自适应高度
.step-canvas__card-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
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

.step-canvas__source-tag {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-canvas__missing-badge {
  font-size: 11px;
  color: var(--color-warning-strong);
}

.step-canvas__card-body {
  padding: var(--space-xs) 0 0 0;
}

// 启用改为复选框（原开关文字在窄列里竖排折行）
.step-canvas__enabled {
  flex-shrink: 0;
  white-space: nowrap;
}

// 「操作」后的下拉箭头：提示其为下拉菜单入口
.step-canvas__caret {
  margin-left: 2px;
  font-size: 12px;
}

// 验证器 / 提取器计数常驻头部（交互 10 §1.2）
.step-canvas__count-tag {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-canvas__card-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-800);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
