import { describe, expect, it } from 'vitest'
import {
  collectRegisteredPrefixes,
  filterAssistantLinks,
  isAllowedAssistantLink,
} from './assistantLinkWhitelist'

const PREFIXES = ['/workspace', '/workspaces', '/admin'] as const

describe('isAllowedAssistantLink 站内路径判定', () => {
  it('匹配已注册前缀的 / 开头相对路径放行', () => {
    expect(isAllowedAssistantLink('/workspace', PREFIXES)).toBe(true)
    expect(isAllowedAssistantLink('/workspace/projects/bugs/123', PREFIXES)).toBe(true)
    expect(isAllowedAssistantLink('/admin/dashboard', PREFIXES)).toBe(true)
  })

  it('前缀按路径段边界匹配，/workspace 不误匹配 /workspaces', () => {
    expect(isAllowedAssistantLink('/workspaces', ['/workspace'])).toBe(false)
    expect(isAllowedAssistantLink('/workspaces', PREFIXES)).toBe(true)
    expect(isAllowedAssistantLink('/workspace-evil', PREFIXES)).toBe(false)
  })

  it('站外绝对 URL 与协议相对地址拒绝', () => {
    expect(isAllowedAssistantLink('https://evil.com', PREFIXES)).toBe(false)
    expect(isAllowedAssistantLink('//evil.com', PREFIXES)).toBe(false)
  })

  it('伪协议拒绝', () => {
    expect(isAllowedAssistantLink('javascript:alert(1)', PREFIXES)).toBe(false)
    expect(isAllowedAssistantLink('data:text/html,x', PREFIXES)).toBe(false)
  })
})

describe('filterAssistantLinks 链接过滤', () => {
  it('站内链接原样保留', () => {
    const input = '[缺陷详情](/workspace/projects/bugs/123)'
    expect(filterAssistantLinks(input, PREFIXES)).toBe(input)
  })

  it('站外链接降级为纯文本（保留文字）', () => {
    expect(filterAssistantLinks('[百度](https://www.baidu.com)', PREFIXES)).toBe('百度')
    expect(filterAssistantLinks('[站外](http://evil.com/x)', PREFIXES)).toBe('站外')
  })

  it('伪协议链接降级为纯文本', () => {
    expect(filterAssistantLinks('[x](javascript:alert(1))', PREFIXES)).toBe('x')
    expect(filterAssistantLinks('[x](vbscript:msgbox(1))', PREFIXES)).toBe('x')
  })

  it('协议相对链接降级为纯文本', () => {
    expect(filterAssistantLinks('[x](//evil.com)', PREFIXES)).toBe('x')
  })

  it('站外图片降级为 alt 文本，站内图片保留', () => {
    expect(filterAssistantLinks('![图](https://evil.com/a.png)', PREFIXES)).toBe('图')
    expect(filterAssistantLinks('![图](/workspace/x.png)', PREFIXES)).toBe('![图](/workspace/x.png)')
  })

  it('显式自动链接 <https://...> 一律降级为纯文本（白名单仅放行 / 相对路径）', () => {
    expect(filterAssistantLinks('<https://evil.com>', PREFIXES)).toBe('https://evil.com')
    expect(filterAssistantLinks('<https://localhost/workspace/x>', PREFIXES)).toBe('https://localhost/workspace/x')
  })

  it('代码块与行内代码内容不被过滤', () => {
    const input = '```\n[百度](https://www.baidu.com)\n```'
    expect(filterAssistantLinks(input, PREFIXES)).toBe(input)
    expect(filterAssistantLinks('`[百度](https://www.baidu.com)`', PREFIXES)).toBe('`[百度](https://www.baidu.com)`')
  })

  it('混合内容：仅过滤站外链接，站内与正文不受影响', () => {
    const input = '说明见[文档](/workspace/projects/1)与[外链](https://evil.com)，代码 `[a](https://x.com)` 不动。'
    expect(filterAssistantLinks(input, PREFIXES)).toBe('说明见[文档](/workspace/projects/1)与外链，代码 `[a](https://x.com)` 不动。')
  })
})

describe('collectRegisteredPrefixes 路由前缀收集', () => {
  it('从路由记录收集顶层前缀并去重', () => {
    const routes = [
      { path: '/' },
      { path: '/workspaces' },
      { path: '/workspace/:workspaceId' },
      { path: '/workspace/projects/bugs/:bugId' },
      { path: '/admin/dashboard' },
      { path: '/:pathMatch(.*)*' },
    ]
    const prefixes = collectRegisteredPrefixes(routes)
    expect(prefixes).toContain('/workspace')
    expect(prefixes).toContain('/workspaces')
    expect(prefixes).toContain('/admin')
    expect(prefixes).not.toContain('/:pathMatch(.*)*')
  })
})
