<script setup lang="ts">
/**
 * 右键菜单壳（三模式组件共用）：teleport 到 body 的 fixed 定位容器与菜单样式，
 * 菜单项由各模式组件经 slot 传入；点击任意菜单项后整体关闭（由父层收到 close 处理）。
 */
defineProps<{ x: number; y: number }>()

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <teleport to="body">
    <div
      class="mindmap-context-menu"
      :style="{ left: x + 'px', top: y + 'px' }"
      @click="emit('close')"
    >
      <slot />
    </div>
  </teleport>
</template>

<style scoped lang="scss">
.mindmap-context-menu {
  position: fixed;
  z-index: 9999;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  box-shadow: var(--el-box-shadow-light);
  padding: 4px 0;
  min-width: 170px;
}

/* 菜单项由父组件经 slot 传入，样式须用 :slotted 才能命中 */
:slotted(.mindmap-context-menu__item) {
  padding: 7px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}

:slotted(.mindmap-context-menu__item:hover) {
  background: var(--el-fill-color-light);
}

:slotted(.mindmap-context-menu__item--danger) {
  color: var(--el-color-danger);
}

:slotted(.mindmap-context-menu__item--indent) {
  padding-left: 28px;
}

:slotted(.mindmap-context-menu__subtitle) {
  padding: 5px 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: default;
}

:slotted(.mindmap-context-menu__divider) {
  height: 1px;
  background: var(--el-border-color-lighter);
  margin: 4px 0;
}
</style>
