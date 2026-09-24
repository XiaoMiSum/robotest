import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResult, Project, ProjectStatusCounts } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchProjects: vi.fn(),
  fetchProjectStatusCounts: vi.fn(),
  authStore: {
    setActiveProject: vi.fn(),
    activeWorkspace: { id: 'workspace-1', name: '质量中台', workspaceRole: 'c0000000-0000-0000-0000-000000000002' },
  },
  router: { push: vi.fn() },
}))

vi.mock('@/services/workspace', () => ({
  fetchProjects: mocks.fetchProjects,
  fetchProjectStatusCounts: mocks.fetchProjectStatusCounts,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.authStore,
}))

vi.mock('vue-router', () => ({
  useRouter: () => mocks.router,
}))

import { useProjectListPage } from './useProjectListPage'

function project(overrides: Partial<Project> = {}): Project {
  return {
    id: 'project-1',
    name: '核心功能测试',
    description: '核心模块回归',
    status: 'active',
    isDefault: false,
    startTime: '2026-06-01T00:00:00Z',
    endTime: '2026-12-31T00:00:00Z',
    createdBy: { id: 'user-1', name: '测试用户' },
    createdAt: '2026-05-01T00:00:00Z',
    ...overrides,
  }
}

function page(overrides: Partial<PageResult<Project>> = {}): PageResult<Project> {
  return {
    list: [project()],
    total: 1,
    ...overrides,
  }
}

function counts(overrides: Partial<ProjectStatusCounts> = {}): ProjectStatusCounts {
  return { active: 5, archived: 1, ...overrides }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.fetchProjects.mockResolvedValue(page())
  mocks.fetchProjectStatusCounts.mockResolvedValue(counts())
  mocks.router.push.mockResolvedValue(undefined)
})

describe('useProjectListPage', () => {
  it('加载分页列表和独立状态数量，并使用名称/描述关键词', async () => {
    const sut = useProjectListPage({ autoLoad: false })
    sut.keyword.value = '  核心  '
    sut.status.value = 'archived'

    await sut.loadProjects()

    expect(mocks.fetchProjects).toHaveBeenCalledWith({
      keyword: '核心',
      status: 'archived',
      pageNo: 1,
      pageSize: 12,
    })
    expect(mocks.fetchProjectStatusCounts).toHaveBeenCalledWith({ keyword: '核心' })
    expect(sut.projects.value[0]?.name).toBe('核心功能测试')
    expect(sut.counts.value).toEqual({ active: 5, archived: 1 })
    expect(sut.isWorkspaceEmpty.value).toBe(false)
  })

  it('关键词防抖查询，Enter 可立即查询', async () => {
    vi.useFakeTimers()
    try {
      const sut = useProjectListPage({ autoLoad: false })
      sut.keyword.value = '核心'
      await vi.advanceTimersByTimeAsync(299)
      expect(mocks.fetchProjects).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1)
      expect(mocks.fetchProjects).toHaveBeenCalledTimes(1)
      await sut.handleSearch()
      expect(mocks.fetchProjects).toHaveBeenCalledTimes(1)
    } finally {
      vi.useRealTimers()
    }
  })

  it('忽略晚到的旧列表响应', async () => {
    let resolveFirst!: (value: PageResult<Project>) => void
    let resolveSecond!: (value: PageResult<Project>) => void
    mocks.fetchProjects
      .mockReturnValueOnce(new Promise<PageResult<Project>>((resolve) => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise<PageResult<Project>>((resolve) => { resolveSecond = resolve }))
    const sut = useProjectListPage({ autoLoad: false })

    const first = sut.loadProjects()
    sut.keyword.value = '新项目'
    const second = sut.loadProjects()
    resolveSecond(page({ list: [project({ id: 'new', name: '新项目' })], total: 1 }))
    await second
    resolveFirst(page({ list: [project({ id: 'old', name: '旧项目' })], total: 1 }))
    await first

    expect(sut.projects.value[0]?.id).toBe('new')
  })

  it('只有活跃项目可以进入项目工作台', () => {
    const sut = useProjectListPage({ autoLoad: false })

    sut.enterProject(project({ status: 'archived' }))
    expect(mocks.authStore.setActiveProject).not.toHaveBeenCalled()
    expect(mocks.router.push).not.toHaveBeenCalled()

    sut.enterProject(project())
    expect(mocks.authStore.setActiveProject).toHaveBeenCalledWith('project-1', '核心功能测试')
    expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/dashboard')
  })
})
