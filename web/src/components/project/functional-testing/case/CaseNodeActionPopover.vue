<script setup lang="ts">
import { computed } from 'vue'
import { Link, MagicStick } from '@element-plus/icons-vue'

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
  'ai-optimize': []
  'link-requirement': []
}>()

const style = computed(() => ({
  left: `${props.x}px`,
  top: `${props.y}px`,
}))

function onAiOptimize(): void {
  if (!props.canLaunchAi) return
  emit('ai-optimize')
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
        <el-tooltip :content="aiGateReason || '对选中用例发起 AI 优化'" placement="right">
          <el-button text class="case-node-popover__item case-node-popover__item--ai" :disabled="!canLaunchAi" @click="onAiOptimize">
            <el-icon><MagicStick /></el-icon>
            <span>AI 优化</span>
          </el-button>
        </el-tooltip>
      </template>

      <el-tooltip content="维护当前用例关联的需求" placement="right">
        <el-button text class="case-node-popover__item case-node-popover__item--link" @click="onLinkRequirement">
          <el-icon><Link /></el-icon>
          <span>关联需求</span>
        </el-button>
      </el-tooltip>
    </div>
  </teleport>
</template>

<style scoped lang="scss">
.case-node-popover {
  position: fixed;
  z-index: 9998;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.case-node-popover__item {
  justify-content: flex-start;
  gap: 6px;
}

.case-node-popover__item--ai {
  --el-button-text-color: var(--color-success);
  --el-button-hover-text-color: var(--color-success-strong);
  animation: case-node-ai-pulse 1.6s ease-in-out infinite;

  &.is-disabled { animation: none; }
}

.case-node-popover__item--link {
  --el-button-text-color: var(--color-primary-500);
  --el-button-hover-text-color: var(--color-primary-600);
}

@keyframes case-node-ai-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}
</style>
