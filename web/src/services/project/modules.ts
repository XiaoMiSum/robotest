import { get, post, put, del } from '@/services'
import type { ProjectModule } from '@/types'

// ==================== 项目模块（V1.2 统一模块树） ====================

// assetType 非空时后端按资产类型统计各模块 count（项目模块详细设计 3.1.1）
export function fetchProjectModuleTree(
  assetType?: 'testcase' | 'interface' | 'scene',
): Promise<ProjectModule[]> {
  return get('/project/modules', assetType ? { assetType } : undefined)
}

export function createProjectModule(data: {
  parentId: string | null
  name: string
}): Promise<ProjectModule> {
  return post('/project/modules', data)
}

// parentId 或 targetIndex 非空时为拖拽移动：parentId 为目标父目录（null 表示根层级）
export function updateProjectModule(
  id: string,
  data: { name?: string; parentId?: string | null; targetIndex?: number },
): Promise<ProjectModule> {
  return put(`/project/modules/${id}`, data)
}

export function deleteProjectModule(id: string): Promise<void> {
  return del(`/project/modules/${id}`)
}
