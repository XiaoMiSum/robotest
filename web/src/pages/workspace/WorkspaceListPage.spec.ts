import { describe, expect, it } from 'vitest'
import pageSource from './WorkspaceListPage.vue?raw'
import composableSource from '../../composables/workspace/useWorkspaceListPage.ts?raw'

describe('WorkspaceListPage demo strings', () => {
  it('保留演示稿的页头、筛选、排序和创建文案', () => {
    expect(pageSource).toContain('我的空间')
    expect(pageSource).toContain('选择一个工作空间开始协作，或创建新的空间')
    expect(pageSource).toContain('搜索空间名称')
    expect(pageSource).toContain('按最近访问排序')
    expect(pageSource).toContain('我管理的')
    expect(pageSource).toContain('从空间开始组织你的项目与成员')
  })

  it('使用约定的网格和分页容量', () => {
    expect(pageSource).toContain('repeat(auto-fill, minmax(300px, 1fr))')
    expect(pageSource).toContain(':page-sizes="[12, 24, 48]"')
  })

  it('状态过滤条件位于搜索输入框之前', () => {
    expect(pageSource.indexOf('workspace-list-page__segment')).toBeLessThan(
      pageSource.indexOf('workspace-list-page__search"'),
    )
  })

  it('创建入口由 workspace:create 权限控制', () => {
    expect(composableSource).toContain("hasPermission('workspace:create')")
    expect(pageSource).toContain('v-if="canCreate"')
  })
})
