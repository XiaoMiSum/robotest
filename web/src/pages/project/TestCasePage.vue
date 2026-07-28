<script setup lang="ts">
import { ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import ModuleTree from '@/components/project/ModuleTree.vue'
import MindMapEditor from '@/components/project/MindMapEditor.vue'

const selectedDocId = ref('')
const selectedDocName = ref('')

function handleSelectDocument(docId: string, docName: string) {
  selectedDocId.value = docId
  selectedDocName.value = docName
}

// 处于文档中时离开需二次确认，防止误触打断编辑（切换文档的确认在 ModuleTree 内）；
// 供路由守卫与父组件（功能测试页子页面切换不走路由）共用，文案与判断单点维护
async function confirmLeave(): Promise<boolean> {
  if (!selectedDocId.value) return true
  try {
    await ElMessageBox.confirm('确定离开当前文档吗？', '离开页面', { type: 'warning' })
    return true
  } catch {
    return false
  }
}

onBeforeRouteLeave(confirmLeave)

defineExpose({ confirmLeave })
</script>

<template>
  <div class="test-case-page">
    <div class="test-case-page__workspace">
      <el-card shadow="never" class="test-case-page__tree-card">
        <ModuleTree @select-document="handleSelectDocument" />
      </el-card>
      <el-card shadow="never" class="test-case-page__editor-card">
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
      </el-card>
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
  gap: var(--space-lg);
  flex: 1;
  overflow: hidden;
}

.test-case-page__tree-card {
  width: 240px;
  flex-shrink: 0;
  :deep(.el-card__body) {
    padding: 0;
    overflow: auto;
    height: 100%;
  }
}

.test-case-page__editor-card {
  flex: 1;
  min-width: 0;
  :deep(.el-card__body) {
    padding: 0;
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
  }
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
