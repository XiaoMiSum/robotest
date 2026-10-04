import { describe, expect, it } from 'vitest'
import pageSource from './FileListPage.vue?raw'
import routerSource from '../../router/index.ts?raw'

describe('FileListPage 交互边界（详设 9）', () => {
  it('列表具备搜索、下载、复制临时链接与权限受控删除', () => {
    expect(pageSource).toContain("hasPermission('file:delete')")
    expect(pageSource).toContain('handleSearch')
    expect(pageSource).toContain('handleDownload')
    expect(pageSource).toContain('handleCopyLink')
    expect(pageSource).toContain('fetchFileAccessUrl')
    expect(pageSource).toContain('handleDelete')
    expect(pageSource).toContain('ElMessageBox.confirm')
    expect(pageSource).toContain('formatFileSize')
    expect(pageSource).toContain('formatDateTime')
    // 加载与刷新失败必须经统一消息提示（UI-PAGE-11）
    expect(pageSource).toContain('ElMessage.error')
  })

  it('路由以 file:view 注册系统维护菜单（详设 9）', () => {
    expect(routerSource).toContain("path: 'files'")
    expect(routerSource).toContain("name: 'AdminFiles'")
    expect(routerSource).toContain("permission: 'file:view'")
    expect(routerSource).toContain("icon: 'FolderOpened'")
  })
})
