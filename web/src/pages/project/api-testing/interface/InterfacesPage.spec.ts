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

describe('InterfaceWorkspace 页签头形态', () => {
  it('整条标签头对齐快速调试：36px 页签、内缩下划线、无边框新建入口', () => {
    expect(workspaceSource).toContain('addable')
    expect(workspaceSource).not.toContain('type="card"')
    expect(workspaceSource).toContain('@tab-add="handleHeaderCreate"')
    expect(workspaceSource).toContain('padding: 4px 88px 0 6px')
    expect(workspaceSource).toContain('height: 36px')
    expect(workspaceSource).toContain('padding: 0 12px')
    expect(workspaceSource).toContain('background: var(--color-primary-500)')
    expect(workspaceSource).not.toContain('border-style: dashed')
    expect(workspaceSource).toContain('height: 26px')
    expect(workspaceSource).toContain('color: var(--color-neutral-400)')
  })

  it('导入按钮为主色 link 小按钮带图标，且与新建入口上下边缘齐平', () => {
    expect(workspaceSource).toContain('<el-button link type="primary" size="small" data-test="interface-import-btn"')
    expect(workspaceSource).toContain('<el-icon><Download /></el-icon>')
    expect(workspaceSource).not.toContain('interface-create-btn')
    expect(workspaceSource).toContain('top: 4px')
    expect(workspaceSource).toContain('right: 6px')
    expect(workspaceSource).toContain('height: 36px')
    expect(workspaceSource).toContain(':deep(.el-button)')
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
