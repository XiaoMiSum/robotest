<script setup lang="ts">
import { computed } from 'vue'
import { useAiStore } from '@/stores/ai'

/**
 * 对话模型选择器（交互式 AI 功能入口复用，通用规范见交互设计 2.8 / 详细设计 4.11）。
 *
 * 数据源为 stores/ai 的已启用模型清单；默认选中用户上次选择（本地记忆），失效静默回退系统默认；
 * 切换即写入记忆并作用于本次及后续所有交互式调用；仅一个可用模型时不渲染（无选择意义）。
 */
const aiStore = useAiStore()

// 当前展示的选中项：记忆值仍有效则用之，否则系统默认
const currentValue = computed<string | undefined>(() => {
  const remembered = aiStore.selectedModelId
  if (remembered && aiStore.chatModels.some((m) => m.id === remembered)) {
    return remembered
  }
  return aiStore.chatModels.find((m) => m.isDefault)?.id
})

// 仅一个可用模型时不渲染
const visible = computed(() => aiStore.chatModels.length > 1)

function handleChange(id: string): void {
  aiStore.setSelectedModelId(id)
}
</script>

<template>
  <el-select
    v-if="visible"
    :model-value="currentValue"
    size="small"
    placeholder="选择模型"
    class="ai-model-select"
    @update:model-value="handleChange"
  >
    <template #prefix>
      <el-icon><MagicStick /></el-icon>
    </template>
    <el-option v-for="m in aiStore.chatModels" :key="m.id" :label="m.name" :value="m.id">
      <span class="ai-model-select__name">{{ m.name }}</span>
      <el-tag v-if="m.isDefault" size="small" type="info" class="ai-model-select__badge">默认</el-tag>
    </el-option>
  </el-select>
</template>

<style scoped lang="scss">
.ai-model-select {
  width: 160px;
}

.ai-model-select__name {
  margin-right: var(--space-sm);
}

.ai-model-select__badge {
  float: right;
}
</style>
