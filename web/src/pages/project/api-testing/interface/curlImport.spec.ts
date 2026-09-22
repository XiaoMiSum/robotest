import { describe, expect, it } from 'vitest'
import { parseCurlImport, splitCurlCommands, toParsedOperation } from './curlImport'

describe('splitCurlCommands', () => {
  it('按 curl 开头行切分多条命令', () => {
    const commands = splitCurlCommands(
      ["curl -X GET 'https://a.com/ping'", "curl -X POST 'https://a.com/login' -d '{}'"].join('\n'),
    )
    expect(commands).toHaveLength(2)
    expect(commands[0]).toContain('/ping')
    expect(commands[1]).toContain('/login')
  })

  it('续行归属前一条命令', () => {
    const commands = splitCurlCommands(["curl -X POST 'https://a.com/login' \\", "-H 'Content-Type: application/json'", "curl -X GET 'https://a.com/ping'"].join('\n'))
    expect(commands).toHaveLength(2)
    expect(commands[0]).toContain('Content-Type')
  })
})

describe('parseCurlImport', () => {
  it('解析绝对 URL 为相对路径并拆出查询参数', () => {
    const operations = parseCurlImport("curl 'https://api.example.com/api/users?page=1&size=10'")
    expect(operations).toHaveLength(1)
    expect(operations[0].method).toBe('GET')
    expect(operations[0].path).toBe('/api/users')
    expect(operations[0].queryParams).toEqual([
      { key: 'page', value: '1', enabled: true },
      { key: 'size', value: '10', enabled: true },
    ])
  })

  it('映射请求头与 JSON 请求体', () => {
    const operations = parseCurlImport(
      "curl -X POST 'https://api.example.com/api/auth/login' -H 'X-Token: abc' -d '{\"user\":\"a\"}'",
    )
    expect(operations[0].headers).toContainEqual({ key: 'X-Token', value: 'abc', enabled: true })
    expect(operations[0].body).toEqual({ type: 'json', content: { user: 'a' } })
  })

  it('无 URL 的畸形命令被跳过，不影响其余导入', () => {
    const operations = parseCurlImport(["curl --help", "curl 'https://api.example.com/ping'"].join('\n'))
    expect(operations).toHaveLength(1)
    expect(operations[0].path).toBe('/ping')
  })
})

describe('toParsedOperation', () => {
  it('以 method + path 作为导入名称来源', () => {
    const operation = toParsedOperation({
      method: 'DELETE',
      url: 'https://api.example.com/api/users/1',
      headers: [],
      bodyType: null,
      bodyContent: null,
    })
    expect(operation.name).toBe('DELETE /api/users/1')
    expect(operation.body).toBeUndefined()
  })
})