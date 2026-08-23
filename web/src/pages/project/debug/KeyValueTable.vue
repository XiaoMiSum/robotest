<script setup lang="ts">
import type { ApiDebugKeyValue } from '@/types'

const entries = defineModel<ApiDebugKeyValue[]>('entries', { required: true })

const props = defineProps<{
  placeholderKey?: string
}>()

const emit = defineEmits<{ (e: 'change'): void }>()

function addRow() {
  entries.value.push({ key: '', value: '', enabled: true })
  emit('change')
}

function removeRow(index: number) {
  entries.value.splice(index, 1)
  emit('change')
}
</script>

<template>
  <div class="kv-table">
    <table>
      <thead>
        <tr>
          <th class="kv-table__col-enable">启用</th>
          <th>{{ placeholderKey ?? '名称' }}</th>
          <th>值</th>
          <th class="kv-table__col-op" />
        </tr>
      </thead>
      <tbody>
        <tr v-for="(entry, index) in entries" :key="index">
          <td><el-checkbox v-model="entry.enabled" size="small" @change="emit('change')" /></td>
          <td>
            <el-input v-model="entry.key" size="small" :placeholder="props.placeholderKey ?? '名称'" @input="emit('change')" />
          </td>
          <td>
            <el-input v-model="entry.value" size="small" placeholder="值" @input="emit('change')" />
          </td>
          <td>
            <el-button link type="danger" size="small" @click="removeRow(index)">删除</el-button>
          </td>
        </tr>
        <tr v-if="!entries.length">
          <td colspan="4" class="kv-table__empty">暂无条目</td>
        </tr>
      </tbody>
    </table>
    <el-button size="small" text type="primary" @click="addRow">+ 添加</el-button>
  </div>
</template>

<style lang="scss" scoped>
.kv-table {
  table {
    width: 100%;
    border-collapse: collapse;
  }

  th {
    text-align: left;
    font-weight: normal;
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
    padding-bottom: 4px;
  }

  td {
    padding: 2px 6px 2px 0;
  }

  &__col-enable,
  &__col-op {
    width: 52px;
  }

  &__empty {
    color: var(--color-neutral-300);
    font-size: var(--font-size-xs);
    text-align: center;
    padding: 8px 0;
  }
}
</style>
