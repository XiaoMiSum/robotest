<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiInterfaceImportPreview, ApiInterfaceImportResult } from '@/types'
import {
  importInterfacesFile,
  importInterfacesUrl,
  previewInterfaceImport,
} from '@/services/apiInterface'
import { summarizeImportResult } from '../interfacesModel'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'imported', result: ApiInterfaceImportResult): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const FORMAT_OPTIONS = [
  { value: '', label: '自动识别' },
  { value: 'swagger', label: 'Swagger / OpenAPI' },
  { value: 'postman', label: 'Postman Collection' },
  { value: 'har', label: 'HAR' },
  { value: 'jmeter', label: 'JMeter (.jmx)' },
]

const sourceMode = ref<'file' | 'url'>('file')
const fileInput = ref<File | null>(null)
const urlText = ref('')
const formatHint = ref('')
const importing = ref(false)
/** 预览结果：仅展示不入库，用户确认后再执行导入 */
const preview = ref<ApiInterfaceImportPreview | null>(null)
const importResult = ref<ApiInterfaceImportResult | null>(null)

watch(visible, (open) => {
  if (open) reset()
})

function reset() {
  fileInput.value = null
  urlText.value = ''
  formatHint.value = ''
  preview.value = null
  importResult.value = null
}

function pickFile(file: File | undefined) {
  if (!file) return
  fileInput.value = file
  // 切换文件后旧预览失效
  preview.value = null
}

async function handlePreview() {
  const file = fileInput.value
  if (!file) {
    ElMessage.warning('请先选择导入文件')
    return
  }
  try {
    preview.value = await previewInterfaceImport(file, formatHint.value || undefined)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '解析失败，请检查文件格式')
  }
}

async function handleImport() {
  if (importing.value) return
  importing.value = true
  try {
    let result: ApiInterfaceImportResult
    if (sourceMode.value === 'url') {
      if (!urlText.value.trim()) {
        ElMessage.warning('请输入 Swagger 文档 URL')
        return
      }
      result = await importInterfacesUrl(urlText.value.trim(), formatHint.value || undefined)
    } else {
      if (!fileInput.value) {
        ElMessage.warning('请先选择导入文件')
        return
      }
      result = await importInterfacesFile(fileInput.value, formatHint.value || undefined)
    }
    importResult.value = result
    ElMessage.success(summarizeImportResult(result))
    if (result.errors.length) {
      ElMessage.warning(`部分条目失败（${result.errors.length}），详见导入结果`)
    }
    emit('imported', result)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="导入接口" width="720px" destroy-on-close>
    <el-radio-group v-model="sourceMode" class="import-dialog__mode">
      <el-radio-button value="file">文件导入</el-radio-button>
      <el-radio-button value="url">Swagger URL</el-radio-button>
    </el-radio-group>

    <div v-if="sourceMode === 'file'" class="import-dialog__section">
      <input
        type="file"
        accept=".json,.yaml,.yml,.har,.jmx"
        data-test="import-file-input"
        @change="pickFile(($event.target as HTMLInputElement).files?.[0])"
      />
    </div>
    <div v-else class="import-dialog__section">
      <el-input
        v-model="urlText"
        placeholder="https://petstore.example.com/v2/swagger.json"
        clearable
        data-test="import-url-input"
      />
    </div>

    <div class="import-dialog__section import-dialog__format">
      <span class="import-dialog__label">格式</span>
      <el-select v-model="formatHint" style="width: 220px" data-test="import-format-select">
        <el-option v-for="option in FORMAT_OPTIONS" :key="option.value" :value="option.value" :label="option.label" />
      </el-select>
    </div>

    <template v-if="preview">
      <el-divider content-position="left">预览（{{ preview.items.length }} 条）</el-divider>
      <el-table :data="preview.items" size="small" max-height="240" data-test="preview-table">
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="method" label="方法" width="90" />
        <el-table-column prop="path" label="路径" min-width="200" show-overflow-tooltip />
        <el-table-column label="动作" width="100">
          <template #default="{ row }">
            <el-tag :type="row.action === 'create' ? 'success' : row.action === 'update' ? 'warning' : 'info'" size="small">
              {{ row.action === 'create' ? '新建' : row.action === 'update' ? '覆盖更新' : '跳过' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template v-if="importResult?.errors.length">
      <el-divider content-position="left">失败明细</el-divider>
      <ul class="import-dialog__errors">
        <li v-for="(item, index) in importResult.errors" :key="index">{{ item.source }}：{{ item.message }}</li>
      </ul>
    </template>

    <template #footer>
      <el-button v-if="sourceMode === 'file' && !preview" :disabled="!fileInput" data-test="preview-btn" @click="handlePreview">
        预 览
      </el-button>
      <el-button type="primary" :loading="importing" :data-test="'import-confirm-btn'" @click="handleImport">执 行 导 入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.import-dialog__mode {
  margin-bottom: var(--space-lg);
}

.import-dialog__section {
  margin-bottom: var(--space-md);
}

.import-dialog__format {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.import-dialog__label {
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}

.import-dialog__errors {
  margin: 0;
  padding-left: var(--space-lg);
  color: var(--color-danger-600, #c45656);
  font-size: var(--font-size-sm);
  li + li {
    margin-top: 4px;
  }
}
</style>
