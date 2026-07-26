<script setup lang="ts">
</script>

<template>
  <div class="api-test-placeholder">
    <div class="api-test-placeholder__inner">
      <div class="api-test-placeholder__icon">
        <el-icon :size="48"><Connection /></el-icon>
      </div>
      <div class="api-test-placeholder__title">接口测试</div>
      <p class="api-test-placeholder__desc">功能建设中，敬请期待...</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.api-test-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.api-test-placeholder__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.api-test-placeholder__icon {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-xl);
  background: var(--color-neutral-100);
  color: var(--color-neutral-400);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-lg);
}

.api-test-placeholder__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-700);
  margin: 0 0 var(--space-sm);
}

.api-test-placeholder__desc {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-400);
  margin: 0;
}
</style>
