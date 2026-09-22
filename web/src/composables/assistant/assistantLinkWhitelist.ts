/**
 * 助手回复链接白名单过滤（全局智能助手详细设计 4.6 / 概要 8 输出防护）：
 * 仅渲染站内相对路径（`/` 开头且匹配已注册路由前缀）的链接，
 * 外部 URL 与伪协议一律降级为纯文本，防止 LLM 输出跳转外站的链接。
 * 纯函数 + 前缀参数注入，便于单测覆盖站内/站外/伪协议用例（§5.3）。
 */

/** 代码块/行内代码占位符，避免过滤逻辑误伤代码内容中的伪链接文本 */
const CODE_PATTERN = /`[^`\n]+`|```[\s\S]*?```/g

/** 提取代码段并替换为占位符，返回 [占位文本, 代码段列表] */
function maskCode(markdown: string): [string, string[]] {
  const codeSpans: string[] = []
  const masked = markdown.replace(CODE_PATTERN, (code) => {
    codeSpans.push(code)
    return `\u0000CODE${codeSpans.length - 1}\u0000`
  })
  return [masked, codeSpans]
}

/** 还原代码占位符为原始代码段 */
function restoreCode(masked: string, codeSpans: string[]): string {
  // 占位符以 NUL 包裹，markdown 正文不可能出现该字符，正则必须含 NUL 才能精确还原，故豁免 no-control-regex
  // eslint-disable-next-line no-control-regex
  return masked.replace(/\u0000CODE(\d+)\u0000/g, (_match, index) => codeSpans[Number(index)] ?? '')
}

/**
 * 链接目标是否为站内相对路径：`/` 开头（排除 `//` 协议相对地址）且
 * 命中已注册路由前缀（按路径段边界匹配，避免 `/workspace` 误匹配 `/workspaces`）。
 */
export function isAllowedAssistantLink(url: string, prefixes: readonly string[]): boolean {
  if (!url.startsWith('/') || url.startsWith('//')) return false
  return prefixes.some((prefix) => url === prefix || url.startsWith(`${prefix}/`))
}

/** 从路由记录收集站内顶层路径前缀（如 /workspace、/admin），供白名单匹配已注册路由 */
export function collectRegisteredPrefixes(routes: Array<{ path: string }>): string[] {
  const prefixes = new Set<string>()
  for (const route of routes) {
    const segment = route.path.split('/')[1]
    if (segment && !segment.startsWith(':')) prefixes.add(`/${segment}`)
  }
  return [...prefixes]
}

/**
 * markdown 链接语法 [text](url) / ![alt](url)，支持 title 后缀。
 * url 内允许一层括号（伪协议如 javascript:alert(1)），但不含空格。
 */
const MARKDOWN_LINK_PATTERN = /(!?)\[([^\]]*)\]\(((?:[^()\s]|\([^()\s]*\))+)(?:\s+(?:"[^"]*"|'[^']*'))?\)/g

/** 显式自动链接 <https://...> */
const AUTOLINK_PATTERN = /<((?:https?|ftp):\/\/[^>\s]+)>/g

/**
 * 过滤 markdown 中的链接：站内白名单链接原样保留；
 * 外部/伪协议链接降级为纯文本（普通链接保留文字，图片保留 alt 文本，自动链接保留 URL 文本）。
 */
export function filterAssistantLinks(markdown: string, prefixes: readonly string[]): string {
  const [masked, codeSpans] = maskCode(markdown)
  const filtered = masked
    .replace(MARKDOWN_LINK_PATTERN, (_match, _bang: string, text: string, url: string) => {
      return isAllowedAssistantLink(url, prefixes) ? _match : text
    })
    .replace(AUTOLINK_PATTERN, (match, url: string) => {
      return isAllowedAssistantLink(url, prefixes) ? match : url
    })
  return restoreCode(filtered, codeSpans)
}
