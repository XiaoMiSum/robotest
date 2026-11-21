<script setup lang="ts">
import { useBugDuplicateScan } from '@/composables/project/bug/useBugDuplicateScan'

defineProps<{
  aiAvailable: boolean
}>()

const emit = defineEmits<{ submitted: [] }>()

const { scanBusy, submitScan } = useBugDuplicateScan(() => emit('submitted'))
</script>

<template>
  <div class="duplicate-scan-actions">
    <el-button :disabled="!aiAvailable" :loading="scanBusy" @click="submitScan">
      存量重复扫描
    </el-button>
  </div>
</template>
