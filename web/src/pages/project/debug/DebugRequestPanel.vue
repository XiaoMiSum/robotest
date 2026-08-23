<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { ApiEnvironmentListItem, DebugTab } from '@/types'
import { fetchEnvironments } from '@/services/apiEnvironment'
import { HTTP_METHODS } from '../debugModel'
import KeyValueTable from './KeyValueTable.vue'

const tab = defineModel<DebugTab>('tab', { required: true })

defineProps<{ executing: boolean }>()

const emit = defineEmits<{ (e: 'execute', environmentId?: string): void }>()

// ==================== 环境选择 ====================

const environments = ref<ApiEnvironmentListItem[]>([])
const environmentId = ref('')

onMounted(async () => {
  try {
    environments.value = await fetchEnvironments()
    // 缺省选中项目默认环境：相对 URL 拼接与 ${变量} 引用的取值来源
    environmentId.value = environments.value.find((env) => env.isDefault)?.id ?? environments.value[0]?.id ?? ''
  } catch {
    // 环境加载失败不阻塞调试，绝对 URL 场景无需环境
  }
})

function markDirty() {
  tab.value.dirty = true
}

// ==================== 参数页签 ====================

type ParamTab = 'params' | 'headers' | 'body' | 'auth' | 'processors'

const activeParamTab = ref<ParamTab>('params')

// none 非 null 表示「不携带请求体」；其余三类型内容独立缓存（SRS 3.1）；binary 类型 V1.2 不提供
type SelectableBodyType = 'none' | 'json' | 'form' | 'raw'
const selectedBodyType = ref<SelectableBodyType>('none')

watch(
  tab,
  (current) => {
    if (current.bodies.none !== null) {
      selectedBodyType.value = 'none'
      return
    }
    if (current.bodies.json !== '') selectedBodyType.value = 'json'
    else if (current.bodies.form !== '') selectedBodyType.value = 'form'
    else if (current.bodies.raw !== '') selectedBodyType.value = 'raw'
    else selectedBodyType.value = 'none'
  },
  { immediate: true },
)

function selectNone() {
  tab.value.bodies.none = true
  markDirty()
}

function selectRealType(type: Exclude<SelectableBodyType, 'none'>) {
  tab.value.bodies.none = null
  selectedBodyType.value = type
  markDirty()
}

const bodyEditorValue = computed({
  get(): string {
    if (selectedBodyType.value === 'none') return ''
    const content = tab.value.bodies[selectedBodyType.value]
    return typeof content === 'string' ? content : content === null || content === undefined ? '' : JSON.stringify(content, null, 2)
  },
  set(value: string) {
    const type = selectedBodyType.value
    if (type === 'none') return
    tab.value.bodies[type] = value
    markDirty()
  },
})

const BODY_PLACEHOLDERS = {
  json: '{ "key": "value" }',
  form: 'k1=v1&k2=v2',
  raw: '原始文本',
} as const

const bodyPlaceholder = computed(() =>
  selectedBodyType.value === 'none' ? '' : BODY_PLACEHOLDERS[selectedBodyType.value],
)

// ==================== 认证（Basic 前端换算 Authorization 头） ====================

// ==================== 前置处理器（Ryze 元件 JSON 结构） ====================

const processorText = ref('')
const processorInvalid = ref(false)

watch(
  tab,
  (current) => {
    processorText.value = current.processors?.length ? JSON.stringify(current.processors, null, 2) : ''
    processorInvalid.value = false
  },
  { immediate: true },
)

function applyProcessors() {
  const text = processorText.value.trim()
  if (!text) {
    tab.value.processors = []
    processorInvalid.value = false
    markDirty()
    return
  }
  try {
    const parsed: unknown = JSON.parse(text)
    if (!Array.isArray(parsed)) throw new Error('not array')
    tab.value.processors = parsed as Record<string, unknown>[]
    processorInvalid.value = false
    markDirty()
  } catch {
    // 非法 JSON 不提交，保留输入待修正
    processorInvalid.value = true
  }
}
</script>

<template>
  <div class="req-panel">
    <div class="req-panel__url-row">
      <el-select v-model="tab.method" class="req-panel__method" @change="markDirty">
        <el-option v-for="method in HTTP_METHODS" :key="method" :label="method" :value="method" />
      </el-select>
      <el-input
        v-model="tab.url"
        placeholder="https://example.com/api 或 /relative/path"
        clearable
        @input="markDirty"
        @keyup.enter="emit('execute', environmentId || undefined)"
      />
      <el-button
        type="primary"
        :loading="executing"
        :disabled="!tab.url.trim()"
        @click="emit('execute', environmentId || undefined)"
      >
        发送
      </el-button>
    </div>

    <div class="req-panel__env-row">
      <span class="req-panel__label">环境</span>
      <el-select v-model="environmentId" size="small" clearable placeholder="默认环境" class="req-panel__env">
        <el-option
          v-for="env in environments"
          :key="env.id"
          :label="`${env.name}${env.isDefault ? '（默认）' : ''}`"
          :value="env.id"
        />
      </el-select>
      <el-tooltip content="相对路径拼接所选环境 baseUrl；${变量} 取自该环境变量" placement="bottom">
        <el-icon class="req-panel__hint"><InfoFilled /></el-icon>
      </el-tooltip>
      <span class="req-panel__spacer" />
      <el-tag v-if="tab.dirty" size="small" type="warning" effect="plain">●</el-tag>
    </div>

    <el-tabs v-model="activeParamTab" class="req-panel__tabs">
      <el-tab-pane label="Params" name="params" />
      <el-tab-pane label="Headers" name="headers" />
      <el-tab-pane label="请求体" name="body" />
      <el-tab-pane label="认证" name="auth" />
      <el-tab-pane label="前置处理器" name="processors" />
    </el-tabs>

    <KeyValueTable
      v-if="activeParamTab === 'params'"
      v-model:entries="tab.params"
      placeholder-key="参数名"
      @change="markDirty"
    />
    <KeyValueTable
      v-else-if="activeParamTab === 'headers'"
      v-model:entries="tab.headers"
      placeholder-key="Header 名"
      @change="markDirty"
    />

    <div v-else-if="activeParamTab === 'body'" class="req-panel__body">
      <el-radio-group :model-value="selectedBodyType" size="small" @update:model-value="(t) => (typeof t === 'string' ? (t === 'none' ? selectNone() : selectRealType(t as 'json' | 'form' | 'raw')) : undefined)">
        <el-radio-button value="none">none</el-radio-button>
        <el-radio-button value="json">json</el-radio-button>
        <el-radio-button value="form">form-data</el-radio-button>
        <el-radio-button value="raw">raw</el-radio-button>
      </el-radio-group>
      <p v-if="selectedBodyType === 'none'" class="req-panel__tip">本请求不携带请求体</p>
      <el-input
        v-else
        v-model="bodyEditorValue"
        type="textarea"
        :rows="8"
        :placeholder="bodyPlaceholder"
        :class="{ 'is-json': selectedBodyType === 'json' }"
      />
      <p class="req-panel__tip">切换类型保留已填内容；binary 类型随后续版本提供</p>
    </div>

    <div v-else-if="activeParamTab === 'auth'" class="req-panel__auth">
      <el-form label-width="90px" size="small" @submit.prevent>
        <el-form-item label="认证方式">
          <el-select v-model="tab.auth.type" @change="markDirty">
            <el-option label="No Auth" value="none" />
            <el-option label="Basic Auth" value="basic" />
            <!-- Digest 需服务端 WWW-Authenticate challenge 才能计算摘要，V1.2 不提供 -->
            <el-option label="Digest Auth" value="digest" disabled />
          </el-select>
        </el-form-item>
        <template v-if="tab.auth.type === 'basic'">
          <el-form-item label="用户名">
            <el-input v-model="tab.auth.username" @input="markDirty" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="tab.auth.password" type="password" show-password @input="markDirty" />
          </el-form-item>
          <p class="req-panel__tip">提交时换算为 Authorization: Basic 头；手工同名头优先</p>
        </template>
      </el-form>
    </div>

    <div v-else class="req-panel__processors">
      <p class="req-panel__tip">以 Ryze 元件 JSON 数组结构配置，执行前透传引擎</p>
      <el-input
        v-model="processorText"
        type="textarea"
        :rows="6"
        :class="{ 'is-invalid': processorInvalid }"
        placeholder='[{ "testclass": "...", "config": { ... } }]'
        @blur="applyProcessors"
      />
      <p v-if="processorInvalid" class="req-panel__tip req-panel__tip--error">JSON 格式有误，修正后才会随请求提交</p>
    </div>

    <div class="req-panel__settings">
      <span class="req-panel__label">响应超时(ms)</span>
      <el-input-number
        v-model="tab.responseTimeoutMs"
        :min="1000"
        :step="1000"
        size="small"
        controls-position="right"
        @change="markDirty"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.req-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  overflow-y: auto;

  &__url-row {
    display: flex;
    gap: var(--space-sm);

    .el-input {
      flex: 1;
    }
  }

  &__method {
    width: 120px;
  }

  &__env-row,
  &__settings {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
  }

  &__env {
    width: 200px;
  }

  &__spacer {
    flex: 1;
  }

  &__label {
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);
  }

  &__hint {
    color: var(--color-neutral-300);
  }

  &__tip {
    margin: var(--space-xs) 0 0;
    font-size: var(--font-size-xs);
    color: var(--color-neutral-400);

    &--error {
      color: var(--color-danger-500, #f56c6c);
    }
  }

  &__tabs {
    :deep(.el-tabs__header) {
      margin-bottom: var(--space-sm);
    }
  }

  .is-json :deep(textarea),
  .req-panel__processors textarea {
    font-family: ui-monospace, SFMono-Regular, monospace;
    font-size: var(--font-size-xs);
  }

  .is-invalid :deep(textarea) {
    border-color: var(--color-danger-500, #f56c6c);
  }
}
</style>
