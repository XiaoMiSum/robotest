import { describe, expect, it } from 'vitest'
import pageSource from './InterfacesPage.vue?raw'
import workspaceSource from './InterfaceWorkspace.vue?raw'

describe('InterfacesPage 列表表格高度', () => {
  it('表格区固定高度沿页面高度链路取窗口剩余可用空间，数据超出时表体内滚动', () => {
    expect(pageSource).toContain('class="interfaces-page__table"')
    expect(pageSource).toContain('height="100%"')
    expect(pageSource).toContain('min-height: 0')
  })
})

describe('InterfacesPage 搜索栏容器', () => {
  it('搜索栏不设卡片容器，仅保留工具条且不被压缩', () => {
    expect(pageSource).not.toContain('interfaces-page__search-card')
    expect(pageSource).toContain('<div class="interfaces-page__toolbar">')
    expect(pageSource).toContain('flex-shrink: 0')
  })

  it('搜索栏顶部留白与新增/编辑页表单一致为 12px', () => {
    expect(pageSource).toContain('padding-top: var(--space-md)')
  })
})

describe('InterfaceWorkspace 标签头形态', () => {
  it('直接沿用快速调试页标签头结构，不再用 el-tabs 覆盖样式', () => {
    expect(workspaceSource).not.toContain('<el-tabs')
    expect(workspaceSource).toContain('class="workspace-tabbar"')
    expect(workspaceSource).toContain('class="workspace-tabs"')
    expect(workspaceSource).toContain('class="workspace-tab"')
    expect(workspaceSource).toContain('class="workspace-tabbar__add"')
    expect(workspaceSource).toContain('padding: 4px 6px 0')
    expect(workspaceSource).toContain('height: 36px')
    expect(workspaceSource).toContain('padding: 0 12px')
    expect(workspaceSource).toContain('bottom: -1px')
    expect(workspaceSource).toContain('background: var(--color-primary-500)')
  })

  it('导入按钮与快速调试一致：主色 link 小按钮 + 下载图标，固定在最右', () => {
    expect(workspaceSource).toContain('class="workspace-tabbar__right"')
    expect(workspaceSource).toContain('<el-button link type="primary" size="small" data-test="interface-import-btn"')
    expect(workspaceSource).toContain('<el-icon><Download /></el-icon>')
    expect(workspaceSource).toContain('margin-left: auto')
    expect(workspaceSource).not.toContain('interface-create-btn')
  })
})

describe('InterfacesPage KeepAlive 恢复时列表刷新', () => {
  it('列表页经 onActivated 加载数据（新建/编辑器返回列表 Tab 时刷新），不再依赖 onMounted', () => {
    expect(pageSource).toContain('onActivated')
    expect(pageSource).toContain('onActivated(async () => {')
    // 列表页常驻 KeepAlive，onActivated 才能在缓存恢复时再次触发
    expect(workspaceSource).toContain('<KeepAlive>')
    expect(workspaceSource).toContain('v-if="active === LIST_KEY"')
  })
})

describe('InterfacesPage 列表样板对齐', () => {
  it('名称列使用可聚焦标题组件并接入统一加载反馈', () => {
    expect(pageSource).toContain('ListTitleLink')
    expect(pageSource).toContain('ListLoadFeedback')
    expect(pageSource).toContain('hasLoaded')
    expect(pageSource).toContain('loadError')
    expect(pageSource).toContain('mode="refresh-error"')
  })
})

describe('InterfacesPage 列设置入口', () => {
  it('列设置入口置于操作列表头右侧，工具栏不再展示列设置', () => {
    expect(pageSource).toContain('class="interfaces-page__action-header"')
    expect(pageSource).toContain('justify-content: flex-end')
    expect(pageSource).toContain('icon-only')
    expect(pageSource.match(/<TableColumnSetting/g)).toHaveLength(1)
  })
})
