<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { ApiDebugSaveAsInterfaceReq, ApiInterfaceItem, DebugTab, ProjectModule } from '@/types'
import { fetchProjectModuleTree } from '@/services/project'
import { fetchInterfacePage } from '@/services/apiInterface'
import { saveDebugRecordAsInterface } from '@/services/apiDebug'
import { buildRequestSnapshot } from '../debugModel'

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

async function loadModules() {
  try {
    modules.value = await fetchProjectModuleTree('interface')
  } catch {
    // 模块加载失败不阻塞保存
  }
}

// ==================== attach：接口选择器 ====================

const interfaceList = ref<ApiInterfaceItem[]>([])
const interfaceId = ref('')

async function loadInterfaces() {
  try {
    const page = await fetchInterfacePage({
      pageNo: 1,
      pageSize: 50,
      moduleId: moduleId.value || undefined,
    })
    interfaceList.value = page.list
    if (!interfaceId.value && page.list.length) {
      interfaceId.value = page.list[0].id
    }
  } catch {
    // 接口列表加载失败时保存按钮由后端校验兜底
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (!visible) return
    name.value = ''
    mode.value = 'create'
    moduleId.value = ''
    interfaceId.value = ''
    formRef.value?.resetFields()
    loadModules()
  },
  { immediate: true },
)

watch(mode, async (current) => {
  formRef.value?.clearValidate()
  if (current === 'attach') {
    await loadInterfaces()
  }
})

watch(moduleId, async (current) => {
  if (mode.value === 'attach' && current) {
    interfaceId.value = ''
    await loadInterfaces()
  }
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
  } catch {
    // 拦截器已统一提示错误信息
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
          <el-select v-model="moduleId" placeholder="请选择所属模块" class="save-dialog__full">
            <el-option
              v-for="opt in moduleOptions"
              :key="opt.id"
              :label="indentLabel(opt)"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="已有接口" prop="interfaceId">
          <el-select
            v-model="interfaceId"
            filterable
            placeholder="搜索并选择接口"
            class="save-dialog__full"
          >
            <el-option
              v-for="item in interfaceList"
              :key="item.id"
              :label="interfaceLabel(item)"
              :value="item.id"
            />
          </el-select>
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

  &__tip {
    margin: 0 0 4px 90px;
    font-size: 12px;
    color: var(--color-neutral-400, #909399);
    line-height: 1.6;
  }
}
</style>
