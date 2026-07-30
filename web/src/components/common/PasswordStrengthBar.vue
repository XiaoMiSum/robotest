<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ password: string }>()

const STRENGTH_COLORS = ['var(--color-danger)', 'var(--color-warning)', '#f97316', 'var(--color-success)']

const strength = computed(() => {
  const val = props.password
  if (!val) return 0
  // 长度不足 8 时按长度比例给出基础强度，保证始终有视觉反馈
  if (val.length < 8) return Math.max(1, Math.floor(val.length / 2))
  return [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((re) => re.test(val)).length
})

const label = computed(() => {
  const s = strength.value
  if (s <= 0) return ''
  if (s <= 1) return '弱'
  if (s === 2) return '较弱'
  if (s === 3) return '中'
  return '强'
})

const color = computed(() => {
  const s = strength.value
  if (s <= 0) return 'var(--color-neutral-300)'
  return STRENGTH_COLORS[s - 1]
})
</script>

<template>
  <div v-if="password" class="pwd-strength">
    <div class="pwd-strength__bar">
      <div
        v-for="i in 4"
        :key="i"
        class="pwd-strength__segment"
        :class="{ 'pwd-strength__segment--active': i <= strength }"
        :style="i <= strength ? { backgroundColor: STRENGTH_COLORS[i - 1] } : undefined"
      />
    </div>
    <span class="pwd-strength__label" :style="{ color }">{{ label }}</span>
  </div>
</template>

<style scoped lang="scss">
.pwd-strength {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: 6px;
  width: 100%;
}

.pwd-strength__bar {
  flex: 1;
  display: flex;
  gap: 4px;
}

.pwd-strength__segment {
  flex: 1;
  height: 4px;
  border-radius: var(--radius-full);
  background-color: var(--color-neutral-200);
  transition: background-color 0.3s ease;
}

.pwd-strength__label {
  font-size: var(--font-size-2xs);
  font-weight: 500;
  white-space: nowrap;
}
</style>
