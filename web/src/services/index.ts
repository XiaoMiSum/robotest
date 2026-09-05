import axios, { type AxiosRequestConfig } from 'axios'
import type { Result } from '@/types'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// --- Token management (sessionStorage: 标签页关闭即失效，XSS 无法跨会话持久窃取) ---
const TOKEN_KEY = 'robotest_access_token'
const REFRESH_KEY = 'robotest_refresh_token'

export function getAccessToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_KEY)
}

export function setTokens(access: string, refresh: string): void {
  sessionStorage.setItem(TOKEN_KEY, access)
  sessionStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens(): void {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}

// --- Request interceptor: inject Authorization header ---
api.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  // Inject workspace context header if stored
  const workspaceId = localStorage.getItem('robotest_active_workspace')
  if (workspaceId) {
    config.headers['X-Active-Workspace'] = workspaceId
  }

  // Inject project context header if stored
  const projectId = localStorage.getItem('robotest_active_project')
  if (projectId) {
    config.headers['X-Active-Project'] = projectId
  }

  return config
})

// --- Response interceptor: unwrap Result<T> and handle 401 ---
let isRefreshing = false
// 会话已判定失效（刷新失败过一次）：此后所有 401 直接失败终止，不再反复刷新/重定向，避免死循环
let sessionInvalidated = false

interface PendingRequest {
  config: AxiosRequestConfig & { _retry?: boolean }
  resolve: (value: unknown) => void
  reject: (reason?: Error) => void
}
let pendingRequests: PendingRequest[] = []

/**
 * 认证过期统一出口：使用 refresh token（仅经 X-Refresh-Token 头传递）换取新 token 后重放原请求。
 * 后端以 HTTP 200 + 业务码 401 表达认证失败（JWT 过滤链），故 HTTP 状态与业务码两路都汇聚到此。
 * 单飞：并发 401 仅发起一次刷新，其余排队等待后重放。
 */
async function handleUnauthorized(
  originalRequest: AxiosRequestConfig & { _retry?: boolean },
): Promise<unknown> {
  // 会话已失效：立即失败，不触碰后端，避免登录页残留请求反复触发刷新/重定向
  if (sessionInvalidated) {
    throw new Error('登录已过期')
  }

  if (isRefreshing) {
    // 并发 401 排队在此刷新完成后重放；_retry 标记防止已在刷新的请求再次排队
    return new Promise<unknown>((resolve, reject) => {
      pendingRequests.push({ config: { ...originalRequest, _retry: true }, resolve, reject })
    })
  }

  originalRequest._retry = true
  isRefreshing = true

  try {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      throw new Error('No refresh token')
    }

    // 刷新接口匿名可达（仅信任请求头中的 refresh token），直接调用且绕过拦截器，避免循环 401
    const response = await axios.post<Result<unknown>>('/api/auth/refresh', null, {
      headers: { 'X-Refresh-Token': refreshToken },
    })

    const result = response.data
    if (result.code !== 200 || !result.data) {
      throw new Error('Refresh failed')
    }

    const data = result.data as { accessToken: string; refreshToken: string }
    setTokens(data.accessToken, data.refreshToken)

    // 重放排队请求（请求拦截器会依据已更新的会话 token 自动注入新 Authorization）
    const queued = pendingRequests
    pendingRequests = []
    queued.forEach(({ config, resolve, reject }) => api(config).then(resolve, reject))
    return api(originalRequest)
  } catch {
    // 刷新失败：会话判定失效，释放排队请求（防止永久挂起）、清理会话并跳转登录（仅一次）
    sessionInvalidated = true
    const expiredError = new Error('登录已过期')
    pendingRequests.forEach(({ reject }) => reject(expiredError))
    pendingRequests = []
    clearTokens()
    localStorage.removeItem('robotest_active_workspace')
    localStorage.removeItem('robotest_active_project')
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
    throw expiredError
  } finally {
    isRefreshing = false
  }
}

api.interceptors.response.use(
  (response) => {
    // Blob 响应（文件下载）无 Result 包装，直接透传数据
    if (response.data instanceof Blob) {
      return response.data as never
    }
    // Unwrap Result<T> → return data field
    const result = response.data as Result<unknown>
    if (result.code === 200) {
      // 任意成功响应即恢复会话健康态，解除会话失效标记（覆盖重新登录成功后的场景）
      sessionInvalidated = false
      return result.data as never
    }
    // 401（后端返回 HTTP 200 + code 401）：未重试过则走刷新流程
    const config = response.config as AxiosRequestConfig & { _retry?: boolean }
    if (result.code === 401 && !config._retry) {
      return handleUnauthorized(config) as unknown as never
    }
    // Non-200 business error: reject with the message; 附加 code 供调用方识别具体业务错误（如 6004 限流）
    const error = new Error(result.msg || '请求失败') as Error & { code?: number }
    error.code = result.code
    return Promise.reject(error)
  },
  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    // HTTP 状态 401（兼容后端以真实 401 状态码表达认证失败的情况），同样走刷新
    // ；_retry 已置位（刷新后重放）仍 401 则不再刷新，防止重放递归触发刷新
    if (error.response?.status === 401 && !originalRequest._retry) {
      try {
        await handleUnauthorized(originalRequest)
      } catch (refreshError) {
        return Promise.reject(refreshError)
      }
      return
    }

    // Non-401 errors
    const message = error.response?.data?.msg || error.message || '网络错误'
    return Promise.reject(new Error(message))
  },
)

export default api
