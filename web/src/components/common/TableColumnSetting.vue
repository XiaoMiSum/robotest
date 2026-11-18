<script setup lang="ts">
import { computed } from 'vue'
import type { TableColumnMeta } from '@/composables/common/useTableColumns'

const props = defineProps<{
  columns: TableColumnMeta[]
  visibleKeys: string[]
  iconOnly?: boolean
}>()

const emit = defineEmits<{
  change: [key: string, visible: boolean]
  selectAll: []
  reset: []
}>()

const optionalColumns = computed(() => props.columns.filter((c) => !c.pinned))
const allSelected = computed(() => optionalColumns.value.every((c) => props.visibleKeys.includes(c.key)))

// el-checkbox 的 change 载荷是宽类型，仅布尔勾选有意义
function onToggle(key: string, value: string | number | boolean) {
  emit('change', key, value === true)
}
</script>

<template>
  <el-popover placement="bottom-start" :width="220" trigger="click" popper-class="table-column-setting__popover">
    <template #reference>
      <el-button :text="!iconOnly" :link="iconOnly" :aria-label="'列设置'">
        <el-icon><Setting /></el-icon>
        <span v-if="!iconOnly">列设置</span>
      </el-button>
    </template>
    <div class="table-column-setting">
      <div class="table-column-setting__title">显示列</div>
      <el-checkbox-group :model-value="visibleKeys" class="table-column-setting__list">
        <el-checkbox
          v-for="col in columns"
          :key="col.key"
          :value="col.key"
          :disabled="col.pinned"
          @change="(value: string | number | boolean) => onToggle(col.key, value)"
        >
          {{ col.label }}
        </el-checkbox>
      </el-checkbox-group>
      <div class="table-column-setting__actions">
        <el-button link size="small" :disabled="allSelected" @click="emit('selectAll')">全选</el-button>
        <el-button link size="small" @click="emit('reset')">恢复默认</el-button>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.table-column-setting__title {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  margin-bottom: var(--space-sm);
}
.table-column-setting__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  max-height: 320px;
  overflow-y: auto;
}
.table-column-setting__actions {
  display: flex;
  justify-content: space-between;
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-200);
}
</style>
