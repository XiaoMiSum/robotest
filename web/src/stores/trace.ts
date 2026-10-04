import { defineStore } from 'pinia'
import type { TraceGapType, TraceNodeType } from '@/types'

/** 矩阵筛选条件（返回矩阵页保活），coverage / requirementStatus 为服务端单选枚举串 */
export interface TraceMatrixFilters {
  requirementStatus: string
  coverage: string
  keyword: string
}

function emptyFilters(): TraceMatrixFilters {
  return { requirementStatus: '', coverage: '', keyword: '' }
}

/** 链路抽屉起点：需求详情「查看追溯」与缺口引导均以此进入 */
export interface TraceChainOrigin {
  type: TraceNodeType
  id: string
  title: string
  /** 矩阵单元格点击时的定位类型，抽屉内对该类型节点高亮 */
  focusType?: TraceNodeType
}

export const useTraceStore = defineStore('trace', {
  state: () => ({
    /** matrix 矩阵视图 / gaps 缺口视图（交互 04 §2.1 顶部切换） */
    view: 'matrix' as 'matrix' | 'gaps',
    filters: emptyFilters(),
    pageNo: 1,
    pageSize: 20,
    gapType: 'uncovered_requirement' as TraceGapType,
    /** 非 null 表示链路抽屉打开，跨页返回时保留定位 */
    chainOrigin: null as TraceChainOrigin | null,
  }),
  getters: {
    /** 生效筛选条件数（筛选角标） */
    filterCount: (state): number =>
      (state.filters.requirementStatus ? 1 : 0) +
      (state.filters.coverage ? 1 : 0) +
      (state.filters.keyword.trim() ? 1 : 0),
  },
  actions: {
    resetFilters() {
      this.filters = emptyFilters()
      this.pageNo = 1
    },
    applyFilters(filters: TraceMatrixFilters) {
      this.filters = { ...filters }
      this.pageNo = 1
    },
    setView(view: 'matrix' | 'gaps') {
      this.view = view
    },
    setGapType(type: TraceGapType) {
      this.gapType = type
      this.pageNo = 1
    },
    openChain(origin: TraceChainOrigin) {
      this.chainOrigin = origin
    },
    closeChain() {
      this.chainOrigin = null
    },
  },
})
