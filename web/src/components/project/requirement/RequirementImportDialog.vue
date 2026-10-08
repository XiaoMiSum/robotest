<script setup lang="ts">
import { ref, watch } from 'vue'
import type { UploadFile } from 'element-plus'
import { useRequirementImport } from '@/composables/project/requirement/useRequirementImport'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 导入任务已提交并跳转任务详情页 */
  submitted: []
}>()

const { selectedFile, fileError, submitting, reset, pickFile, removeFile, submit } =
  useRequirementImport()

/** 仅用于列表回显，选中结果以 selectedFile 为准 */
const fileList = ref<UploadFile[]>([])

// 白名单与 20MB 上限与后端 3.8 一致，accept 只做选择器过滤，真正校验在 pickFile
const ACCEPT = '.md,.docx,.png,.jpg,.jpeg,.gif,.webp,.bmp'

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    reset()
    fileList.value = []
  },
)

function handleChange(uploadFile: UploadFile): void {
  const raw = uploadFile.raw
  if (!raw) return
  if (pickFile(raw)) {
    fileList.value = [uploadFile]
  } else {
    // 预校验失败即时提示，不保留非法文件（交互 06 §2.3.1）
    fileList.value = []
  }
}

function handleRemove(): void {
  removeFile()
  fileList.value = []
}

async function handleSubmit(): Promise<void> {
  const submitted = await submit()
  if (submitted) {
    emit('update:modelValue', false)
    emit('submitted')
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="导入需求"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <el-upload
      v-model:file-list="fileList"
      drag
      :auto-upload="false"
      :limit="1"
      :accept="ACCEPT"
      :on-change="handleChange"
      :on-remove="handleRemove"
      :disabled="submitting"
    >
      <el-icon class="import-dialog__upload-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
      <template #tip>
        <div class="import-dialog__hint">
          支持 Markdown（.md）、Word（.docx）与图片，单文件不超过 20MB；提交后在任务详情页跟踪进度并审核导入结果
        </div>
      </template>
    </el-upload>

    <el-alert
      v-if="fileError"
      :title="fileError"
      type="error"
      show-icon
      :closable="false"
      class="import-dialog__error"
    />

    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="selectedFile === null"
        @click="handleSubmit"
      >开始导入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.import-dialog__upload-icon {
  margin-bottom: var(--space-xs);
  color: var(--color-neutral-400);
  font-size: 40px;
}

.import-dialog__hint {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  line-height: 1.5;
}

.import-dialog__error {
  margin-top: var(--space-md);
}
</style>
