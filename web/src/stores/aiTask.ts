import { defineStore } from 'pinia'
import { fetchAiTask } from '@/services/ai'
import type { AiTaskDetail, AiTaskStatus } from '@/types'

/** 列表筛选条件（返回列表页保活）；空串表示不筛选 */
export interface AiTaskListFilters {
  type: string
  status: string
}

function emptyFilters(): AiTaskListFilters {
  return { type: '', status: '' }
}

/** 终态不再轮询（详设 2.4 状态分支） */
export function isTerminalTaskStatus(status: AiTaskStatus): boolean {
  return status === 'succeeded' || status === 'failed' || status === 'cancelled'
}

const POLL_INTERVAL_MS = 2000

export const useAiTaskStore = defineStore('aiTask', {
  state: () => ({
    filters: emptyFilters(),
    pageNo: 1,
    pageSize: 20,
    // 详情缓存：切回详情页先展示旧值再刷新，避免闪空
    detail: null as AiTaskDetail | null,
    detailId: '',
    /** 轮询连接中断（UI-PAGE-11：恢复后自动续轮询） */
    pollError: false,
    polling: false,
    pollTimer: null as ReturnType<typeof setInterval> | null,
    // 慢请求未返回时不叠加下一次轮询
    pollBusy: false,
  }),
  actions: {
    resetFilters() {
      this.filters = emptyFilters()
      this.pageNo = 1
    },
    /** 筛选条件变化回第一页 */
    applyFilters(filters: AiTaskListFilters) {
      this.filters = { ...filters }
      this.pageNo = 1
    },
    cacheDetail(detail: AiTaskDetail) {
      this.detail = detail
      this.detailId = detail.taskId
    },
    takeCachedDetail(id: string): AiTaskDetail | null {
      return this.detailId === id ? this.detail : null
    },
    /** 拉取详情并写入缓存；网络失败置中断标记但不终止轮询 */
    async pollOnce(taskId: string): Promise<AiTaskDetail | null> {
      try {
        const detail = await fetchAiTask(taskId)
        this.cacheDetail(detail)
        this.pollError = false
        return detail
      } catch {
        this.pollError = true
        return null
      }
    },
    startPolling(taskId: string) {
      this.stopPolling()
      this.polling = true
      this.pollTimer = setInterval(() => {
        void this.pollTick(taskId)
      }, POLL_INTERVAL_MS)
    },
    stopPolling() {
      if (this.pollTimer !== null) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
      this.polling = false
    },
    async pollTick(taskId: string): Promise<void> {
      if (this.pollBusy) return
      this.pollBusy = true
      try {
        const detail = await this.pollOnce(taskId)
        if (detail !== null && isTerminalTaskStatus(detail.status)) {
          this.stopPolling()
        }
      } finally {
        this.pollBusy = false
      }
    },
  },
})
