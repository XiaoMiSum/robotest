import { defineStore } from 'pinia'
import type { RequirementDetail, RequirementStatus } from '@/types'

/** 列表筛选条件（返回列表页保活）；status / moduleIds 对应服务端逗号分隔多选 */
export interface RequirementListFilters {
  status: RequirementStatus[]
  moduleIds: string[]
  ownerId: string
  systemVersion: string
  keyword: string
}

function emptyFilters(): RequirementListFilters {
  return { status: [], moduleIds: [], ownerId: '', systemVersion: '', keyword: '' }
}

export const useRequirementStore = defineStore('requirement', {
  state: () => ({
    filters: emptyFilters(),
    pageNo: 1,
    pageSize: 20,
    // 详情缓存：切回详情页先展示旧值再刷新，避免闪空
    detail: null as RequirementDetail | null,
    detailId: '',
  }),
  getters: {
    /** 生效筛选条件数（筛选角标） */
    filterCount: (state): number =>
      (state.filters.status.length > 0 ? 1 : 0) +
      (state.filters.moduleIds.length > 0 ? 1 : 0) +
      (state.filters.ownerId ? 1 : 0) +
      (state.filters.systemVersion ? 1 : 0) +
      (state.filters.keyword.trim() ? 1 : 0),
  },
  actions: {
    resetFilters() {
      this.filters = emptyFilters()
      this.pageNo = 1
    },
    /** 任何筛选条件变化都回到第一页 */
    applyFilters(filters: RequirementListFilters) {
      this.filters = { ...filters }
      this.pageNo = 1
    },
    cacheDetail(detail: RequirementDetail) {
      this.detail = detail
      this.detailId = detail.id
    },
    takeCachedDetail(id: string): RequirementDetail | null {
      return this.detailId === id ? this.detail : null
    },
  },
})
