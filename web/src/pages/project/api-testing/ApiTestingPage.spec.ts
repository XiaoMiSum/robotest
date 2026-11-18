import { describe, expect, it } from 'vitest'
import pageSource from './ApiTestingPage.vue?raw'

describe('ApiTestingPage 子模块入口', () => {
  it('所有子模块常驻渲染，不按权限点过滤且无空态', () => {
    for (const key of ['debug', 'interfaces', 'mocks', 'scenes', 'reports', 'schedules']) {
      expect(pageSource).toContain(`key: '${key}'`)
    }
    for (const key of ['environments', 'gitlab', 'functions', 'assets']) {
      expect(pageSource).toContain(`key: '${key}'`)
    }
    expect(pageSource).toContain('v-for="item in settingsItems"')
    expect(pageSource).not.toContain('isSettingsItemVisible')
    expect(pageSource).not.toContain('permissionAny')
    expect(pageSource).not.toContain('暂无可用功能模块')
  })

  it('GitLab 集成入口渲染配置子页', () => {
    expect(pageSource).toContain("key: 'gitlab'")
    expect(pageSource).toContain("import GitLabSettingsPage from './gitlab/GitLabSettingsPage.vue'")
    expect(pageSource).toContain('<GitLabSettingsPage v-else-if="activeMenu === \'gitlab\'" />')
  })
})
