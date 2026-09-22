<script setup lang="ts">
defineProps<{
  autoSaveLabel: string
  isCreateMode: boolean
}>()
</script>

<template>
  <footer class="scene-editor__footer">
    <span class="scene-editor__autosave">{{ autoSaveLabel || (isCreateMode ? '草稿未保存' : '自动保存已开启') }}</span>
  </footer>
</template>
