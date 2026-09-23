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
  <div class="ai-master">
    <span class="ai-master__status" :class="enabled ? 'is-on' : 'is-off'">
      <span class="ai-master__dot" />
      {{ enabled ? 'AI 已启用' : 'AI 已停用' }}
    </span>
    <span class="ai-master__control">
      AI 能力总开关
      <el-switch
        v-model="enabled"
        :loading="loading"
        :before-change="() => emit('before-change')"
      />
    </span>
  </div>
</template>

<style scoped lang="scss">
.ai-master {
  display: inline-flex;
  align-items: center;
  gap: var(--space-md);
  flex-shrink: 0;
}

/* 状态徽标：点色 + 文案双通道（绿=启用 / 灰=停用），不依赖单一颜色感知 */
.ai-master__status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
}

.ai-master__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ai-master__status.is-on .ai-master__dot {
  background: var(--color-success);
}

.ai-master__status.is-off .ai-master__dot {
  background: var(--color-neutral-400);
}

.ai-master__control {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-700);
}
</style>
