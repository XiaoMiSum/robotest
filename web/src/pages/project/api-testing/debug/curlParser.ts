/*
 * cURL 命令解析器（纯前端，快速调试详细设计 4.2）。
 * 仅做文本解析，不执行命令；不支持的参数静默忽略。
 * 兼容 Windows CMD 复制格式：`^` 续行符与 `^"` 转义引号在解析前归一化。
 */

export interface ParsedCurl {
  method: string
  url: string
  headers: { key: string; value: string; enabled: boolean }[]
  bodyType: 'json' | 'raw' | 'form' | null
  bodyContent: unknown
}

const LINE_CONTINUATION = /\\\r?\n/g
/** Windows CMD 格式标记：`^` 续行或 `^"` 转义引号；命中才做 CMD 归一化，避免误伤含字面 ^ 的 bash 命令 */
const WINDOWS_CMD_MARKER = /(\^\s*\r?\n)|(\^")/
const WINDOWS_LINE_CONT = /[ \t]*\^(\r?\n)/g
const WINDOWS_ESCAPE = /\^(.)/g
/** 不支持但带取值的常见参数：跳过时须连同取值一起丢弃，防止取值被误判为 URL */
const VALUE_FLAGS_IGNORED = new Set([
  '-x', '--proxy', '--proxy-user', '--noproxy',
  '-A', '--user-agent', '-e', '--referer',
  '--cacert', '--capath', '--cert', '--key', '--resolve',
  '-m', '--max-time', '--connect-timeout', '--retry',
])

/** 解析 cURL 命令；未找到 URL 时抛错 */
export function parseCurl(command: string): ParsedCurl {
  const tokens = tokenize(normalizeWindowsCmd(command ?? ''))
  let method: string | null = null
  let url: string | null = null
  const headerMap = new Map<string, string>()
  let dataType: 'json' | 'form' | null = null
  const dataValues: string[] = []
  const formData: Record<string, string> = {}

  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i]
    if (VALUE_FLAGS_IGNORED.has(token)) {
      i++
      continue
    }
    switch (token) {
      case '-X':
      case '--request': {
        if (i + 1 < tokens.length) method = tokens[++i].toUpperCase()
        break
      }
      case '-H':
      case '--header': {
        if (i + 1 < tokens.length) collectHeader(headerMap, tokens[++i])
        break
      }
      case '-d':
      case '--data':
      case '--data-raw':
      case '--data-binary':
      case '--data-urlencode': {
        if (i + 1 < tokens.length) {
          if (dataType === null) dataType = 'json'
          dataValues.push(tokens[++i])
        }
        break
      }
      case '-F':
      case '--form': {
        if (i + 1 < tokens.length) {
          dataType = 'form'
          collectForm(formData, tokens[++i])
        }
        break
      }
      case '-b':
      case '--cookie': {
        if (i + 1 < tokens.length) headerMap.set('Cookie', tokens[++i])
        break
      }
      default: {
        // 首个含协议或路径的位置参数视为目标 URL，其余未知参数静默跳过
        if (!token.startsWith('-') && url === null && looksLikeUrl(token)) {
          url = token
        }
      }
    }
  }

  if (url === null) {
    throw new Error('cURL 命令中未找到请求 URL')
  }
  // curl 原生语义：携带请求体且未显式指定方法时默认 POST
  const resolvedMethod = method ?? (dataValues.length || Object.keys(formData).length ? 'POST' : 'GET')
  const resolvedBody = resolveContent(dataType, dataValues.join('&'), formData)
  return {
    method: resolvedMethod,
    url,
    headers: toHeaderList(headerMap),
    bodyType: resolvedBody.type,
    bodyContent: resolvedBody.content,
  }
}

/** Windows CMD 转义归一化：`^` 续行为空格，`^X` 还原为字面 X（含 `^"` → `"`、`^^` → `^`） */
export function normalizeWindowsCmd(command: string): string {
  if (!WINDOWS_CMD_MARKER.test(command)) return command
  return command.replace(WINDOWS_LINE_CONT, ' ').replace(WINDOWS_ESCAPE, '$1')
}

function looksLikeUrl(token: string): boolean {
  return token.includes('://') || token.startsWith('/')
}

function collectHeader(headerMap: Map<string, string>, raw: string): void {
  const colon = raw.indexOf(':')
  if (colon >= 0) {
    headerMap.set(raw.slice(0, colon).trim(), raw.slice(colon + 1).trim())
  } else if (raw.trim()) {
    headerMap.set(raw.trim(), '')
  }
}

function collectForm(formData: Record<string, string>, raw: string): void {
  const eq = raw.indexOf('=')
  if (eq > 0) {
    const key = raw.slice(0, eq)
    const value = raw.slice(eq + 1)
    // @file 语法指向本地文件，前端无文件系统访问能力，丢弃该字段
    if (!value.startsWith('@')) formData[key] = value
  }
}

function resolveContent(
  dataType: 'json' | 'form' | null,
  dataValue: string,
  formData: Record<string, string>,
): { type: 'json' | 'raw' | 'form' | null; content: unknown } {
  if (dataType === 'form') {
    return { type: 'form', content: Object.keys(formData).length ? formData : null }
  }
  if (!dataValue) {
    return { type: null, content: null }
  }
  if (dataType === 'json') {
    // JSON 解析成功返回结构化对象，失败降级为 raw 字符串，避免编辑器展示损坏的 JSON
    try {
      return { type: 'json', content: JSON.parse(dataValue) as unknown }
    } catch {
      return { type: 'raw', content: dataValue }
    }
  }
  return { type: 'raw', content: dataValue }
}

function toHeaderList(headerMap: Map<string, string>): { key: string; value: string; enabled: boolean }[] {
  const headers: { key: string; value: string; enabled: boolean }[] = []
  headerMap.forEach((value, key) => {
    headers.push({ key, value, enabled: true })
  })
  return headers
}

/**
 * 分词：合并行续接符后按 shell 引用规则切分。
 * 双引号内仅 `\"` 与 `\\` 为转义，单引号内为字面量。
 */
export function tokenize(command: string): string[] {
  const normalized = command.replace(LINE_CONTINUATION, ' ')
  const tokens: string[] = []
  let current = ''
  let inSingle = false
  let inDouble = false

  const flush = (): void => {
    if (current) {
      tokens.push(current)
      current = ''
    }
  }

  for (let i = 0; i < normalized.length; i++) {
    const c = normalized.charAt(i)
    if (inSingle) {
      if (c === "'") {
        inSingle = false
      } else {
        current += c
      }
    } else if (inDouble) {
      if (c === '\\' && i + 1 < normalized.length && (normalized.charAt(i + 1) === '"' || normalized.charAt(i + 1) === '\\')) {
        current += normalized.charAt(++i)
      } else if (c === '"') {
        inDouble = false
      } else {
        current += c
      }
    } else if (c === "'") {
      inSingle = true
    } else if (c === '"') {
      inDouble = true
    } else if (/\s/.test(c)) {
      flush()
    } else if (c === '\\' && i + 1 < normalized.length) {
      current += normalized.charAt(++i)
    } else {
      current += c
    }
  }
  flush()
  return tokens
}