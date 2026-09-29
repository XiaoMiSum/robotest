<script setup lang="ts">
defineProps<{
  modelValue: boolean
  expiresAt: string
  maxUses: number | null
  submitting: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:expiresAt': [value: string]
  'update:maxUses': [value: number | null]
  submit: []
}>()

function setVisible(value: boolean): void {
  emit('update:modelValue', value)
}

// 清空日期时控件可能回吐 null/undefined，统一归一到表单约定的字符串
function handleExpiresAtChange(value: unknown): void {
  emit('update:expiresAt', typeof value === 'string' ? value : '')
}

function handleMaxUsesChange(value: unknown): void {
  emit('update:maxUses', typeof value === 'number' ? value : null)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="生成邀请链接"
    width="460px"
    @update:model-value="setVisible"
  >
    <el-form label-position="top">
      <el-form-item label="过期时间">
        <el-date-picker
          :model-value="expiresAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="留空表示永不过期"
          class="invitation-create-dialog__date-picker"
          @update:model-value="handleExpiresAtChange"
        />
      </el-form-item>
      <el-form-item label="最大使用次数">
        <el-input-number
          :model-value="maxUses"
          :min="1"
          :max="10000"
          placeholder="留空表示不限"
          class="invitation-create-dialog__uses-input"
          @update:model-value="handleMaxUsesChange"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="setVisible(false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="emit('submit')">生成</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.invitation-create-dialog__date-picker,
.invitation-create-dialog__uses-input {
  width: 100%;
}
</style>
