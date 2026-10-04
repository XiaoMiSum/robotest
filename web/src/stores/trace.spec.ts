import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTraceStore } from './trace'

const EMPTY_FILTERS = { requirementStatus: '', coverage: '', keyword: '' }

describe('trace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('初始 state', () => {
    it('默认矩阵视图、空筛选与第一页', () => {
      const store = useTraceStore()
      expect(store.view).toBe('matrix')
      expect(store.filters).toEqual(EMPTY_FILTERS)
      expect(store.pageNo).toBe(1)
      expect(store.pageSize).toBe(20)
      expect(store.gapType).toBe('uncovered_requirement')
      expect(store.chainOrigin).toBeNull()
    })
  })

  describe('getter filterCount', () => {
    it('空筛选为 0', () => {
      const store = useTraceStore()
      expect(store.filterCount).toBe(0)
    })

    it('条件逐项累加，关键词含非空白字符计 1', () => {
      const store = useTraceStore()
      store.filters.requirementStatus = 'draft'
      expect(store.filterCount).toBe(1)
      store.filters.coverage = 'covered'
      expect(store.filterCount).toBe(2)
      store.filters.keyword = '  kw  '
      // 角标按 trim 后判定，首尾空白不产生额外计数
      expect(store.filterCount).toBe(3)
    })

    it('纯空白关键词不计数', () => {
      const store = useTraceStore()
      store.filters.requirementStatus = 'draft'
      store.filters.coverage = 'covered'
      store.filters.keyword = '   '
      expect(store.filterCount).toBe(2)
      store.filters.keyword = '\t\n'
      expect(store.filterCount).toBe(2)
    })
  })

  describe('action resetFilters', () => {
    it('清空筛选并回到第一页', () => {
      const store = useTraceStore()
      store.filters = { requirementStatus: 'draft', coverage: 'covered', keyword: 'kw' }
      store.pageNo = 4
      store.resetFilters()
      expect(store.filters).toEqual(EMPTY_FILTERS)
      expect(store.pageNo).toBe(1)
    })
  })

  describe('action applyFilters', () => {
    it('整体替换筛选并回到第一页', () => {
      const store = useTraceStore()
      store.filters = { requirementStatus: 'draft', coverage: '', keyword: '' }
      store.pageNo = 3
      store.applyFilters({ requirementStatus: '', coverage: 'covered', keyword: '登录' })
      expect(store.filters).toEqual({ requirementStatus: '', coverage: 'covered', keyword: '登录' })
      expect(store.pageNo).toBe(1)
    })
  })

  describe('action setView', () => {
    it('切换视图不影响分页', () => {
      const store = useTraceStore()
      store.pageNo = 2
      store.setView('gaps')
      expect(store.view).toBe('gaps')
      expect(store.pageNo).toBe(2)
      store.setView('matrix')
      expect(store.view).toBe('matrix')
    })
  })

  describe('action setGapType', () => {
    it('切换缺口类型并回到第一页', () => {
      const store = useTraceStore()
      store.pageNo = 3
      store.setGapType('orphan_case')
      expect(store.gapType).toBe('orphan_case')
      expect(store.pageNo).toBe(1)
    })
  })

  describe('action openChain / closeChain', () => {
    it('打开记录起点，关闭置空', () => {
      const store = useTraceStore()
      store.openChain({ type: 'requirement', id: 'r1', title: 'REQ-001 登录' })
      expect(store.chainOrigin).toEqual({ type: 'requirement', id: 'r1', title: 'REQ-001 登录' })
      store.closeChain()
      expect(store.chainOrigin).toBeNull()
    })
  })
})
