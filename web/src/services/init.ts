import api from '@/services/index'

/** 检查系统是否已初始化 */
export async function checkInitStatus(): Promise<{ initialized: boolean }> {
  return api.get('/auth/init/status') as unknown as Promise<{ initialized: boolean }>
}

/** 初始化系统（创建 admin 账号） */
export async function setupInit(password: string): Promise<void> {
  await api.post('/auth/init/setup', { password })
}
