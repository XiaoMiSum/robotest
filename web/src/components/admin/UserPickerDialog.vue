<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchSimpleUserList, fetchWorkspaces } from '@/services/admin'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    // 已关联用户 ID，用于在候选中过滤
    excludeIds?: string[]
    // 是否显示空间选择器
    showWorkspace?: boolean
  }>(),
  { title: '选择用户', excludeIds: () => [], showWorkspace: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [userIds: string[], workspaceIds?: string[]]
}>()

const loading = ref(false)
const options = ref<{ id: string; name: string }[]>([])
const selectedIds = ref<string[]>([])

// 空间选择
const wsLoading = ref(false)
const wsOptions = ref<{ id: string; name: string }[]>([])
const selectedWsIds = ref<string[]>([])

async function searchUsers(keyword: string) {
  if (!keyword) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const list = await fetchSimpleUserList(keyword)
    const exclude = props.excludeIds ?? []
    options.value = list.filter((u) => !exclude.includes(u.id))
  } catch {
    options.value = []
  } finally {
    loading.value = false
  }
}

async function loadWorkspaces() {
  wsLoading.value = true
  try {
    const page = await fetchWorkspaces({ pageNo: 1, pageSize: 100 })
    wsOptions.value = page.list.map((w) => ({ id: w.id, name: w.name }))
  } catch {
    wsOptions.value = []
  } finally {
    wsLoading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

function handleConfirm() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请至少选择一个用户')
    return
  }
  if (props.showWorkspace && !selectedWsIds.value.length) {
    ElMessage.warning('请至少选择一个空间')
    return
  }
  emit('confirm', [...selectedIds.value], props.showWorkspace ? [...selectedWsIds.value] : undefined)
}

// 每次打开重置选择态
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      selectedIds.value = []
      options.value = []
      selectedWsIds.value = []
      if (props.showWorkspace) loadWorkspaces()
    }
  },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title || '选择用户'"
    width="480px"
    @update:model-value="close"
  >
    <el-select
      v-model="selectedIds"
      multiple
      filterable
      remote
      reserve-keyword
      placeholder="输入用户姓名搜索（仅活跃用户）"
      :remote-method="searchUsers"
      :loading="loading"
      style="width: 100%"
    >
      <el-option
        v-for="u in options"
        :key="u.id"
        :label="u.name"
        :value="u.id"
      />
    </el-select>

    <!-- 空间选择（仅空间角色） -->
    <template v-if="showWorkspace">
      <div class="user-picker__ws-divider">选择要关联的空间</div>
      <el-select
        v-model="selectedWsIds"
        multiple
        filterable
        placeholder="搜索并选择空间（可多选）"
        :loading="wsLoading"
        style="width: 100%"
      >
        <el-option
          v-for="ws in wsOptions"
          :key="ws.id"
          :label="ws.name"
          :value="ws.id"
        />
      </el-select>
    </template>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.user-picker__ws-divider {
  margin: 16px 0 8px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
</style>
