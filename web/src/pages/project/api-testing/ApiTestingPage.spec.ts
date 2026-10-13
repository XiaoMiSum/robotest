import { describe, expect, it } from 'vitest'
import pageSource from './ApiTestingPage.vue?raw'

describe('ApiTestingPage GitLab 集成入口', () => {
  it('项目设置组按任一权限点放行 GitLab 集成入口并渲染配置子页', () => {
    expect(pageSource).toContain("key: 'gitlab'")
    expect(pageSource).toContain("permissionAny: ['api-env:view', 'api-scene:view']")
    expect(pageSource).toContain('isSettingsItemVisible')
    expect(pageSource).toContain("import GitLabSettingsPage from './gitlab/GitLabSettingsPage.vue'")
    expect(pageSource).toContain('<GitLabSettingsPage v-else-if="activeMenu === \'gitlab\'" />')
  })
})
