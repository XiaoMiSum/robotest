// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import WorkspaceCard from './WorkspaceCard.vue'
import type { WorkspaceItem } from '@/types'

function makeWorkspace(overrides: Partial<WorkspaceItem> = {}): WorkspaceItem {
  return {
    id: 'workspace-1',
    name: '质量中台',
    description: '核心业务线质量保障',
    workspaceRole: 'c0000000-0000-0000-0000-000000000001',
    workspaceRoleName: '管理员',
    defaultProjectId: 'project-1',
    defaultProjectName: '不应在卡片显示的默认项目',
    memberCount: 1024,
    projectCount: 6,
    testCaseCount: 12345,
    status: 'active',
    createdAt: '2026-09-18T02:24:00',
    lastAccessedAt: null,
    ...overrides,
  }
}

describe('WorkspaceCard', () => {
  it('渲染真实角色、真实统计与可访问的原生按钮', async () => {
    const wrapper = mount(WorkspaceCard, {
      props: { workspace: makeWorkspace(), active: true },
    })

    const button = wrapper.find('button')
    expect(button.exists()).toBe(true)
    expect(button.attributes('aria-label')).toBe('进入工作空间：质量中台')
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('1,024成员')
    expect(wrapper.text()).toContain('6项目')
    expect(wrapper.text()).toContain('12,345用例')
    expect(wrapper.text()).not.toContain('不应在卡片显示的默认项目')
    expect(wrapper.find('.workspace-card__current').text()).toBe('当前空间')

    await button.trigger('click')
    expect(wrapper.emitted('enter')).toHaveLength(1)
  })

  it('归档卡片同时展示归档和只读文字，且不渲染交互按钮', async () => {
    const wrapper = mount(WorkspaceCard, {
      props: {
        workspace: makeWorkspace({
          status: 'dissolved',
          workspaceRole: 'c0000000-0000-0000-0000-000000000002',
          workspaceRoleName: '成员',
        }),
      },
    })

    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.find('[aria-label^="已归档工作空间"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('已归档')
    expect(wrapper.text()).toContain('只读')
    expect(wrapper.text()).toContain('成员')

    await wrapper.trigger('click')
    expect(wrapper.emitted('enter')).toBeUndefined()
  })
})
