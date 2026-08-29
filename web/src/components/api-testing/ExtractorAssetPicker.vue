<template>
  <el-dialog
    v-model="visible"
    title="从公共组件获取提取器"
    width="580px"
    :close-on-click-modal="false"
  >
    <p class="extractor-picker__tip">仅展示启用的提取器资产；引入为复制，得到独立副本，与源资产无关联。</p>
    <div class="extractor-picker__search">
      <el-input
        :model-value="keyword"
        placeholder="搜索提取器名称..."
        clearable
        @update:model-value="handleKeywordInput"
        @keyup.enter="emit('search')"
      >
        <template #append>
          <el-button :icon="Search" @click="emit('search')">搜索</el-button>
        </template>
      </el-input>
    </div>
    <el-table
      :data="items"
      size="small"
      class="extractor-picker__table"
      max-height="320"
      empty-text="暂无可用提取器"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="40" />
      <el-table-column label="名称" prop="name" min-width="160" show-overflow-tooltip />
      <el-table-column label="作用域" width="90" align="center">
        <template #default="{ row }">
          {{ scopeLabel((row as ApiComponentListItem).scope) }}
        </template>
      </el-table-column>
      <el-table-column label="描述" prop="description" min-width="150" show-overflow-tooltip />
    </el-table>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="loading"
        :disabled="selected.length === 0"
        @click="handleConfirm"
      >
        引入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { ApiComponentListItem, ApiComponentScope } from '@/types'

const props = defineProps<{
  modelValue: boolean
  loading: boolean
  items: ApiComponentListItem[]
  keyword: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'update:keyword', value: string): void
  (e: 'search'): void
  (e: 'confirm', items: ApiComponentListItem[]): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const selected = ref<ApiComponentListItem[]>([])

watch(() => props.modelValue, (value) => {
  // 每次关闭清空勾选，避免下次打开残留上次的选择
  if (!value) selected.value = []
})

function handleKeywordInput(value: string) {
  emit('update:keyword', value)
}

function handleSelectionChange(rows: ApiComponentListItem[]) {
  selected.value = rows
}

function handleConfirm() {
  if (selected.value.length === 0) return
  emit('confirm', selected.value)
  visible.value = false
}

function scopeLabel(scope: ApiComponentScope): string {
  return SCOPE_LABELS[scope] ?? scope
}

const SCOPE_LABELS: Record<ApiComponentScope, string> = {
  global: '公共',
  workspace: '空间',
  project: '项目',
}
</script>

<style scoped>
.extractor-picker__tip {
  margin: 0 0 var(--space-sm);
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.extractor-picker__search {
  margin-bottom: var(--space-sm);
}

.extractor-picker__table {
  width: 100%;
}
</style>