<script setup lang="ts">
import { computed, ref } from 'vue'
import { validateImportText, IMPORT_TEXT_MAX_LENGTH } from './aiPanelModes'

/**
 * 「AI 指令」输入区（交互设计 4.1）：脑图工具栏展开的双模式输入区。
 * 梯队一仅交付导入模式（US-AI-016）：模式徽标固定「导入」，指令翻译模式（US-AI-015，梯队四）
 * 开关置灰提示后续版本提供；自动模式判定逻辑随 015 一并交付。
 */
const emit = defineEmits<{
  /** 携文本发起导入解析（打开 import 模式生成抽屉） */
  execute: [text: string]
  close: []
}>()

const text = ref('')

// 超长即时红字提示并禁用执行（交互设计 4.5）；空文本仅禁用不提示
const lengthError = computed(() =>
  text.value.length > IMPORT_TEXT_MAX_LENGTH ? validateImportText(text.value) : null,
)
const canExecute = computed(() => text.value.trim().length > 0 && !lengthError.value)

function execute(): void {
  if (!canExecute.value) return
  emit('execute', text.value)
}
</script>

<template>
  <div class="ai-command-bar">
    <div class="ai-command-bar__main">
      <el-tag size="small" class="ai-command-bar__mode" type="primary">✨ 导入</el-tag>
      <el-tooltip content="批量指令将在后续版本提供" placement="top">
        <el-tag size="small" type="info" class="ai-command-bar__mode is-disabled">指令</el-tag>
      </el-tooltip>
      <el-input
        v-model="text"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 6 }"
        resize="none"
        class="ai-command-bar__input"
        placeholder="粘贴外部需求文档、Excel 用例列表等文本，AI 将解析为用例结构插入当前脑图…"
        @keydown.enter.exact.prevent="execute"
      />
      <AiModelSelect />
      <el-button type="primary" size="small" :disabled="!canExecute" @click="execute">执行</el-button>
      <el-button size="small" text @click="emit('close')"><el-icon><Close /></el-icon></el-button>
    </div>
    <div v-if="lengthError" class="ai-command-bar__error">{{ lengthError }}</div>
  </div>
</template>

<style scoped lang="scss">
.ai-command-bar {
  padding: 6px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.ai-command-bar__main {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.ai-command-bar__mode {
  flex-shrink: 0;
  margin-top: 4px;

  &.is-disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.ai-command-bar__input {
  flex: 1;
}

.ai-command-bar__error {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-danger, #f56c6c);
}
</style>
