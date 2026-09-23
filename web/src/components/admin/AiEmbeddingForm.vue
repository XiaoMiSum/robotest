<script setup lang="ts">
import { computed } from 'vue'
import type { AiProviderPreset, AiProviderUniqueParam, AiTask } from '@/types'

export interface AiEmbeddingFormState {
  provider: string
  baseUrl: string
  model: string
  dimension: number | null
  apiKey: string
  apiKeyConfigured: boolean
  keySuffix: string | null
  uniqueValues: Record<string, unknown>
  customParams: string
}

const model = defineModel<AiEmbeddingFormState>({ required: true })
const rebuildDialog = defineModel<boolean>('rebuildDialogVisible', { default: false })

const props = defineProps<{
  providers: AiProviderPreset[]
  uniqueParams: AiProviderUniqueParam[]
  modelHints: string[]
  configured: boolean
  testing: boolean
  saving: boolean
  rebuildTask: AiTask | null
  rebuildRetryable: boolean
}>()

const emit = defineEmits<{
  (e: 'test'): void
  (e: 'save'): void
  (e: 'open-rebuild'): void
  (e: 'retry-rebuild'): void
}>()

// 卡头摘要仅在已配置时展示，未配置不拼残缺串
const headSummary = computed(() => {
  if (!props.configured) return ''
  const providerName =
    props.providers.find((p) => p.key === model.value.provider)?.name ?? model.value.provider
  const dimension = model.value.dimension ? ` · ${model.value.dimension} 维` : ''
  return `${providerName} · ${model.value.model}${dimension}`
})

// 成功态不出徽标（无需再关注），其余状态以语义色+文案提示详情入口
const rebuildBadge = computed(() => {
  const task = props.rebuildTask
  if (!task || task.status === 'success') return null
  const label: Record<string, string> = {
    pending: '排队中',
    running: `重建中 ${task.progress}%`,
    failed: '重建失败',
    cancelled: '重建已取消',
  }
  const failed = task.status === 'failed' || task.status === 'cancelled'
  return { label: label[task.status] ?? task.status, tone: failed ? 'is-failed' : 'is-running' }
})

const taskStatus = computed(() => {
  const map: Record<string, { label: string; tone: string }> = {
    pending: { label: '排队中', tone: 'status--warning' },
    running: { label: '进行中', tone: 'status--warning' },
    failed: { label: '失败', tone: 'status--danger' },
    cancelled: { label: '已取消', tone: 'status--neutral' },
    success: { label: '成功', tone: 'status--success' },
  }
  const status = props.rebuildTask?.status ?? 'pending'
  return map[status] ?? { label: status, tone: 'status--neutral' }
})

const progressStatus = computed<'exception' | 'success' | undefined>(() => {
  const status = props.rebuildTask?.status
  if (status === 'failed') return 'exception'
  if (status === 'success') return 'success'
  return undefined
})

// 重建期间向量不完整，语义检索降级为关键词兜底；成功后恢复
const semanticState = computed(() => {
  if (props.rebuildTask?.status === 'success') {
    return { tone: 'status--success', label: '正常', hint: '向量索引已就绪' }
  }
  return { tone: 'status--warning', label: '降级中', hint: '重建完成前由关键词匹配兜底' }
})
</script>

<template>
  <el-card shadow="never" class="ai-embedding">
    <template #header>
      <div class="ai-embedding__header">
        <el-icon class="ai-embedding__icon"><DataLine /></el-icon>
        <span>Embedding 模型</span>
        <span v-if="headSummary" class="ai-embedding__sub">{{ headSummary }}</span>
        <el-tag
          class="ai-embedding__state"
          :type="configured ? 'success' : 'info'"
          size="small"
          effect="light"
        >
          <span class="ai-embedding__dot" />{{ configured ? '已配置' : '未配置' }}
        </el-tag>
        <span
          v-if="rebuildBadge"
          class="ai-embedding__task"
          :class="rebuildBadge.tone"
          role="button"
          tabindex="0"
          title="点击查看重建任务详情"
          @click.stop.prevent="emit('open-rebuild')"
          @keydown.enter.prevent="emit('open-rebuild')"
        >
          <el-icon v-if="rebuildBadge.tone === 'is-running'" class="is-loading">
            <Loading />
          </el-icon>
          <el-icon v-else><CircleCloseFilled /></el-icon>
          {{ rebuildBadge.label }}
        </span>
      </div>
    </template>
    <div class="ai-embedding__body">
      <el-form-item label="供应商">
        <el-select v-model="model.provider" class="ai-embedding__control">
          <el-option v-for="p in providers" :key="p.key" :label="p.name" :value="p.key" />
        </el-select>
      </el-form-item>
      <el-form-item label="模型名">
        <el-select v-model="model.model" class="ai-embedding__control" filterable allow-create default-first-option>
          <el-option v-for="m in modelHints" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="服务地址">
        <el-input v-model="model.baseUrl" class="ai-embedding__control" />
      </el-form-item>
      <el-form-item label="向量维度">
        <el-input-number v-model="model.dimension" :min="1" :max="2000" class="ai-embedding__control" />
      </el-form-item>
      <el-form-item label="API 密钥" class="ai-embedding__full">
        <el-input
          v-model="model.apiKey"
          type="password"
          show-password
          class="ai-embedding__control"
          :placeholder="
            model.apiKeyConfigured
              ? `已配置（末位 ${model.keySuffix ?? '****'}），留空不修改`
              : '请输入密钥'
          "
        />
      </el-form-item>
      <el-form-item v-for="param in uniqueParams" :key="param.key" :label="param.label">
        <el-input v-model="model.uniqueValues[param.key] as string" class="ai-embedding__control" />
        <span class="ai-embedding__hint">{{ param.description }}</span>
      </el-form-item>
      <el-collapse class="ai-embedding__advanced ai-embedding__full">
        <el-collapse-item title="高级自定义参数（JSON）" name="embeddingAdvanced">
          <el-input v-model="model.customParams" type="textarea" :rows="4" />
        </el-collapse-item>
      </el-collapse>
      <div class="ai-embedding__actions ai-embedding__full">
        <el-button :loading="testing" @click="emit('test')">
          <el-icon><Connection /></el-icon>连通性测试
        </el-button>
        <el-button type="primary" :loading="saving" @click="emit('save')">保存</el-button>
      </div>
    </div>

    <el-dialog v-model="rebuildDialog" title="向量重建任务详情" width="520px">
      <template v-if="rebuildTask">
        <div class="rebuild-row">
          <span class="rebuild-row__k">任务状态</span>
          <span class="rebuild-row__v">
            <span class="status" :class="taskStatus.tone">
              <span class="dot" />{{ taskStatus.label }}
            </span>
          </span>
        </div>
        <div class="rebuild-row">
          <span class="rebuild-row__k">进度</span>
          <span class="rebuild-row__v">
            <el-progress
              class="rebuild-progress"
              :percentage="Math.min(100, Math.max(0, rebuildTask.progress))"
              :status="progressStatus"
            />
          </span>
        </div>
        <div v-if="rebuildTask.errorMessage" class="rebuild-row">
          <span class="rebuild-row__k">失败原因</span>
          <span class="rebuild-row__v rebuild-err">
            <el-icon><CircleCloseFilled /></el-icon>
            {{ rebuildTask.errorMessage }}
          </span>
        </div>
        <div class="rebuild-row">
          <span class="rebuild-row__k">语义检索</span>
          <span class="rebuild-row__v">
            <span class="status" :class="semanticState.tone">
              <span class="dot" />{{ semanticState.label }}
            </span>
            <span class="rebuild-hint">{{ semanticState.hint }}</span>
          </span>
        </div>
        <div class="rebuild-row">
          <span class="rebuild-row__k">触发方式</span>
          <span class="rebuild-row__v">Embedding 模型/维度变更自动触发，逐项目分批重建</span>
        </div>
      </template>
      <template #footer>
        <el-button @click="rebuildDialog = false">关闭</el-button>
        <el-button v-if="rebuildRetryable" type="primary" @click="emit('retry-rebuild')">
          重试
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped lang="scss">
.ai-embedding {
  margin-bottom: var(--space-lg);
}

.ai-embedding__header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.ai-embedding__icon {
  color: var(--color-primary-500);
  font-size: 16px;
}

.ai-embedding__sub {
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: var(--color-neutral-500);
}

.ai-embedding__state {
  margin-left: var(--space-sm);
  font-weight: 500;
}

.ai-embedding__dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  margin-right: 4px;
}

/* 卡头重建徽标：形状+语义色+文案三重编码（同星标口径），蓝仅留给交互 hover */
.ai-embedding__task {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
  font-size: var(--font-size-xs);
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--transition-base),
    background var(--transition-base);

  &.is-failed {
    color: var(--color-danger-strong);
  }

  &.is-running {
    color: var(--color-warning);
  }

  &:hover {
    border-color: var(--color-primary-500);
    background: var(--color-primary-50);
  }

  .el-icon {
    font-size: 13px;
  }
}

.ai-embedding__body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--space-xl);
  padding-top: var(--space-sm);
}

.ai-embedding__control {
  width: 100%;
}

.ai-embedding__full {
  grid-column: 1 / -1;
}

.ai-embedding__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-100);
}

.ai-embedding__advanced {
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  padding: 0 var(--space-sm);

  :deep(.el-collapse-item__header) {
    /* 行高与内边距沿用卡级折叠原值，卡级折叠移除后避免高级参数头视觉跳变 */
    font-size: 13px;
    color: var(--color-neutral-500);
    line-height: 1.5;
    padding: 4px 0;
    border-bottom: none;
    height: 36px;
  }

  :deep(.el-collapse-item__wrap) {
    border-bottom: none;
    background: transparent;
  }
}

.ai-embedding__hint {
  width: 100%;
  line-height: 1.5;
  margin-top: 4px;
  color: var(--color-neutral-400);
  font-size: 12px;
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

.status--warning .dot {
  background: var(--color-warning);
}

.status--danger .dot {
  background: var(--color-danger);
}

.status--neutral .dot {
  background: var(--color-neutral-400);
}

/* 详情弹层行：进度/原因/重试口径收进弹层（位置与交互改卡头入口，已备案） */
.rebuild-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 7px 0;
  font-size: var(--font-size-sm);
}

.rebuild-row__k {
  flex: none;
  width: 72px;
  color: var(--color-neutral-500);
}

.rebuild-row__v {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
  color: var(--color-neutral-800);
}

.rebuild-progress {
  flex: 1;
  min-width: 180px;
}

.rebuild-err {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-danger-strong);

  .el-icon {
    flex-shrink: 0;
  }
}

.rebuild-hint {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}
</style>
