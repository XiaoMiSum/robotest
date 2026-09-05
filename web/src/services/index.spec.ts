import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import api, {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens,
} from './index'

// 运行环境为 node，无浏览器全局对象；以下仅内存实现，驱动拦截器所需的 sessionStorage/localStorage/window
class MemoryStorage {
  private readonly store = new Map<string, string>()

  getItem(key: string): string | null {
    return this.store.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value))
  }

  removeItem(key: string): void {
    this.store.delete(key)
  }

  clear(): void {
    this.store.clear()
  }
}

const sessionStorageMock = new MemoryStorage()
const localStorageMock = new MemoryStorage()
const windowLocation = { pathname: '/app', href: '' }

;(globalThis as unknown as { sessionStorage: MemoryStorage }).sessionStorage = sessionStorageMock
;(globalThis as unknown as { localStorage: MemoryStorage }).localStorage = localStorageMock
;(globalThis as unknown as { window: { location: typeof windowLocation } }).window = {
  location: windowLocation,
}

type RequestConfig = InternalAxiosRequestConfig & { _retry?: boolean }

function okResponse(config: RequestConfig, body: unknown): AxiosResponse {
  return { data: body, status: 200, statusText: 'OK', headers: {}, config }
}

/** 用 stub adapter 替换真实网络层，让响应拦截器按需驱动 */
function withAdapter(handler: (config: RequestConfig) => AxiosResponse): void {
  api.defaults.adapter = async (config) => handler(config as RequestConfig)
}

beforeEach(async () => {
  sessionStorageMock.clear()
  localStorageMock.clear()
  localStorageMock.setItem('robotest_active_workspace', 'ws-1')
  windowLocation.pathname = '/app'
  windowLocation.href = ''
  vi.restoreAllMocks()
  // 通过一次成功响应把会话失效标记复位，保证用例相互隔离
  withAdapter((config) => okResponse(config, { code: 200, data: null }))
  await api.get('/__reset')
})

describe('services/index.ts 401 刷新流程', () => {
  it('业务码 401 时携带 X-Refresh-Token 刷新并重放原请求', async () => {
    setTokens('old-access', 'old-refresh')
    const refreshSpy = vi.spyOn(axios, 'post').mockResolvedValueOnce({
      data: { code: 200, data: { accessToken: 'new-access', refreshToken: 'new-refresh' } },
    } as AxiosResponse)

    const calls: string[] = []
    withAdapter((config) => {
      calls.push(`${config.url}:${config._retry ?? false}`)
      if (config._retry) return okResponse(config, { code: 200, data: { value: 42 } })
      return okResponse(config, { code: 401, data: null, msg: '鉴权失败' })
    })

    const result = await api.get<{ value: number }>('/foo')

    expect(calls).toEqual(['/foo:false', '/foo:true'])
    expect(refreshSpy).toHaveBeenCalledTimes(1)
    expect(refreshSpy).toHaveBeenCalledWith(
      '/api/auth/refresh',
      null,
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Refresh-Token': 'old-refresh' }),
      }),
    )
    expect(getAccessToken()).toBe('new-access')
    expect(getRefreshToken()).toBe('new-refresh')
    expect(result).toEqual({ value: 42 })
  })

  it('刷新后重放仍返回 401 时不再二次刷新（_retry 拦截），直接失败', async () => {
    setTokens('old-access', 'old-refresh')
    const refreshSpy = vi.spyOn(axios, 'post').mockResolvedValueOnce({
      data: { code: 200, data: { accessToken: 'new-access', refreshToken: 'new-refresh' } },
    } as AxiosResponse)

    const calls: string[] = []
    withAdapter((config) => {
      calls.push(`${config.url}:${config._retry ?? false}`)
      return okResponse(config, { code: 401, data: null, msg: '鉴权失败' })
    })

    await expect(api.get('/foo')).rejects.toThrow('鉴权失败')
    expect(calls).toEqual(['/foo:false', '/foo:true'])
    expect(refreshSpy).toHaveBeenCalledTimes(1)
  })

  it('刷新失败后会话失效，后续 401 直接失败且不再请求后端（杜绝死循环）', async () => {
    setTokens('old-access', 'old-refresh')
    const refreshSpy = vi
      .spyOn(axios, 'post')
      .mockRejectedValueOnce(new Error('refresh failed'))

    const calls: string[] = []
    withAdapter((config) => {
      calls.push(`${config.url}:${config._retry ?? false}`)
      return okResponse(config, { code: 401, data: null, msg: '鉴权失败' })
    })

    await expect(api.get('/foo')).rejects.toThrow('登录已过期')
    // 会话已失效：第二个 401 不再触碰后端，也不再发起刷新
    await expect(api.get('/bar')).rejects.toThrow('登录已过期')

    expect(refreshSpy).toHaveBeenCalledTimes(1)
    expect(calls).toEqual(['/foo:false', '/bar:false'])
    expect(getAccessToken()).toBeNull()
  })

  it('会话失效后任意成功响应复位标记，后续 401 重新走刷新（覆盖重新登录场景）', async () => {
    setTokens('old-access', 'old-refresh')
    vi.spyOn(axios, 'post').mockRejectedValueOnce(new Error('refresh failed'))
    withAdapter((config) => okResponse(config, { code: 401, data: null, msg: '鉴权失败' }))
    await api.get('/first').catch(() => undefined)

    // 登录页等成功响应将标记复位
    withAdapter((config) => okResponse(config, { code: 200, data: { ok: true } }))
    await api.get('/login-success')

    // 重新登录成功写入新 token
    setTokens('a-2', 'r-2')

    // 标记复位后再次 401 会重新发起刷新
    const secondRefresh = vi.spyOn(axios, 'post').mockResolvedValueOnce({
      data: { code: 200, data: { accessToken: 'a2', refreshToken: 'r2' } },
    } as AxiosResponse)
    withAdapter((config) => {
      if (config._retry) return okResponse(config, { code: 200, data: { ok: true } })
      return okResponse(config, { code: 401, data: null, msg: '鉴权失败' })
    })
    await api.get('/again')
    expect(secondRefresh).toHaveBeenCalledTimes(1)
  })

  it('无 refresh token 时直接清理会话并失败，不请求后端', async () => {
    clearTokens()
    const refreshSpy = vi.spyOn(axios, 'post')
    withAdapter((config) => okResponse(config, { code: 401, data: null, msg: '鉴权失败' }))
    await expect(api.get('/foo')).rejects.toThrow('登录已过期')
    expect(refreshSpy).not.toHaveBeenCalled()
  })

  it('HTTP 状态 401 同样触发刷新流程（兼容后端真实 401）', async () => {
    setTokens('old-access', 'old-refresh')
    const refreshSpy = vi.spyOn(axios, 'post').mockResolvedValueOnce({
      data: { code: 200, data: { accessToken: 'new-access', refreshToken: 'new-refresh' } },
    } as AxiosResponse)

    withAdapter((config) => {
      if (config._retry) return okResponse(config, { code: 200, data: { ok: true } })
      const axiosLikeError = Object.assign(new Error('Unauthorized'), {
        response: { status: 401, data: {} },
        config,
      })
      throw axiosLikeError
    })

    // 刷新后重放成功（_retry 分支返回业务成功）
    await api.get('/foo')
    expect(refreshSpy).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBe('new-access')
  })
})
