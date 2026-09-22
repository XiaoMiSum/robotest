import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createRole, deleteRole, fetchRoleList, updateRole } from '@/services/admin'
import type { RoleTreeNode, RoleType } from '@/types'

export function useRoleTree(onSelect: (node: { id: string; isSystem: boolean; type: string }) => void, onCleared: () => void) {
  const treeData = ref<RoleTreeNode[]>([])
  const loading = ref(false)
  const currentId = ref('')

  const GROUP_CONFIG: Record<RoleType, { label: string; id: string }> = {
    system: { label: '系统角色', id: 'type-system' },
    workspace: { label: '工作空间角色', id: 'type-workspace' },
  }

  function buildTree(roles: RoleTreeNode[]): RoleTreeNode[] {
    const groups = new Map<RoleType, RoleTreeNode>()
    for (const [type, cfg] of Object.entries(GROUP_CONFIG) as [RoleType, { label: string; id: string }][]) {
      groups.set(type, {
        id: cfg.id,
        name: cfg.label,
        type,
        isGroup: true,
        children: [],
      })
    }
    for (const role of roles) {
      const group = groups.get(role.type)
      if (group) {
        group.children!.push(role)
      }
    }
    return Array.from(groups.values()).filter((g) => g.children!.length > 0 || g.type === 'system' || g.type === 'workspace')
  }

  async function load() {
    loading.value = true
    try {
      const flat = await fetchRoleList()
      treeData.value = buildTree(flat)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载角色树失败')
    } finally {
      loading.value = false
    }
  }

  function handleNodeClick(node: RoleTreeNode) {
    if (node.isGroup) return
    currentId.value = node.id
    onSelect({ id: node.id, isSystem: node.isSystem ?? false, type: node.type })
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
      onSelect({ id, isSystem: false, type: group.type })
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
        onCleared()
      }
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '删除失败')
    }
  }

  onMounted(load)

  return {
    treeData,
    loading,
    currentId,
    load,
    handleNodeClick,
    handleAdd,
    handleRename,
    handleDelete,
  }
}
