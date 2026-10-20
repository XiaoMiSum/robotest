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

describe('InterfacesPage KeepAlive 恢复时列表刷新', () => {
  it('列表页经 onActivated 加载数据（新建/编辑器返回列表 Tab 时刷新），不再依赖 onMounted', () => {
    expect(pageSource).toContain("import { computed, onActivated, ref, watch } from 'vue'")
    expect(pageSource).toContain('onActivated(async () => {')
    // 列表页常驻 KeepAlive，onActivated 才能在缓存恢复时再次触发
    expect(workspaceSource).toContain('<KeepAlive>')
    expect(workspaceSource).toContain('v-if="active === LIST_KEY"')
  })
})
