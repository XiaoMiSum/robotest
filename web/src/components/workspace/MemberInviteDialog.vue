<script setup lang="ts">
import type { UserSimple } from '@/types'

defineProps<{
  modelValue: boolean
  selectedUserIds: string[]
  userOptions: UserSimple[]
  searching: boolean
  submitting: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:selectedUserIds': [value: string[]]
  search: [keyword: string]
  submit: []
}>()

function setVisible(value: boolean): void {
  emit('update:modelValue', value)
}

function handleSelectedChange(value: unknown): void {
  emit('update:selectedUserIds', Array.isArray(value) ? (value as string[]) : [])
}

function handleSearch(keyword: unknown): void {
  emit('search', typeof keyword === 'string' ? keyword : '')
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="邀请成员"
    width="520px"
    @update:model-value="setVisible"
  >
    <p class="member-invite-dialog__tip">新成员将使用空间成员角色加入。</p>
    <el-select
      :model-value="selectedUserIds"
      class="member-invite-dialog__select"
      multiple
      filterable
      remote
      reserve-keyword
      placeholder="输入姓名、用户名或邮箱搜索"
      :remote-method="handleSearch"
      :loading="searching"
      @update:model-value="handleSelectedChange"
    >
      <el-option
        v-for="user in userOptions"
        :key="user.id"
        :label="user.name"
        :value="user.id"
      />
    </el-select>
    <template #footer>
      <el-button @click="setVisible(false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="emit('submit')">
        确认邀请
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.member-invite-dialog__tip {
  margin: 0 0 var(--space-md);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}

.member-invite-dialog__select {
  width: 100%;
}
</style>
