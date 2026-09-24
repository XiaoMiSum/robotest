<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { ApiDebugSaveAsInterfaceReq, ApiInterfaceItem, DebugTab, ProjectModule } from '@/types'
import { fetchProjectModuleTree } from '@/services/project'
import { fetchInterfacePage } from '@/services/project/api-testing/interface'
import { saveDebugRecordAsInterface } from '@/services/project/api-testing/debug'
import { buildRequestSnapshot } from '@/composables/project/api-testing/debug/debugModel'

const props = defineProps<{
  visible: boolean
  recordId: string
  /** 当前 tab 表单状态，用于构建请求快照 */
  tab: DebugTab
  /** 环境 ID */
  environmentId?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved', interfaceId: string): void
}>()

// ==================== 字段 ====================

const formRef = ref<FormInstance>()
const mode = ref<'create' | 'attach'>('create')
const name = ref('')
const modules = ref<ProjectModule[]>([])
const moduleId = ref<string>('')
const moduleLoading = ref(false)
const moduleError = ref<string | null>(null)
const interfaceLoading = ref(false)
const interfaceError = ref<string | null>(null)
let moduleRequestId = 0
let interfaceRequestId = 0

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

const createRules = computed<FormRules>(() => ({
  name: [{ required: true, message: '请输入接口名称', trigger: 'blur' }],
  moduleId: [{ required: true, message: '请选择所属模块', trigger: 'change' }],
}))

const attachRules = computed<FormRules>(() => ({
  interfaceId: [{ required: true, message: '请选择归属的接口定义', trigger: 'change' }],
}))

const activeRules = computed(() => mode.value === 'create' ? createRules.value : attachRules.value)

interface ModuleOption {
  id: string
  label: string
  depth: number
}

const moduleOptions = computed<ModuleOption[]>(() => {
  const result: ModuleOption[] = []
  const walk = (nodes: ProjectModule[], depth: number) => {
    for (const node of nodes) {
      if (node.type === 'directory') {
        result.push({ id: node.id, label: node.name, depth })
        walk(node.children, depth + 1)
      } else {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(modules.value, 0)
  return result
})

function indentLabel(option: ModuleOption): string {
  return `${'　'.repeat(option.depth)}${option.label}`
}

async function loadModules(): Promise<void> {
  const requestId = ++moduleRequestId
  moduleLoading.value = true
  moduleError.value = null
  try {
    const result = await fetchProjectModuleTree('interface')
    if (requestId !== moduleRequestId) return
    modules.value = result
  } catch (err) {
    if (requestId !== moduleRequestId) return
    const message = errorMessage(err, '加载接口模块失败')
    modules.value = []
    moduleError.value = message
    ElMessage.error(message)
  } finally {
    if (requestId === moduleRequestId) {
      moduleLoading.value = false
    }
  }
}

// ==================== attach：接口选择器 ====================

const interfaceList = ref<ApiInterfaceItem[]>([])
const interfaceId = ref('')

async function loadInterfaces(): Promise<void> {
  const requestId = ++interfaceRequestId
  interfaceLoading.value = true
  interfaceError.value = null
  try {
    const page = await fetchInterfacePage({
      pageNo: 1,
      pageSize: 50,
      moduleId: moduleId.value || undefined,
    })
    if (requestId !== interfaceRequestId) return
    interfaceList.value = page.list
    if (!interfaceId.value && page.list.length) {
      interfaceId.value = page.list[0].id
    }
  } catch (err) {
    if (requestId !== interfaceRequestId) return
    const message = errorMessage(err, '加载接口候选列表失败')
    interfaceError.value = message
    ElMessage.error(message)
  } finally {
    if (requestId === interfaceRequestId) {
      interfaceLoading.value = false
    }
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (!visible) {
      moduleRequestId += 1
      interfaceRequestId += 1
      moduleLoading.value = false
      interfaceLoading.value = false
      return
    }
    name.value = ''
    mode.value = 'create'
    moduleId.value = ''
    interfaceId.value = ''
    modules.value = []
    interfaceList.value = []
    moduleError.value = null
    interfaceError.value = null
    formRef.value?.resetFields()
    void loadModules()
  },
  { immediate: true },
)

watch(mode, async (current) => {
  formRef.value?.clearValidate()
  interfaceRequestId += 1
  if (current === 'attach') {
    interfaceError.value = null
    await loadInterfaces()
  } else {
    interfaceLoading.value = false
    interfaceError.value = null
    interfaceList.value = []
  }
})

watch(moduleId, async (current) => {
  if (mode.value === 'attach' && current) {
    interfaceId.value = ''
    await loadInterfaces()
  }
})

function retryModules(): void {
  void loadModules()
}

function retryInterfaces(): void {
  void loadInterfaces()
}

onBeforeUnmount(() => {
  moduleRequestId += 1
  interfaceRequestId += 1
  moduleLoading.value = false
  interfaceLoading.value = false
})

function interfaceLabel(item: ApiInterfaceItem): string {
  return `${item.method} ${item.path} — ${item.name}`
}

// ==================== 提交 ====================

const saving = ref(false)

const selectedInterface = computed(() => interfaceList.value.find((item) => item.id === interfaceId.value) ?? null)

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const req: ApiDebugSaveAsInterfaceReq = { mode: mode.value }
  if (mode.value === 'create') {
    req.name = name.value.trim()
    req.moduleId = moduleId.value
  } else {
    req.interfaceId = interfaceId.value
    req.changeVersion = selectedInterface.value?.changeVersion
  }
  req.request = buildRequestSnapshot(props.tab)
  const resp = props.tab.response
  if (resp?.responseStatus) {
    req.responseExample = {
      status: resp.responseStatus,
      headers: resp.responseHeaders ?? null,
      body: resp.responseBody ?? null,
    }
  }
  saving.value = true
  try {
    const result = await saveDebugRecordAsInterface(props.recordId, req)
    ElMessage.success(mode.value === 'create' ? '已保存为接口定义' : '已更新接口定义')
    emit('update:visible', false)
    emit('saved', result.interfaceId)
  } catch (err) {
    ElMessage.error(errorMessage(err, '保存接口定义失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'create' ? '保存为接口定义' : '归属已有接口定义'"
    width="520px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form ref="formRef" :model="{ name, moduleId, interfaceId }" :rules="activeRules" label-width="90px" @submit.prevent>
      <el-form-item label="接口名称" prop="name">
        <el-input v-model="name" placeholder="请输入接口名称" :disabled="mode === 'attach'" />
      </el-form-item>
      <el-form-item label="归属方式">
        <el-radio-group v-model="mode">
          <el-radio-button value="create">新建接口</el-radio-button>
          <el-radio-button value="attach">归属已有接口定义</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <template v-if="mode === 'create'">
        <el-form-item label="所属模块" prop="moduleId">
          <el-select
            v-model="moduleId"
            placeholder="请选择所属模块"
            class="save-dialog__full"
            :loading="moduleLoading"
          >
            <el-option
              v-for="opt in moduleOptions"
              :key="opt.id"
              :label="indentLabel(opt)"
              :value="opt.id"
            />
          </el-select>
          <div v-if="moduleError" class="save-dialog__load-error" role="alert">
            <span>{{ moduleError }}</span>
            <el-button link type="primary" @click="retryModules">重试</el-button>
          </div>
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="已有接口" prop="interfaceId">
          <el-select
            v-model="interfaceId"
            filterable
            placeholder="搜索并选择接口"
            class="save-dialog__full"
            :loading="interfaceLoading"
          >
            <el-option
              v-for="item in interfaceList"
              :key="item.id"
              :label="interfaceLabel(item)"
              :value="item.id"
            />
          </el-select>
          <div v-if="interfaceError" class="save-dialog__load-error" role="alert">
            <span>{{ interfaceError }}</span>
            <el-button link type="primary" @click="retryInterfaces">重试</el-button>
          </div>
        </el-form-item>
      </template>
    </el-form>
    <p class="save-dialog__tip">
      {{ mode === 'create' ? '以当前调试请求（方法/URL/头/参数/请求体）创建新的接口定义' : '以当前调试请求覆盖所选接口定义的最新版本（并发修改将被拒绝）' }}
    </p>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.save-dialog {
  &__full {
    width: 100%;
  }

  &__load-error {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-sm);
    margin-top: var(--space-xs);
    color: var(--color-danger);
    font-size: var(--font-size-xs);
  }

  &__tip {
    margin: 0 0 4px 90px;
    font-size: 12px;
    color: var(--color-neutral-400);
    line-height: 1.6;
  }
}
</style>
