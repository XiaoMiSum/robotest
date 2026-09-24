<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { AiModelFormState, AiProviderPreset, AiProviderUniqueParam } from '@/types'

const visible = defineModel<boolean>('visible', { required: true })
const form = defineModel<AiModelFormState>('form', { required: true })

const props = defineProps<{
  mode: 'create' | 'edit'
  providers: AiProviderPreset[]
  uniqueParams: AiProviderUniqueParam[]
  modelHints: string[]
  saving: boolean
  testing: boolean
}>()

const emit = defineEmits<{
  (e: 'provider-change', value: string): void
  (e: 'save'): void
  (e: 'test'): void
}>()

const formRef = ref<FormInstance>()

function validateApiKey(
  _rule: unknown,
  value: unknown,
  callback: (error?: Error) => void,
): void {
  const required = props.mode === 'create' || !form.value.apiKeyConfigured
  if (required && (typeof value !== 'string' || !value.trim())) {
    callback(new Error('请输入 API 密钥'))
    return
  }
  callback()
}

const rules: FormRules = {
  name: [{ required: true, whitespace: true, message: '请输入显示名', trigger: [] }],
  provider: [{ required: true, message: '请选择供应商', trigger: [] }],
  baseUrl: [{ required: true, whitespace: true, message: '请输入服务地址', trigger: [] }],
  model: [{ required: true, whitespace: true, message: '请输入模型名', trigger: [] }],
  apiKey: [{ validator: validateApiKey, trigger: [] }],
}

async function validateForm(): Promise<boolean> {
  if (!formRef.value) return false
  try {
    await formRef.value.validate()
    return true
  } catch {
    return false
  }
}

async function handleSave(): Promise<void> {
  if (await validateForm()) emit('save')
}

async function handleTest(): Promise<void> {
  if (await validateForm()) emit('test')
}

function clearValidation(): void {
  formRef.value?.clearValidate()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="props.mode === 'create' ? '新建对话模型' : '编辑对话模型'"
    width="560px"
    @open="clearValidation"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" @submit.prevent>
      <el-form-item label="显示名" prop="name">
        <el-input v-model="form.name" placeholder="如 GPT-4o、DeepSeek-V3" />
      </el-form-item>
      <el-form-item label="供应商" prop="provider">
        <el-select v-model="form.provider" @change="(v: string) => emit('provider-change', v)">
          <el-option v-for="p in providers" :key="p.key" :label="p.name" :value="p.key" />
        </el-select>
      </el-form-item>
      <el-form-item label="服务地址" prop="baseUrl">
        <el-input v-model="form.baseUrl" placeholder="OpenAI 兼容根路径，不含 /chat/completions" />
      </el-form-item>
      <el-form-item label="模型名" prop="model">
        <el-select v-model="form.model" filterable allow-create default-first-option placeholder="选择或输入模型名">
          <el-option v-for="m in modelHints" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="API 密钥" prop="apiKey">
        <el-input
          v-model="form.apiKey"
          type="password"
          show-password
          :placeholder="
            form.apiKeyConfigured
              ? `已配置（末位 ${form.keySuffix ?? '****'}），留空不修改`
              : '请输入密钥'
          "
        />
      </el-form-item>
      <el-form-item v-for="param in uniqueParams" :key="param.key" :label="param.label">
        <el-switch
          v-if="param.type === 'boolean'"
          v-model="form.uniqueValues[param.key] as boolean"
        />
        <el-select
          v-else-if="param.type === 'enum'"
          v-model="form.uniqueValues[param.key] as string"
        >
          <el-option v-for="opt in param.options" :key="opt" :label="opt" :value="opt" />
        </el-select>
        <el-input-number
          v-else-if="param.type === 'number'"
          v-model="form.uniqueValues[param.key] as number"
        />
        <el-input v-else v-model="form.uniqueValues[param.key] as string" />
        <span class="ai-model-dialog__hint">{{ param.description }}</span>
      </el-form-item>
      <el-collapse class="ai-model-dialog__advanced">
        <el-collapse-item title="高级自定义参数（JSON）" name="modelAdvanced">
          <el-input v-model="form.customParams" type="textarea" :rows="4" />
        </el-collapse-item>
      </el-collapse>
    </el-form>
    <template #footer>
      <el-button :loading="testing" @click="handleTest">连通性测试</el-button>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.ai-model-dialog__advanced {
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

.ai-model-dialog__hint {
  width: 100%;
  line-height: 1.5;
  margin-top: 4px;
  color: var(--color-neutral-400);
  font-size: 12px;
}
</style>