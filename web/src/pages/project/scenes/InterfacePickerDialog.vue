<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiInterfaceDetail, ApiInterfaceItem, ApiSceneStepItem } from '@/types'
import { fetchInterfacePage, fetchInterfaceDetail } from '@/services/apiInterface'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', step: ApiSceneStepItem): void
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => (visible.value = v))
watch(visible, (v) => emit('update:modelValue', v))

const interfaceOptions = ref<ApiInterfaceItem[]>([])
const interfaceSearch = ref('')
const interfaceLoading = ref(false)
const selectedId = ref('')
const importing = ref(false)

async function loadInterfaces() {
  interfaceLoading.value = true
  try {
    const resp = await fetchInterfacePage({ pageNo: 1, pageSize: 50, search: interfaceSearch.value || undefined })
    interfaceOptions.value = resp.list
  } catch {
    interfaceOptions.value = []
  } finally {
    interfaceLoading.value = false
  }
}

watch(visible, async (v) => {
  if (!v) return
  selectedId.value = ''
  interfaceSearch.value = ''
  await loadInterfaces()
})

function newStepId(): string {
  return `new-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

/** 把接口详情客户端构造成一个步骤，不请求后端；source 记录源接口以便后续关联同步 */
function buildStepFromInterface(detail: ApiInterfaceDetail): ApiSceneStepItem {
  const requestConfig: Record<string, unknown> = {
    method: detail.method,
    url: detail.path,
    headers: detail.headers ?? [],
    params: detail.params ?? [],
    body: detail.body ?? { type: 'none', content: null },
    conditionExpression: '',
  }
  // 接口侧验证器/提取器不含场景步骤所需的 id/name/enabled，补齐以防序列化崩溃
  const validators = (detail.validators ?? []).map((v) => ({
    id: crypto.randomUUID(),
    name: `断言 ${(v as Record<string, unknown>).target ?? 'custom'}`,
    enabled: true,
    ...v,
  }))
  const extractors = (detail.extractors ?? []).map((e) => ({
    id: crypto.randomUUID(),
    name: `提取器 ${(e as Record<string, unknown>).source ?? 'custom'}`,
    enabled: true,
    ...e,
  }))
  return {
    id: newStepId(),
    name: detail.name,
    stepType: 'http',
    sortOrder: 0,
    enabled: true,
    sourceType: 'copy',
    sourceInterfaceId: detail.id,
    sourceInterfaceName: detail.name,
    requestConfig,
    variables: [],
    // 接口定义不再包含处理器，场景步骤处理器由场景侧单独配置
    processors: [],
    validators,
    extractors,
  }
}

async function handleImport() {
  if (!selectedId.value) { ElMessage.warning('请选择接口'); return }
  importing.value = true
  try {
    const detail = await fetchInterfaceDetail(selectedId.value)
    emit('select', buildStepFromInterface(detail))
    visible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="从接口添加步骤"
    width="640px"
    destroy-on-close
  >
    <el-form label-position="top">
      <el-form-item label="选择接口" required>
        <el-select
          v-model="selectedId"
          filterable
          remote
          :remote-method="(q: string) => { interfaceSearch = q; loadInterfaces() }"
          :loading="interfaceLoading"
          placeholder="搜索接口名称"
          style="width: 100%"
        >
          <el-option
            v-for="item in interfaceOptions"
            :key="item.id"
            :value="item.id"
            :label="`${item.method} ${item.path} - ${item.name}`"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="importing" @click="handleImport">导入</el-button>
    </template>
  </el-dialog>
</template>