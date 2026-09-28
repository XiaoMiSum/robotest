import axios from 'axios'
import api from '@/services/index'

export async function fetchPermissions(): Promise<string[]> {
  return api.post('/auth/permissions') as unknown as Promise<string[]>
}

export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await api.post('/auth/change-password', { oldPassword, newPassword })
}

/**
 * 退出登录：分别撤销服务端 access / refresh 双令牌（认证详细设计 5.1）。
 *
 * 令牌在调用时快照进请求头并绕过拦截器：登出接口为匿名可达，过期的 access 若走
 * 拦截器会触发一次无谓的刷新重放；短超时保证离线时登出不被阻塞（失败即 best-effort 放行）。
 */
export async function revokeSession(
  accessToken: string | null,
  refreshToken: string | null,
): Promise<void> {
  await axios.post('/api/auth/logout', null, {
    headers: {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(refreshToken ? { 'X-Refresh-Token': refreshToken } : {}),
    },
    timeout: 3000,
  })
}
