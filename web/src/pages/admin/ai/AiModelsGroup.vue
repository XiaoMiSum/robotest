<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { testAiModel } from '@/services/aiAdmin'
import { useAiAdminStore } from '@/stores/aiAdmin'
import type { AiModel, AiModelCapability, AiModelCreatePayload, AiModelUpdatePayload } from '@/types'

const store = useAiAdminStore()

/** 能力标签（详设 3.3：capabilities ⊆ {chat, vision, embedding}） */
const CAPABILITY_OPTIONS: Array<{ value: AiModelCapability; label: string }> = [
  { value: 'chat', label: '对话' },
  { value: 'vision', label: '视觉' },
  { value: 'embedding', label: '嵌入' },
]

/** 数据由壳层首载拉取，此处仅处理重试与变更（shellReady 门控后挂载） */
const loading = computed(() => store.modelsLoading)

// ---------- 添加 / 编辑弹窗 ----------

const dialogVisible = ref(false)
const editingModel = ref<AiModel | null>(null)
const formRef = ref<FormInstance>()
const formError = ref('')
const saving = ref(false)

const form = reactive({
  name: '',
  provider: '',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  capabilities: [] as AiModelCapability[],
  priority: 100,
  enabled: true,
  inputPrice: null as number | null,
  outputPrice: null as number | null,
})

function isDefault(model: AiModel): boolean {
  return store.settings?.defaultModelId === model.id
}

function resetForm() {
  form.name = ''
  form.provider = ''
  form.baseUrl = ''
  form.apiKey = ''
  form.modelName = ''
  form.capabilities = ['chat']
  form.priority = 100
  form.enabled = true
  form.inputPrice = null
  form.outputPrice = null
}

function openCreate() {
  editingModel.value = null
  resetForm()
  formError.value = ''
  dialogVisible.value = true
}

function openEdit(model: AiModel) {
  editingModel.value = model
  form.name = model.name
  form.provider = model.provider
  form.baseUrl = model.baseUrl
  form.apiKey = '' // 密钥不回显：空 = 不修改
  form.modelName = model.modelName
  form.capabilities = [...model.capabilities]
  form.priority = model.priority
  form.enabled = model.enabled
  form.inputPrice = model.inputPrice
  form.outputPrice = model.outputPrice
  formError.value = ''
  dialogVisible.value = true
}

function validateApiKey(_rule: unknown, value: string, callback: (error?: Error) => void) {
  // 创建必填；编辑时空值表示不替换既有密钥（交互 2.2）
  if (!editingModel.value && !value.trim()) {
    callback(new Error('密钥不能为空'))
    return
  }
  callback()
}

const rules = computed<FormRules>(() => ({
  name: [
    { required: true, message: '请输入配置名称', trigger: 'blur' },
    { max: 50, message: '长度不能超过 50 个字符', trigger: 'blur' },
  ],
  provider: [{ required: true, message: '请输入供应商', trigger: 'blur' }],
  baseUrl: [
    { required: true, message: '请输入端点地址', trigger: 'blur' },
    { max: 500, message: '长度不能超过 500 个字符', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/i, message: '需为 http(s) 合法地址', trigger: 'blur' },
  ],
  apiKey: [
    { required: true, message: '请输入密钥', trigger: 'blur' },
    { validator: validateApiKey, trigger: 'blur' },
  ],
  modelName: [
    { required: true, message: '请输入模型标识', trigger: 'blur' },
    { max: 100, message: '长度不能超过 100 个字符', trigger: 'blur' },
  ],
  capabilities: [
    {
      validator: (_rule: unknown, value: AiModelCapability[], callback: (error?: Error) => void) =>
        value.length > 0 ? callback() : callback(new Error('至少选择一个能力标签')),
      trigger: 'change',
    },
  ],
}))

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  formError.value = ''
  try {
    if (editingModel.value) {
      const payload: AiModelUpdatePayload = {
        name: form.name,
        provider: form.provider,
        baseUrl: form.baseUrl,
        modelName: form.modelName,
        capabilities: form.capabilities,
        priority: form.priority,
        enabled: form.enabled,
        inputPrice: form.inputPrice ?? undefined,
        outputPrice: form.outputPrice ?? undefined,
      }
      // 空密钥不入载荷：后端非 null 即替换，传空会覆盖既有密钥
      if (form.apiKey.trim()) payload.apiKey = form.apiKey.trim()
      await store.updateModel(editingModel.value.id, payload)
    } else {
      const payload: AiModelCreatePayload = {
        name: form.name,
        provider: form.provider,
        baseUrl: form.baseUrl,
        apiKey: form.apiKey.trim(),
        modelName: form.modelName,
        capabilities: form.capabilities,
        priority: form.priority,
        enabled: form.enabled,
        inputPrice: form.inputPrice ?? undefined,
        outputPrice: form.outputPrice ?? undefined,
      }
      await store.createModel(payload)
    }
    ElMessage.success(editingModel.value ? '模型已更新' : '模型已添加')
    dialogVisible.value = false
  } catch (error) {
    // 保存失败内联展示并保留输入值，不关闭弹窗（交互 2.2）
    formError.value = error instanceof Error && error.message ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

// ---------- 启停与连通性测试 ----------

async function toggleEnabled(model: AiModel, next: boolean) {
  if (!next && isDefault(model)) {
    ElMessage.warning('该模型为默认模型，请先切换默认再停用')
    return
  }
  try {
    await store.updateModel(model.id, { enabled: next })
    ElMessage.success(next ? '模型已启用' : '模型已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '操作失败')
  }
}

const testingId = ref<string | null>(null)

async function runTest(model: AiModel) {
  testingId.value = model.id
  try {
    const result = await testAiModel(model.id)
    ElMessage.success(`连接成功（${result.latencyMs}ms）`)
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '连接测试失败')
  } finally {
    testingId.value = null
    // 无论成败后端均回写 lastTest（交互 2.4），刷新列表同步连通性列
    void store.loadModels()
  }
}
</script>

<template>
  <div class="model-group">
    <div class="model-group__toolbar">
      <el-button type="primary" @click="openCreate">添加模型</el-button>
    </div>

    <!-- 列表错误（UI-PAGE-11：页面捕获 + 重试） -->
    <div v-if="store.modelsError" class="model-group__error">
      <span>{{ store.modelsError }}</span>
      <el-button size="small" @click="store.loadModels()">重试</el-button>
    </div>

    <!-- 空态：引导添加第一个模型（交互 2.2） -->
    <el-empty
      v-else-if="!loading && store.models.length === 0"
      description="还没有模型配置，添加第一个模型"
    >
      <el-button type="primary" @click="openCreate">添加第一个模型</el-button>
    </el-empty>

    <el-table v-else v-loading="loading" :data="store.models" class="model-group__table">
      <el-table-column label="名称" prop="name" min-width="120" show-overflow-tooltip />
      <el-table-column label="供应商" prop="provider" width="100" show-overflow-tooltip />
      <el-table-column label="BaseURL" prop="baseUrl" min-width="160" show-overflow-tooltip />
      <el-table-column label="模型标识" prop="modelName" min-width="130" show-overflow-tooltip />
      <el-table-column label="默认标记" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="isDefault(row as AiModel)" size="small" type="primary">默认</el-tag>
          <span v-else class="model-group__muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="76" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :disabled="isDefault(row as AiModel) && row.enabled"
            @change="(next: boolean | string | number) => toggleEnabled(row as AiModel, next === true)"
          />
        </template>
      </el-table-column>
      <el-table-column label="连通性" min-width="140">
        <template #default="{ row }">
          <span v-if="!row.lastTest" class="model-group__test model-group__test--unknown">
            未知
          </span>
          <span
            v-else-if="row.lastTest.success"
            class="model-group__test model-group__test--ok"
            :title="row.lastTest.msg ?? ''"
          >
            ✔ {{ row.lastTest.latencyMs }}ms
          </span>
          <span
            v-else
            class="model-group__test model-group__test--fail"
            :title="row.lastTest.msg ?? ''"
          >
            ✘ {{ row.lastTest.msg }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="176" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row as AiModel)">编辑</el-button>
          <el-button
            link
            type="primary"
            size="small"
            :loading="testingId === row.id"
            @click="runTest(row as AiModel)"
          >
            测试
          </el-button>
          <el-button
            v-if="row.enabled"
            link
            type="danger"
            size="small"
            :disabled="isDefault(row as AiModel)"
            @click="toggleEnabled(row as AiModel, false)"
          >
            停用
          </el-button>
          <el-button
            v-else
            link
            type="primary"
            size="small"
            @click="toggleEnabled(row as AiModel, true)"
          >
            启用
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingModel ? '编辑模型' : '添加模型'"
      width="560px"
      destroy-on-close
    >
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="model-group__dialog-error" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="form.name" placeholder="如：gpt-x 主力" maxlength="50" />
        </el-form-item>
        <el-form-item label="供应商" prop="provider">
          <el-input v-model="form.provider" placeholder="自由文本，如 openai / azure / deepseek" />
        </el-form-item>
        <el-form-item label="端点地址" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.example.com/v1" maxlength="500" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="
              editingModel?.keyConfigured ? '已配置（输入新值以替换）' : 'sk-…'
            "
            maxlength="500"
          />
        </el-form-item>
        <el-form-item label="模型标识" prop="modelName">
          <el-input v-model="form.modelName" placeholder="上游模型 id，如 gpt-4o-mini" maxlength="100" />
        </el-form-item>
        <el-form-item label="能力标签" prop="capabilities">
          <el-checkbox-group v-model="form.capabilities">
            <el-checkbox v-for="cap in CAPABILITY_OPTIONS" :key="cap.value" :value="cap.value">
              {{ cap.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="form.priority" :min="0" :precision="0" :step="10" />
          <span class="model-group__field-hint">数值越大越优先选用</span>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch
            v-model="form.enabled"
            :disabled="editingModel !== null && isDefault(editingModel)"
          />
          <span
            v-if="editingModel !== null && isDefault(editingModel)"
            class="model-group__field-hint"
          >
            默认模型不可停用
          </span>
        </el-form-item>
        <el-form-item label="输入单价" prop="inputPrice">
          <el-input-number v-model="form.inputPrice" :min="0" :precision="4" :step="0.1" />
          <span class="model-group__field-hint">元 / 百万 token，未配置按 0 计费</span>
        </el-form-item>
        <el-form-item label="输出单价" prop="outputPrice">
          <el-input-number v-model="form.outputPrice" :min="0" :precision="4" :step="0.1" />
          <span class="model-group__field-hint">元 / 百万 token</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.model-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-height: 0;
}

.model-group__toolbar {
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

.model-group__error {
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

.model-group__muted {
  color: var(--color-neutral-400);
}

.model-group__test {
  font-size: var(--font-size-sm);

  &--unknown {
    color: var(--color-neutral-400);
  }

  &--ok {
    color: var(--color-success);
  }

  &--fail {
    display: inline-block;
    max-width: 180px;
    color: var(--color-danger);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    vertical-align: bottom;
  }
}

.model-group__dialog-error {
  margin-bottom: var(--space-md);
}

.model-group__field-hint {
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}
</style>
