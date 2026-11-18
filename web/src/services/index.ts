import axios, { type AxiosRequestConfig } from 'axios'
import type { Result } from '@/types'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// --- Token management (localStorage: 同一浏览器多标签页共享登录态，XSS 约束见安全规范 2.1) ---
const TOKEN_KEY = 'robotest_access_token'
const REFRESH_KEY = 'robotest_refresh_token'

export const ACTIVE_WORKSPACE_STORAGE_KEY = 'robotest_active_workspace'
export const ACTIVE_PROJECT_STORAGE_KEY = 'robotest_active_project'

export interface ActiveRequestContext {
  workspaceId: string | null
  projectId: string | null
}

export type RequestContextScope = 'none' | 'workspace' | 'project'

export interface ContextHeaderOptions {
  url?: string
  method?: string
  headers?: unknown
}

function normalizeContextId(value: string | null | undefined): string | null {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

function readContextValue(key: string): string | null {
  try {
    if (typeof localStorage === 'undefined') return null
    return normalizeContextId(localStorage.getItem(key))
  } catch {
    return null
  }
}

function writeContextValue(key: string, value: string | null): void {
  try {
    if (typeof localStorage === 'undefined') return
    const normalized = normalizeContextId(value)
    if (normalized) {
      localStorage.setItem(key, normalized)
    } else {
      localStorage.removeItem(key)
    }
  } catch {
    // 存储不可用时不能让请求层因持久化失败而崩溃，调用方仍可保留内存状态
  }
}

/** 读取活动上下文的唯一持久化快照，项目必须与工作空间同时存在。 */
export function getActiveContext(): ActiveRequestContext {
  const workspaceId = readContextValue(ACTIVE_WORKSPACE_STORAGE_KEY)
  const projectId = workspaceId ? readContextValue(ACTIVE_PROJECT_STORAGE_KEY) : null
  return { workspaceId, projectId }
}

export function setActiveWorkspaceId(workspaceId: string | null): void {
  writeContextValue(ACTIVE_WORKSPACE_STORAGE_KEY, workspaceId)
}

export function setActiveProjectId(projectId: string | null): void {
  writeContextValue(ACTIVE_PROJECT_STORAGE_KEY, projectId)
}

export function clearActiveContext(): void {
  setActiveWorkspaceId(null)
  setActiveProjectId(null)
}

function normalizeRequestPath(url: string | undefined): string {
  const withoutQuery = (url ?? '').split(/[?#]/, 1)[0] ?? ''
  const withoutOrigin = withoutQuery.replace(/^[a-z][a-z\d+.-]*:\/\/[^/]+/i, '')
  const path = withoutOrigin === '/api' ? '/' : withoutOrigin.replace(/^\/api(?=\/|$)/, '')
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return normalizedPath.length > 1 ? normalizedPath.replace(/\/+$/, '') : normalizedPath
}

// 邀请加入三步为匿名公开接口（详设 1.2 补充说明）：携带过期 Authorization 会触发 JWT 过滤链 401，
// 进而走刷新→失败→硬跳登录，使加入页实质上依赖登录态
const ANONYMOUS_PUBLIC_PATHS = new Set([
  '/workspace/invitations/verify',
  '/workspace/invitations/check-email',
  '/workspace/invitations/join',
])

/** 匿名公开接口：不注入 Authorization，401 不走会话刷新/跳登录流程，交由页面展示错误 */
export function isAnonymousPublicApi(url: string | undefined): boolean {
  return ANONYMOUS_PUBLIC_PATHS.has(normalizeRequestPath(url))
}

/** 依据接口域划分上下文，避免把活动空间/项目泄漏到公共或管理域请求。 */
export function getRequestContextScope(
  url: string | undefined,
  _method: string | undefined = 'get',
): RequestContextScope {
  const path = normalizeRequestPath(url)
  if (path === '/auth/permissions') return 'workspace'
  if (
    path === '/workspace/invitations/verify' ||
    path === '/workspace/invitations/check-email' ||
    path === '/workspace/invitations/join'
  ) {
    return 'none'
  }
  if (path === '/project' || path.startsWith('/project/')) return 'project'
  // AI 任务资源挂 /api/ai/tasks 但按项目范围过滤（详设 3.6.3），需带项目头；/ai/status 仍为 none
  if (path === '/ai/tasks' || path.startsWith('/ai/tasks/')) return 'project'
  // 助手为项目级能力（详设 3.1）：全部会话 / 消息 / 执行接口统一携带工作空间与项目头，
  // 目标项目取 X-Active-Project（详设 3.5 / 4.2②），故一律按 project 作用域注入双头
  if (path === '/ai/conversations' || path.startsWith('/ai/conversations/')) {
    return 'project'
  }
  if (path === '/workspace' || path.startsWith('/workspace/')) return 'workspace'
  return 'none'
}

export function hasRequestHeader(headers: unknown, name: string): boolean {
  if (headers === null || headers === undefined) return false
  const normalizedName = name.toLowerCase()
  if (typeof Headers !== 'undefined' && headers instanceof Headers) {
    return headers.has(name)
  }
  if (Array.isArray(headers)) {
    return headers.some(
      (entry) =>
        Array.isArray(entry) &&
        typeof entry[0] === 'string' &&
        entry[0].toLowerCase() === normalizedName,
    )
  }
  if (typeof headers !== 'object') return false
  return Object.keys(headers).some((key) => key.toLowerCase() === normalizedName)
}

function readRequestHeader(headers: unknown, name: string): string | null {
  if (headers === null || headers === undefined) return null
  if (typeof Headers !== 'undefined' && headers instanceof Headers) {
    return headers.get(name)
  }
  if (Array.isArray(headers)) {
    const entry = headers.find(
      (item) =>
        Array.isArray(item) &&
        typeof item[0] === 'string' &&
        item[0].toLowerCase() === name.toLowerCase(),
    )
    return Array.isArray(entry) && entry[1] != null ? String(entry[1]) : null
  }
  if (typeof headers !== 'object') return null
  const getter = (headers as { get?: unknown }).get
  if (typeof getter === 'function') {
    const value = (getter as (headerName: string) => unknown).call(headers, name)
    return value == null ? null : String(value)
  }
  const key = Object.keys(headers).find(
    (headerName) => headerName.toLowerCase() === name.toLowerCase(),
  )
  if (!key) return null
  const value = (headers as Record<string, unknown>)[key]
  return value == null ? null : String(value)
}

/** Axios 与 SSE 共用的上下文头适配器；调用方已显式提供的头永远优先。 */
export function getContextHeaders(options: ContextHeaderOptions = {}): Record<string, string> {
  const scope = getRequestContextScope(options.url, options.method)
  if (scope === 'none') return {}

  const context = getActiveContext()
  const headers: Record<string, string> = {}
  const hasExplicitWorkspace = hasRequestHeader(options.headers, 'X-Active-Workspace')
  if (context.workspaceId && !hasExplicitWorkspace) {
    headers['X-Active-Workspace'] = context.workspaceId
  }
  const explicitWorkspace = readRequestHeader(options.headers, 'X-Active-Workspace')
  const workspaceMatches = !hasExplicitWorkspace || explicitWorkspace === context.workspaceId
  if (
    scope === 'project' &&
    context.projectId &&
    workspaceMatches &&
    !hasRequestHeader(options.headers, 'X-Active-Project')
  ) {
    headers['X-Active-Project'] = context.projectId
  }
  return headers
}

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

// --- Request interceptor: inject Authorization and scoped context headers ---
api.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token && !isAnonymousPublicApi(config.url) && !hasRequestHeader(config.headers, 'Authorization')) {
    config.headers.Authorization = `Bearer ${token}`
  }

  for (const [name, value] of Object.entries(
    getContextHeaders({ url: config.url, method: config.method, headers: config.headers }),
  )) {
    config.headers[name] = value
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

/** 跨标签页串行化：Web Locks 缺失（旧环境 / node 测试）时退回页内单飞 */
async function runExclusive(name: string, task: () => Promise<void>): Promise<void> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined
  if (locks && typeof locks.request === 'function') {
    await locks.request(name, task)
    return
  }
  await task()
}

/**
 * 跨标签页会话同步（storage 事件仅由其他文档的写入触发）：
 * 令牌被清空（登出 / 刷新失败）→ 本页标记会话失效并回落登录页；
 * 其他标签页新登录而本页停留在登录页 → 刷新进入已登录态（令牌轮换不触发，避免误跳）。
 */
export function handleStorageEvent(event: {
  key: string | null
  oldValue: string | null
  newValue: string | null
}): void {
  if (event.key !== null && event.key !== TOKEN_KEY) return
  if (!getAccessToken()) {
    sessionInvalidated = true
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
    return
  }
  if (event.oldValue === null && event.newValue !== null && window.location.pathname === '/login') {
    window.location.reload()
  }
}

// 模块加载即注册；node 测试环境无 window 时跳过，测试直接调用 handleStorageEvent
if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
  window.addEventListener('storage', (event) => handleStorageEvent(event))
}

/**
 * 认证过期统一出口：使用 refresh token（仅经 X-Refresh-Token 头传递）换取新 token 后重放原请求。
 * 后端以 HTTP 200 + 业务码 401 表达认证失败（JWT 过滤链），故 HTTP 状态与业务码两路都汇聚到此。
 * 单飞：并发 401 仅发起一次刷新，其余排队等待后重放；跨标签页经 Web Locks 排队，
 * 持锁后重读令牌，其他标签页已完成轮换则直接复用其结果。
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

    await runExclusive('robotest_token_refresh', async () => {
      // 持锁后重读：等锁期间其他标签页已完成轮换则跳过刷新，防止同一刷新令牌被并发挤兑
      const current = getRefreshToken()
      if (!current) {
        throw new Error('No refresh token')
      }
      if (current !== refreshToken) return

      // 刷新接口匿名可达（仅信任请求头中的 refresh token），直接调用且绕过拦截器，避免循环 401
      const response = await axios.post<Result<unknown>>('/api/auth/refresh', null, {
        headers: { 'X-Refresh-Token': current },
      })

      const result = response.data
      if (result.code !== 200 || !result.data) {
        throw new Error('Refresh failed')
      }

      const data = result.data as { accessToken: string; refreshToken: string }
      setTokens(data.accessToken, data.refreshToken)
    })

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
    clearActiveContext()
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
      // 匿名公开接口的 401 与会话无关：按业务错误 reject，不触发刷新/硬跳登录
      if (isAnonymousPublicApi(config.url)) {
        const publicError = new Error(result.msg || '请求失败') as Error & { code?: number }
        publicError.code = result.code
        return Promise.reject(publicError)
      }
      return handleUnauthorized(config) as unknown as never
    }
    // Non-200 business error: reject with the message; 附加 code 供调用方识别具体业务错误（如 429 限流）
    const error = new Error(result.msg || '请求失败') as Error & { code?: number }
    error.code = result.code
    return Promise.reject(error)
  },
  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    // HTTP 状态 401（兼容后端以真实 401 状态码表达认证失败的情况），同样走刷新
    // ；_retry 已置位（刷新后重放）仍 401 则不再刷新，防止重放递归触发刷新
    if (error.response?.status === 401 && !originalRequest._retry) {
      // 匿名公开接口不参与会话刷新，直接失败交页面提示
      if (isAnonymousPublicApi(originalRequest.url)) {
        return Promise.reject(new Error(error.response?.data?.msg || '请求失败'))
      }
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

// ==================== 统一 Helper 函数 ====================
// 所有 service 文件应从 '@/services' 导入这些 helper，而非本地定义

/** 通用 GET 请求 — params 自动序列化为 query string */
export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return api.get(url, { params }) as unknown as Promise<T>
}

/** 通用 POST 请求 — 支持可选 config（timeout、signal、headers 等） */
export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return api.post(url, data, config) as unknown as Promise<T>
}

/** 通用 PUT 请求 — 支持可选 config */
export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return api.put(url, data, config) as unknown as Promise<T>
}

/** 通用 PATCH 请求 — 支持可选 config */
export function patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return api.patch(url, data, config) as unknown as Promise<T>
}

/** 通用 DELETE 请求 — 支持可选 config（含 data 用于批量删除） */
export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return api.delete(url, config) as unknown as Promise<T>
}

export default api
