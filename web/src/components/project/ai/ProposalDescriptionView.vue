<script setup lang="ts">
import MarkdownView from '@/components/common/MarkdownView.vue'

defineProps<{ content: string }>()
</script>

<template>
  <div class="proposal-description">
    <MarkdownView :content="content" />
  </div>
</template>

<style scoped lang="scss">
// 审核卡片比正文场景紧凑，收敛 Vditor 预览的标题与列表间距
.proposal-description {
  :deep(.markdown-view h1),
  :deep(.markdown-view h2) {
    margin: 0 0 var(--space-sm);
    font-size: var(--font-size-base);
    font-weight: 600;
    color: var(--color-neutral-900);
  }

  :deep(.markdown-view h3),
  :deep(.markdown-view h4) {
    margin: var(--space-sm) 0 4px;
    font-size: var(--font-size-sm);
    font-weight: 600;
    color: var(--color-neutral-900);
  }

  :deep(.markdown-view p) {
    margin: 0 0 var(--space-sm);
  }

  :deep(.markdown-view ul),
  :deep(.markdown-view ol) {
    margin: 0 0 var(--space-sm);
    padding-left: 22px;
  }

  :deep(.markdown-view li) {
    margin-bottom: 4px;
  }
}
</style>
