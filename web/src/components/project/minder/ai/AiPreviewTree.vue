<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ElTree } from 'element-plus'
import type { AiPreviewNode } from './aiMount'
import { typeBadge, priorityBadge } from '../badges'

/**
 * AI 生成结果预览树（交互设计 2.3）：
 * 默认全选全展开，父子级联勾选；节点芯片沿用类型/优先级徽标配色。
 * el-tree 的 checked 集合不含半选父节点，而半选父节点自身应保留（仅剔除其未勾选子孙），
 * 因此 getCheckedKeySet 取 checked 与 halfChecked 的并集供挂载过滤。
 */
const props = defineProps<{ nodes: AiPreviewNode[] }>()

const treeRef = ref<InstanceType<typeof ElTree>>()
const defaultCheckedKeys = ref<string[]>([])

function collectAllKeys(nodes: AiPreviewNode[]): string[] {
  return nodes.flatMap((node) => [node.key, ...collectAllKeys(node.children)])
}

// 结果变化（重新生成）时重置为全选
watch(
  () => props.nodes,
  (nodes) => {
    defaultCheckedKeys.value = collectAllKeys(nodes)
  },
  { immediate: true },
)

/** 勾选键集合 = 全选 + 半选（半选父节点须保留自身，仅剔除未勾选的子孙） */
function getCheckedKeySet(): Set<string> {
  const tree = treeRef.value
  if (!tree) return new Set(defaultCheckedKeys.value)
  const checked = tree.getCheckedKeys(false) as string[]
  const half = tree.getHalfCheckedKeys() as string[]
  return new Set([...checked, ...half])
}

defineExpose({ getCheckedKeySet })
</script>

<template>
  <el-tree
    ref="treeRef"
    :data="nodes"
    node-key="key"
    show-checkbox
    default-expand-all
    :default-checked-keys="defaultCheckedKeys"
    :props="{ label: 'title', children: 'children' }"
    class="ai-preview-tree"
  >
    <template #default="{ data }">
      <span class="ai-preview-node">
        <span
          v-if="typeBadge(data.type)"
          class="ai-preview-badge"
          :style="{ backgroundColor: typeBadge(data.type)?.color }"
        >{{ typeBadge(data.type)?.label }}</span>
        <span
          v-if="priorityBadge(data.priority)"
          class="ai-preview-badge"
          :style="{ backgroundColor: priorityBadge(data.priority)?.color }"
        >{{ data.priority }}</span>
        <span class="ai-preview-title">{{ data.title }}</span>
      </span>
    </template>
  </el-tree>
</template>

<style scoped lang="scss">
.ai-preview-tree {
  --el-tree-node-content-height: 30px;
}

.ai-preview-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.ai-preview-badge {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 3px;
  font-size: 11px;
  line-height: 18px;
  color: #fff;
}

.ai-preview-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
