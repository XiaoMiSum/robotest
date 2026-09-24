import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  del: vi.fn(),
}))

vi.mock('@/services', () => ({
  get: mocks.get,
  post: mocks.post,
  put: mocks.put,
  patch: mocks.patch,
  del: mocks.del,
}))

import { fetchMyWorkspaces, setActiveWorkspacePreference } from './workspace'

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockResolvedValue({ list: [], total: 0, counts: { all: 0, managed: 0, archived: 0 } })
  mocks.put.mockResolvedValue(undefined)
})

describe('workspace service', () => {
  it('列表请求携带关键词、范围和分页参数', () => {
    void fetchMyWorkspaces({ keyword: '质量', scope: 'managed', pageNo: 2, pageSize: 24 })

    expect(mocks.get).toHaveBeenCalledWith('/workspaces', {
      keyword: '质量',
      scope: 'managed',
      pageNo: 2,
      pageSize: 24,
    })
  })

  it('设置活跃空间使用空请求体和目标空间请求头', () => {
    void setActiveWorkspacePreference('workspace-target')

    expect(mocks.put).toHaveBeenCalledWith('/workspaces/active', undefined, {
      headers: { 'X-Active-Workspace': 'workspace-target' },
    })
  })
})
