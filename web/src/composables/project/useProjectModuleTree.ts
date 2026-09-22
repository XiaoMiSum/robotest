import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import {
  createProjectModule,
  createTestCase,
  deleteProjectModule,
  deleteTestCase,
  fetchProjectModuleTree,
  updateProjectModule,
  updateTestCase,
} from '@/services/project'
import type { ProjectModule } from '@/types'

export function useProjectModuleTree({
  getAssetType,
  getCollapseOnParentClick,
  getTreeRef,
  onSelectDocument,
  onSelectModule,
}: {
  getAssetType: () => 'testcase' | 'interface' | 'scene'
  getCollapseOnParentClick: () => boolean | undefined
  getTreeRef: () => { filter(keyword: string): void; setCurrentKey(key: string): void } | undefined
  onSelectDocument: (docId: string, docName: string) => void
  onSelectModule: (moduleId: string) => void
}) {
  // 文档模式：testcase 资产按"目录 + 用例文档"组织，点文档 emit selectDocument
  // 筛选模式：interface/scene 资产只有目录，点目录 emit selectModule 供父页过滤列表
  const isDocumentMode = computed(() => getAssetType() === 'testcase')

  const treeData = ref<ProjectModule[]>([])
  const loading = ref(false)
  const currentDocId = ref('')

  async function load() {
    loading.value = true
    try {
      treeData.value = await fetchProjectModuleTree(getAssetType())
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载模块树失败')
    } finally {
      loading.value = false
    }
  }

  interface TreeNodeToggle {
    expanded: boolean
    expand(): void
    collapse(): void
  }

  async function handleNodeClick(data: ProjectModule, node: TreeNodeToggle) {
    // 目录：筛选模式选中即过滤并展开/收起；文档模式保持纯展开/收起
    if (data.type === 'directory') {
      const collapseEnabled = getCollapseOnParentClick() ?? false
      // 已展开时再点击：仅当允许收起才收起，否则保持展开；未展开时一律展开
      if (node.expanded) {
        if (collapseEnabled) node.collapse()
      } else {
        node.expand()
      }
      if (isDocumentMode.value) {
        // el-tree 点击已把高亮抢到目录上，回退到已打开的文档
        if (currentDocId.value) getTreeRef()?.setCurrentKey(currentDocId.value)
      } else {
        onSelectModule(data.id)
      }
      return
    }
    // 文档节点仅在文档模式下存在
    if (!isDocumentMode.value || data.id === currentDocId.value) return
    // 已打开文档时切换需二次确认，防止误触打断编辑
    if (currentDocId.value) {
      try {
        await ElMessageBox.confirm('确定离开当前文档，切换到其他文档吗？', '切换文档', {
          type: 'warning',
        })
      } catch {
        getTreeRef()?.setCurrentKey(currentDocId.value)
        return
      }
    }
    currentDocId.value = data.id
    onSelectDocument(data.id, data.name)
  }

  async function handleCreate(parent: ProjectModule | null, type: 'directory' | 'document') {
    // 筛选模式只有目录资产，文档创建入口不可达
    const createType: 'directory' | 'document' = isDocumentMode.value ? type : 'directory'
    const typeLabel = createType === 'directory' ? '目录' : '文档'
    try {
      const { value } = await ElMessageBox.prompt(`请输入${typeLabel}名称`, `新建${typeLabel}`, {
        inputPattern: /\S+/,
        inputErrorMessage: '名称不能为空',
      })
      if (createType === 'directory') {
        await createProjectModule({ parentId: parent?.id ?? null, name: value.trim() })
      } else {
        await createTestCase({ moduleId: parent?.id ?? null, name: value.trim() })
      }
      ElMessage.success(`${typeLabel}已创建`)
      load()
    } catch (err) {
      if (err === 'cancel' || err === 'close') return
      ElMessage.error(err instanceof Error ? err.message : '创建失败')
    }
  }

  async function handleRename(node: ProjectModule) {
    try {
      const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
        inputValue: node.name,
        inputPattern: /\S+/,
        inputErrorMessage: '名称不能为空',
      })
      if (node.type === 'directory' || !isDocumentMode.value) {
        await updateProjectModule(node.id, { name: value.trim() })
      } else {
        await updateTestCase(node.id, { name: value.trim() })
      }
      ElMessage.success('已重命名')
      load()
    } catch (err) {
      if (err === 'cancel' || err === 'close') return
      ElMessage.error(err instanceof Error ? err.message : '重命名失败')
    }
  }

  async function handleDelete(node: ProjectModule) {
    const typeLabel = node.type === 'directory' ? '目录' : '文档'
    const confirmMsg =
      node.type === 'directory'
        ? `确定删除目录「${node.name}」吗？目录必须为空才能删除。`
        : `确定删除文档「${node.name}」吗？文档下所有用例数据将被级联删除。`
    try {
      await ElMessageBox.confirm(confirmMsg, `删除${typeLabel}`, { type: 'warning' })
    } catch {
      return
    }
    try {
      if (node.type === 'directory' || !isDocumentMode.value) {
        await deleteProjectModule(node.id)
      } else {
        await deleteTestCase(node.id)
      }
      ElMessage.success('已删除')
      if (currentDocId.value === node.id) {
        currentDocId.value = ''
      }
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '删除失败')
    }
  }

  // 定时静默刷新：多人协同下模块/文档可能被他人增删改，轮询保证左侧树最终一致；
  // 不走 loading 遮罩且失败不弹窗，避免每分钟打断用户编辑（错误在下次轮询自愈，手动操作仍走 load() 提示）
  const POLL_INTERVAL = 60_000
  let pollTimer: ReturnType<typeof setInterval> | null = null

  async function silentRefresh() {
    try {
      treeData.value = await fetchProjectModuleTree(getAssetType())
    } catch {
      // 静默忽略，等待下一轮
    }
  }

  onMounted(() => {
    void load()
    pollTimer = setInterval(silentRefresh, POLL_INTERVAL)
  })

  onBeforeUnmount(() => {
    if (pollTimer) clearInterval(pollTimer)
  })

  // el-tree 拖拽回调的 Node.data 是宽松的 TreeNodeData，业务上即模块节点
  function nodeData(node: Node): ProjectModule {
    return node.data as ProjectModule
  }

  function allowDrop(dragging: Node, drop: Node, type: 'prev' | 'inner' | 'next'): boolean {
    // 文档是叶子节点，只允许放入目录内部；同级前后排序不限
    if (type === 'inner') return nodeData(drop).type === 'directory'
    // 文档拖到目录行只允许放入内部：el-tree 按行高上下边缘判为 before/after，
    // 放行会被解析成移到目录的父层级（根目录时 parentId 为 null），与"拖入目录"预期不符
    if (nodeData(dragging).type === 'document' && nodeData(drop).type === 'directory') return false
    return true
  }

  async function handleNodeDrop(dragging: Node, drop: Node, dropType: 'before' | 'after' | 'inner') {
    const parentNode = dropType === 'inner' ? drop : drop.parent
    const parentId = parentNode && parentNode.level > 0 ? nodeData(parentNode).id : null
    const siblings = parentNode?.childNodes ?? []
    const dragId = nodeData(dragging).id
    const targetIndex = Math.max(
      0,
      siblings.findIndex((n) => nodeData(n).id === dragId),
    )
    const dragNode = nodeData(dragging)
    try {
      if (dragNode.type === 'directory' || !isDocumentMode.value) {
        await updateProjectModule(dragId, { parentId, targetIndex })
      } else {
        await updateTestCase(dragId, { moduleId: parentId, targetIndex })
      }
      ElMessage.success('移动成功')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '移动失败')
    }
    await load()
    if (currentDocId.value) {
      await nextTick()
      getTreeRef()?.setCurrentKey(currentDocId.value)
    }
  }

  return {
    isDocumentMode,
    treeData,
    loading,
    currentDocId,
    load,
    handleNodeClick,
    handleCreate,
    handleRename,
    handleDelete,
    silentRefresh,
    allowDrop,
    handleNodeDrop,
    nodeData,
  }
}
