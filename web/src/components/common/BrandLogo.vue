<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 32 })
</script>

<template>
  <span
    class="brand-logo"
    :style="{ width: `${size}px`, height: `${size}px`, borderRadius: `${Math.round(size / 4)}px` }"
  >
    <svg
      :width="Math.round(size * 0.68)"
      :height="Math.round(size * 0.68)"
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <!-- 天线 -->
      <circle cx="12" cy="3" r="1.4" fill="currentColor" />
      <rect x="11.35" y="4.2" width="1.3" height="2.4" rx="0.65" fill="currentColor" />
      <!-- 耳朵 -->
      <rect x="1.6" y="10.4" width="2" height="5" rx="1" fill="currentColor" />
      <rect x="20.4" y="10.4" width="2" height="5" rx="1" fill="currentColor" />
      <!-- 头部 -->
      <rect x="4.4" y="6.6" width="15.2" height="12.4" rx="3.6" fill="currentColor" />
      <!-- 眼睛与嘴:挖空效果,取容器背景色 -->
      <circle cx="9.2" cy="11.6" r="1.5" class="brand-logo__cutout" />
      <circle cx="14.8" cy="11.6" r="1.5" class="brand-logo__cutout" />
      <rect x="9" y="15" width="6" height="1.4" rx="0.7" class="brand-logo__cutout" />
    </svg>
  </span>
</template>

<style scoped lang="scss">
.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--color-primary-600);
  color: var(--color-neutral-0);
}

.brand-logo__cutout {
  fill: var(--color-primary-600);
}
</style>
