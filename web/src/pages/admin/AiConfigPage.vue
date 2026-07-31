<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchAiConfig,
  fetchAiProviders,
  fetchAiRebuildTask,
  fetchAiStatistics,
  retryAiRebuildTask,
  saveAiConfig,
  testAiConnectivity,
} from '@/services/admin'
import type {
  AiConfig,
  AiConfigSavePayload,
  AiProviderPreset,
  AiStatistics,
  AiTask,
} from '@/types'
import {
  buildDefaultUniqueParams,
  getByPath,
  mergeExtraParams,
  resolveDefaultBaseUrl,
  resolveModelHints,
  resolveUniqueParams,
} from './aiConfigForm'

type Scope = 'chat' | 'embedding'

const loading = ref(false)
const saving = ref(false)
const activeTab = ref('config')
const providers = ref<AiProviderPreset[]>([])
const config = ref<AiConfig | null>(null)
const expectedUpdatedAt = ref<string | null>(null)

// 两组表单模型
const form = reactive({
  enabled: false,
  chat: {
    provider: 'custom',
    baseUrl: '',
    model: '',
    apiKey: '',
    apiKeyConfigured: false,
    keySuffix: '' as string | null,
    uniqueValues: {} as Record<string, unknown>,
    customParams: '{}',
  },
  embedding: {
    enabled: false,
    provider: '',
    baseUrl: '',
    model: '',
    dimension: null as number | null,
    apiKey: '',
    apiKeyConfigured: false,
    keySuffix: '' as string | null,
    uniqueValues: {} as Record<string, unknown>,
    customParams: '{}',
  },
})

// 系统配置项（settings）以 JSON 文本编辑
const settingsText = ref('{}')

// 连通性测试状态
const testing = reactive({ chat: false, embedding: false })

// 向量重建任务
const rebuildTask = ref<AiTask | null>(null)

// 调用统计
const statistics = ref<AiStatistics | null>(null)
const statQuery = reactive({ groupBy: 'functionType' })

const chatProviderOptions = computed(() =>
  providers.value.filter((p) => p.scopes.includes('chat')),
)
const embeddingProviderOptions = computed(() =>
  providers.value.filter((p) => p.scopes.includes('embedding')),
)

function presetOf(key: string): AiProviderPreset | undefined {
  return providers.value.find((p) => p.key === key)
}

const chatUniqueParams = computed(() => resolveUniqueParams(presetOf(form.chat.provider), 'chat'))
const chatModelHints = computed(() => resolveModelHints(presetOf(form.chat.provider), 'chat'))
const embeddingUniqueParams = computed(() =>
  resolveUniqueParams(presetOf(form.embedding.provider), 'embedding'),
)
const embeddingModelHints = computed(() =>
  resolveModelHints(presetOf(form.embedding.provider), 'embedding'),
)

const rebuildRetryable = computed(
  () => rebuildTask.value?.status === 'failed' || rebuildTask.value?.status === 'cancelled',
)

async function loadAll() {
  loading.value = true
  try {
    providers.value = await fetchAiProviders()
    const loaded = await fetchAiConfig()
    if (loaded) applyConfig(loaded)
    await loadRebuildTask()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载 AI 配置失败')
  } finally {
    loading.value = false
  }
}

function applyConfig(loaded: AiConfig) {
  config.value = loaded
  expectedUpdatedAt.value = loaded.updatedAt
  form.enabled = loaded.enabled
  form.chat.provider = loaded.chat.provider
  form.chat.baseUrl = loaded.chat.baseUrl
  form.chat.model = loaded.chat.model
  form.chat.apiKey = ''
  form.chat.apiKeyConfigured = loaded.chat.apiKey.configured
  form.chat.keySuffix = loaded.chat.apiKey.keySuffix
  form.chat.uniqueValues = extractUniqueValues(loaded.chat.extraParams, 'chat', loaded.chat.provider)
  form.chat.customParams = JSON.stringify(loaded.chat.extraParams ?? {}, null, 2)

  if (loaded.embedding) {
    form.embedding.enabled = true
    form.embedding.provider = loaded.embedding.provider
    form.embedding.baseUrl = loaded.embedding.baseUrl
    form.embedding.model = loaded.embedding.model
    form.embedding.dimension = loaded.embedding.dimension
    form.embedding.apiKey = ''
    form.embedding.apiKeyConfigured = loaded.embedding.apiKey.configured
    form.embedding.keySuffix = loaded.embedding.apiKey.keySuffix
    form.embedding.uniqueValues = extractUniqueValues(
      loaded.embedding.extraParams,
      'embedding',
      loaded.embedding.provider,
    )
    form.embedding.customParams = JSON.stringify(loaded.embedding.extraParams ?? {}, null, 2)
  }
  settingsText.value = JSON.stringify(loaded.settings ?? {}, null, 2)
}

// 从已存 extraParams 回填独有配置项控件值（按模板键路径寻址）
function extractUniqueValues(
  extraParams: Record<string, unknown>,
  scope: Scope,
  provider: string,
): Record<string, unknown> {
  const values: Record<string, unknown> = {}
  for (const param of resolveUniqueParams(presetOf(provider), scope)) {
    const value = param.key.includes('.')
      ? getByPath(extraParams ?? {}, param.key)
      : (extraParams ?? {})[param.key]
    values[param.key] = value !== undefined ? value : param.defaultValue
  }
  return values
}

async function loadRebuildTask() {
  try {
    rebuildTask.value = await fetchAiRebuildTask()
  } catch {
    rebuildTask.value = null
  }
}

async function handleChatProviderChange(next: string) {
  if (form.chat.baseUrl || Object.keys(form.chat.uniqueValues).length) {
    try {
      await ElMessageBox.confirm(
        '切换供应商将重置该组服务地址与独有配置项（模型名与密钥保留待核对），是否继续？',
        '切换供应商',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  form.chat.baseUrl = resolveDefaultBaseUrl(presetOf(next), 'chat')
  form.chat.uniqueValues = buildDefaultUniqueParams(resolveUniqueParams(presetOf(next), 'chat'))
}

async function handleEmbeddingProviderChange(next: string) {
  if (form.embedding.baseUrl || Object.keys(form.embedding.uniqueValues).length) {
    try {
      await ElMessageBox.confirm(
        '切换供应商将重置该组服务地址与独有配置项（模型名与密钥保留待核对），是否继续？',
        '切换供应商',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  form.embedding.baseUrl = resolveDefaultBaseUrl(presetOf(next), 'embedding')
  form.embedding.uniqueValues = buildDefaultUniqueParams(
    resolveUniqueParams(presetOf(next), 'embedding'),
  )
}

function parseJsonObject(text: string, label: string): Record<string, unknown> {
  if (!text.trim()) return {}
  const parsed: unknown = JSON.parse(text)
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error(`${label}必须为 JSON 对象`)
  }
  return parsed as Record<string, unknown>
}

async function handleSave() {
  let chatCustom: Record<string, unknown>
  let embeddingCustom: Record<string, unknown>
  let settings: Record<string, unknown>
  try {
    chatCustom = parseJsonObject(form.chat.customParams, '对话高级参数')
    embeddingCustom = parseJsonObject(form.embedding.customParams, 'Embedding 高级参数')
    settings = parseJsonObject(settingsText.value, '系统配置项')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '参数格式错误')
    return
  }

  const payload: AiConfigSavePayload = {
    enabled: form.enabled,
    chat: {
      provider: form.chat.provider,
      baseUrl: form.chat.baseUrl,
      model: form.chat.model,
      apiKey: form.chat.apiKey || null,
      extraParams: mergeExtraParams(form.chat.uniqueValues, chatCustom),
    },
    embedding: form.embedding.enabled
      ? {
          provider: form.embedding.provider,
          baseUrl: form.embedding.baseUrl,
          model: form.embedding.model,
          dimension: form.embedding.dimension,
          apiKey: form.embedding.apiKey || null,
          extraParams: mergeExtraParams(form.embedding.uniqueValues, embeddingCustom),
        }
      : null,
    settings,
    expectedUpdatedAt: expectedUpdatedAt.value,
  }

  saving.value = true
  try {
    const saved = await saveAiConfig(payload)
    applyConfig(saved)
    ElMessage.success('保存成功')
    await loadRebuildTask()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleTest(target: Scope) {
  testing[target] = true
  try {
    const group = target === 'chat' ? form.chat : form.embedding
    const result = await testAiConnectivity({
      target,
      chat:
        target === 'chat'
          ? {
              provider: form.chat.provider,
              baseUrl: form.chat.baseUrl,
              model: form.chat.model,
              apiKey: form.chat.apiKey || null,
            }
          : undefined,
      embedding:
        target === 'embedding'
          ? {
              provider: form.embedding.provider,
              baseUrl: form.embedding.baseUrl,
              model: form.embedding.model,
              dimension: form.embedding.dimension,
              apiKey: form.embedding.apiKey || null,
            }
          : undefined,
    })
    void group
    if (result.ok) {
      ElMessage.success(`连通成功（${result.latencyMs ?? '-'}ms）：${result.detail ?? ''}`)
    } else {
      ElMessage.warning(`连通失败：${result.detail ?? '未知错误'}`)
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '连通性测试失败')
  } finally {
    testing[target] = false
  }
}

async function handleRetryRebuild() {
  try {
    await retryAiRebuildTask()
    ElMessage.success('已重新入队向量重建任务')
    await loadRebuildTask()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重试失败')
  }
}

async function loadStatistics() {
  try {
    statistics.value = await fetchAiStatistics({ groupBy: statQuery.groupBy })
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载统计失败')
  }
}

function handleTabChange(tab: string | number) {
  if (tab === 'statistics' && !statistics.value) loadStatistics()
}

onMounted(loadAll)
</script>

<template>
  <div class="ai-config-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="AI 配置" name="config">
        <el-form v-loading="loading" label-width="120px" class="ai-config-form">
          <el-form-item label="AI 能力总开关">
            <el-switch v-model="form.enabled" />
            <span class="ai-config-page__hint">关闭后前端隐藏全部 AI 入口，进行中任务被取消</span>
          </el-form-item>

          <el-divider content-position="left">对话模型</el-divider>
          <el-form-item label="供应商">
            <el-select v-model="form.chat.provider" @change="handleChatProviderChange">
              <el-option
                v-for="p in chatProviderOptions"
                :key="p.key"
                :label="p.name"
                :value="p.key"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="服务地址">
            <el-input v-model="form.chat.baseUrl" placeholder="OpenAI 兼容根路径，不含 /chat/completions" />
          </el-form-item>
          <el-form-item label="模型名">
            <el-select
              v-model="form.chat.model"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入模型名"
            >
              <el-option v-for="m in chatModelHints" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="API 密钥">
            <el-input
              v-model="form.chat.apiKey"
              type="password"
              show-password
              :placeholder="
                form.chat.apiKeyConfigured
                  ? `已配置（末位 ${form.chat.keySuffix ?? '****'}），留空不修改`
                  : '请输入密钥'
              "
            />
          </el-form-item>
          <el-form-item
            v-for="param in chatUniqueParams"
            :key="param.key"
            :label="param.label"
          >
            <el-switch
              v-if="param.type === 'boolean'"
              v-model="form.chat.uniqueValues[param.key] as boolean"
            />
            <el-select
              v-else-if="param.type === 'enum'"
              v-model="form.chat.uniqueValues[param.key] as string"
            >
              <el-option v-for="opt in param.options" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <el-input-number
              v-else-if="param.type === 'number'"
              v-model="form.chat.uniqueValues[param.key] as number"
            />
            <el-input v-else v-model="form.chat.uniqueValues[param.key] as string" />
            <span class="ai-config-page__hint">{{ param.description }}</span>
          </el-form-item>
          <el-collapse>
            <el-collapse-item title="高级自定义参数（JSON）" name="chatAdvanced">
              <el-input v-model="form.chat.customParams" type="textarea" :rows="4" />
            </el-collapse-item>
          </el-collapse>
          <el-form-item>
            <el-button :loading="testing.chat" @click="handleTest('chat')">连通性测试</el-button>
          </el-form-item>

          <el-divider content-position="left">
            Embedding 模型
            <el-switch v-model="form.embedding.enabled" class="ai-config-page__group-switch" />
          </el-divider>
          <template v-if="form.embedding.enabled">
            <el-form-item label="供应商">
              <el-select v-model="form.embedding.provider" @change="handleEmbeddingProviderChange">
                <el-option
                  v-for="p in embeddingProviderOptions"
                  :key="p.key"
                  :label="p.name"
                  :value="p.key"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="服务地址">
              <el-input v-model="form.embedding.baseUrl" />
            </el-form-item>
            <el-form-item label="模型名">
              <el-select
                v-model="form.embedding.model"
                filterable
                allow-create
                default-first-option
              >
                <el-option v-for="m in embeddingModelHints" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item label="向量维度">
              <el-input-number v-model="form.embedding.dimension" :min="1" :max="2000" />
            </el-form-item>
            <el-form-item label="API 密钥">
              <el-input
                v-model="form.embedding.apiKey"
                type="password"
                show-password
                :placeholder="
                  form.embedding.apiKeyConfigured
                    ? `已配置（末位 ${form.embedding.keySuffix ?? '****'}），留空不修改`
                    : '请输入密钥'
                "
              />
            </el-form-item>
            <el-form-item v-for="param in embeddingUniqueParams" :key="param.key" :label="param.label">
              <el-input v-model="form.embedding.uniqueValues[param.key] as string" />
              <span class="ai-config-page__hint">{{ param.description }}</span>
            </el-form-item>
            <el-collapse>
              <el-collapse-item title="高级自定义参数（JSON）" name="embeddingAdvanced">
                <el-input v-model="form.embedding.customParams" type="textarea" :rows="4" />
              </el-collapse-item>
            </el-collapse>
            <el-form-item>
              <el-button :loading="testing.embedding" @click="handleTest('embedding')">
                连通性测试
              </el-button>
            </el-form-item>
          </template>

          <el-divider content-position="left">系统配置项</el-divider>
          <el-collapse>
            <el-collapse-item title="配置项键值（JSON）" name="settings">
              <el-input v-model="settingsText" type="textarea" :rows="10" />
            </el-collapse-item>
          </el-collapse>

          <el-alert
            v-if="rebuildTask"
            class="ai-config-page__rebuild"
            :type="rebuildRetryable ? 'error' : 'info'"
            :closable="false"
          >
            向量重建任务状态：{{ rebuildTask.status }}（进度 {{ rebuildTask.progress }}%）
            <span v-if="rebuildTask.errorMessage">，原因：{{ rebuildTask.errorMessage }}</span>
            <el-button
              v-if="rebuildRetryable"
              size="small"
              type="primary"
              link
              @click="handleRetryRebuild"
            >
              重试
            </el-button>
          </el-alert>

          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="调用统计" name="statistics">
        <div class="ai-config-page__stat-bar">
          <el-radio-group v-model="statQuery.groupBy" @change="loadStatistics">
            <el-radio-button value="functionType">按功能</el-radio-button>
            <el-radio-button value="workspace">按空间</el-radio-button>
            <el-radio-button value="day">按日期</el-radio-button>
          </el-radio-group>
        </div>
        <template v-if="statistics">
          <el-descriptions :column="3" border class="ai-config-page__stat-summary">
            <el-descriptions-item label="总调用次数">{{ statistics.totalCalls }}</el-descriptions-item>
            <el-descriptions-item label="总 Token">{{ statistics.totalTokens }}</el-descriptions-item>
            <el-descriptions-item label="失败次数">{{ statistics.failedCalls }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="statistics.items" border>
            <el-table-column prop="key" label="维度" />
            <el-table-column prop="calls" label="调用次数" width="120" />
            <el-table-column prop="tokens" label="Token" width="140" />
            <el-table-column prop="avgDurationMs" label="平均耗时(ms)" width="140" />
            <el-table-column prop="failed" label="失败" width="100" />
          </el-table>
        </template>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.ai-config-form {
  max-width: 720px;
}
.ai-config-page__hint {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.ai-config-page__group-switch {
  margin-left: 12px;
}
.ai-config-page__rebuild {
  margin: 12px 0;
}
.ai-config-page__stat-bar {
  margin-bottom: 16px;
}
.ai-config-page__stat-summary {
  margin-bottom: 16px;
}
</style>
