<script setup lang="ts">
import { watch } from 'vue'
import { useWorkspaceCreate } from '@/composables/workspace/useWorkspaceCreate'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [id: string]
}>()

const {
  formRef,
  form,
  rules,
  submitting,
  adminSearching,
  adminOptions,
  reset,
  searchAdmins,
  submit,
} = useWorkspaceCreate()

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) reset()
  },
  { immediate: true },
)

function setVisible(value: boolean): void {
  emit('update:modelValue', value)
}

async function handleSubmit(): Promise<void> {
  const id = await submit()
  if (id === null) return
  emit('created', id)
  setVisible(false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="新建工作空间"
    width="480px"
    @update:model-value="setVisible"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" @submit.prevent>
      <el-form-item label="名称" prop="name">
        <el-input
          v-model="form.name"
          placeholder="请输入工作空间名称"
          maxlength="50"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="管理员" prop="adminUserId">
        <el-select
          v-model="form.adminUserId"
          filterable
          remote
          reserve-keyword
          clearable
          placeholder="输入姓名搜索（仅活跃用户）"
          :remote-method="searchAdmins"
          :loading="adminSearching"
          style="width: 100%"
        >
          <el-option
            v-for="user in adminOptions"
            :key="user.id"
            :label="user.name"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="请输入工作空间描述（可选）"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="setVisible(false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
