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

import {
  fetchInvitationCopyLink,
  fetchMembers,
  fetchMyWorkspaces,
  setActiveWorkspacePreference,
} from './workspace'

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockResolvedValue({ list: [], total: 0, counts: { all: 0, managed: 0, archived: 0 } })
  mocks.post.mockResolvedValue({ token: 'invite-token' })
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

  it('成员列表请求携带关键词、角色和分页参数', () => {
    void fetchMembers({ keyword: '张', workspaceRole: 'role-admin', pageNo: 2, pageSize: 20 })

    expect(mocks.get).toHaveBeenCalledWith('/workspace/members', {
      keyword: '张',
      workspaceRole: 'role-admin',
      pageNo: 2,
      pageSize: 20,
    })
  })

  it('邀请链接通过专用 POST 接口按需获取', () => {
    void fetchInvitationCopyLink('invitation-1')

    expect(mocks.post).toHaveBeenCalledWith('/workspace/invitations/invitation-1/copy-link')
  })

  it('设置活跃空间使用空请求体和目标空间请求头', () => {
    void setActiveWorkspacePreference('workspace-target')

    expect(mocks.put).toHaveBeenCalledWith('/workspaces/active', undefined, {
      headers: { 'X-Active-Workspace': 'workspace-target' },
    })
  })
})
