/*
 * cURL 导入解析适配（接口管理详细设计 4.4）：把粘贴的多条 cURL 命令归一为后端导入 DTO 操作列表。
 * 复用快速调试的 curlParser；区别在于这里要为接口定义提取相对路径与查询参数，剥离 host。
 */

import { parseCurl, type ParsedCurl } from '@/pages/project/api-testing/debug/curlParser'
import type { ApiParsedImportOperation } from '@/services/project/interface'

/** 按行切分多条 cURL 命令；续行会跟随上一个命令，直到下一条 curl 开头 */
export function splitCurlCommands(text: string): string[] {
  const lines = text.split(/\r?\n/)
  const commands: string[] = []
  let buffer: string[] = []
  for (const line of lines) {
    const startsCommand = /^\s*(curl|curl\.exe)(\s|$)/.test(line)
    if (startsCommand && buffer.length) {
      commands.push(buffer.join('\n'))
      buffer = []
    }
    buffer.push(line)
  }
  if (buffer.length) commands.push(buffer.join('\n'))
  return commands
}

/** 解析输入中的全部 cURL 命令为导入操作；无 URL 的命令（如仅 curl --help）静默丢弃 */
export function parseCurlImport(text: string): ApiParsedImportOperation[] {
  const operations: ApiParsedImportOperation[] = []
  for (const command of splitCurlCommands(text ?? '')) {
    try {
      operations.push(toParsedOperation(parseCurl(command)))
    } catch {
      // 单条解析失败不影响其余命令批量导入
    }
  }
  return operations
}

/** shell 相对路径 + 查询参数：接口定义以 path 存储，host 仅用于调试页回放，导入时不保留 */
export function toParsedOperation(parsed: ParsedCurl): ApiParsedImportOperation {
  const { path, queryParams } = splitUrl(parsed.url)
  return {
    name: `${parsed.method} ${path}`,
    method: parsed.method,
    path,
    headers: parsed.headers,
    queryParams,
    body: parsed.bodyType
      ? { type: parsed.bodyType, content: parsed.bodyContent }
      : undefined,
  }
}

function splitUrl(raw: string): {
  path: string
  queryParams: { key: string; value: string; enabled: boolean }[]
} {
  try {
    const url = new URL(raw)
    const queryParams = [...url.searchParams.entries()].map(([key, value]) => ({
      key,
      value,
      enabled: true,
    }))
    return { path: url.pathname, queryParams }
  } catch {
    // 非绝对 URL（如相对路径）时按首段切分
    const questionIndex = raw.indexOf('?')
    const path = questionIndex >= 0 ? raw.slice(0, questionIndex) : raw
    const query = questionIndex >= 0 ? raw.slice(questionIndex + 1) : ''
    const queryParams = query
      ? query.split('&').map((pair) => {
          const [key, value] = pair.split('=')
          return { key: key || '', value: value ? decodeURIComponent(value) : '', enabled: true }
        })
      : []
    return { path, queryParams }
  }
}