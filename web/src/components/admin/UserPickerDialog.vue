<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchUsers } from '@/services/admin'

const props = defineProps<{
  modelValue: boolean
  title?: string
  // 已关联用户 ID，用于在候选中过滤
  excludeIds?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [userIds: string[]]
}>()

const loading = ref(false)
const options = ref<{ id: string; username: string; email: string }[]>([])
const selectedIds = ref<string[]>([])

async function searchUsers(keyword: string) {
  if (!keyword) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const page = await fetchUsers({ keyword, status: 'active', pageNo: 1, pageSize: 20 })
    const exclude = props.excludeIds ?? []
    options.value = page.list
      .filter((u) => !exclude.includes(u.id))
      .map((u) => ({ id: u.id, username: u.username, email: u.email }))
  } catch {
    options.value = []
  } finally {
    loading.value = false
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
  emit('confirm', [...selectedIds.value])
}

// 每次打开重置选择态
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      selectedIds.value = []
      options.value = []
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
      placeholder="输入用户名 / 邮箱搜索（仅活跃用户）"
      :remote-method="searchUsers"
      :loading="loading"
      style="width: 100%"
    >
      <el-option
        v-for="u in options"
        :key="u.id"
        :label="`${u.username} (${u.email})`"
        :value="u.id"
      />
    </el-select>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>
