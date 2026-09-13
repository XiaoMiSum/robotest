<script setup lang="ts">
const enabled = defineModel<boolean>({ required: true })
defineProps<{
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'before-change'): Promise<boolean>
}>()
</script>

<template>
  <el-card shadow="never" class="ai-master-switch">
    <div class="ai-master-switch__row">
      <div class="ai-master-switch__icon">
        <el-icon :size="22"><Cpu /></el-icon>
      </div>
      <div class="ai-master-switch__text">
        <div class="ai-master-switch__title">AI 能力总开关</div>
        <div class="ai-master-switch__hint">
          关闭后前端隐藏全部 AI 入口，进行中任务被取消；开启需已启用至少一个对话模型
        </div>
      </div>
      <el-switch
        v-model="enabled"
        :loading="loading"
        size="large"
        :before-change="() => emit('before-change')"
      />
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.ai-master-switch__row {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.ai-master-switch__icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-lg);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
  flex-shrink: 0;
}

.ai-master-switch__text {
  flex: 1;
}

.ai-master-switch__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-neutral-800);
}

.ai-master-switch__hint {
  font-size: 12px;
  color: var(--color-neutral-500);
  margin-top: 2px;
}
</style>