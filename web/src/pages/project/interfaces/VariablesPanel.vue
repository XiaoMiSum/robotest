<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiInterfaceVariablePayload } from '@/types'
import { fetchInterfaceVariables, updateInterfaceVariables } from '@/services/apiInterface'
import { buildVariablesPayload } from '../interfacesModel'

const props = defineProps<{ interfaceId: string }>()

const rows = ref<ApiInterfaceVariablePayload[]>([])
const loading = ref(false)
const saving = ref(false)

function emptyRow(): ApiInterfaceVariablePayload {
  return { name: '', defaultValue: '', description: '', required: false, sortOrder: 0 }
}

watch(
  () => props.interfaceId,
  async (id) => {
    if (!id) return
    loading.value = true
    try {
      const list = await fetchInterfaceVariables(id)
      rows.value = list.length ? list.map((row) => ({ ...row })) : [emptyRow()]
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '变量加载失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

function addRow() {
  rows.value.push(emptyRow())
}

async function removeRow(index: number) {
  rows.value.splice(index, 1)
}

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    await updateInterfaceVariables(props.interfaceId, buildVariablesPayload(rows.value))
    ElMessage.success('变量已保存（未包含的既有变量将被删除）')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="variables-panel">
    <table class="variables-panel__table">
      <thead>
        <tr>
          <th class="col-required">必填</th>
          <th>名称</th>
          <th>默认值</th>
          <th>描述</th>
          <th class="col-op" />
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="index">
          <td><el-checkbox v-model="row.required" size="small" /></td>
          <td><el-input v-model="row.name" size="small" placeholder="变量名" data-test="variable-name-input" /></td>
          <td><el-input v-model="row.defaultValue" size="small" placeholder="默认值" /></td>
          <td><el-input v-model="row.description" size="small" placeholder="描述" /></td>
          <td class="col-op">
            <el-button link size="small" type="danger" :data-test="'variable-remove-btn'" @click="removeRow(index)">删除</el-button>
          </td>
        </tr>
        <tr v-if="!rows.length">
          <td colspan="5" class="empty">暂无变量</td>
        </tr>
      </tbody>
    </table>

    <div class="variables-panel__actions">
      <el-button size="small" data-test="variable-add-btn" @click="addRow">添加变量</el-button>
      <el-button size="small" type="primary" :loading="saving" data-test="variable-save-btn" @click="save">保存变量</el-button>
    </div>
    <p class="variables-panel__hint">保存为全量覆盖：请求中未包含的既有变量将被删除。</p>
  </div>
</template>

<style scoped lang="scss">
.variables-panel__table {
  width: 100%;
  border-collapse: collapse;

  th,
  td {
    padding: 6px 8px;
    text-align: left;
    font-size: var(--font-size-sm);
    border-bottom: 1px solid var(--color-neutral-200);
  }

  th {
    color: var(--color-neutral-500);
    font-weight: 500;
  }

  .col-required {
    width: 56px;
  }

  .col-op {
    width: 64px;
    text-align: right;
  }

  .empty {
    text-align: center;
    color: var(--color-neutral-400);
  }
}

.variables-panel__actions {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}

.variables-panel__hint {
  margin: var(--space-sm) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}
</style>
