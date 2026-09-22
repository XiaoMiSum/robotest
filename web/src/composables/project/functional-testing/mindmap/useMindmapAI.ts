/**
 * 脑图 AI 面板状态管理（US-AI-001/002/004/007）
 * 生成/补全双实例各自常驻：关闭抽屉仅隐藏，会话随实例保留（交互设计 2.2）
 * 重置信号仅当目标节点变化（complete）/ 外部带新文本发起时自增，触发组件内全量重置
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDocumentRequirements, setDocumentRequirements } from '@/services/project'
import { mountGeneratedNodes, type MountTargetSource } from '@/components/project/functional-testing/minder/ai/aiMount'
import type { AiGeneratedNode } from '@/types'
import type { AiPanelMode } from '@/components/project/functional-testing/minder/ai/aiPanelModes'
import type { RequirementSummary } from '@/types'

interface ReselectTreeNode {
  id: string
  label: string
  children: ReselectTreeNode[]
}

interface MinderInstance {
  getRoot?: () => MountTargetSource | null
}

export function useMindmapAI(
  getMinder: () => MinderInstance | null,
  getSelectedNodeData: () => Record<string, unknown> | null,
  docId: () => string,
) {
  // ==================== 需求关联（US-AI-004，不受 AI 开关控制） ====================
  const reqSelectorVisible = ref(false)
  const associatedReqIds = ref<string[]>([])

  async function openRequirementSelector() {
    try {
      const list = await getDocumentRequirements(docId())
      associatedReqIds.value = list.map((r) => r.id)
      reqSelectorVisible.value = true
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载关联需求失败')
    }
  }

  async function handleRequirementConfirm(selected: RequirementSummary[]) {
    try {
      await setDocumentRequirements(docId(), selected.map((r) => r.id))
      associatedReqIds.value = selected.map((r) => r.id)
      ElMessage.success('已更新文档关联需求')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '保存关联失败')
    }
  }

  // ==================== AI 面板状态 ====================
  const aiGenerateVisible = ref(false)
  const aiCompleteVisible = ref(false)
  const aiGenerateSession = ref(0)
  const aiCompleteSession = ref(0)
  const aiPanelMode = ref<AiPanelMode>('generate')
  const aiGenerateTargetNodeId = ref('')
  const aiGenerateTargetPath = ref('')
  const aiGenerateInitialText = ref('')
  const aiCompleteTargetNodeId = ref('')
  const aiCompleteTargetPath = ref('')
  const aiCompleteInitialText = ref('')
  const missingPointsVisible = ref(false)
  const aiPendingNodes = ref<AiGeneratedNode[] | null>(null)
  const aiReselectVisible = ref(false)
  const aiReselectTree = ref<ReselectTreeNode[]>([])

  function getLiveRoot(): MountTargetSource | null {
    const m = getMinder()
    return m?.getRoot?.() ?? null
  }

  function findNodePath(node: MountTargetSource | null, id: string): string[] | null {
    if (!node) return null
    const title = (node.data.text as string) ?? ''
    if (node.data.id === id) return [title]
    for (const child of node.getChildren()) {
      const sub = findNodePath(child, id)
      if (sub) return [title, ...sub]
    }
    return null
  }

  function openAiPanel() {
    const root = getLiveRoot()
    if (!root) return
    const targetId = (root.data.id as string) || ''
    if (!targetId) return
    aiGenerateTargetNodeId.value = targetId
    aiGenerateTargetPath.value = (findNodePath(root, targetId) ?? []).join(' > ')
    aiPanelMode.value = 'generate'
    aiGenerateInitialText.value = ''
    aiPendingNodes.value = null
    aiGenerateVisible.value = true
    aiCompleteVisible.value = false
  }

  function openAiGeneratePrefilled(text: string, root: MountTargetSource): void {
    const targetId = (root.data.id as string) || ''
    if (!targetId) return
    aiGenerateTargetNodeId.value = targetId
    aiGenerateTargetPath.value = (findNodePath(root, targetId) ?? []).join(' > ')
    aiPanelMode.value = 'generate'
    aiGenerateInitialText.value = text
    aiPendingNodes.value = null
    aiGenerateSession.value++
    aiGenerateVisible.value = true
    aiCompleteVisible.value = false
  }

  let aiReadyPollTimer: ReturnType<typeof setInterval> | null = null

  function stopAiReadyPoll(): void {
    if (aiReadyPollTimer) clearInterval(aiReadyPollTimer)
    aiReadyPollTimer = null
  }

  function openAiGenerateWithText(text: string): void {
    const root = getLiveRoot()
    if (root) {
      openAiGeneratePrefilled(text, root)
      return
    }
    let tries = 0
    stopAiReadyPoll()
    aiReadyPollTimer = setInterval(() => {
      const live = getLiveRoot()
      tries += 1
      if (live) {
        openAiGeneratePrefilled(text, live)
        stopAiReadyPoll()
      } else if (tries > 60) {
        stopAiReadyPoll()
      }
    }, 150)
  }

  function openAiCompletePanel() {
    const root = getLiveRoot()
    const selected = getSelectedNodeData()
    if (!root || !selected || selected.type !== 'case') return
    const targetId = (selected.id as string) || ''
    if (!targetId) return
    const targetChanged = targetId !== aiCompleteTargetNodeId.value
    aiCompleteTargetNodeId.value = targetId
    aiCompleteTargetPath.value = (findNodePath(root, targetId) ?? []).join(' > ')
    aiPanelMode.value = 'complete'
    aiCompleteInitialText.value = ''
    aiPendingNodes.value = null
    if (targetChanged) aiCompleteSession.value++
    aiCompleteVisible.value = true
    aiGenerateVisible.value = false
  }

  function buildReselectTree(node: MountTargetSource | null): ReselectTreeNode[] {
    if (!node) return []
    return [{
      id: (node.data.id as string) ?? '',
      label: (node.data.text as string) ?? '',
      children: node.getChildren().flatMap((child) => buildReselectTree(child)),
    }]
  }

  function handleAiMount(nodes: AiGeneratedNode[]) {
    const m = getMinder()
    if (!m) return
    const count = mountGeneratedNodes(
      m as unknown as Parameters<typeof mountGeneratedNodes>[0],
      aiPanelMode.value === 'complete' ? aiCompleteTargetNodeId.value : aiGenerateTargetNodeId.value,
      nodes,
    )
    if (count === null) {
      if (aiPanelMode.value === 'complete') {
        ElMessage.error('节点已被删除，无法挂载')
        return
      }
      aiPendingNodes.value = nodes
      aiReselectTree.value = buildReselectTree(getLiveRoot())
      aiReselectVisible.value = true
      ElMessage.warning('挂载目标已被删除，请重新选择挂载位置')
      return
    }
    ElMessage.success(`已挂载 ${count} 个 AI 生成节点`)
    aiPendingNodes.value = null
    if (aiPanelMode.value === 'complete') {
      aiCompleteVisible.value = false
    } else {
      aiGenerateVisible.value = false
    }
  }

  function handleAiReselect(node: ReselectTreeNode) {
    const pending = aiPendingNodes.value
    aiReselectVisible.value = false
    if (!pending) return
    const root = getLiveRoot()
    const path = (findNodePath(root, node.id) ?? []).join(' > ')
    aiGenerateTargetNodeId.value = node.id
    aiGenerateTargetPath.value = path
    handleAiMount(pending)
  }

  function resetPanels() {
    aiGenerateVisible.value = false
    aiCompleteVisible.value = false
    missingPointsVisible.value = false
  }

  return {
    // 需求关联
    reqSelectorVisible,
    associatedReqIds,
    openRequirementSelector,
    handleRequirementConfirm,
    // AI 面板
    aiGenerateVisible,
    aiCompleteVisible,
    aiGenerateSession,
    aiCompleteSession,
    aiPanelMode,
    aiGenerateTargetNodeId,
    aiGenerateTargetPath,
    aiGenerateInitialText,
    aiCompleteTargetNodeId,
    aiCompleteTargetPath,
    aiCompleteInitialText,
    missingPointsVisible,
    aiPendingNodes,
    aiReselectVisible,
    aiReselectTree,
    // 方法
    getLiveRoot,
    findNodePath,
    openAiPanel,
    openAiGenerateWithText,
    openAiCompletePanel,
    handleAiMount,
    handleAiReselect,
    stopAiReadyPoll,
    resetPanels,
  }
}
