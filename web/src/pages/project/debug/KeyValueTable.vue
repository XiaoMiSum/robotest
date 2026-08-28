<script setup lang="ts">
import { onMounted } from 'vue'
import type { ApiDebugKeyValue } from '@/types'

const entries = defineModel<ApiDebugKeyValue[]>('entries', { required: true })

const props = defineProps<{
  placeholderKey?: string
  /** 渲染描述列（Postman 风格 Params/Headers），提交执行时剥离 */
  showDescription?: boolean
  /** Key 列为可下拉选择的常用名（allow-create 支持自定义），缺省为自由文本输入 */
  suggestions?: readonly string[]
}>()

const emit = defineEmits<{ (e: 'change'): void }>()

function emptyRow(): ApiDebugKeyValue {
  return { key: '', value: '', enabled: true, ...(props.showDescription ? { description: '' } : {}) }
}

function rowFilled(row: ApiDebugKeyValue): boolean {
  return row.key.trim() !== '' || row.value.trim() !== ''
}

/** 保证存在一行可编辑行：清掉多余尾部空行，末行非空时自动追加一行（与 Postman 一致） */
function ensureTrailingRow() {
  const rows = entries.value
  while (rows.length > 1 && !rowFilled(rows[rows.length - 1])) {
    rows.pop()
  }
  const last = rows[rows.length - 1]
  if (!last || rowFilled(last)) {
    rows.push(emptyRow())
  }
}

/** 内容变更：先补空行再通知父级（补行本身不触发 change） */
function notify() {
  ensureTrailingRow()
  emit('change')
}

function removeRow(index: number) {
  entries.value.splice(index, 1)
  notify()
}

onMounted(() => {
  if (!entries.value.length) {
    entries.value.push(emptyRow())
  }
})
</script>

<template>
  <div class="kv-table">
    <table>
      <thead>
        <tr>
          <th>{{ placeholderKey ?? 'Key' }}</th>
          <th>Value</th>
          <th v-if="props.showDescription">Description</th>
          <th class="kv-table__col-enable" />
          <th class="kv-table__col-op" />
        </tr>
      </thead>
      <tbody>
        <tr v-for="(entry, index) in entries" :key="index" class="kv-table__row">
          <td>
            <el-select
              v-if="props.suggestions"
              :model-value="entry.key"
              filterable
              allow-create
              :placeholder="props.placeholderKey ?? 'Key'"
              class="kv-table__key-select"
              @update:model-value="entry.key = $event ?? ''; notify()"
            >
              <el-option v-for="name in props.suggestions" :key="name" :label="name" :value="name" />
            </el-select>
            <el-input
              v-else
              v-model="entry.key"
              :placeholder="props.placeholderKey ?? 'Key'"
              @input="notify()"
            />
          </td>
          <td>
            <el-input v-model="entry.value" placeholder="Value" @input="notify()" />
          </td>
          <td v-if="props.showDescription">
            <el-input v-model="entry.description" placeholder="Description" @input="notify()" />
          </td>
          <td class="kv-table__col-enable">
            <el-checkbox v-model="entry.enabled" @change="notify()" />
          </td>
          <td class="kv-table__col-op">
            <el-button link type="danger" @click="removeRow(index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </td>
        </tr>
      </tbody>
    </table>
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
    font-weight: 500;
    font-size: 11px;
    color: var(--color-neutral-400, #909399);
    text-transform: uppercase;
    padding: 0 6px 8px 0;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
  }

  td {
    padding: 4px 6px 4px 0;
  }

  &__col-enable {
    width: 30px;
    text-align: center;
  }

  &__col-op {
    width: 30px;
    text-align: center;
  }

  &__row {
    transition: background 0.1s;

    &:hover {
      background: var(--color-neutral-50, #fafafa);
    }
  }

  &__key-select {
    width: 100%;

    :deep(.el-input__wrapper) {
      padding: 1px 8px;
    }

    :deep(.el-select__caret) {
      display: none;
    }
  }

  &__empty {
    color: var(--color-neutral-300, #c0c4cc);
    font-size: 12px;
    text-align: center;
    padding: 20px 0 !important;
  }
}
</style>