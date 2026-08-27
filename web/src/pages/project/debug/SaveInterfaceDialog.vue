<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiDebugSaveAsInterfaceReq, ApiInterfaceItem, ProjectModule } from '@/types'
import { fetchProjectModuleTree } from '@/services/project'
import { fetchInterfacePage } from '@/services/apiInterface'
import { saveDebugRecordAsInterface } from '@/services/apiDebug'

const props = defineProps<{
  visible: boolean
  recordId: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved', interfaceId: string): void
}>()

// ==================== 字段 ====================

const mode = ref<'create' | 'attach'>('create')
const name = ref('')
const modules = ref<ProjectModule[]>([])
const moduleId = ref<string>('')

interface ModuleOption {
  id: string
  label: string
  depth: number
}

const moduleOptions = computed<ModuleOption[]>(() => {
  const result: ModuleOption[] = []
  const walk = (nodes: ProjectModule[], depth: number) => {
    for (const node of nodes) {
      // 仅目录可作为归属模块（文档非接口资产）
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
    // 模块加载失败不阻塞保存；模块列表为空时后端按未分组处理
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
    loadModules()
  },
  { immediate: true },
)

watch(mode, async (current) => {
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
  const req: ApiDebugSaveAsInterfaceReq = { mode: mode.value }
  if (mode.value === 'create') {
    if (!name.value.trim()) {
      ElMessageBox.alert('请输入接口名称', '提示', { type: 'warning' }).catch(() => {})
      return
    }
    req.name = name.value.trim()
    req.moduleId = moduleId.value || undefined
  } else {
    if (!interfaceId.value) {
      ElMessageBox.alert('请选择归属的接口定义', '提示', { type: 'warning' }).catch(() => {})
      return
    }
    req.interfaceId = interfaceId.value
    req.changeVersion = selectedInterface.value?.changeVersion
  }
  saving.value = true
  try {
    const resp = await saveDebugRecordAsInterface(props.recordId, req)
    ElMessage.success(mode.value === 'create' ? '已保存为接口定义' : '已更新接口定义')
    emit('update:visible', false)
    emit('saved', resp.interfaceId)
  } catch {
    // 拦截器已统一提示错误信息（含 changeVersion 乐观锁冲突）
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
    <el-form label-width="90px" size="default" @submit.prevent>
      <el-form-item label="接口名称">
        <el-input v-model="name" placeholder="保存后成为接口管理中的资产" :disabled="mode === 'attach'" />
      </el-form-item>
      <el-form-item label="归属方式">
        <el-radio-group v-model="mode">
          <el-radio-button value="create">新建接口</el-radio-button>
          <el-radio-button value="attach">归属已有接口定义</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <template v-if="mode === 'create'">
        <el-form-item label="所属模块">
          <el-select v-model="moduleId" clearable placeholder="未选择时保存到无分组" class="save-dialog__full">
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
        <el-form-item label="已有接口">
          <el-select
            v-model="interfaceId"
            filterable
            placeholder="搜索并选择接口"
            class="save-dialog__full"
            @clear="interfaceId = ''"
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