<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiInterfaceChangeLogItem } from '@/types'
import { fetchInterfaceChangeLogs } from '@/services/apiInterface'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ interfaceId: string; modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const rows = ref<ApiInterfaceChangeLogItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)

const ACTION_LABELS: Record<string, string> = {
  create: '创建',
  update: '编辑',
  copy: '复制',
  import: '导入',
}

watch(
  [visible, pageNo],
  async ([open]) => {
    if (!open) return
    loading.value = true
    try {
      const page = await fetchInterfaceChangeLogs(props.interfaceId, pageNo.value, pageSize)
      rows.value = page.list
      total.value = page.total
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '变更历史加载失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog v-model="visible" title="变更历史" width="640px" destroy-on-close>
    <el-table v-loading="loading" :data="rows" size="small" max-height="400">
      <el-table-column label="版本" width="80">
        <template #default="{ row }">v{{ row.changeVersion }}</template>
      </el-table-column>
      <el-table-column label="动作" width="90">
        <template #default="{ row }">
          <el-tag size="small">{{ ACTION_LABELS[row.action] ?? row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <template #empty>暂无变更记录</template>
    </el-table>
    <el-pagination
      v-model:current-page="pageNo"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next, total"
      small
      class="change-logs-panel__pagination"
    />
  </el-dialog>
</template>

<style scoped lang="scss">
.change-logs-panel__pagination {
  margin-top: var(--space-md);
  justify-content: flex-end;
}
</style>
