<script setup lang="ts">
import { useCaseSelector } from '@/composables/project/functional-testing/case/useCaseSelector'
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElTree } from 'element-plus'
import CaseSelectTree from '@/components/project/functional-testing/case/CaseSelectTree.vue'
import type { TestCaseNode } from '@/types'

// 注入树的统一文档 key：AI 提案不在真实文档树内，仅作勾选归集锚点
const INJECTED_DOC_ID = '__ai_generated__'

const props = defineProps<{
  modelValue: boolean
  // 调整场景传入当前规划用例，打开时回显预选；创建场景不传，行为不变
  initialSelected?: { documentId: string; caseIds: string[] }[]
  // 单选模式：仅可勾选一个用例节点（缺陷关联用例场景），默认多选保持既有调用方行为
  single?: boolean
  // 注入树模式：直接勾选 AI 提案映射的节点树，跳过后端模块 / 文档加载（详设 5.2）
  injectedNodes?: TestCaseNode[]
  // 内联模式：不包裹 el-dialog，供生成面板嵌入（详设 5.2）
  inline?: boolean
  // 注入树模式：已采纳用例 key，只读回看且默认勾选
  adoptedCaseIds?: string[]
  showAdopted?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [selectedNodes: { documentId: string; caseIds: string[] }[]]
  /** 注入树模式下回传所选用例节点，id 即提案 key */
  confirmNodes: [nodes: TestCaseNode[]]
  /** 勾选变化实时回传，供内联承载面板驱动动作区 */
  selectionChange: [caseIds: string[]]
  'update:showAdopted': [value: boolean]
}>()

const {
  loading,
  modules,
  selectedDocId,
  docNodes,
  docLoading,
  filterKeyword,
  filterPriority,
  handleNodeClick,
  loadModules,
} = useCaseSelector()

const treeRef = ref<InstanceType<typeof ElTree>>()

const selectedMap = ref<Record<string, Set<string>>>({})

const lockedSet = computed(() => new Set(props.adoptedCaseIds ?? []))

const priorities = ['P0', 'P1', 'P2', 'P3']

const showFullDoc = ref(false)

const injectedRoot = computed<TestCaseNode | null>(() => {
  if (!props.injectedNodes?.length) return null
  return {
    id: INJECTED_DOC_ID,
    parentId: null,
    type: 'normal',
    title: 'AI 生成用例',
    priority: null,
    sortOrder: 0,
    version: 1,
    children: props.injectedNodes,
  }
})

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
  const root = injectedRoot.value ?? docNodes.value
  if (!root) return null
  if (injectedRoot.value || showFullDoc.value) return root
  return pruneToCases(root)
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

function collectCaseIds(node: TestCaseNode, acc: string[]) {
  if (node.type === 'case') acc.push(node.id)
  node.children.forEach((child) => collectCaseIds(child, acc))
}

const selectableCaseIds = computed<string[]>(() => {
  const root = selectableRoot.value
  if (!root) return []
  const ids: string[] = []
  collectCaseIds(root, ids)
  return ids.filter((id) => !lockedSet.value.has(id))
})

// 全选作用于整棵勾选树的用例节点：部分子树已选时呈半选态，再点补齐整枝
const allSelected = computed(() => {
  const ids = selectableCaseIds.value
  if (!ids.length) return false
  const set = selectedMap.value[selectedDocId.value]
  return ids.every((id) => set?.has(id) === true)
})

const someSelected = computed(() => {
  const set = selectedMap.value[selectedDocId.value]
  return selectableCaseIds.value.some((id) => set?.has(id) === true)
})

function updateShowAdopted(value: boolean | string | number): void {
  emit('update:showAdopted', value === true)
}

function toggleSelectAll(): void {
  const root = selectableRoot.value
  if (root) handleToggle(root)
}

function emitSelection(): void {
  const ids: string[] = []
  Object.values(selectedMap.value).forEach((set) => ids.push(...set))
  emit('selectionChange', ids)
}

// 级联勾选：整枝已全选则整枝取消，否则补齐整枝（含嵌套用例）；单选模式为跨文档互斥的替换式选中
function handleToggle(node: TestCaseNode) {
  if (!selectedDocId.value) return
  if (props.single) {
    if (node.type !== 'case') return
    const already = selectedMap.value[selectedDocId.value]?.has(node.id)
    selectedMap.value = already ? {} : { [selectedDocId.value]: new Set([node.id]) }
    emitSelection()
    return
  }
  if (!selectedMap.value[selectedDocId.value]) {
    selectedMap.value = { ...selectedMap.value, [selectedDocId.value]: new Set() }
  }
  const set = selectedMap.value[selectedDocId.value]
  const caseIds: string[] = []
  collectCaseIds(node, caseIds)
  const actionable = caseIds.filter((id) => !lockedSet.value.has(id))
  const allSelected = actionable.length > 0 && actionable.every((id) => set.has(id))
  actionable.forEach((id) => {
    if (allSelected) {
      set.delete(id)
    } else {
      set.add(id)
    }
  })
  selectedMap.value = { ...selectedMap.value }
  emitSelection()
}

function selectedNodesOf(root: TestCaseNode | null, ids: Set<string>): TestCaseNode[] {
  const found: TestCaseNode[] = []
  function walk(node: TestCaseNode) {
    if (node.type === 'case' && ids.has(node.id)) found.push(node)
    node.children.forEach(walk)
  }
  if (root) walk(root)
  return found
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
  emit('confirmNodes', selectedNodesOf(injectedRoot.value ?? docNodes.value, new Set(result.flatMap((item) => item.caseIds))))
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
      if (!props.injectedNodes) loadModules()
      emitSelection()
    }
  },
)

onMounted(() => {
  if (props.modelValue && !props.injectedNodes) loadModules()
})

// 注入树模式无需后端模块树：切换「展示已采纳」时只剔除失效 key 并补回锁定项，保留用户新勾选
watch(
  [() => props.injectedNodes, () => props.adoptedCaseIds, () => props.modelValue],
  ([nodes, adopted, visible]) => {
    if (!nodes || !visible) return
    selectedDocId.value = INJECTED_DOC_ID
    docNodes.value = injectedRoot.value
    const presentIds: string[] = []
    nodes.forEach((node) => collectCaseIds(node, presentIds))
    const present = new Set(presentIds)
    const next = new Set<string>()
    ;(adopted ?? []).forEach((id) => {
      if (present.has(id)) next.add(id)
    })
    selectedMap.value[INJECTED_DOC_ID]?.forEach((id) => {
      if (present.has(id)) next.add(id)
    })
    selectedMap.value = { ...selectedMap.value, [INJECTED_DOC_ID]: next }
    emitSelection()
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog
    v-if="!inline"
    class="case-selector-dialog"
    :model-value="modelValue"
    title="选择关联用例"
    width="1500px"
    @update:model-value="close"
  >
    <div class="case-selector">
      <div v-if="!injectedNodes" v-loading="loading" class="case-selector__tree">
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
            <el-checkbox v-if="!injectedNodes" v-model="showFullDoc" size="small">展示全量文档视图</el-checkbox>
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
              :locked-ids="lockedSet"
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

  <div v-else v-show="modelValue" class="case-selector-inline">
    <div class="case-selector">
      <div class="case-selector__content">
        <div v-if="!selectedDocId" class="case-selector__hint">
          <el-empty description="暂无可勾选的用例" :image-size="60" />
        </div>
        <div v-else class="case-selector__body">
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
            <div class="case-selector__toolbar-left">
              <el-checkbox
                v-if="injectedNodes"
                :model-value="allSelected"
                :indeterminate="someSelected && !allSelected"
                size="small"
                @change="toggleSelectAll"
              >
                全选
              </el-checkbox>
              <el-checkbox
                v-if="adoptedCaseIds?.length"
                :model-value="showAdopted"
                size="small"
                @change="updateShowAdopted"
              >
                展示已采纳用例
              </el-checkbox>
              <span class="case-selector__tip">勾选要插入脑图的用例及属性节点</span>
            </div>
            <span class="case-selector__count">已选 {{ totalSelected }} 个用例</span>
          </div>
          <div class="case-selector__canvas">
            <CaseSelectTree
              v-if="selectableRoot && (!filterSets || filterSets.visible.size > 0)"
              :node="selectableRoot"
              :stats="nodeStats"
              :visible-ids="filterSets ? filterSets.visible : null"
              :matched-ids="filterSets ? filterSets.matched : null"
              :single="single"
              :locked-ids="lockedSet"
              @toggle="handleToggle"
            />
            <el-empty
              v-else
              :description="selectableRoot ? '无匹配用例' : '暂无可勾选的用例'"
              :image-size="40"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.case-selector {
  display: flex;
  height: 100%;
  gap: 12px;
}

.case-selector-inline {
  height: 100%;
  min-height: 0;
}

.case-selector__tree {
  width: 220px;
  flex-shrink: 0;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
  // 只隐藏视觉滚动条：overflow 滚动与键盘滚动必须保留（UI-SC-03）
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    width: 0;
    height: 0;
    display: none;
  }
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

.case-selector__toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
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
/*
 * CODE-008 例外：Element Plus Dialog 默认 Teleport 到 body，根节点不会带组件 scopeId；
 * 规则限定在 .case-selector-dialog，避免把弹窗布局扩散到其他弹窗。
 * 规则来源：docs/00-spec/10-engineering/01-frontend.md §8。
 */
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
