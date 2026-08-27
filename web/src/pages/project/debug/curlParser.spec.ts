import { describe, expect, it } from 'vitest'
import { parseCurl, tokenize, normalizeWindowsCmd } from './curlParser'

describe('curlParser', () => {
  it('解析 POST 方法与请求体 JSON（含双引号转义）', () => {
    const parsed = parseCurl(
      "curl -X POST 'https://staging.example.com/api/auth/login' -H 'Content-Type: application/json' -d '{\"username\":\"admin\"}'",
    )
    expect(parsed.method).toBe('POST')
    expect(parsed.url).toBe('https://staging.example.com/api/auth/login')
    expect(parsed.headers).toContainEqual({ key: 'Content-Type', value: 'application/json', enabled: true })
    expect(parsed.bodyType).toBe('json')
    expect(parsed.bodyContent).toEqual({ username: 'admin' })
  })

  it('提取 URL 查询参数', () => {
    const parsed = parseCurl('curl https://example.com/api/users?page=1')
    expect(parsed.url).toBe('https://example.com/api/users?page=1')
    expect(parsed.method).toBe('GET')
    expect(parsed.bodyType).toBeNull()
  })

  it('解析 -F 表单为 form 请求体', () => {
    const parsed = parseCurl("curl -X POST https://example.com/upload -F 'name=robotest' -F 'file=@/tmp/a.png'")
    expect(parsed.bodyType).toBe('form')
    expect(parsed.bodyContent).toEqual({ name: 'robotest' })
  })

  it('解析 -b cookie 为 Cookie 请求头', () => {
    const parsed = parseCurl("curl https://example.com/ -b 'SESSION=abc; THEME=dark'")
    expect(parsed.headers).toContainEqual({ key: 'Cookie', value: 'SESSION=abc; THEME=dark', enabled: true })
  })

  it('非 JSON 文本负载降级为 raw', () => {
    const parsed = parseCurl("curl -X POST https://example.com -d 'a=1&b=2'")
    expect(parsed.bodyType).toBe('raw')
    expect(parsed.bodyContent).toBe('a=1&b=2')
  })

  it('未携带 URL 抛错', () => {
    expect(() => parseCurl("curl -X POST -H 'A: b'")).toThrow('cURL 命令中未找到请求 URL')
  })

  it('解析不支持参数并忽略其取值', () => {
    const parsed = parseCurl(
      'curl --proxy http://127.0.0.1:8888 -k -L -s https://example.com/api --cert ./a.pem',
    )
    expect(parsed.url).toBe('https://example.com/api')
  })

  it('Windows CMD 转义引号与 ^ 续行归一化', () => {
    const cmd = [
      'curl ^"https://www.baidu.com/^" ^',
      '-H ^"Accept: text/html^" ^',
      '-H ^"sec-ch-ua: ^\\^"Chromium^\\^";v=^\\^"148^\\^"^"',
    ].join('\n')
    const parsed = parseCurl(cmd)
    expect(parsed.url).toBe('https://www.baidu.com/')
    expect(parsed.headers).toContainEqual({ key: 'Accept', value: 'text/html', enabled: true })
    expect(parsed.headers).toContainEqual({ key: 'sec-ch-ua', value: '"Chromium";v="148"', enabled: true })
  })

  it('深度双引号转义分词', () => {
    expect(tokenize('curl -d \'{"a":"x y"}\' -H "X-We\\"ird: 1"')).toEqual([
      'curl', '-d', '{"a":"x y"}', '-H', 'X-We"ird: 1',
    ])
  })

  it('normalizeWindowsCmd 不处理无 CMD 标记的 bash 命令', () => {
    expect(normalizeWindowsCmd('curl https://a.com?x=1^2')).toBe('curl https://a.com?x=1^2')
  })
})