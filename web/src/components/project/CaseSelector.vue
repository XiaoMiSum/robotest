<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElTree } from 'element-plus'
import { fetchDocumentNodes, fetchProjectModuleTree } from '@/services/project'
import CaseSelectTree from '@/components/project/CaseSelectTree.vue'
import type { ProjectModule, TestCaseNode } from '@/types'

const props = defineProps<{
  modelValue: boolean
  // 调整场景传入当前规划用例，打开时回显预选；创建场景不传，行为不变
  initialSelected?: { documentId: string; caseIds: string[] }[]
  // 单选模式：仅可勾选一个用例节点（缺陷关联用例场景），默认多选保持既有调用方行为
  single?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [selectedNodes: { documentId: string; caseIds: string[] }[]]
}>()

const loading = ref(false)
const modules = ref<ProjectModule[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const selectedDocId = ref('')
const docNodes = ref<TestCaseNode | null>(null)
const docLoading = ref(false)

const selectedMap = ref<Record<string, Set<string>>>({})

const filterKeyword = ref('')
const filterPriority = ref('')
const priorities = ['P0', 'P1', 'P2', 'P3']

const showFullDoc = ref(false)

// 精简视图：只保留用例节点及其祖先链（含嵌套用例），剔除前置/步骤/预期明细子树与无用例空分枝，
// 保证与全量视图有可见差异；否则常规文档两视图渲染一致，切换项形同虚设
function pruneToCases(node: TestCaseNode): TestCaseNode | null {
  const children = node.children
    .map(pruneToCases)
    .filter((c): c is TestCaseNode => c !== null)
  if (node.type === 'case') return { ...node, children }
  if (!children.length) return null
  return { ...node, children }
}

const selectableRoot = computed(() => {
  if (!docNodes.value) return null
  return showFullDoc.value ? docNodes.value : pruneToCases(docNodes.value)
})

// 预聚合各节点子树的用例数与已选数，驱动芯片全选/半选态；单选模式只统计节点自身，避免嵌套用例导致半选态
const nodeStats = computed(() => {
  const stats: Record<string, { total: number; selected: number }> = {}
  const root = selectableRoot.value
  if (!root) return stats
  const set = selectedMap.value[selectedDocId.value]
  function walk(node: TestCaseNode): { total: number; selected: number } {
    let total = 0
    let selected = 0
    if (node.type === 'case') {
      total = 1
      selected = set?.has(node.id) ? 1 : 0
    }
    node.children.forEach((child) => {
      const s = walk(child)
      if (!props.single) {
        total += s.total
        selected += s.selected
      }
    })
    stats[node.id] = { total, selected }
    return stats[node.id]
  }
  walk(root)
  return stats
})

// 过滤仅针对用例节点：命中用例及其祖先链可见，其余分枝隐藏
const filterSets = computed(() => {
  const root = selectableRoot.value
  const keyword = filterKeyword.value.trim().toLowerCase()
  if (!root || (!keyword && !filterPriority.value)) return null
  const visible = new Set<string>()
  const matched = new Set<string>()
  const ancestors: string[] = []
  function walk(node: TestCaseNode) {
    if (
      node.type === 'case' &&
      (!keyword || node.title.toLowerCase().includes(keyword)) &&
      (!filterPriority.value || node.priority === filterPriority.value)
    ) {
      matched.add(node.id)
      visible.add(node.id)
      ancestors.forEach((id) => visible.add(id))
    }
    ancestors.push(node.id)
    node.children.forEach(walk)
    ancestors.pop()
  }
  walk(root)
  return { visible, matched }
})

const totalSelected = computed(() => {
  let count = 0
  Object.values(selectedMap.value).forEach((set) => {
    count += set.size
  })
  return count
})

async function loadModules() {
  loading.value = true
  try {
    // 规划用例需在左树展示文档节点，必须带 assetType=testcase（后端仅该类型合并文档节点）
    modules.value = await fetchProjectModuleTree('testcase')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模块树失败')
  } finally {
    loading.value = false
  }
}

async function handleNodeClick(data: ProjectModule) {
  if (data.type !== 'document') return
  selectedDocId.value = data.id
  filterKeyword.value = ''
  filterPriority.value = ''
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

function collectCaseIds(node: TestCaseNode, acc: string[]) {
  if (node.type === 'case') acc.push(node.id)
  node.children.forEach((child) => collectCaseIds(child, acc))
}

// 级联勾选：整枝已全选则整枝取消，否则补齐整枝（含嵌套用例）；单选模式为跨文档互斥的替换式选中
function handleToggle(node: TestCaseNode) {
  if (!selectedDocId.value) return
  if (props.single) {
    if (node.type !== 'case') return
    const already = selectedMap.value[selectedDocId.value]?.has(node.id)
    selectedMap.value = already ? {} : { [selectedDocId.value]: new Set([node.id]) }
    return
  }
  if (!selectedMap.value[selectedDocId.value]) {
    selectedMap.value[selectedDocId.value] = new Set()
  }
  const set = selectedMap.value[selectedDocId.value]
  const caseIds: string[] = []
  collectCaseIds(node, caseIds)
  const allSelected = caseIds.length > 0 && caseIds.every((id) => set.has(id))
  caseIds.forEach((id) => {
    if (allSelected) {
      set.delete(id)
    } else {
      set.add(id)
    }
  })
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
    ElMessage.warning(props.single ? '请选择一个用例' : '请至少选择一个用例')
    return
  }
  emit('confirm', result)
  close()
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      const map: Record<string, Set<string>> = {}
      props.initialSelected?.forEach((s) => {
        map[s.documentId] = new Set(s.caseIds)
      })
      selectedMap.value = map
      docNodes.value = null
      selectedDocId.value = ''
      filterKeyword.value = ''
      filterPriority.value = ''
      // 弹窗内容常驻不销毁，el-tree 会按 node-key 恢复上次点击的高亮，
      // 需显式清除，避免左树残留高亮而右侧为空态的不一致
      treeRef.value?.setCurrentKey(null)
      loadModules()
    }
  },
)

onMounted(() => {
  if (props.modelValue) loadModules()
})
</script>

<template>
  <el-dialog
    class="case-selector-dialog"
    :model-value="modelValue"
    title="选择关联用例"
    width="1500px"
    @update:model-value="close"
  >
    <div class="case-selector">
      <!-- 左侧模块树 -->
      <div v-loading="loading" class="case-selector__tree">
        <el-tree
          ref="treeRef"
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

      <!-- 右侧脑图式勾选树 -->
      <div class="case-selector__content">
        <div v-if="!selectedDocId" class="case-selector__hint">
          <el-empty description="请在左侧选择一个文档" :image-size="60" />
        </div>
        <div v-else v-loading="docLoading" class="case-selector__body">
          <div class="case-selector__filters">
            <el-input v-model="filterKeyword" size="small" placeholder="搜索用例名称" clearable>
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select
              v-model="filterPriority"
              size="small"
              placeholder="优先级"
              clearable
              class="case-selector__priority"
            >
              <el-option v-for="p in priorities" :key="p" :label="p" :value="p" />
            </el-select>
          </div>
          <div class="case-selector__toolbar">
            <el-checkbox v-model="showFullDoc" size="small">展示全量文档视图</el-checkbox>
            <div class="case-selector__toolbar-right">
              <span class="case-selector__tip">{{
                single ? '仅可关联一个用例，再次选择将替换' : '勾选任意节点，其子孙用例将一并规划'
              }}</span>
              <span class="case-selector__count">已选 {{ totalSelected }} 个用例</span>
            </div>
          </div>
          <div class="case-selector__canvas">
            <CaseSelectTree
              v-if="selectableRoot && (!filterSets || filterSets.visible.size > 0)"
              :node="selectableRoot"
              :stats="nodeStats"
              :visible-ids="filterSets ? filterSets.visible : null"
              :matched-ids="filterSets ? filterSets.matched : null"
              :single="single"
              @toggle="handleToggle"
            />
            <el-empty
              v-else
              :description="selectableRoot ? '无匹配用例' : '该文档暂无用例节点'"
              :image-size="40"
            />
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
  height: 100%;
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
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
}

.case-selector__body {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.case-selector__hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.case-selector__filters {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.case-selector__priority {
  width: 100px;
  flex-shrink: 0;
}

.case-selector__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.case-selector__tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.case-selector__toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.case-selector__count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.case-selector__canvas {
  flex: 1;
  overflow: auto;
  padding: 8px 4px;
}
</style>

// 弹窗根元素（.el-dialog）无组件 scopeId，scoped 选择器无法命中根元素；
// class 经 fallthrough attrs 透传至根元素，故高度布局须用全局样式 + class 锚点（同 AiPreviewDialog）。
<style lang="scss">
.case-selector-dialog {
  position: relative;
  height: 80vh;
  margin: 10vh auto;
  display: flex;
  flex-direction: column;

  .el-dialog__body {
    position: relative;
    flex: 1;
    min-height: 0;
    overflow: auto;
  }
}
</style>
