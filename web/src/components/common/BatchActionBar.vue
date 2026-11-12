<script setup lang="ts">
// 批量入口收口为底部浮动条，避免工具栏/页脚各自一套批量形态导致交互不一致
const emit = defineEmits<{
  (e: 'clear'): void
}>()

defineProps<{
  count: number
}>()
</script>

<template>
  <div v-if="count > 0" class="batch-action-bar" role="region" aria-label="批量操作">
    <span class="batch-action-bar__count">已选 <b>{{ count }}</b> 项</span>
    <span class="batch-action-bar__sep" />
    <slot />
    <span class="batch-action-bar__sep" />
    <button type="button" class="batch-action-bar__clear" @click="emit('clear')">清空</button>
  </div>
</template>

<style scoped lang="scss">
.batch-action-bar {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 80;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 999px;
  background: rgba(32, 35, 41, .96);
  color: #fff;
  font-size: 14px;
  box-shadow: 0 12px 32px -10px rgba(0, 0, 0, .45);
}

.batch-action-bar__count b {
  color: #7cc0ff;
  font-weight: 700;
}

.batch-action-bar__sep {
  width: 1px;
  height: 18px;
  background: rgba(255, 255, 255, .2);
}

.batch-action-bar__clear {
  background: none;
  border: none;
  padding: 0 4px;
  color: rgba(255, 255, 255, .65);
  cursor: pointer;
  font-size: 14px;

  &:hover {
    color: #fff;
  }
}
</style>
