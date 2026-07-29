import api from '@/services/index'

export async function fetchPermissions(): Promise<string[]> {
  return api.post('/auth/permissions') as unknown as Promise<string[]>
}

export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await api.post('/auth/change-password', { oldPassword, newPassword })
}
