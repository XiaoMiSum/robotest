<script setup lang="ts">
import type { AiProviderPreset, AiProviderUniqueParam } from '@/types'

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

defineProps<{
  open: string[]
  providers: AiProviderPreset[]
  uniqueParams: AiProviderUniqueParam[]
  modelHints: string[]
  configured: boolean
  testing: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: string[]): void
  (e: 'test'): void
  (e: 'save'): void
}>()
</script>

<template>
  <el-card shadow="never" class="ai-embedding">
    <el-collapse :model-value="open" @update:model-value="(v: string[]) => emit('update:open', v)">
      <el-collapse-item name="embedding">
        <template #title>
          <div class="ai-embedding__header">
            <el-icon class="ai-embedding__icon"><DataLine /></el-icon>
            <span>Embedding 模型</span>
            <el-tag
              class="ai-embedding__state"
              :type="configured ? 'success' : 'info'"
              size="small"
              effect="light"
            >
              {{ configured ? '已配置' : '未配置' }}
            </el-tag>
          </div>
        </template>
        <div class="ai-embedding__body">
          <el-form-item label="供应商" label-position="top">
            <el-select v-model="model.provider" class="ai-embedding__control">
              <el-option v-for="p in providers" :key="p.key" :label="p.name" :value="p.key" />
            </el-select>
          </el-form-item>
          <el-form-item label="模型名" label-position="top">
            <el-select v-model="model.model" class="ai-embedding__control" filterable allow-create default-first-option>
              <el-option v-for="m in modelHints" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="服务地址" label-position="top">
            <el-input v-model="model.baseUrl" class="ai-embedding__control" />
          </el-form-item>
          <el-form-item label="向量维度" label-position="top">
            <el-input-number v-model="model.dimension" :min="1" :max="2000" class="ai-embedding__control" />
          </el-form-item>
          <el-form-item label="API 密钥" label-position="top" class="ai-embedding__full">
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
          <el-form-item v-for="param in uniqueParams" :key="param.key" :label="param.label" label-position="top">
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
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<style scoped lang="scss">
.ai-embedding {
  margin-bottom: var(--space-lg);

  :deep(.el-collapse-item__header) {
    height: auto;
    line-height: 1.5;
    padding: 4px 0;
    border-bottom: 1px solid var(--color-neutral-100);
  }

  :deep(.el-collapse-item__wrap) {
    border-bottom: none;
  }
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

.ai-embedding__state {
  margin-left: var(--space-sm);
  font-weight: 500;
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
    font-size: 13px;
    color: var(--color-neutral-500);
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
</style>