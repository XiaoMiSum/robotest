import axios, { type AxiosRequestConfig } from 'axios'
import type { Result } from '@/types'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// --- Token management (localStorage) ---
const TOKEN_KEY = 'robotest_access_token'
const REFRESH_KEY = 'robotest_refresh_token'

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function setTokens(access: string, refresh: string): void {
  localStorage.setItem(TOKEN_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
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
let pendingRequests: Array<(token: string) => void> = []

api.interceptors.response.use(
  (response) => {
    // Unwrap Result<T> → return data field
    const result = response.data as Result<unknown>
    if (result.code === 200) {
      return result.data as never
    }
    // Non-200 business error: reject with the message
    return Promise.reject(new Error(result.msg || '请求失败'))
  },
  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    // 401: attempt token refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // Queue this request until refresh completes
        return new Promise((resolve) => {
          pendingRequests.push((token: string) => {
            if (originalRequest.headers) {
              ;(originalRequest.headers as Record<string, string>).Authorization = `Bearer ${token}`
            }
            resolve(api(originalRequest))
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
          pendingRequests.forEach((cb) => cb(data.accessToken))
          pendingRequests = []

          return api(originalRequest)
        }
        throw new Error('Refresh failed')
      } catch {
        // Refresh failed: clear tokens and redirect to login
        clearTokens()
        localStorage.removeItem('robotest_active_workspace')
        localStorage.removeItem('robotest_active_project')
        window.location.href = '/login'
        return Promise.reject(error)
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
