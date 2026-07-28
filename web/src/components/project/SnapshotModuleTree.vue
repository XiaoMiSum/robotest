<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElTree } from 'element-plus'
import type { SnapshotModule } from '@/types'

// 只读快照文档树：数据由详情页加载（评审/计划接口不同），组件仅负责展示与文档选中
const props = defineProps<{
  data: SnapshotModule[]
  currentDocId: string
}>()

const emit = defineEmits<{
  selectDocument: [docId: string, docName: string]
}>()

const treeProps = { label: 'name', children: 'children' }
const treeRef = ref<InstanceType<typeof ElTree>>()

// 数据异步到达或外部切换文档后同步高亮
watch(
  () => [props.data, props.currentDocId] as const,
  async () => {
    if (props.currentDocId) treeRef.value?.setCurrentKey(props.currentDocId)
  },
  { flush: 'post' },
)

// el-tree 节点对象仅取展开切换所需的最小结构（完整 Node 类型未随组件导出）
interface TreeNodeToggle {
  expanded: boolean
  expand(): void
  collapse(): void
}

function handleNodeClick(data: SnapshotModule, node: TreeNodeToggle) {
  // 目录整行点击即切换展开/收起（与用例页模块树交互一致）
  if (data.type === 'directory') {
    if (node.expanded) node.collapse()
    else node.expand()
    if (props.currentDocId) treeRef.value?.setCurrentKey(props.currentDocId)
    return
  }
  if (data.id === props.currentDocId) return
  emit('selectDocument', data.id, data.name)
}
</script>

<template>
  <div class="snapshot-tree">
    <el-tree
      ref="treeRef"
      :data="data"
      :props="treeProps"
      node-key="id"
      :indent="12"
      default-expand-all
      :expand-on-click-node="false"
      highlight-current
      :current-node-key="currentDocId"
      @node-click="handleNodeClick"
    >
      <template #default="{ data: node }">
        <span class="snapshot-tree__label">
          <el-icon v-if="node.type === 'directory'" class="snapshot-tree__icon snapshot-tree__icon--folder"><Folder /></el-icon>
          <el-icon v-else class="snapshot-tree__icon snapshot-tree__icon--doc"><Document /></el-icon>
          <span class="snapshot-tree__name">{{ node.name }}</span>
        </span>
      </template>
    </el-tree>

    <el-empty v-if="!data.length" description="暂无快照文档" :image-size="40" />
  </div>
</template>

<style scoped lang="scss">
.snapshot-tree {
  padding: var(--space-sm);
}

.snapshot-tree__label {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.snapshot-tree__icon--folder {
  color: var(--color-warning);
}

.snapshot-tree__icon--doc {
  color: var(--color-primary, #409eff);
}

.snapshot-tree__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
}
</style>
