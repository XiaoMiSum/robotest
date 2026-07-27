<script setup lang="ts">
import { ref } from 'vue'
import ModuleTree from '@/components/project/ModuleTree.vue'
import MindMapEditor from '@/components/project/MindMapEditor.vue'

const selectedDocId = ref('')
const selectedDocName = ref('')

function handleSelectDocument(docId: string, docName: string) {
  selectedDocId.value = docId
  selectedDocName.value = docName
}
</script>

<template>
  <div class="test-case-page">
    <div class="test-case-page__workspace">
      <div class="test-case-page__tree-panel">
        <ModuleTree @select-document="handleSelectDocument" />
      </div>
      <div class="test-case-page__editor-panel">
        <div v-if="!selectedDocId" class="test-case-page__placeholder">
          <el-empty description="请在左侧模块树中选择一个文档" />
        </div>
        <template v-else>
          <div class="test-case-page__doc-header">
            <el-icon><Document /></el-icon>
            <span>{{ selectedDocName }}</span>
          </div>
          <MindMapEditor :doc-id="selectedDocId" mode="edit" />
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.test-case-page {
  display: flex;
  height: 100%;
}

.test-case-page__workspace {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.test-case-page__tree-panel {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--color-neutral-200);
  overflow: auto;
}

.test-case-page__editor-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.test-case-page__doc-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-700);
  border-bottom: 1px solid var(--color-neutral-200);
  flex-shrink: 0;
}

.test-case-page__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}
</style>
