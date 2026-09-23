<script setup lang="ts">
import { ref } from 'vue'
import { MagicStick, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { InterfaceEditorForm } from '@/composables/project/api-testing/interface/interfacesModel'

const form = defineModel<InterfaceEditorForm>({ required: true })

const responseTab = ref<'body' | 'headers'>('body')

function activeResponseText(): { get: string; set: (v: string) => void } {
  return responseTab.value === 'body'
    ? { get: form.value.responseBodyText, set: (v) => { form.value.responseBodyText = v } }
    : { get: form.value.responseHeadersText, set: (v) => { form.value.responseHeadersText = v } }
}

function handleFormatResponse() {
  const ref = activeResponseText()
  const text = ref.get.trim()
  if (!text) return
  try {
    ref.set(JSON.stringify(JSON.parse(text) as unknown, null, 2))
  } catch {
    ElMessage.warning('JSON 格式非法，无法格式化')
  }
}

async function handleCopyResponse() {
  const text = activeResponseText().get
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}
</script>

<template>
  <div class="interface-editor__response" data-test="editor-response-card">
    <div class="interface-editor__response-toolbar">
      <span class="interface-editor__response-title">响应示例</span>
      <div class="interface-editor__response-modes">
        <button
          class="interface-editor__response-mode"
          :class="{ 'is-active': responseTab === 'body' }"
          @click="responseTab = 'body'"
        >Body</button>
        <button
          class="interface-editor__response-mode"
          :class="{ 'is-active': responseTab === 'headers' }"
          @click="responseTab = 'headers'"
        >Headers</button>
      </div>
      <el-tooltip content="格式化（修正 JSON 缩进）" placement="top">
        <button class="interface-editor__response-icon" @click="handleFormatResponse">
          <el-icon><MagicStick /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip content="复制" placement="top">
        <button class="interface-editor__response-icon" @click="handleCopyResponse">
          <el-icon><CopyDocument /></el-icon>
        </button>
      </el-tooltip>
    </div>

    <div class="interface-editor__response-body">
      <el-input
        v-if="responseTab === 'body'"
        v-model="form.responseBodyText"
        type="textarea"
        :rows="12"
        class="interface-editor__response-editor"
        placeholder='{"code": 200, "data": {...}}'
        data-test="editor-response-body-input"
      />
      <el-input
        v-else
        v-model="form.responseHeadersText"
        type="textarea"
        :rows="12"
        class="interface-editor__response-editor"
        placeholder='{"Content-Type": "application/json", "X-Request-Id": "..."}'
        data-test="editor-response-headers-input"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.interface-editor__response {
  flex: 1;
  min-height: 80px;
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.interface-editor__response-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-neutral-100);
}

.interface-editor__response-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-800);
}

.interface-editor__response-modes {
  display: flex;
  gap: 2px;
}

.interface-editor__response-mode {
  padding: 4px 10px;
  font-size: 12px;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-neutral-500);
  transition: all 0.15s;

  &:hover {
    color: var(--color-neutral-700);
  }

  &.is-active {
    background: var(--color-neutral-100);
    color: var(--color-neutral-800);
    font-weight: 500;
  }
}

.interface-editor__response-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  background: none;
  border-radius: 4px;
  color: var(--color-neutral-400);
  cursor: pointer;
  font-size: 14px;
  transition: color 0.15s, background 0.15s;

  &:hover {
    color: var(--color-neutral-700);
    background: var(--color-neutral-100);
  }
}

.interface-editor__response-body {
  min-height: 0;
}

.interface-editor__response-editor {
  :deep(.el-textarea__inner) {
    font-family: ui-monospace, SFMono-Regular, monospace;
  }
}
</style>
