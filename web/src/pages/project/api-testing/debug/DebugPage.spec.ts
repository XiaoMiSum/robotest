import { describe, expect, it } from 'vitest'
import pageSource from './DebugPage.vue?raw'

describe('DebugPage 调试标签栏', () => {
  it('标签名截断时悬停查看全文，不引入 tabindex 与键盘切换机制', () => {
    expect(pageSource).toContain(':content="tabTitle(tab)"')
    expect(pageSource).toContain(':trigger="[\'hover\']"')
    expect(pageSource).not.toContain(' tabindex=')
    expect(pageSource).not.toContain('role="tab"')
  })

  it('标签名 tooltip 不改变切换标签与双击重命名的既有事件', () => {
    expect(pageSource).toContain('@click="switchTab(tab.id)"')
    expect(pageSource).toContain('@dblclick="startRename(tab)"')
    expect(pageSource).toContain('class="debug-tab__label"')
  })
})
