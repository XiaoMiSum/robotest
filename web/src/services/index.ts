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

interface PendingRequest {
  config: AxiosRequestConfig & { _retry?: boolean }
  resolve: (value: unknown) => void
  reject: (reason?: Error) => void
}
let pendingRequests: PendingRequest[] = []

api.interceptors.response.use(
  (response) => {
    // Blob 响应（文件下载）无 Result 包装，直接透传数据
    if (response.data instanceof Blob) {
      return response.data as never
    }
    // Unwrap Result<T> → return data field
    const result = response.data as Result<unknown>
    if (result.code === 200) {
      return result.data as never
    }
    // Non-200 business error: reject with the message; 附加 code 供调用方识别具体业务错误（如 6004 限流）
    const error = new Error(result.msg || '请求失败') as Error & { code?: number }
    error.code = result.code
    return Promise.reject(error)
  },
  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    // 401: attempt token refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // Queue this request until refresh completes；_retry 标记防止刷新重试再次 401 时重复排队
        return new Promise<unknown>((resolve, reject) => {
          pendingRequests.push({
            config: { ...originalRequest, _retry: true },
            resolve,
            reject,
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const refreshToken = getRefreshToken()
        if (!refreshToken) {
          throw new Error('No refresh token')
        }

        // Call refresh endpoint directly (bypass interceptor)
        const response = await axios.post<Result<unknown>>('/api/auth/refresh', null, {
          headers: { 'X-Refresh-Token': refreshToken },
        })

        const result = response.data
        if (result.code === 200) {
          const data = result.data as { accessToken: string; refreshToken: string }
          setTokens(data.accessToken, data.refreshToken)

          // Retry original request
          if (originalRequest.headers) {
            ;(originalRequest.headers as Record<string, string>).Authorization =
              `Bearer ${data.accessToken}`
          }

          // Retry queued requests
          pendingRequests.forEach(({ config, resolve }) => {
            if (config.headers) {
              ;(config.headers as Record<string, string>).Authorization = `Bearer ${data.accessToken}`
            }
            resolve(api(config))
          })
          pendingRequests = []

          return api(originalRequest)
        }
        throw new Error('Refresh failed')
      } catch (refreshError) {
        // Refresh failed: reject queued requests（防止永久挂起），清理并跳转登录
        const expiredError = refreshError instanceof Error ? refreshError : new Error('登录已过期')
        pendingRequests.forEach(({ reject }) => reject(expiredError))
        pendingRequests = []
        clearTokens()
        localStorage.removeItem('robotest_active_workspace')
        localStorage.removeItem('robotest_active_project')
        window.location.href = '/login'
        return Promise.reject(expiredError)
      } finally {
        isRefreshing = false
      }
    }

    // Non-401 errors
    const message = error.response?.data?.msg || error.message || '网络错误'
    return Promise.reject(new Error(message))
  },
)

export default api
