<script setup lang="ts">
import type { TestCaseNode } from '@/types'

const props = defineProps<{
  node: TestCaseNode
  // 父组件预聚合的子树用例统计（total/selected），驱动全选与半选态
  stats: Record<string, { total: number; selected: number }>
  // 过滤生效时的可见节点集合，null 表示不过滤
  visibleIds: Set<string> | null
  // 过滤命中的用例节点集合，用于高亮
  matchedIds: Set<string> | null
  // 单选模式：仅用例节点可勾选，分枝节点不提供级联全选
  single?: boolean
}>()

const emit = defineEmits<{ toggle: [node: TestCaseNode] }>()

// 类型徽标文案与配色与脑图徽标（minder/badges.ts）保持一致
const TYPE_BADGES: Record<string, { label: string; color: string }> = {
  case: { label: '用例', color: '#a464ff' },
  precondition: { label: '前置', color: '#409eff' },
  step: { label: '步骤', color: '#67c23a' },
  expected: { label: '预期', color: '#e6a23c' },
}

function stat(id: string): { total: number; selected: number } {
  return props.stats[id] ?? { total: 0, selected: 0 }
}

function isChecked(id: string): boolean {
  const s = stat(id)
  return s.total > 0 && s.selected === s.total
}

function isIndeterminate(id: string): boolean {
  const s = stat(id)
  return s.selected > 0 && s.selected < s.total
}

// 子树不含任何用例的节点（前置/步骤/预期或空分枝）仅展示不可勾选；单选模式下分枝节点也不可勾选
function isSelectable(node: TestCaseNode): boolean {
  if (props.single) return node.type === 'case'
  return stat(node.id).total > 0
}

function handleChipClick(node: TestCaseNode) {
  if (isSelectable(node)) emit('toggle', node)
}

function visibleChildren(node: TestCaseNode): TestCaseNode[] {
  const visible = props.visibleIds
  if (!visible) return node.children
  return node.children.filter((c) => visible.has(c.id))
}
</script>

<template>
  <div class="cst-node">
    <div
      class="cst-chip"
      :class="{
        'cst-chip--case': node.type === 'case',
        'cst-chip--checked': isChecked(node.id),
        'cst-chip--matched': matchedIds?.has(node.id),
        'cst-chip--plain': !isSelectable(node),
      }"
      @click="handleChipClick(node)"
    >
      <el-checkbox
        v-if="isSelectable(node)"
        :model-value="isChecked(node.id)"
        :indeterminate="isIndeterminate(node.id)"
        @click.stop
        @change="emit('toggle', node)"
      />
      <span
        v-if="TYPE_BADGES[node.type]"
        class="cst-chip__type"
        :style="{ background: TYPE_BADGES[node.type].color }"
      >
        {{ TYPE_BADGES[node.type].label }}
      </span>
      <span class="cst-chip__title">{{ node.title }}</span>
      <span
        v-if="node.type === 'case' && node.priority"
        class="cst-chip__priority"
        :class="`cst-chip__priority--${node.priority.toLowerCase()}`"
      >
        {{ node.priority }}
      </span>
    </div>

    <template v-if="visibleChildren(node).length">
      <div class="cst-stub" />
      <div class="cst-children">
        <div v-for="child in visibleChildren(node)" :key="child.id" class="cst-branch">
          <CaseSelectTree
            :node="child"
            :stats="stats"
            :visible-ids="visibleIds"
            :matched-ids="matchedIds"
            :single="single"
            @toggle="emit('toggle', $event)"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.cst-node {
  display: flex;
  align-items: center;
  width: max-content;
}

.cst-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 14px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  white-space: nowrap;
  font-size: 13px;
  flex-shrink: 0;
  transition: border-color 0.15s, background-color 0.15s;

  :deep(.el-checkbox) {
    height: auto;
  }
}

.cst-chip:hover {
  border-color: var(--el-color-primary-light-5);
}

.cst-chip--case {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.cst-chip--checked {
  background: var(--el-color-primary-light-8);
  border-color: var(--el-color-primary);
}

.cst-chip--matched .cst-chip__title {
  color: var(--el-color-primary);
  font-weight: 600;
}

.cst-chip--plain {
  cursor: default;
  border-style: dashed;
  color: var(--el-text-color-secondary);
}

.cst-chip--plain:hover {
  border-color: var(--el-border-color);
}

.cst-chip__title {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 优先级徽标配色与脑图徽标保持一致 */
.cst-chip__priority {
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  border-radius: 8px;
  padding: 0 6px;
  line-height: 16px;
}

.cst-chip__type {
  font-size: 11px;
  color: #fff;
  border-radius: 8px;
  padding: 0 6px;
  line-height: 16px;
  flex-shrink: 0;
}

.cst-chip__priority--p0 { background: #f56c6c; }
.cst-chip__priority--p1 { background: #e6a23c; }
.cst-chip__priority--p2 { background: #409eff; }
.cst-chip__priority--p3 { background: #909399; }

/* 父芯片到子分支纵轨的水平连线 */
.cst-stub {
  width: 16px;
  border-top: 1px solid var(--el-border-color);
  align-self: center;
  flex-shrink: 0;
}

.cst-children {
  display: flex;
  flex-direction: column;
}

/* 括号线：纵轨贯穿分支高度，首末分支各截一半形成包裹感 */
.cst-branch {
  position: relative;
  display: flex;
  align-items: center;
  padding: 4px 0 4px 16px;
}

.cst-branch::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-left: 1px solid var(--el-border-color);
}

.cst-branch:first-child::before {
  top: 50%;
}

.cst-branch:last-child::before {
  bottom: 50%;
}

.cst-branch::after {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 16px;
  border-top: 1px solid var(--el-border-color);
}
</style>
