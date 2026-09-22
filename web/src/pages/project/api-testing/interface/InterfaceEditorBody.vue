<script setup lang="ts">
import { MagicStick } from '@element-plus/icons-vue'
import KeyValueTable from '@/pages/project/api-testing/debug/KeyValueTable.vue'
import type { ApiDebugRawSubtype } from '@/types'
import type { InterfaceEditorForm } from '@/pages/project/api-testing/interface/interfacesModel'

const form = defineModel<InterfaceEditorForm>({ required: true })

const BODY_TYPES = [
  { value: 'none', label: 'none' },
  { value: 'urlencoded', label: 'x-www-form-urlencoded' },
  { value: 'raw', label: 'raw' },
] as const satisfies ReadonlyArray<{ value: InterfaceEditorForm['bodyType']; label: string }>

const RAW_SUBTYPES: ApiDebugRawSubtype[] = ['text', 'json', 'xml', 'html', 'javascript']

function pickBodyType(type: InterfaceEditorForm['bodyType']) {
  form.value.bodyType = type
  if (type === 'raw' && !form.value.rawSubtype) form.value.rawSubtype = 'json'
}

function formatJsonBody() {
  try {
    const parsed: unknown = JSON.parse(form.value.rawText)
    form.value.rawText = JSON.stringify(parsed, null, 2)
  } catch {
    import('element-plus').then(({ ElMessage }) => ElMessage.warning('请求体不是合法 JSON，无法格式化'))
  }
}
</script>

<template>
  <div class="interface-editor__body">
    <div class="interface-editor__body-bar">
      <div class="interface-editor__body-types">
        <button
          v-for="t in BODY_TYPES"
          :key="t.value"
          class="interface-editor__body-type"
          :class="{ 'is-active': form.bodyType === t.value }"
          data-test="editor-body-type-group"
          @click="pickBodyType(t.value)"
        >
          {{ t.label }}
        </button>
        <template v-if="form.bodyType === 'raw'">
          <el-select v-model="form.rawSubtype" class="interface-editor__raw-select">
            <el-option
              v-for="s in RAW_SUBTYPES"
              :key="s"
              :label="s[0].toUpperCase() + s.slice(1)"
              :value="s"
            />
          </el-select>
          <el-tooltip v-if="form.rawSubtype === 'json'" content="格式化（修正 JSON 缩进）" placement="top">
            <button class="interface-editor__body-icon" @click="formatJsonBody">
              <el-icon><MagicStick /></el-icon>
            </button>
          </el-tooltip>
        </template>
      </div>
    </div>

    <p v-if="form.bodyType === 'none'" class="interface-editor__hint">该请求不携带请求体。</p>

    <KeyValueTable
      v-else-if="form.bodyType === 'urlencoded'"
      v-model:entries="form.urlencodedRows"
      placeholder-key="Key"
    />

    <textarea
      v-else
      v-model="form.rawText"
      class="interface-editor__body-editor"
      placeholder="原始文本（支持变量引用）"
      spellcheck="false"
      :rows="12"
    />
  </div>
</template>

<style scoped lang="scss">
.interface-editor__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.interface-editor__body-bar {
  display: flex;
  align-items: center;
}

.interface-editor__body-types {
  display: flex;
  align-items: center;
  gap: 2px;
  background: var(--color-neutral-50, #fafafa);
  border-radius: 6px;
  padding: 2px;
  width: fit-content;
  flex-wrap: wrap;
}

.interface-editor__body-type {
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

.interface-editor__raw-select {
  width: 120px;
  margin-left: 4px;

  :deep(.el-select__wrapper) {
    min-height: 28px;
    padding: 1px 8px;
  }
}

.interface-editor__body-editor {
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
}

.interface-editor__hint {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.interface-editor__body-icon {
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
</style>
