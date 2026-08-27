<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { ApiDebugBodyKind, ApiDebugRawSubtype, ApiEnvironmentListItem, DebugTab } from '@/types'
import { fetchEnvironments } from '@/services/apiEnvironment'
import { HTTP_METHODS } from '../debugModel'
import KeyValueTable from './KeyValueTable.vue'

const tab = defineModel<DebugTab>('tab', { required: true })

defineProps<{ executing: boolean; canSave: boolean }>()

const emit = defineEmits<{ (e: 'execute', environmentId?: string): void; (e: 'save'): void }>()

// ==================== 环境选择 ====================

const environments = ref<ApiEnvironmentListItem[]>([])
const environmentId = ref('')

onMounted(async () => {
  try {
    environments.value = await fetchEnvironments()
    environmentId.value = environments.value.find((env) => env.isDefault)?.id ?? environments.value[0]?.id ?? ''
  } catch {
    // 环境加载失败不阻塞调试
  }
})

function markDirty() {
  tab.value.dirty = true
}

// ==================== 参数页签（对齐 Postman：Params/Auth/Headers/Body） ====================

type ParamTab = 'params' | 'auth' | 'headers' | 'body'

const activeParamTab = ref<ParamTab>('params')

// ==================== 请求体（Postman 四态） ====================

const BODY_TYPES = [
  { value: 'none', label: 'none' },
  { value: 'urlencoded', label: 'x-www-form-urlencoded' },
  { value: 'raw', label: 'raw' },
] as const satisfies ReadonlyArray<{ value: ApiDebugBodyKind; label: string }>
const SUBTYPES: ApiDebugRawSubtype[] = ['text', 'json', 'xml', 'html', 'javascript']

function pickBodyType(type: ApiDebugBodyKind) {
  tab.value.bodyType = type
  if (type === 'raw' && !tab.value.bodies.raw) {
    tab.value.bodies.raw = { text: '', subtype: 'text' }
  }
  markDirty()
}

const rawSubtype = computed<ApiDebugRawSubtype>({
  get() {
    return tab.value.bodies.raw?.subtype ?? 'text'
  },
  set(subtype: ApiDebugRawSubtype) {
    if (!tab.value.bodies.raw) tab.value.bodies.raw = { text: '', subtype }
    else tab.value.bodies.raw.subtype = subtype
    markDirty()
  },
})

const rawText = computed({
  get(): string {
    return tab.value.bodies.raw?.text ?? ''
  },
  set(text: string) {
    if (!tab.value.bodies.raw) tab.value.bodies.raw = { text, subtype: 'text' }
    else tab.value.bodies.raw.text = text
    markDirty()
  },
})

function formatJsonBody() {
  try {
    const parsed: unknown = JSON.parse(rawText.value)
    rawText.value = JSON.stringify(parsed, null, 2)
  } catch {
    // 非合法 JSON 不提供格式化，保持原样
  }
}

// ==================== Headers 常用头预置 ====================

const COMMON_HEADERS = [
  'Accept',
  'Authorization',
  'Content-Type',
  'Cookie',
  'User-Agent',
  'X-Requested-With',
  'If-None-Match',
  'Origin',
] as const

function addPresetHeader(name: string) {
  if (tab.value.headers.some((h) => h.key.toLowerCase() === name.toLowerCase())) return
  tab.value.headers.push({ key: name, value: '', enabled: true, description: '' })
  markDirty()
}

// ==================== Method 颜色 ====================

const METHOD_COLORS: Record<string, string> = {
  GET: '#61affe',
  POST: '#49cc90',
  PUT: '#fca130',
  PATCH: '#50e3c2',
  DELETE: '#f93e3e',
  OPTIONS: '#0d5aa7',
  HEAD: '#9012fe',
  CONNECT: '#e8d44d',
}

const methodColor = computed(() => METHOD_COLORS[tab.value.method.toUpperCase()] ?? '#999')
</script>

<template>
  <div class="req-panel">
    <!-- URL Bar -->
    <div class="req-panel__url-bar">
      <div class="req-panel__method-wrap" :style="{ '--method-color': methodColor }">
        <el-select v-model="tab.method" class="req-panel__method" @change="markDirty">
          <el-option v-for="method in HTTP_METHODS" :key="method" :label="method" :value="method" />
        </el-select>
      </div>
      <el-input
        v-model="tab.url"
        class="req-panel__url"
        placeholder="输入请求 URL，例如 https://api.example.com/users"
        clearable
        @input="markDirty"
        @keyup.enter="emit('execute', environmentId || undefined)"
      />
      <el-tooltip v-if="!canSave" content="请先发送请求获取调试记录" placement="bottom">
        <span class="req-panel__save-wrap">
          <el-button class="req-panel__save" size="default" disabled>保存</el-button>
        </span>
      </el-tooltip>
      <el-button
        v-else
        class="req-panel__save"
        size="default"
        @click="emit('save')"
      >
        保存
      </el-button>
      <el-button
        class="req-panel__send"
        type="primary"
        size="default"
        :loading="executing"
        :disabled="!tab.url.trim()"
        @click="emit('execute', environmentId || undefined)"
      >
        <template v-if="!executing">发送</template>
      </el-button>
    </div>

    <!-- Environment Bar -->
    <div class="req-panel__env-bar">
      <el-select v-model="environmentId" size="small" clearable placeholder="选择环境（可选）" class="req-panel__env">
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
      <div class="req-panel__env-spacer" />
      <span class="req-panel__timeout-label">超时</span>
      <el-input-number
        v-model="tab.responseTimeoutMs"
        :min="1000"
        :step="1000"
        size="small"
        controls-position="right"
        class="req-panel__timeout"
        @change="markDirty"
      />
      <span class="req-panel__timeout-unit">ms</span>
      <el-tag v-if="tab.dirty" size="small" type="warning" effect="plain" class="req-panel__dirty">未保存</el-tag>
    </div>

    <!-- Param Tabs -->
    <div class="req-panel__tabs">
      <button
        v-for="item in (['params', 'auth', 'headers', 'body'] as const)"
        :key="item"
        class="req-panel__tab"
        :class="{ 'is-active': activeParamTab === item }"
        @click="activeParamTab = item"
      >
        {{ item === 'params' ? 'Params' : item === 'auth' ? 'Auth' : item === 'headers' ? 'Headers' : 'Body' }}
        <span v-if="item === 'headers' && tab.headers.length" class="req-panel__tab-count">{{ tab.headers.length }}</span>
        <span v-else-if="item === 'params' && tab.params.length" class="req-panel__tab-count">{{ tab.params.length }}</span>
      </button>
    </div>

    <!-- Tab Content -->
    <div class="req-panel__content">
      <KeyValueTable
        v-if="activeParamTab === 'params'"
        v-model:entries="tab.params"
        placeholder-key="参数名"
        show-description
        @change="markDirty"
      />

      <div v-else-if="activeParamTab === 'auth'" class="req-panel__auth">
        <el-form label-width="90px" size="small" @submit.prevent>
          <el-form-item label="认证方式">
            <el-select v-model="tab.auth.type" @change="markDirty">
              <el-option label="No Auth" value="none" />
              <el-option label="Bearer Token" value="bearer" />
              <el-option label="API Key" value="apiKey" />
              <el-option label="Basic Auth" value="basic" />
              <el-option label="Digest Auth" value="digest" disabled />
            </el-select>
          </el-form-item>
          <template v-if="tab.auth.type === 'bearer'">
            <el-form-item label="Token">
              <el-input v-model="tab.auth.token" type="password" show-password placeholder="输入 Bearer Token" @input="markDirty" />
            </el-form-item>
            <p class="req-panel__tip">提交时换算为 Authorization: Bearer 头；手工同名头优先</p>
          </template>
          <template v-else-if="tab.auth.type === 'apiKey'">
            <el-form-item label="Key 名">
              <el-input v-model="tab.auth.apiKeyName" placeholder="缺省为 X-API-Key" @input="markDirty" />
            </el-form-item>
            <el-form-item label="Key 值">
              <el-input v-model="tab.auth.apiKeyValue" type="password" show-password @input="markDirty" />
            </el-form-item>
            <p class="req-panel__tip">提交时换算为自定义请求头；手工同名头优先</p>
          </template>
          <template v-else-if="tab.auth.type === 'basic'">
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

      <KeyValueTable
        v-else-if="activeParamTab === 'headers'"
        v-model:entries="tab.headers"
        placeholder-key="Header 名"
        show-description
        :suggestions="COMMON_HEADERS"
        @change="markDirty"
      >
        <template #actions>
          <el-dropdown trigger="click" class="req-panel__preset">
            <el-button size="small" text>
              常用头预置
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="name in COMMON_HEADERS" :key="name" @click="addPresetHeader(name)">
                  {{ name }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </KeyValueTable>

      <div v-else-if="activeParamTab === 'body'" class="req-panel__body">
        <div class="req-panel__body-types">
          <button
            v-for="t in BODY_TYPES"
            :key="t.value"
            class="req-panel__body-type"
            :class="{ 'is-active': tab.bodyType === t.value }"
            @click="pickBodyType(t.value)"
          >
            {{ t.label }}
          </button>
        </div>

        <div v-if="tab.bodyType === 'none'" class="req-panel__body-empty">
          该请求不携带请求体
        </div>

        <KeyValueTable
          v-else-if="tab.bodyType === 'urlencoded'"
          v-model:entries="tab.bodies.urlencoded"
          placeholder-key="Key"
          @change="markDirty"
        />

        <div v-else class="req-panel__raw">
          <div class="req-panel__raw-controls">
            <span class="req-panel__raw-label">类型</span>
            <el-select v-model="rawSubtype" size="small" class="req-panel__raw-select">
              <el-option v-for="s in SUBTYPES" :key="s" :label="s[0].toUpperCase() + s.slice(1)" :value="s" />
            </el-select>
            <el-button v-if="rawSubtype === 'json'" size="small" text @click="formatJsonBody">格式化</el-button>
          </div>
          <textarea
            v-model="rawText"
            class="req-panel__body-editor"
            placeholder="原始文本（支持变量引用）"
            spellcheck="false"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.req-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;

  // ==================== URL Bar ====================
  &__url-bar {
    display: flex;
    gap: 8px;
    padding: 10px 14px;
    align-items: center;
  }

  &__method-wrap {
    flex-shrink: 0;
  }

  &__method {
    width: 110px;

    :deep(.el-input__wrapper) {
      background: var(--method-color, #999);
      box-shadow: none !important;
      border-radius: 6px;
    }

    :deep(.el-input__inner) {
      color: #fff;
      font-weight: 700;
      font-family: ui-monospace, SFMono-Regular, monospace;
      letter-spacing: 0.5px;
    }

    :deep(.el-select__caret) {
      color: rgba(255, 255, 255, 0.8) !important;
    }
  }

  &__url {
    flex: 1;

    :deep(.el-input__wrapper) {
      font-family: ui-monospace, SFMono-Regular, monospace;
      font-size: 13px;
      padding: 6px 12px;
    }
  }

  &__save-wrap {
    display: inline-flex;
  }

  &__save {
    height: 32px;
    padding: 0 16px;
    font-weight: 500;
    border-radius: 6px;
    flex-shrink: 0;
  }

  &__send {
    height: 32px;
    padding: 0 20px;
    font-weight: 600;
    border-radius: 6px;
    flex-shrink: 0;
  }

  // ==================== Environment Bar ====================
  &__env-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 14px 8px;
  }

  &__env {
    width: 200px;
  }

  &__env-spacer {
    flex: 1;
  }

  &__hint {
    color: var(--color-neutral-300, #c0c4cc);
    cursor: help;
  }

  &__timeout-label {
    font-size: 12px;
    color: var(--color-neutral-400, #909399);
  }

  &__timeout {
    width: 90px;
  }

  &__timeout-unit {
    font-size: 12px;
    color: var(--color-neutral-400, #909399);
    margin-left: -4px;
  }

  &__dirty {
    margin-left: 8px;
  }

  // ==================== Param Tabs ====================
  &__tabs {
    display: flex;
    gap: 0;
    padding: 0 14px;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
  }

  &__tab {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 8px 14px;
    font-size: 12px;
    font-weight: 500;
    color: var(--color-neutral-500, #909399);
    background: none;
    border: none;
    border-bottom: 2px solid transparent;
    cursor: pointer;
    transition: color 0.15s, border-color 0.15s;
    white-space: nowrap;

    &:hover {
      color: var(--color-neutral-700, #606266);
    }

    &.is-active {
      color: var(--color-primary-500, #409eff);
      border-bottom-color: var(--color-primary-500, #409eff);
    }
  }

  &__tab-count {
    background: var(--color-neutral-100, #e8e8e8);
    color: var(--color-neutral-500, #909399);
    font-size: 10px;
    padding: 0 5px;
    border-radius: 8px;
    line-height: 16px;

    .is-active & {
      background: var(--color-primary-100, #ecf5ff);
      color: var(--color-primary-500, #409eff);
    }
  }

  // ==================== Content ====================
  &__content {
    flex: 1;
    overflow: auto;
    padding: 10px 14px;
    min-height: 0;
  }

  // ==================== Preset ====================
  &__preset {
    margin-left: 8px;
  }

  // ==================== Body ====================
  &__body {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__body-types {
    display: flex;
    gap: 2px;
    background: var(--color-neutral-50, #fafafa);
    border-radius: 6px;
    padding: 3px;
    width: fit-content;
    flex-wrap: wrap;
  }

  &__body-type {
    padding: 4px 12px;
    font-size: 12px;
    border: none;
    background: none;
    border-radius: 4px;
    cursor: pointer;
    color: var(--color-neutral-500, #909399);
    transition: all 0.15s;

    &:hover {
      color: var(--color-neutral-700, #606266);
    }

    &.is-active {
      background: var(--color-bg, #fff);
      color: var(--color-neutral-800, #303133);
      font-weight: 500;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
    }
  }

  &__body-empty {
    padding: 32px 0;
    text-align: center;
    color: var(--color-neutral-300, #c0c4cc);
    font-size: 13px;
  }

  &__raw {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__raw-controls {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__raw-label {
    font-size: 12px;
    color: var(--color-neutral-400, #909399);
  }

  &__raw-select {
    width: 120px;
  }

  &__body-editor {
    width: 100%;
    min-height: 160px;
    max-height: 400px;
    resize: vertical;
    padding: 12px;
    font-family: ui-monospace, SFMono-Regular, monospace;
    font-size: 12px;
    line-height: 1.6;
    color: #d4d4d4;
    background: #1e1e1e;
    border: 1px solid #333;
    border-radius: 6px;
    outline: none;
    tab-size: 2;

    &::placeholder {
      color: #555;
    }

    &:focus {
      border-color: var(--color-primary-500, #409eff);
    }

    &.is-invalid {
      border-color: var(--color-danger-500, #f56c6c);
    }
  }

  // ==================== Auth ====================
  &__auth {
    max-width: 420px;
  }

  // ==================== Tip ====================
  &__tip {
    margin: 4px 0 0;
    font-size: 11px;
    color: var(--color-neutral-400, #909399);

    &--error {
      color: var(--color-danger-500, #f56c6c);
    }
  }
}
</style>