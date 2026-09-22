<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import ProjectModuleTree from '@/components/project/ProjectModuleTree.vue'
import CaseMindMap from '@/components/project/CaseMindMap.vue'
import { fetchProjectModuleTree } from '@/services/project'
import type { ProjectModule } from '@/types'

const route = useRoute()
const router = useRouter()

const selectedDocId = ref('')
const selectedDocName = ref('')
const caseMindMapRef = ref<InstanceType<typeof CaseMindMap>>()

function handleSelectDocument(docId: string, docName: string) {
  selectedDocId.value = docId
  selectedDocName.value = docName
}

function findDocument(nodes: ProjectModule[], id: string): ProjectModule | null {
  for (const n of nodes) {
    if (n.id === id && n.type === 'document') return n
    const found = findDocument(n.children ?? [], id)
    if (found) return found
  }
  return null
}

// 外部跳转（缺陷关联用例 / 遗漏测试点「转用例生成」）经 ?documentId= 直达文档：
// 消费后清除参数避免刷新或切换文档后残留重复触发；页面内再次跳转经 watch 触发
async function consumeExternalJump(docId: string, aiText: string): Promise<void> {
  if (!docId && !aiText) return
  router.replace({ query: { ...route.query, documentId: undefined, aiGenerate: undefined } })
  if (docId && docId !== selectedDocId.value) {
    try {
      const doc = findDocument(await fetchProjectModuleTree('testcase'), docId)
      if (doc) handleSelectDocument(doc.id, doc.name)
    } catch {
      // 文档不存在或加载失败时停留在空态
      return
    }
  }
  await nextTick()
  if (aiText) caseMindMapRef.value?.openAiGenerateWithText(aiText)
}

// 成组监听直达参数：documentId / aiGenerate 任一变化即重新消费（含同文档重复跳转）
watch(
  () => [String(route.query.documentId ?? ''), String(route.query.aiGenerate ?? '')] as const,
  ([docId, aiText]) => void consumeExternalJump(docId, aiText),
)
void consumeExternalJump(String(route.query.documentId ?? ''), String(route.query.aiGenerate ?? ''))

// 处于文档中时离开需二次确认，防止误触打断编辑（切换文档的确认在 ProjectModuleTree 内）
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
        <ProjectModuleTree asset-type="testcase" @select-document="handleSelectDocument" />
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
          <CaseMindMap ref="caseMindMapRef" :doc-id="selectedDocId" />
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
  width: 280px;
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
