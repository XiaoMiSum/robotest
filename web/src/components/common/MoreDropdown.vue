<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'
import type { ElDropdown } from 'element-plus'

defineOptions({ inheritAttrs: false })

const dropdownRef = ref<InstanceType<typeof ElDropdown> | null>(null)
const rootRef = ref<HTMLElement | null>(null)

// Safari 对空白区域的 click 派发与 Chromium 不同，用唯一类名识别自身菜单，避免按下菜单时误收起
const menuClass = `more-dropdown-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`

function onDocumentMousedown(event: MouseEvent): void {
  const target = event.target
  if (!(target instanceof Node)) return
  if (rootRef.value?.contains(target)) return
  if (
    event.composedPath().some((node) => node instanceof Element && node.closest(`.${menuClass}`))
  ) {
    return
  }
  dropdownRef.value?.handleClose()
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentMousedown, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentMousedown, true)
})
</script>

<template>
  <el-dropdown ref="dropdownRef" v-bind="$attrs" :popper-class="menuClass">
    <span ref="rootRef" class="more-dropdown__trigger"><slot name="trigger" /></span>
    <template #dropdown>
      <slot name="dropdown" />
    </template>
  </el-dropdown>
</template>

<style scoped lang="scss">
.more-dropdown__trigger {
  display: inline-flex;
  align-items: center;
}
</style>
