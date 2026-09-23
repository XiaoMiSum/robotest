<script setup lang="ts">
import { computed } from 'vue'
import type { AiChatModel } from '@/types'

const props = defineProps<{
  models: AiChatModel[]
  rowTestingId: string | null
  providerLabel: (provider: string) => string
}>()

const emit = defineEmits<{
  (e: 'create'): void
  (e: 'edit', row: AiChatModel): void
  (e: 'test', row: AiChatModel): void
  (e: 'set-default', row: AiChatModel): void
  (e: 'toggle-enabled', row: AiChatModel): void
  (e: 'delete', row: AiChatModel): void
}>()

const enabledCount = computed(() => props.models.filter((m) => m.enabled).length)

function asModel(row: unknown): AiChatModel {
  return row as AiChatModel
}

// 禁用原因须与 disabled 条件同源，否则启用行也会挂出「不可」提示
function setDefaultTitle(row: unknown): string | undefined {
  const model = asModel(row)
  if (model.isDefault) return '已是默认模型'
  if (!model.enabled) return '停用模型不可设为默认'
  return undefined
}
</script>

<template>
  <el-card shadow="never" class="ai-model-table">
    <template #header>
      <div class="ai-model-table__header">
        <span class="ai-model-table__title-row">
          <el-icon class="ai-model-table__icon"><ChatDotRound /></el-icon>
          <span class="ai-model-table__title">
            对话模型
            <span class="ai-model-table__subtitle">{{ models.length }} 个 · {{ enabledCount }} 已启用</span>
          </span>
        </span>
        <el-button class="ai-model-table__extra" size="small" type="primary" @click="emit('create')">
          <el-icon><Plus /></el-icon>新建模型
        </el-button>
      </div>
    </template>
    <el-table :data="models" size="small" class="ai-model-table__body">
      <el-table-column label="名称" min-width="140">
        <template #default="{ row }">
          <span class="cell-main">{{ row.name }}</span>
          <span class="cell-sub mono">{{ row.model }}</span>
        </template>
      </el-table-column>
      <el-table-column label="供应商" width="190">
        <template #default="{ row }">
          <span class="cell-main">{{ providerLabel(row.provider) }}</span>
          <span class="cell-sub">{{ row.provider }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <span class="status" :class="row.enabled ? 'status--success' : 'status--neutral'">
            <span class="dot" />{{ row.enabled ? '启用' : '停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="默认" width="56" align="center">
        <template #default="{ row }">
          <el-icon v-if="row.isDefault" class="ai-model-table__star" title="默认模型">
            <StarFilled />
          </el-icon>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="240">
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
            :title="setDefaultTitle(row)"
            @click="emit('set-default', asModel(row))"
          >
            设为默认
          </el-button>
          <el-button
            link
            type="warning"
            size="small"
            :disabled="row.isDefault && row.enabled"
            :title="row.isDefault && row.enabled ? '默认模型不可停用，需先转移默认' : undefined"
            @click="emit('toggle-enabled', asModel(row))"
          >
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button
            link
            type="danger"
            size="small"
            :disabled="row.isDefault"
            :title="row.isDefault ? '默认模型不可删除' : undefined"
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
.ai-model-table__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.ai-model-table__title-row {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
}

.ai-model-table__icon {
  color: var(--color-primary-500);
  font-size: 16px;
}

.ai-model-table__title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-neutral-900);
}

.ai-model-table__subtitle {
  margin-left: 8px;
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: var(--color-neutral-500);
}

.ai-model-table__extra {
  margin-left: auto;

  .el-icon {
    margin-right: 4px;
  }
}

/* 单元格两行结构（主字 + 次字），对齐 demo cell-main/cell-sub 口径 */
.cell-main {
  color: var(--color-neutral-900);
  font-weight: 500;
}

.cell-sub {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  margin-top: 2px;
  font-weight: 400;
}

.mono {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
}

/* 状态点标：点色 + 文案双通道，不依赖单一颜色感知 */
.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
}

.status .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status--success .dot {
  background: var(--color-success);
}

.status--neutral .dot {
  background: var(--color-neutral-400);
}

.ai-model-table__star {
  color: var(--color-warning);
}

.ai-model-table__empty {
  font-size: 13px;
  color: var(--color-neutral-400);
}
</style>
