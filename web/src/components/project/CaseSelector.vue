<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchModuleTree, fetchDocumentNodes } from '@/services/project'
import type { TestCaseModule, TestCaseNode } from '@/types'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [selectedNodes: { documentId: string; caseIds: string[] }[]]
}>()

const loading = ref(false)
const modules = ref<TestCaseModule[]>([])
const selectedDocId = ref('')
const docNodes = ref<TestCaseNode | null>(null)
const docLoading = ref(false)

const selectedMap = ref<Record<string, Set<string>>>({})

const currentCases = computed(() => {
  if (!docNodes.value) return []
  const cases: { id: string; title: string; priority: string | null }[] = []
  function walk(node: TestCaseNode) {
    if (node.type === 'case') {
      cases.push({ id: node.id, title: node.title, priority: node.priority })
    }
    node.children.forEach(walk)
  }
  docNodes.value.children.forEach(walk)
  return cases
})

const allChecked = computed(() => {
  if (!currentCases.value.length) return false
  const set = selectedMap.value[selectedDocId.value]
  if (!set) return false
  return currentCases.value.every((c) => set.has(c.id))
})

const totalSelected = computed(() => {
  let count = 0
  Object.values(selectedMap.value).forEach((set) => { count += set.size })
  return count
})

async function loadModules() {
  loading.value = true
  try {
    modules.value = await fetchModuleTree()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模块树失败')
  } finally {
    loading.value = false
  }
}

async function handleNodeClick(data: TestCaseModule) {
  if (data.type !== 'document') return
  selectedDocId.value = data.id
  docLoading.value = true
  try {
    const result = await fetchDocumentNodes(data.id)
    docNodes.value = result.node
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载文档节点失败')
    docNodes.value = null
  } finally {
    docLoading.value = false
  }
}

function toggleCase(caseId: string) {
  if (!selectedDocId.value) return
  if (!selectedMap.value[selectedDocId.value]) {
    selectedMap.value[selectedDocId.value] = new Set()
  }
  const set = selectedMap.value[selectedDocId.value]
  if (set.has(caseId)) {
    set.delete(caseId)
  } else {
    set.add(caseId)
  }
}

function toggleAll() {
  if (!selectedDocId.value || !currentCases.value.length) return
  if (!selectedMap.value[selectedDocId.value]) {
    selectedMap.value[selectedDocId.value] = new Set()
  }
  const set = selectedMap.value[selectedDocId.value]
  if (allChecked.value) {
    currentCases.value.forEach((c) => set.delete(c.id))
  } else {
    currentCases.value.forEach((c) => set.add(c.id))
  }
}

function close() {
  emit('update:modelValue', false)
}

function handleConfirm() {
  const result: { documentId: string; caseIds: string[] }[] = []
  Object.entries(selectedMap.value).forEach(([docId, set]) => {
    if (set.size > 0) {
      result.push({ documentId: docId, caseIds: [...set] })
    }
  })
  if (!result.length) {
    ElMessage.warning('请至少选择一个用例')
    return
  }
  emit('confirm', result)
  close()
}

watch(() => props.modelValue, (val) => {
  if (val) {
    selectedMap.value = {}
    docNodes.value = null
    selectedDocId.value = ''
    loadModules()
  }
})

onMounted(() => { if (props.modelValue) loadModules() })
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="选择关联用例"
    width="720px"
    @update:model-value="close"
  >
    <div class="case-selector">
      <!-- 左侧模块树 -->
      <div class="case-selector__tree" v-loading="loading">
        <el-tree
          :data="modules"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          default-expand-all
          highlight-current
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <span>
              <el-icon v-if="data.type === 'directory'"><Folder /></el-icon>
              <el-icon v-else><Document /></el-icon>
              {{ data.name }}
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右侧用例列表 -->
      <div class="case-selector__content">
        <div v-if="!selectedDocId" class="case-selector__hint">
          <el-empty description="请在左侧选择一个文档" :image-size="60" />
        </div>
        <div v-else v-loading="docLoading">
          <div class="case-selector__toolbar">
            <el-checkbox :model-value="allChecked" @change="toggleAll">全选当前文档用例</el-checkbox>
            <span class="case-selector__count">已选 {{ totalSelected }} 个用例</span>
          </div>
          <div class="case-selector__list">
            <div
              v-for="c in currentCases"
              :key="c.id"
              class="case-selector__item"
              @click="toggleCase(c.id)"
            >
              <el-checkbox
                :model-value="selectedMap[selectedDocId]?.has(c.id) ?? false"
                @click.stop
                @change="toggleCase(c.id)"
              />
              <span class="case-selector__item-title">{{ c.title }}</span>
              <el-tag v-if="c.priority" size="small" type="info">{{ c.priority }}</el-tag>
            </div>
            <el-empty v-if="!currentCases.length" description="该文档暂无用例节点" :image-size="40" />
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定关联 ({{ totalSelected }})</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.case-selector {
  display: flex;
  height: 400px;
  gap: 12px;
}

.case-selector__tree {
  width: 220px;
  flex-shrink: 0;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
}

.case-selector__content {
  flex: 1;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
}

.case-selector__hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.case-selector__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.case-selector__count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.case-selector__list {
  max-height: 340px;
  overflow-y: auto;
}

.case-selector__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  cursor: pointer;
  border-radius: 4px;
}

.case-selector__item:hover {
  background: var(--el-fill-color-light);
}

.case-selector__item-title {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
