<script setup lang="ts">
import { computed } from 'vue'
import { Link, MagicStick, Sort } from '@element-plus/icons-vue'

const props = defineProps<{
  visible: boolean
  x: number
  y: number
  aiEntryVisible: boolean
  aiGateReason: string
  canLaunchAi: boolean
}>()

const emit = defineEmits<{
  close: []
  'ai-complete': []
  'ai-priority': []
  'link-requirement': []
}>()

const style = computed(() => ({
  left: `${props.x}px`,
  top: `${props.y}px`,
}))

function onAiComplete(): void {
  if (!props.canLaunchAi) return
  emit('ai-complete')
}

function onAiPriority(): void {
  if (!props.canLaunchAi) return
  emit('ai-priority')
}

function onLinkRequirement(): void {
  emit('link-requirement')
}
</script>

<template>
  <teleport to="body">
    <div
      v-if="visible"
      class="case-node-popover"
      :style="style"
      @click.stop
      @contextmenu.prevent
    >
      <template v-if="aiEntryVisible">
        <el-tooltip :content="aiGateReason || '对选中用例发起 AI 补全'" placement="right">
          <button
            type="button"
            class="case-node-popover__item"
            :class="{ 'is-disabled': !canLaunchAi }"
            :disabled="!canLaunchAi"
            @click="onAiComplete"
          >
            <el-icon><MagicStick /></el-icon>
            <span>AI 补全</span>
          </button>
        </el-tooltip>
        <el-tooltip :content="aiGateReason || '对选中用例发起级别推荐'" placement="right">
          <button
            type="button"
            class="case-node-popover__item"
            :class="{ 'is-disabled': !canLaunchAi }"
            :disabled="!canLaunchAi"
            @click="onAiPriority"
          >
            <el-icon><Sort /></el-icon>
            <span>级别推荐</span>
          </button>
        </el-tooltip>
        <div class="case-node-popover__divider" />
      </template>

      <el-tooltip content="维护当前用例关联的需求" placement="right">
        <button
          type="button"
          class="case-node-popover__item"
          @click="onLinkRequirement"
        >
          <el-icon><Link /></el-icon>
          <span>关联需求</span>
        </button>
      </el-tooltip>
    </div>
  </teleport>
</template>

<style scoped lang="scss">
@use '../minder/minder-menu' as menu;

.case-node-popover {
  position: fixed;
  z-index: 9998;
  min-width: 168px;
  padding: 4px 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  box-shadow: var(--el-box-shadow-light);
}

.case-node-popover__item {
  @include menu.minder-menu-row;
  width: 100%;
  padding: 7px 16px;
  font-size: menu.$minder-menu-font-size;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  justify-content: flex-start;
  gap: 8px;

  &:hover {
    background: menu.$minder-menu-hover-fill;
    color: menu.$minder-menu-hover-color;
  }

  &.is-disabled,
  &:disabled {
    color: var(--el-text-color-disabled);
    cursor: not-allowed;

    &:hover {
      background: transparent;
      color: var(--el-text-color-disabled);
    }
  }
}

.case-node-popover__divider {
  height: 1px;
  margin: 4px 0;
  background: var(--el-border-color-lighter);
}
</style>
