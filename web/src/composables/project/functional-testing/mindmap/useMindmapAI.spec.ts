import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MountTargetSource } from '@/components/project/functional-testing/minder/ai/aiMount'
import type { AiGeneratedNode, RequirementSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  getDocumentRequirements: vi.fn<() => Promise<RequirementSummary[]>>(),
  setDocumentRequirements: vi.fn<() => Promise<void>>(),
  mountGeneratedNodes: vi.fn<(minder: unknown, targetId: string, nodes: AiGeneratedNode[]) => number | null>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  getDocumentRequirements: mocks.getDocumentRequirements,
  setDocumentRequirements: mocks.setDocumentRequirements,
}))

vi.mock('@/components/project/functional-testing/minder/ai/aiMount', () => ({
  mountGeneratedNodes: mocks.mountGeneratedNodes,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useMindmapAI } from './useMindmapAI'

function buildNode(id: string, text: string, children: MountTargetSource[] = []): MountTargetSource {
  return {
    data: { id, text },
    getChildren: () => children,
  }
}

const sampleNodes: AiGeneratedNode[] = [
  { title: '用例A', type: 'case', priority: 'P1', children: [] },
]

describe('useMindmapAI', () => {
  let root: MountTargetSource
  let child1: MountTargetSource
  let child2: MountTargetSource

  beforeEach(() => {
    vi.clearAllMocks()
    child2 = buildNode('n3', 'Leaf')
    child1 = buildNode('n2', 'Child', [child2])
    root = buildNode('n1', 'Root', [child1])
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function makeSut(overrides?: { getMinder?: () => unknown; getSelected?: () => Record<string, unknown> | null; docId?: () => string }) {
    const getMinder = overrides?.getMinder ?? (() => ({
      getRoot: () => root,
      select: vi.fn(),
      refresh: vi.fn(),
      fire: vi.fn(),
      createNode: vi.fn(),
    }))
    const getSelected = overrides?.getSelected ?? (() => null)
    const docId = overrides?.docId ?? (() => 'doc-1')

    return useMindmapAI(
      getMinder as () => { getRoot: () => MountTargetSource | null } | null,
      getSelected,
      docId,
    )
  }

  describe('初始状态', () => {
    it('所有面板默认关闭', () => {
      const s = makeSut()
      expect(s.aiGenerateVisible.value).toBe(false)
      expect(s.aiCompleteVisible.value).toBe(false)
      expect(s.missingPointsVisible.value).toBe(false)
      expect(s.aiReselectVisible.value).toBe(false)
      expect(s.aiPendingNodes.value).toBeNull()
      expect(s.aiPanelMode.value).toBe('generate')
      expect(s.associatedReqIds.value).toEqual([])
      expect(s.reqSelectorVisible.value).toBe(false)
    })

    it('session 计数器初始为 0', () => {
      const s = makeSut()
      expect(s.aiGenerateSession.value).toBe(0)
      expect(s.aiCompleteSession.value).toBe(0)
    })
  })

  describe('需求关联', () => {
    it('openRequirementSelector 加载已有关联并打开选择器', async () => {
      mocks.getDocumentRequirements.mockResolvedValue([{ id: 'r1' }, { id: 'r2' }] as RequirementSummary[])
      const s = makeSut()
      await s.openRequirementSelector()
      expect(s.associatedReqIds.value).toEqual(['r1', 'r2'])
      expect(s.reqSelectorVisible.value).toBe(true)
      expect(mocks.getDocumentRequirements).toHaveBeenCalledWith('doc-1')
    })

    it('openRequirementSelector 失败时显示错误', async () => {
      mocks.getDocumentRequirements.mockRejectedValue(new Error('网络错误'))
      const s = makeSut()
      await s.openRequirementSelector()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
      expect(s.reqSelectorVisible.value).toBe(false)
    })

    it('openRequirementSelector 非 Error 异常显示通用消息', async () => {
      mocks.getDocumentRequirements.mockRejectedValue('string err')
      const s = makeSut()
      await s.openRequirementSelector()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载关联需求失败')
    })

    it('handleRequirementConfirm 保存并更新关联 ID', async () => {
      mocks.setDocumentRequirements.mockResolvedValue(undefined)
      const s = makeSut()
      const selected = [{ id: 'r1' }, { id: 'r3' }] as RequirementSummary[]
      await s.handleRequirementConfirm(selected)
      expect(mocks.setDocumentRequirements).toHaveBeenCalledWith('doc-1', ['r1', 'r3'])
      expect(s.associatedReqIds.value).toEqual(['r1', 'r3'])
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已更新文档关联需求')
    })

    it('handleRequirementConfirm 失败时显示错误', async () => {
      mocks.setDocumentRequirements.mockRejectedValue(new Error('保存失败'))
      const s = makeSut()
      await s.handleRequirementConfirm([{ id: 'r1' }] as RequirementSummary[])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
    })

    it('handleRequirementConfirm 非 Error 异常显示通用消息', async () => {
      mocks.setDocumentRequirements.mockRejectedValue(42)
      const s = makeSut()
      await s.handleRequirementConfirm([{ id: 'r1' }] as RequirementSummary[])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存关联失败')
    })
  })

  describe('getLiveRoot / findNodePath', () => {
    it('getLiveRoot 返回 minder 根节点', () => {
      const s = makeSut()
      expect(s.getLiveRoot()).toBe(root)
    })

    it('getLiveRoot minder 为 null 时返回 null', () => {
      const s = makeSut({ getMinder: () => null })
      expect(s.getLiveRoot()).toBeNull()
    })

    it('getLiveRoot getRoot 不存在时返回 null', () => {
      const s = makeSut({ getMinder: () => ({}) })
      expect(s.getLiveRoot()).toBeNull()
    })

    it('findNodePath 找到节点返回路径', () => {
      const s = makeSut()
      const path = s.findNodePath(root, 'n3')
      expect(path).toEqual(['Root', 'Child', 'Leaf'])
    })

    it('findNodePath 查找根节点自身', () => {
      const s = makeSut()
      const path = s.findNodePath(root, 'n1')
      expect(path).toEqual(['Root'])
    })

    it('findNodePath 节点不存在返回 null', () => {
      const s = makeSut()
      expect(s.findNodePath(root, 'nonexistent')).toBeNull()
    })

    it('findNodePath root 为 null 返回 null', () => {
      const s = makeSut()
      expect(s.findNodePath(null, 'n1')).toBeNull()
    })
  })

  describe('openAiPanel', () => {
    it('打开 generate 面板并设置根节点信息', () => {
      const s = makeSut()
      s.openAiPanel()
      expect(s.aiGenerateVisible.value).toBe(true)
      expect(s.aiCompleteVisible.value).toBe(false)
      expect(s.aiPanelMode.value).toBe('generate')
      expect(s.aiGenerateTargetNodeId.value).toBe('n1')
      expect(s.aiGenerateTargetPath.value).toBe('Root')
      expect(s.aiGenerateInitialText.value).toBe('')
      expect(s.aiPendingNodes.value).toBeNull()
    })

    it('minder 为 null 时不开面板', () => {
      const s = makeSut({ getMinder: () => null })
      s.openAiPanel()
      expect(s.aiGenerateVisible.value).toBe(false)
    })

    it('根节点 id 为空时不开面板', () => {
      const emptyRoot = buildNode('', '')
      const s = makeSut({
        getMinder: () => ({
          getRoot: () => emptyRoot,
          select: vi.fn(),
          refresh: vi.fn(),
          fire: vi.fn(),
          createNode: vi.fn(),
        }),
      })
      s.openAiPanel()
      expect(s.aiGenerateVisible.value).toBe(false)
    })
  })

  describe('openAiGenerateWithText', () => {
    it('根节点就绪时直接打开面板', () => {
      const s = makeSut()
      s.openAiGenerateWithText('需求')
      expect(s.aiGenerateVisible.value).toBe(true)
      expect(s.aiGenerateInitialText.value).toBe('需求')
    })

    it('根节点未就绪时轮询等待后打开', async () => {
      vi.useFakeTimers()
      let callCount = 0
      const s = makeSut({
        getMinder: () => {
          callCount++
          if (callCount <= 2) return null
          return {
            getRoot: () => root,
            select: vi.fn(),
            refresh: vi.fn(),
            fire: vi.fn(),
            createNode: vi.fn(),
          }
        },
      })
      s.openAiGenerateWithText('需求')
      expect(s.aiGenerateVisible.value).toBe(false)
      await vi.advanceTimersByTimeAsync(500)
      expect(s.aiGenerateVisible.value).toBe(true)
      vi.useRealTimers()
    })

    it('轮询超过 60 次后停止', async () => {
      vi.useFakeTimers()
      const s = makeSut({ getMinder: () => null })
      s.openAiGenerateWithText('需求')
      await vi.advanceTimersByTimeAsync(10000)
      expect(s.aiGenerateVisible.value).toBe(false)
      vi.useRealTimers()
    })
  })

  describe('openAiCompletePanel', () => {
    it('选中 case 节点时打开 complete 面板', () => {
      const s = makeSut({
        getSelected: () => ({ id: 'n2', type: 'case' }),
      })
      s.openAiCompletePanel()
      expect(s.aiCompleteVisible.value).toBe(true)
      expect(s.aiGenerateVisible.value).toBe(false)
      expect(s.aiPanelMode.value).toBe('complete')
      expect(s.aiCompleteTargetNodeId.value).toBe('n2')
      expect(s.aiCompleteTargetPath.value).toBe('Root > Child')
    })

    it('首次打开时自增 session', () => {
      const s = makeSut({
        getSelected: () => ({ id: 'n2', type: 'case' }),
      })
      const before = s.aiCompleteSession.value
      s.openAiCompletePanel()
      expect(s.aiCompleteSession.value).toBe(before + 1)
    })

    it('相同节点再次打开不自增 session', () => {
      const s = makeSut({
        getSelected: () => ({ id: 'n2', type: 'case' }),
      })
      s.openAiCompletePanel()
      const afterFirst = s.aiCompleteSession.value
      s.openAiCompletePanel()
      expect(s.aiCompleteSession.value).toBe(afterFirst)
    })

    it('不同节点再次打开自增 session', () => {
      let selectedId = 'n2'
      const s = makeSut({
        getSelected: () => ({ id: selectedId, type: 'case' }),
      })
      s.openAiCompletePanel()
      const afterFirst = s.aiCompleteSession.value
      selectedId = 'n3'
      s.openAiCompletePanel()
      expect(s.aiCompleteSession.value).toBe(afterFirst + 1)
    })

    it('selected 为 null 时不打开', () => {
      const s = makeSut({ getSelected: () => null })
      s.openAiCompletePanel()
      expect(s.aiCompleteVisible.value).toBe(false)
    })

    it('selected.type 非 case 时不打开', () => {
      const s = makeSut({ getSelected: () => ({ id: 'n2', type: 'module' }) })
      s.openAiCompletePanel()
      expect(s.aiCompleteVisible.value).toBe(false)
    })

    it('root 为 null 时不打开', () => {
      const s = makeSut({
        getMinder: () => null,
        getSelected: () => ({ id: 'n2', type: 'case' }),
      })
      s.openAiCompletePanel()
      expect(s.aiCompleteVisible.value).toBe(false)
    })

    it('selected.id 为空时不打开', () => {
      const s = makeSut({ getSelected: () => ({ id: '', type: 'case' }) })
      s.openAiCompletePanel()
      expect(s.aiCompleteVisible.value).toBe(false)
    })
  })

  describe('handleAiMount', () => {
    it('挂载成功显示成功消息并关闭 generate 面板', () => {
      mocks.mountGeneratedNodes.mockReturnValue(3)
      const s = makeSut()
      s.openAiPanel()
      s.handleAiMount(sampleNodes)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已挂载 3 个 AI 生成节点')
      expect(s.aiGenerateVisible.value).toBe(false)
      expect(s.aiPendingNodes.value).toBeNull()
    })

    it('complete 模式挂载成功关闭 complete 面板', () => {
      mocks.mountGeneratedNodes.mockReturnValue(1)
      const s = makeSut({
        getSelected: () => ({ id: 'n2', type: 'case' }),
      })
      s.openAiCompletePanel()
      s.handleAiMount(sampleNodes)
      expect(s.aiCompleteVisible.value).toBe(false)
    })

    it('minder 为 null 时静默返回', () => {
      const s = makeSut({ getMinder: () => null })
      s.handleAiMount(sampleNodes)
      expect(mocks.mountGeneratedNodes).not.toHaveBeenCalled()
    })

    it('挂载目标已删除 (complete 模式) 显示错误', () => {
      mocks.mountGeneratedNodes.mockReturnValue(null)
      const s = makeSut({
        getSelected: () => ({ id: 'n2', type: 'case' }),
      })
      s.openAiCompletePanel()
      s.handleAiMount(sampleNodes)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('节点已被删除，无法挂载')
    })

    it('挂载目标已删除 (generate 模式) 弹出重选', () => {
      mocks.mountGeneratedNodes.mockReturnValue(null)
      const s = makeSut()
      s.openAiPanel()
      s.handleAiMount(sampleNodes)
      expect(s.aiPendingNodes.value).toEqual(sampleNodes)
      expect(s.aiReselectVisible.value).toBe(true)
      expect(s.aiReselectTree.value.length).toBe(1)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('挂载目标已被删除，请重新选择挂载位置')
    })
  })

  describe('handleAiReselect', () => {
    it('重选后重新挂载到新节点', () => {
      mocks.mountGeneratedNodes.mockReturnValue(1)
      const s = makeSut()
      s.openAiPanel()
      mocks.mountGeneratedNodes.mockReturnValue(null)
      s.handleAiMount(sampleNodes)
      expect(s.aiReselectVisible.value).toBe(true)

      mocks.mountGeneratedNodes.mockReturnValue(1)
      s.handleAiReselect({ id: 'n2', label: 'Child', children: [] })
      expect(s.aiReselectVisible.value).toBe(false)
      expect(s.aiGenerateTargetNodeId.value).toBe('n2')
      expect(s.aiGenerateTargetPath.value).toBe('Root > Child')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已挂载 1 个 AI 生成节点')
    })

    it('无 pending nodes 时重选静默关闭', () => {
      const s = makeSut()
      s.handleAiReselect({ id: 'n2', label: 'Child', children: [] })
      expect(s.aiReselectVisible.value).toBe(false)
    })
  })

  describe('resetPanels', () => {
    it('关闭所有面板', () => {
      const s = makeSut()
      s.openAiPanel()
      s.missingPointsVisible.value = true
      s.resetPanels()
      expect(s.aiGenerateVisible.value).toBe(false)
      expect(s.aiCompleteVisible.value).toBe(false)
      expect(s.missingPointsVisible.value).toBe(false)
    })
  })

  describe('stopAiReadyPoll', () => {
    it('停止轮询并清理 timer', () => {
      const s = makeSut({ getMinder: () => null })
      s.openAiGenerateWithText('需求')
      s.stopAiReadyPoll()
      expect(s.aiGenerateVisible.value).toBe(false)
    })
  })
})
