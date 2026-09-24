import { afterEach, describe, expect, it, vi } from 'vitest'
import { parseSseFrame, useAiStream } from './useAiStream'

describe('useAiStream 非事件流响应处理', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function stubEnv(
    response: Partial<Response>,
    values: {
      accessToken?: string | null
      workspaceId?: string | null
      projectId?: string | null
    } = {},
  ): ReturnType<typeof vi.fn> {
    const fetchMock = vi.fn().mockResolvedValue(response)
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) =>
        key === 'robotest_access_token' ? (values.accessToken ?? null) : null,
    })
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => {
        if (key === 'robotest_active_workspace') return values.workspaceId ?? null
        if (key === 'robotest_active_project') return values.projectId ?? null
        return null
      },
    })
    vi.stubGlobal('fetch', fetchMock)
    return fetchMock
  }

  it('SSE 建立前的业务异常（HTTP 200 + Result JSON）解出 msg 抛给 onError', async () => {
    stubEnv({
      ok: true,
      body: {} as Response['body'],
      headers: new Headers({ 'content-type': 'application/json' }),
      json: () => Promise.resolve({ code: 1000013004, msg: 'AI 调用频率超限' }),
    })
    const error = await new Promise<Error>((resolve) => {
      useAiStream({ url: '/project/ai/cases/generate', onEvent: () => {}, onError: resolve })
    })
    expect(error.message).toBe('AI 调用频率超限')
  })

  it('非 JSON 的异常响应回退通用文案', async () => {
    stubEnv({
      ok: true,
      body: {} as Response['body'],
      headers: new Headers({ 'content-type': 'text/html' }),
      json: () => Promise.reject(new Error('not json')),
    })
    const error = await new Promise<Error>((resolve) => {
      useAiStream({ url: '/project/ai/cases/generate', onEvent: () => {}, onError: resolve })
    })
    expect(error.message).toBe('AI 请求失败')
  })

  it('项目级 SSE 复用 Axios 的活动上下文适配器', async () => {
    const fetchMock = stubEnv(
      {
        ok: true,
        body: {} as Response['body'],
        headers: new Headers({ 'content-type': 'application/json' }),
        json: () => Promise.resolve({ msg: 'stop' }),
      },
      { accessToken: 'access-1', workspaceId: 'workspace-1', projectId: 'project-1' },
    )

    await new Promise<void>((resolve) => {
      useAiStream({
        url: '/project/ai/cases/generate',
        onEvent: () => {},
        onError: () => resolve(),
      })
    })

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit | undefined
    const headers = new Headers(init?.headers)
    expect(headers.get('Authorization')).toBe('Bearer access-1')
    expect(headers.get('X-Active-Workspace')).toBe('workspace-1')
    expect(headers.get('X-Active-Project')).toBe('project-1')
  })

  it('上下文无关 SSE 不注入旧上下文', async () => {
    const fetchMock = stubEnv(
      {
        ok: true,
        body: {} as Response['body'],
        headers: new Headers({ 'content-type': 'application/json' }),
        json: () => Promise.resolve({ msg: 'stop' }),
      },
      { workspaceId: 'workspace-1', projectId: 'project-1' },
    )

    await new Promise<void>((resolve) => {
      useAiStream({
        url: '/workspace/ai/status',
        onEvent: () => {},
        onError: () => resolve(),
      })
    })

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit | undefined
    const headers = new Headers(init?.headers)
    expect(headers.get('X-Active-Workspace')).toBeNull()
    expect(headers.get('X-Active-Project')).toBeNull()
  })

  it('SSE 显式上下文头优先于已保存上下文', async () => {
    const fetchMock = stubEnv(
      {
        ok: true,
        body: {} as Response['body'],
        headers: new Headers({ 'content-type': 'application/json' }),
        json: () => Promise.resolve({ msg: 'stop' }),
      },
      { workspaceId: 'workspace-old', projectId: 'project-old' },
    )

    await new Promise<void>((resolve) => {
      useAiStream({
        url: '/project/ai/cases/generate',
        headers: {
          'X-Active-Workspace': 'workspace-explicit',
          'X-Active-Project': 'project-explicit',
        },
        onEvent: () => {},
        onError: () => resolve(),
      })
    })

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit | undefined
    const headers = new Headers(init?.headers)
    expect(headers.get('X-Active-Workspace')).toBe('workspace-explicit')
    expect(headers.get('X-Active-Project')).toBe('project-explicit')
  })
})

describe('parseSseFrame SSE 帧解析', () => {
  it('解析 delta 帧为 JSON 事件', () => {
    const frame = 'event: delta\ndata: {"content":"增量"}'
    expect(parseSseFrame(frame)).toEqual({ event: 'delta', data: { content: '增量' } })
  })

  it('解析 done 帧携带结构化结果', () => {
    const frame = 'event: done\ndata: {"summaryMarkdown":"## 总结"}'
    expect(parseSseFrame(frame)).toEqual({
      event: 'done',
      data: { summaryMarkdown: '## 总结' },
    })
  })

  it('解析 error 帧', () => {
    const frame = 'event: error\ndata: {"code":6002,"message":"AI 调用失败"}'
    expect(parseSseFrame(frame)).toEqual({
      event: 'error',
      data: { code: 6002, message: 'AI 调用失败' },
    })
  })

  it('未识别事件类型原样透传（如评审摘要 statistics 扩展帧）', () => {
    const frame = 'event: statistics\ndata: {"totalCases":200}'
    expect(parseSseFrame(frame)).toEqual({
      event: 'statistics',
      data: { totalCases: 200 },
    })
  })

  it('无 event 行时默认 message 事件', () => {
    expect(parseSseFrame('data: {"x":1}')).toEqual({ event: 'message', data: { x: 1 } })
  })

  it('注释心跳行返回 null', () => {
    expect(parseSseFrame(': ping')).toBeNull()
  })

  it('无 data 帧返回 null', () => {
    expect(parseSseFrame('event: delta')).toBeNull()
  })

  it('非 JSON data 原样返回文本', () => {
    expect(parseSseFrame('data: 纯文本增量')).toEqual({ event: 'message', data: '纯文本增量' })
  })

  it('多行 data 拼接后解析', () => {
    const frame = 'event: delta\ndata: line1\ndata: line2'
    expect(parseSseFrame(frame)).toEqual({ event: 'delta', data: 'line1\nline2' })
  })
})
