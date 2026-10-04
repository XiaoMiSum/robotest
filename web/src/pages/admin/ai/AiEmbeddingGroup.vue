<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAiAdminStore } from '@/stores/aiAdmin'
import type { AiEmbeddingSavePayload } from '@/types'

const store = useAiAdminStore()

const formRef = ref<FormInstance>()
const saving = ref(false)
const saveError = ref('')

/** 算子取值（详设 2.4：cosine / l2 / inner_product，默认 cosine） */
const OPERATOR_OPTIONS = [
  { value: 'cosine', label: 'cosine（余弦）' },
  { value: 'l2', label: 'l2（欧氏）' },
  { value: 'inner_product', label: 'inner_product（内积）' },
]

const form = reactive({
  provider: '',
  baseUrl: '',
  apiKey: '',
  embeddingModel: '',
  dimensions: null as number | null,
  operator: 'cosine',
  enabled: false,
})

/** 维度 / 算子变更或重建进行中：维度输入只读（交互 2.1.3） */
const dimensionsReadonly = computed(
  () => store.embedding?.requiresReindex === true || store.reindexing,
)

/** 离开拦截的原值基准（fillForm 时刷新；保存成功后随 fillForm 前移） */
const originalSnapshot = ref('')

function fillForm() {
  const embedding = store.embedding
  if (!embedding) return
  form.provider = embedding.provider ?? ''
  form.baseUrl = embedding.baseUrl ?? ''
  form.apiKey = '' // 密钥不回显：空 = 不修改
  form.embeddingModel = embedding.embeddingModel ?? ''
  form.dimensions = embedding.dimensions
  form.operator = embedding.operator || 'cosine'
  form.enabled = embedding.enabled
  originalSnapshot.value = snapshot()
}

function snapshot(): string {
  return JSON.stringify({
    provider: form.provider,
    baseUrl: form.baseUrl,
    apiKey: form.apiKey,
    embeddingModel: form.embeddingModel,
    dimensions: form.dimensions,
    operator: form.operator,
    enabled: form.enabled,
  })
}

function validateApiKey(_rule: unknown, value: string, callback: (error?: Error) => void) {
  // 首建时密钥必填（后端「首次保存需完整配置」口径），已配置则空 = 不替换
  if (!store.embedding?.keyConfigured && !value.trim()) {
    callback(new Error('首次保存需填写密钥'))
    return
  }
  callback()
}

const rules = computed<FormRules>(() => ({
  provider: [{ required: true, message: '请输入服务商', trigger: 'blur' }],
  baseUrl: [
    { required: true, message: '请输入端点地址', trigger: 'blur' },
    { max: 500, message: '长度不能超过 500 个字符', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/i, message: '需为 http(s) 合法地址', trigger: 'blur' },
  ],
  apiKey: [{ validator: validateApiKey, trigger: 'blur' }],
  embeddingModel: [
    { required: true, message: '请输入嵌入模型标识', trigger: 'blur' },
    { max: 100, message: '长度不能超过 100 个字符', trigger: 'blur' },
  ],
  dimensions: [{ required: true, message: '请输入向量维度', trigger: 'change' }],
}))

/** 保存并回写原值基准；返回是否成功（离开拦截「保存后离开」据此决定去留） */
async function submit(): Promise<boolean> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return false
  const embedding = store.embedding
  if (!embedding) return false

  // 维度 / 算子相对原值变化：危险确认后配置才生效（交互 2.3）
  const changed =
    (form.dimensions !== null && form.dimensions !== embedding.dimensions) ||
    form.operator !== embedding.operator
  if (changed) {
    try {
      await ElMessageBox.confirm('维度变更将使既有向量失效，需全量重建后方可继续检索', '危险确认', {
        type: 'warning',
        confirmButtonText: '继续保存',
        confirmButtonClass: 'el-button--danger',
      })
    } catch {
      return false
    }
  }

  saving.value = true
  saveError.value = ''
  try {
    const payload: AiEmbeddingSavePayload = {
      provider: form.provider,
      baseUrl: form.baseUrl,
      embeddingModel: form.embeddingModel,
      dimensions: form.dimensions ?? undefined,
      operator: form.operator,
      enabled: form.enabled,
    }
    if (form.apiKey.trim()) payload.apiKey = form.apiKey.trim()
    await store.saveEmbedding(payload)
    ElMessage.success('向量配置已保存')
    fillForm() // 以保存结果刷新原值基准，避免下次误判「变更」
    return true
  } catch (error) {
    saveError.value = error instanceof Error && error.message ? error.message : '保存失败'
    return false
  } finally {
    saving.value = false
  }
}

// 切换分组存在未保存编辑：离开前拦截（交互 2.6：保存后离开 / 放弃修改 / 取消）
onBeforeRouteLeave(async () => {
  if (snapshot() === originalSnapshot.value) return true
  try {
    await ElMessageBox.confirm('当前修改尚未保存，如何处理？', '离开分组', {
      type: 'warning',
      confirmButtonText: '保存后离开',
      cancelButtonText: '放弃修改',
      distinguishCancelAndClose: true,
      closeOnClickModal: false,
    })
    return await submit() // 保存失败则留在当前分组修正
  } catch (error) {
    // 关闭（X）= 取消离开；取消按钮 = 放弃修改
    return error !== 'close'
  }
})

async function handleRetry() {
  await store.loadEmbedding()
  fillForm()
}

onMounted(() => {
  fillForm()
})
</script>

<template>
  <div class="embedding-group">
    <!-- 加载失败（UI-PAGE-11：页面捕获 + 重试） -->
    <div v-if="store.embeddingError || !store.embedding" class="embedding-group__error">
      <span>{{ store.embeddingError || '向量 API 配置加载失败' }}</span>
      <el-button size="small" @click="handleRetry">重试</el-button>
    </div>

    <el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="112px"
      class="embedding-group__form"
      @submit.prevent
    >
      <el-alert
        v-if="saveError"
        :title="saveError"
        type="error"
        :closable="false"
        class="embedding-group__error-inline"
      />

      <el-form-item label="服务商" prop="provider">
        <el-input v-model="form.provider" placeholder="如 openai / azure" maxlength="30" />
      </el-form-item>
      <el-form-item label="端点地址" prop="baseUrl">
        <el-input
          v-model="form.baseUrl"
          placeholder="https://api.example.com/v1"
          maxlength="500"
        />
      </el-form-item>
      <el-form-item label="API Key" prop="apiKey">
        <el-input
          v-model="form.apiKey"
          type="password"
          show-password
          :placeholder="store.embedding.keyConfigured ? '已配置（输入新值以替换）' : 'sk-…'"
          maxlength="500"
        />
      </el-form-item>
      <el-form-item label="嵌入模型" prop="embeddingModel">
        <el-input
          v-model="form.embeddingModel"
          placeholder="如 text-embedding-3-small"
          maxlength="100"
        />
      </el-form-item>
      <el-form-item label="向量维度" prop="dimensions">
        <el-input-number
          v-model="form.dimensions"
          :min="64"
          :max="4096"
          :precision="0"
          :step="128"
          :disabled="dimensionsReadonly"
        />
        <span v-if="dimensionsReadonly" class="embedding-group__hint">
          需全量重建完成前维度不可变更
        </span>
      </el-form-item>
      <el-form-item label="距离算子" prop="operator">
        <el-select v-model="form.operator" class="embedding-group__operator">
          <el-option
            v-for="option in OPERATOR_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="启用" prop="enabled">
        <el-switch v-model="form.enabled" />
        <span class="embedding-group__hint">未启用则 RAG 与相似检测不可用</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.embedding-group {
  min-height: 0;
}

.embedding-group__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-danger);
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-md);
}

.embedding-group__form {
  max-width: 640px;
}

.embedding-group__error-inline {
  margin-bottom: var(--space-md);
}

.embedding-group__operator {
  width: 240px;
}

.embedding-group__hint {
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}
</style>
