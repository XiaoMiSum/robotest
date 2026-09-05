<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  ApiDebugBodyKind,
  ApiDebugRawSubtype,
  ApiEnvironmentListItem,
  DebugTab,
} from '@/types'
import { fetchEnvironments } from '@/services/apiEnvironment'
import { HTTP_METHODS, setBodyContentTypeHeader } from '../debugModel'
import KeyValueTable from './KeyValueTable.vue'

const tab = defineModel<DebugTab>('tab', { required: true })

const environmentId = defineModel<string>('environmentId', { default: '' })

defineProps<{ executing: boolean; canSave: boolean }>()

const emit = defineEmits<{ (e: 'execute', environmentId?: string): void; (e: 'save'): void }>()

// ==================== 环境选择 ====================

const environments = ref<ApiEnvironmentListItem[]>([])

onMounted(async () => {
  try {
    environments.value = await fetchEnvironments()
    environmentId.value =
      environments.value.find((env) => env.isDefault)?.id ?? environments.value[0]?.id ?? ''
  } catch {
    // 环境加载失败不阻塞调试
  }
})

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
    tab.value.bodies.raw = { text: '', subtype: 'json' }
  }
  setBodyContentTypeHeader(tab.value, type, tab.value.bodies.raw?.subtype)
}

const rawSubtype = computed<ApiDebugRawSubtype>({
  get() {
    return tab.value.bodies.raw?.subtype ?? 'json'
  },
  set(subtype: ApiDebugRawSubtype) {
    if (!tab.value.bodies.raw) tab.value.bodies.raw = { text: '', subtype }
    else tab.value.bodies.raw.subtype = subtype
    if (tab.value.bodyType === 'raw') setBodyContentTypeHeader(tab.value, 'raw', subtype)
  },
})

const rawText = computed({
  get(): string {
    return tab.value.bodies.raw?.text ?? ''
  },
  set(text: string) {
    if (!tab.value.bodies.raw) tab.value.bodies.raw = { text, subtype: 'json' }
    else tab.value.bodies.raw.text = text
  },
})

function formatJsonBody() {
  try {
    const parsed: unknown = JSON.parse(rawText.value)
    rawText.value = JSON.stringify(parsed, null, 2)
  } catch {
    ElMessage.warning('请求体不是合法 JSON，无法格式化')
  }
}

// ==================== Headers 常用头名（Key 下拉建议项） ====================

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
        <el-select v-model="tab.method" class="req-panel__method">
          <el-option v-for="method in HTTP_METHODS" :key="method" :label="method" :value="method" />
        </el-select>
      </div>
      <el-input
        v-model="tab.url"
        class="req-panel__url"
        placeholder="输入请求 URL，例如 https://api.example.com/users"
        clearable
        @keyup.enter="emit('execute', environmentId || undefined)"
      />
      <el-button
        class="req-panel__send"
        type="primary"
        :loading="executing"
        :disabled="!tab.url.trim()"
        @click="emit('execute', environmentId || undefined)"
      >
        <template v-if="!executing">
          <el-icon class="req-panel__btn-icon"><Promotion /></el-icon>发送
        </template>
      </el-button>
      <el-tooltip v-if="!canSave" content="请先发送请求获取调试记录" placement="bottom">
        <span class="req-panel__save-wrap">
          <el-button class="req-panel__save" disabled>
            <el-icon class="req-panel__btn-icon"><Plus /></el-icon>保存
          </el-button>
        </span>
      </el-tooltip>
      <el-button v-else class="req-panel__save" @click="emit('save')">
        <el-icon class="req-panel__btn-icon"><Plus /></el-icon>保存
      </el-button>
    </div>

    <!-- Param Tabs -->
    <div class="req-panel__tabs">
      <button
        v-for="item in ['params', 'body', 'headers', 'auth'] as const"
        :key="item"
        class="req-panel__tab"
        :class="{ 'is-active': activeParamTab === item }"
        @click="activeParamTab = item"
      >
        {{
          item === 'params'
            ? 'Params'
            : item === 'body'
              ? 'Body'
              : item === 'headers'
                ? 'Headers'
                : 'Auth'
        }}
        <span v-if="item === 'headers' && tab.headers.length" class="req-panel__tab-count">{{
          tab.headers.length
        }}</span>
        <span v-else-if="item === 'params' && tab.params.length" class="req-panel__tab-count">{{
          tab.params.length
        }}</span>
      </button>

      <div class="req-panel__tabs-spacer" />

      <div class="req-panel__env">
        <el-select
          v-model="environmentId"
          placeholder="选择环境（可选）"
          class="req-panel__env-select"
        >
          <el-option
            v-for="env in environments"
            :key="env.id"
            :label="`${env.name}${env.isDefault ? '（默认）' : ''}`"
            :value="env.id"
          />
        </el-select>
        <el-tooltip
          content="相对路径拼接所选环境 baseUrl；${变量} 取自该环境变量"
          placement="bottom"
        >
          <el-icon class="req-panel__hint"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
    </div>

    <!-- Tab Content -->
    <div class="req-panel__content">
      <KeyValueTable
        v-if="activeParamTab === 'params'"
        v-model:entries="tab.params"
        placeholder-key="参数名"
        show-description
      />

      <div v-else-if="activeParamTab === 'auth'" class="req-panel__auth">
        <el-form label-width="90px" @submit.prevent>
          <el-form-item label="认证方式">
            <el-select v-model="tab.auth.type">
              <el-option label="No Auth" value="none" />
              <el-option label="Bearer Token" value="bearer" />
              <el-option label="API Key" value="apiKey" />
              <el-option label="Basic Auth" value="basic" />
              <el-option label="Digest Auth" value="digest" disabled />
            </el-select>
          </el-form-item>
          <template v-if="tab.auth.type === 'bearer'">
            <el-form-item label="Token">
              <el-input
                v-model="tab.auth.token"
                type="password"
                show-password
                placeholder="输入 Bearer Token"
              />
            </el-form-item>
            <p class="req-panel__tip">提交时换算为 Authorization: Bearer 头；手工同名头优先</p>
          </template>
          <template v-else-if="tab.auth.type === 'apiKey'">
            <el-form-item label="Key 名">
              <el-input v-model="tab.auth.apiKeyName" placeholder="缺省为 X-API-Key" />
            </el-form-item>
            <el-form-item label="Key 值">
              <el-input v-model="tab.auth.apiKeyValue" type="password" show-password />
            </el-form-item>
            <p class="req-panel__tip">提交时换算为自定义请求头；手工同名头优先</p>
          </template>
          <template v-else-if="tab.auth.type === 'basic'">
            <el-form-item label="用户名">
              <el-input v-model="tab.auth.username" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="tab.auth.password" type="password" show-password />
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
      />

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

          <div v-if="tab.bodyType === 'raw'" class="req-panel__body-types-right">
            <el-select v-model="rawSubtype" class="req-panel__raw-select">
              <el-option
                v-for="s in SUBTYPES"
                :key="s"
                :label="s[0].toUpperCase() + s.slice(1)"
                :value="s"
              />
            </el-select>
            <el-tooltip v-if="rawSubtype === 'json'" content="格式化（修正 JSON 缩进）" placement="top">
              <button class="req-panel__body-format" @click="formatJsonBody">
                <el-icon><MagicStick /></el-icon>
              </button>
            </el-tooltip>
          </div>
        </div>

        <div v-if="tab.bodyType === 'none'" class="req-panel__body-empty">该请求不携带请求体</div>

        <KeyValueTable
          v-else-if="tab.bodyType === 'urlencoded'"
          v-model:entries="tab.bodies.urlencoded"
          placeholder-key="Key"
        />

        <div v-else class="req-panel__raw">
          <textarea
            v-model="rawText"
            class="req-panel__body-editor"
            placeholder="原始文本（支持变量引用）"
            spellcheck="false"
            :rows="12"
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

  &__btn-icon {
    margin-right: 4px;
  }

  // ==================== Environment (tabs 最右) ====================
  &__tabs-spacer {
    flex: 1;
  }

  &__env {
    display: flex;
    align-items: center;
    gap: 6px;
    padding-left: 8px;
  }

  &__env-select {
    width: 200px;

    :deep(.el-select__wrapper) {
      border: none;
      box-shadow: none;
      padding-left: 4px;

      &:hover {
        .el-select__selected-item,
        .el-select__placeholder {
          color: var(--color-primary-500, #409eff);
        }
      }
    }
  }

  &__hint {
    color: var(--color-neutral-300, #c0c4cc);
    cursor: help;
  }

  // ==================== Param Tabs ====================
  &__tabs {
    display: flex;
    gap: 0;
    border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
  }

  &__tab {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 10px 14px;
    font-size: 12px;
    font-weight: 500;
    color: var(--color-neutral-500, #909399);
    background: none;
    border: none;
    border-bottom: 2px solid transparent;
    cursor: pointer;
    transition:
      color 0.15s,
      border-color 0.15s;
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
    padding: 10px;
    min-height: 0;
  }

  // ==================== Body ====================
  &__body {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__body-types {
    display: flex;
    align-items: center;
    gap: 2px;
    background: var(--color-neutral-50, #fafafa);
    border-radius: 6px;
    padding: 2px;
    width: fit-content;
    flex-wrap: wrap;
  }

  &__body-type {
    height: 28px;
    padding: 0 12px;
    display: inline-flex;
    align-items: center;
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
      background: var(--color-primary-500, #409eff);
      color: #fff;
      font-weight: 500;
      box-shadow: none;
    }
  }

  &__body-types-right {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-left: 4px;

    :deep(.el-select__wrapper) {
      min-height: 28px;
      padding: 1px 8px;
    }

    :deep(.el-button) {
      height: 28px;
      padding: 0 8px;
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

  &__raw-select {
    width: 120px;
  }

  &__body-format {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border: none;
    background: none;
    border-radius: 4px;
    color: var(--color-neutral-400, #909399);
    cursor: pointer;
    font-size: 14px;
    transition: color 0.15s, background 0.15s;

    &:hover {
      color: var(--color-neutral-700, #606266);
      background: var(--color-neutral-100, #e8e8e8);
    }
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
