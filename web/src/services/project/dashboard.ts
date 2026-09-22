import { get } from '@/services'
import type { ProjectDashboard } from '@/types'

// ==================== 项目工作台 ====================

export function fetchDashboard(): Promise<ProjectDashboard> {
  return get('/project/dashboard')
}
