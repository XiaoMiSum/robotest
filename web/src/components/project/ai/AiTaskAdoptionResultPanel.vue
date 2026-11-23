<script setup lang="ts">
import type { AiTaskProposalSummary } from '@/types'
import type { AiTaskAdoptionResultMeta } from '@/composables/project/ai/taskPresentation'
import AiProposalReviewPanel from './AiProposalReviewPanel.vue'

defineProps<{
  taskId: string
  taskType: string
  proposals: AiTaskProposalSummary[]
  result: AiTaskAdoptionResultMeta | null
}>()
</script>

<template>
  <AiProposalReviewPanel
    :task-id="taskId"
    :task-type="taskType"
    :proposals="proposals"
    readonly
  />
</template>
