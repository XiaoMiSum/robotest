<script setup lang="ts">
import { CopyDocument } from '@element-plus/icons-vue'

defineProps<{
  modelValue: boolean
  link: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  copy: [link: string]
}>()

function setVisible(value: boolean): void {
  emit('update:modelValue', value)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="邀请链接已创建"
    width="540px"
    :close-on-click-modal="false"
    @update:model-value="setVisible"
  >
    <p class="invitation-link-dialog__tip">请复制链接并发送给受邀成员：</p>
    <el-input :model-value="link" readonly>
      <template #append>
        <el-button :icon="CopyDocument" @click="emit('copy', link)">复制</el-button>
      </template>
    </el-input>
    <template #footer>
      <el-button type="primary" @click="setVisible(false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.invitation-link-dialog__tip {
  margin: 0 0 var(--space-md);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
}
</style>
