<script setup lang="ts">
import { useBugBatchActions } from '@/composables/project/bug/useBugBatchActions'
import { BUG_STATUS_LABEL } from '@/composables/project/bug/bugStatus'

defineProps<{
  aiAvailable: boolean
}>()

const emit = defineEmits<{ submitted: [] }>()

const {
  classifyVisible,
  classifyBusy,
  classifyStatuses,
  classifySummary,
  scanBusy,
  STATUS_OPTIONS,
  openClassify,
  submitClassify,
  submitScan,
} = useBugBatchActions(() => emit('submitted'))
</script>

<template>
  <div class="batch-actions">
    <el-button :disabled="!aiAvailable" @click="openClassify">批量分类</el-button>
    <el-button :disabled="!aiAvailable" :loading="scanBusy" @click="submitScan">
      存量重复扫描
    </el-button>

    <el-dialog v-model="classifyVisible" title="批量分类" width="420px">
      <p class="batch-actions__summary">{{ classifySummary }}</p>
      <el-form label-position="top">
        <el-form-item label="缺陷状态范围">
          <el-checkbox-group v-model="classifyStatuses">
            <el-checkbox v-for="status in STATUS_OPTIONS" :key="status" :value="status">
              {{ BUG_STATUS_LABEL[status] }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classifyVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="classifyBusy"
          :disabled="classifyStatuses.length === 0"
          @click="submitClassify"
        >
          发起
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.batch-actions__summary {
  margin: 0 0 var(--space-md);
  color: var(--color-neutral-600);
}
</style>
