<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMockDetail, createMock, createMockFromInterface, updateMock } from '@/services/apiMock'
import {
  HTTP_METHODS,
  MATCH_RULE_TYPES,
  BODY_TYPES,
  createEmptyMatchRule,
  createEditorForm,
  detailToForm,
  formToPayload,
  type MockEditorForm,
} from '../mocksModel'

const props = defineProps<{
  modelValue: boolean
  mockId: string | null
  interfaceId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'saved'): void
}>()

const visible = ref(props.modelValue)
const saving = ref(false)
const form = ref<MockEditorForm>(createEditorForm())
const responseHeadersInput = ref<Array<{ key: string; value: string }>>([
  { key: 'Content-Type', value: 'application/json' },
])

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val
    if (val) loadForm()
  },
)

watch(visible, (val) => emit('update:modelValue', val))

async function loadForm() {
  if (props.mockId) {
    const detail = await fetchMockDetail(props.mockId)
    form.value = detailToForm(detail)
    responseHeadersInput.value = form.value.responseHeaders.length
      ? form.value.responseHeaders
      : [{ key: 'Content-Type', value: 'application/json' }]
  } else {
    form.value = createEditorForm()
    responseHeadersInput.value = [{ key: 'Content-Type', value: 'application/json' }]
  }
}

function addMatchRule() {
  form.value.matchRules.push(createEmptyMatchRule())
}

function removeMatchRule(index: number) {
  form.value.matchRules.splice(index, 1)
}

function addResponseHeader() {
  responseHeadersInput.value.push({ key: '', value: '' })
}

function removeResponseHeader(index: number) {
  responseHeadersInput.value.splice(index, 1)
}

async function handleSave() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入 Mock 名称')
    return
  }
  if (!form.value.path.trim()) {
    ElMessage.warning('请输入请求路径')
    return
  }
  form.value.responseHeaders = responseHeadersInput.value.filter((h) => h.key.trim())
  const payload = formToPayload(form.value)
  saving.value = true
  try {
    if (props.mockId) {
      await updateMock(props.mockId, payload)
      ElMessage.success('已更新')
    } else if (props.interfaceId) {
      await createMockFromInterface(props.interfaceId, payload)
      ElMessage.success('已创建')
    } else {
      await createMock(payload)
      ElMessage.success('已创建')
    }
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="mockId ? '编辑 Mock' : '新建 Mock'"
    size="640px"
    :close-on-click-modal="false"
  >
    <el-form label-position="top" class="mock-editor">
      <el-divider content-position="left">基本信息</el-divider>

      <el-form-item label="Mock 名称" required>
        <el-input v-model="form.name" maxlength="200" placeholder="如：登录成功 Mock" />
      </el-form-item>

      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
      </el-form-item>

      <div class="mock-editor__row">
        <el-form-item label="请求方法" class="mock-editor__row-item">
          <el-select v-model="form.method" style="width: 100%">
            <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="请求路径" required class="mock-editor__row-item mock-editor__row-item--wide">
          <el-input v-model="form.path" maxlength="500" placeholder="/api/users" />
        </el-form-item>
      </div>

      <div class="mock-editor__row">
        <el-form-item label="启用" class="mock-editor__row-item">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="跟随 API" class="mock-editor__row-item">
          <el-switch v-model="form.followApi" />
          <span class="mock-editor__hint">Mock 未配置响应时使用关联接口的响应示例</span>
        </el-form-item>
        <el-form-item v-if="mockId && form.groupSize > 1" label="同组规则" class="mock-editor__row-item">
          <el-tag type="warning" size="small">{{ form.groupSize }} 条</el-tag>
          <span class="mock-editor__hint">同路径同方法的 Mock 规则总数，优先级排序生效</span>
        </el-form-item>
      </div>

      <el-divider content-position="left">匹配条件</el-divider>

      <div
        v-for="(rule, index) in form.matchRules"
        :key="index"
        class="mock-editor__match-rule"
      >
        <el-select v-model="rule.type" style="width: 130px">
          <el-option
            v-for="t in MATCH_RULE_TYPES"
            :key="t.value"
            :label="t.label"
            :value="t.value"
          />
        </el-select>
        <el-input
          v-model="rule.name"
          placeholder="名称 / JSONPath"
          style="flex: 1"
        />
        <el-input
          v-model="rule.value"
          placeholder="值 / 正则"
          style="flex: 1"
        />
        <el-button
          link
          type="danger"
          :disabled="form.matchRules.length <= 1"
          @click="removeMatchRule(index)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
      <el-button link type="primary" @click="addMatchRule">
        <el-icon><Plus /></el-icon> 添加匹配条件
      </el-button>

      <el-divider content-position="left">响应定义</el-divider>

      <div class="mock-editor__row">
        <el-form-item label="状态码" class="mock-editor__row-item">
          <el-input-number v-model="form.responseStatus" :min="100" :max="599" controls-position="right" />
        </el-form-item>
        <el-form-item label="响应体类型" class="mock-editor__row-item">
          <el-select v-model="form.responseBodyType" style="width: 100%">
            <el-option v-for="t in BODY_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="延迟 (ms)" class="mock-editor__row-item">
          <el-input-number v-model="form.delayMs" :min="0" :max="60000" controls-position="right" />
        </el-form-item>
      </div>

      <el-form-item label="响应头">
        <div
          v-for="(header, index) in responseHeadersInput"
          :key="index"
          class="mock-editor__kv-row"
        >
          <el-input v-model="header.key" placeholder="Key" style="flex: 1" />
          <el-input v-model="header.value" placeholder="Value" style="flex: 1" />
          <el-button link type="danger" @click="removeResponseHeader(index)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <el-button link type="primary" @click="addResponseHeader">
          <el-icon><Plus /></el-icon> 添加响应头
        </el-button>
      </el-form-item>

      <el-form-item label="响应体">
        <el-input
          v-model="form.responseBody"
          type="textarea"
          :rows="8"
          placeholder='{"code": 200, "data": {"token": "mock-${uuid()}"}}'
          class="mock-editor__body-textarea"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.mock-editor {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}

.mock-editor__row {
  display: flex;
  gap: 12px;
}

.mock-editor__row-item {
  flex: 1;
}

.mock-editor__row-item--wide {
  flex: 2;
}

.mock-editor__hint {
  font-size: 12px;
  color: var(--color-neutral-400);
  margin-left: 8px;
}

.mock-editor__match-rule {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.mock-editor__kv-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.mock-editor__body-textarea {
  :deep(textarea) {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 13px;
    line-height: 1.5;
  }
}
</style>
