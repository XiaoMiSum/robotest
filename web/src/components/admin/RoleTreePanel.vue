<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createRole, deleteRole, fetchRoleTree, updateRole } from '@/services/admin'
import type { RoleTreeNode } from '@/types'

const emit = defineEmits<{
  select: [node: { id: string; isSystem: boolean }]
  cleared: []
}>()

const treeProps = { label: 'name', children: 'children' }
const treeData = ref<RoleTreeNode[]>([])
const loading = ref(false)
const currentId = ref('')

async function load() {
  loading.value = true
  try {
    treeData.value = await fetchRoleTree()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载角色树失败')
  } finally {
    loading.value = false
  }
}

function handleNodeClick(node: RoleTreeNode) {
  // 分组节点仅用于归类，不可选中
  if (node.isGroup) return
  currentId.value = node.id
  emit('select', { id: node.id, isSystem: node.isSystem ?? false })
}

async function handleAdd(group: RoleTreeNode) {
  try {
    const { value } = await ElMessageBox.prompt('请输入角色名称', '新增角色', {
      inputPattern: /\S+/,
      inputErrorMessage: '角色名称不能为空',
    })
    const id = await createRole({ name: value.trim(), type: group.type })
    ElMessage.success('角色已创建')
    await load()
    currentId.value = id
    emit('select', { id, isSystem: false })
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '创建角色失败')
  }
}

async function handleRename(node: RoleTreeNode) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的角色名称', '重命名角色', {
      inputValue: node.name,
      inputPattern: /\S+/,
      inputErrorMessage: '角色名称不能为空',
    })
    await updateRole(node.id, { name: value.trim() })
    ElMessage.success('已重命名')
    load()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '重命名失败')
  }
}

async function handleDelete(node: RoleTreeNode) {
  try {
    await ElMessageBox.confirm(`确定要删除角色「${node.name}」吗？`, '确认删除', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteRole(node.id)
    ElMessage.success('已删除')
    if (currentId.value === node.id) {
      currentId.value = ''
      emit('cleared')
    }
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

defineExpose({ reload: load })

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="role-tree">
    <el-tree
      :data="treeData"
      :props="treeProps"
      node-key="id"
      :expand-on-click-node="false"
      default-expand-all
      :current-node-key="currentId"
      highlight-current
      @node-click="handleNodeClick"
    >
      <template #default="{ data }">
        <div class="role-tree__node">
          <span class="role-tree__label">
            {{ data.name }}
            <el-tag
              v-if="data.isGroup && data.userCount != null"
              size="small"
              type="info"
              class="role-tree__count"
            >
              {{ data.userCount }}
            </el-tag>
          </span>
          <span class="role-tree__actions">
            <!-- 分组节点：新增该类型角色 -->
            <el-button v-if="data.isGroup" link size="small" @click.stop="handleAdd(data)">
              <el-icon><Plus /></el-icon>
            </el-button>
            <!-- 具体角色：重命名 / 删除（系统预置角色不可删除） -->
            <template v-else>
              <el-button link size="small" @click.stop="handleRename(data)">
                <el-icon><EditPen /></el-icon>
              </el-button>
              <el-button
                v-if="!data.isSystem"
                link
                size="small"
                type="danger"
                @click.stop="handleDelete(data)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </span>
        </div>
      </template>
    </el-tree>
  </div>
</template>

<style scoped lang="scss">
.role-tree {
  height: 100%;
}

.role-tree__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}

.role-tree__label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.role-tree__count {
  transform: scale(0.85);
}

.role-tree__actions {
  visibility: hidden;
}

.role-tree__node:hover .role-tree__actions {
  visibility: visible;
}
</style>
