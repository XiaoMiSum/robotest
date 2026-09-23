<script setup lang="ts">
import type { AiChatModel } from '@/types'

defineProps<{
  models: AiChatModel[]
  rowTestingId: string | null
}>()

const emit = defineEmits<{
  (e: 'create'): void
  (e: 'edit', row: AiChatModel): void
  (e: 'test', row: AiChatModel): void
  (e: 'set-default', row: AiChatModel): void
  (e: 'toggle-enabled', row: AiChatModel): void
  (e: 'delete', row: AiChatModel): void
}>()

function asModel(row: unknown): AiChatModel {
  return row as AiChatModel
}
</script>

<template>
  <el-card shadow="never" class="ai-model-table">
    <template #header>
      <div class="ai-model-table__header">
        <el-icon class="ai-model-table__icon"><ChatDotRound /></el-icon>
        <span>对话模型</span>
        <el-button class="ai-model-table__extra" size="small" type="primary" @click="emit('create')">
          <el-icon><Plus /></el-icon>新建模型
        </el-button>
      </div>
    </template>
    <el-table :data="models" size="small" class="ai-model-table__body">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="provider" label="供应商" width="120" />
      <el-table-column prop="model" label="模型名" min-width="140" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="默认" width="70" align="center">
        <template #default="{ row }">
          <el-icon v-if="row.isDefault" class="ai-model-table__star"><StarFilled /></el-icon>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="260">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="emit('edit', asModel(row))">编辑</el-button>
          <el-button
            link
            type="primary"
            size="small"
            :loading="rowTestingId === row.id"
            @click="emit('test', asModel(row))"
          >
            测试
          </el-button>
          <el-button
            link
            type="primary"
            size="small"
            :disabled="row.isDefault || !row.enabled"
            @click="emit('set-default', asModel(row))"
          >
            设为默认
          </el-button>
          <el-button
            link
            type="warning"
            size="small"
            :disabled="row.isDefault && row.enabled"
            @click="emit('toggle-enabled', asModel(row))"
          >
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button
            link
            type="danger"
            size="small"
            :disabled="row.isDefault"
            @click="emit('delete', asModel(row))"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="ai-model-table__empty">尚无对话模型，点击右上角新建</span>
      </template>
    </el-table>
  </el-card>
</template>

<style scoped lang="scss">
.ai-model-table {
  margin-bottom: var(--space-lg);
}

.ai-model-table__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.ai-model-table__icon {
  color: var(--color-primary-500);
  font-size: 16px;
}

.ai-model-table__extra {
  margin-left: auto;

  .el-icon {
    margin-right: 4px;
  }
}

.ai-model-table__body :deep(.el-select),
.ai-model-table__body :deep(.el-input-number) {
  width: 100%;
}

.ai-model-table__star {
  color: var(--color-warning);
}

.ai-model-table__empty {
  font-size: 13px;
  color: var(--color-neutral-400);
}
</style>