// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  fetchPermissions: vi.fn<() => Promise<string[]>>(),
}))

vi.mock('@/services/auth', () => ({
  fetchPermissions: mocks.fetchPermissions,
}))

import { useAuthStore } from './auth'
import { useAssistantContextStore } from './assistantContext'

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mocks.fetchPermissions.mockResolvedValue([])
  setActivePinia(createPinia())
})

describe('assistant context store', () => {
  it('从 auth store 活动项目读取上下文并保留脑图选择', () => {
    const auth = useAuthStore()
    auth.setActiveWorkspace({ id: 'workspace-1', name: '质量空间', workspaceRole: 'member' })
    auth.setActiveProject('project-1', '核心项目')

    const context = useAssistantContextStore()
    context.registerMindMap('document-1')
    context.setSelectedNode('node-1')

    expect(context.buildPageContext()).toEqual({
      projectId: 'project-1',
      documentId: 'document-1',
      selectedNodeId: 'node-1',
    })
  })

  it('没有活动项目时不注入孤立项目上下文', () => {
    localStorage.setItem('robotest_active_project', 'orphan-project')

    const context = useAssistantContextStore()

    expect(context.buildPageContext()).toEqual({})
  })
})
