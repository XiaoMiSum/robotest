<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiInterfaceImportPreview, ApiInterfaceImportResult } from '@/types'
import {
  importInterfacesUrl,
  importParsedInterfaces,
  previewInterfaceImportUrl,
  type ApiParsedImportOperation,
} from '@/services/project/api-testing/interface'
import { parseCurlImport } from '@/pages/project/api-testing/interface/curlImport'
import { summarizeImportResult } from './interfacesModel'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'imported', result: ApiInterfaceImportResult): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

// 来源切换：url = Swagger 文档地址；curl = 粘贴 cURL 命令
const sourceMode = ref<'url' | 'curl'>('url')
const urlText = ref('')
const curlText = ref('')
const importing = ref(false)
/** 预览结果：仅展示不入库，用户确认后再执行导入 */
const preview = ref<ApiInterfaceImportPreview | null>(null)
/** curl 本地解析结果（后端暂不复用，见接口管理详细设计 3.4.1） */
const parsedOperations = ref<ApiParsedImportOperation[]>([])
const importResult = ref<ApiInterfaceImportResult | null>(null)

watch(visible, (open) => {
  if (open) reset()
})

watch(sourceMode, () => {
  preview.value = null
  parsedOperations.value = []
})

function reset() {
  urlText.value = ''
  curlText.value = ''
  preview.value = null
  parsedOperations.value = []
  importResult.value = null
}

async function handlePreview() {
  try {
    if (sourceMode.value === 'url') {
      const url = urlText.value.trim()
      if (!url) {
        ElMessage.warning('请输入 Swagger 文档 URL')
        return
      }
      preview.value = await previewInterfaceImportUrl(url)
    } else {
      const parsed = parseCurlImport(curlText.value)
      if (!parsed.length) {
        ElMessage.warning('未解析到任何接口，请检查 cURL 命令格式')
        return
      }
      preview.value = {
        items: parsed.map((operation) => ({
          name: operation.name,
          method: operation.method,
          path: operation.path,
          action: 'create' as const,
          conflict: false,
        })),
        summary: { toCreate: parsed.length, toUpdate: 0, toSkip: 0 },
      }
      parsedOperations.value = parsed
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '解析失败，请检查输入内容')
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
      result = await importInterfacesUrl(urlText.value.trim())
    } else {
      const operations = parsedOperations.value.length
        ? parsedOperations.value
        : parseCurlImport(curlText.value)
      if (!operations.length) {
        ElMessage.warning('未解析到任何接口，请检查 cURL 命令格式')
        return
      }
      result = await importParsedInterfaces(operations)
    }
    importResult.value = result
    ElMessage.success(summarizeImportResult(result))
    if (result.errors.length) {
      ElMessage.warning(`部分条目失败（${result.errors.length}），详见导入结果`)
    } else {
      visible.value = false
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
      <el-radio-button value="url">Swagger URL</el-radio-button>
      <el-radio-button value="curl">cURL</el-radio-button>
    </el-radio-group>

    <div v-if="sourceMode === 'url'" class="import-dialog__section">
      <el-input
        v-model="urlText"
        placeholder="https://petstore.example.com/v2/swagger.json"
        clearable
        data-test="import-url-input"
      />
    </div>
    <div v-else class="import-dialog__section">
      <el-input
        v-model="curlText"
        type="textarea"
        :rows="7"
        placeholder="粘贴 cURL 命令（支持多条），如：curl -X POST 'https://api.example.com/auth/login' -H 'Content-Type: application/json' -d '{&quot;username&quot;:&quot;admin&quot;}'"
        resize="none"
        data-test="import-curl-input"
      />
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
      <el-button v-if="!preview" data-test="preview-btn" @click="handlePreview">
        {{ sourceMode === 'url' ? '解 析 预 览' : '本 地 解 析' }}
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