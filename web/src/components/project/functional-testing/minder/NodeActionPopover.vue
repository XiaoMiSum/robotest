<script setup lang="ts">
/**
 * 用例节点悬浮窗壳（评审/计划共用）：fixed 定位、透明背景、入口水平并排。
 * 默认 teleport 到 body，全屏容器内的模式可经 to 改写挂载点；
 * 入口由各模式组件经 slot 传入，点击浮层不冒泡到画布。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    visible: boolean
    x: number
    y: number
    to?: string | HTMLElement
  }>(),
  { to: 'body' },
)

const style = computed(() => ({
  left: `${props.x}px`,
  top: `${props.y}px`,
}))
</script>

<template>
  <teleport :to="to">
    <div
      v-if="visible"
      class="node-action-popover"
      :style="style"
      @click.stop
      @contextmenu.prevent
    >
      <slot />
    </div>
  </teleport>
</template>

<style scoped lang="scss">
.node-action-popover {
  position: fixed;
  z-index: 9998;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  background: transparent;
}
</style>
