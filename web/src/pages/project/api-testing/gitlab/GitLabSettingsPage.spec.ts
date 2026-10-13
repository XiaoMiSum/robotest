import { describe, expect, it } from 'vitest'
import pageSource from './GitLabSettingsPage.vue?raw'

describe('GitLabSettingsPage 页面责任层', () => {
  it('页签栏常驻连通性徽标与更多菜单，基础配置与导入由页签切换', () => {
    expect(pageSource).toContain('useGitLabRepo')
    expect(pageSource).toContain('el-tab-pane label="基础配置"')
    expect(pageSource).toContain('el-tab-pane label="导入"')
    expect(pageSource).toContain("v-show=\"activeTab === 'basic'\"")
    expect(pageSource).toContain("v-show=\"activeTab === 'import'\"")
    expect(pageSource).toContain("connDown ? '不可达' : '已连通'")
    expect(pageSource).toContain('toggleConnDetail')
    expect(pageSource).toContain('删除配置')
  })

  it('空态给出接入前置清单与立即配置入口，只读态隐藏动作行', () => {
    expect(pageSource).toContain('尚未配置 GitLab 仓库')
    expect(pageSource).toContain('立即配置')
    expect(pageSource).toContain('@Test')
    expect(pageSource).toContain('@RyzeTest')
    expect(pageSource).toContain('<div v-if="canEdit" class="gl-actions">')
    expect(pageSource).toContain('当前账号缺少 api-env:edit 权限，本页只读')
  })

  it('锁定字段只读呈现，测试与保存走责任层，导入页签为占位空态', () => {
    expect(pageSource).toContain('v-if="!creating && repo"')
    expect(pageSource).toContain('创建后锁定')
    expect(pageSource).toContain('@click="runConnectionTest"')
    expect(pageSource).toContain('@click="save"')
    expect(pageSource).toContain('@submit.prevent="save"')
    expect(pageSource).toContain('WP-9.2')
  })
})
