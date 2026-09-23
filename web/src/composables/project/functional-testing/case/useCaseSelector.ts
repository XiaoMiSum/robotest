import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDocumentNodes, fetchProjectModuleTree } from '@/services/project'
import type { ProjectModule, TestCaseNode } from '@/types'

export function useCaseSelector() {
  const loading = ref(false)
  const modules = ref<ProjectModule[]>([])

  const selectedDocId = ref('')
  const docNodes = ref<TestCaseNode | null>(null)
  const docLoading = ref(false)

  const filterKeyword = ref('')
  const filterPriority = ref('')

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

  return {
    loading,
    modules,
    selectedDocId,
    docNodes,
    docLoading,
    filterKeyword,
    filterPriority,
    loadModules,
    handleNodeClick,
  }
}
