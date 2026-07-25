<script setup lang="ts">
import { ref } from 'vue'
import ModuleTree from '@/components/project/ModuleTree.vue'
import MindMapEditor from '@/components/project/MindMapEditor.vue'
import ReviewListPage from '@/pages/project/ReviewListPage.vue'
import PlanListPage from '@/pages/project/PlanListPage.vue'

const activeMenu = ref('cases')
const selectedDocId = ref('')
const selectedDocName = ref('')

const menuItems = [
  { key: 'cases', label: '测试用例', icon: 'Document' },
  { key: 'reviews', label: '测试评审', icon: 'Checked' },
  { key: 'plans', label: '测试计划', icon: 'Calendar' },
]

function handleSelectDocument(docId: string, docName: string) {
  selectedDocId.value = docId
  selectedDocName.value = docName
}
</script>

<template>
  <div class="test-case-page">
    <!-- 左侧菜单 -->
    <aside class="test-case-page__aside">
      <el-menu :default-active="activeMenu" @select="(key: string) => (activeMenu = key)">
        <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <!-- 右侧内容 -->
    <main class="test-case-page__main">
      <!-- 测试用例：模块树 + 脑图 -->
      <template v-if="activeMenu === 'cases'">
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
      </template>
      <ReviewListPage v-else-if="activeMenu === 'reviews'" />
      <PlanListPage v-else-if="activeMenu === 'plans'" />
    </main>
  </div>
</template>

<style scoped lang="scss">
.test-case-page {
  display: flex;
  height: 100%;
}

.test-case-page__aside {
  width: 140px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.test-case-page__aside :deep(.el-menu) {
  border-right: none;
}

.test-case-page__main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.test-case-page__workspace {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.test-case-page__tree-panel {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
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
  gap: 6px;
  padding: 8px 12px;
  font-size: 14px;
  font-weight: 500;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.test-case-page__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}
</style>
