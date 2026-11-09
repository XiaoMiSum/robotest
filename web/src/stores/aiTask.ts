import { defineStore } from 'pinia'
import { fetchAiRunningCount, fetchAiTask, fetchAiTaskEvents } from '@/services/ai'
import type { AiTaskDetail, AiTaskStatus, AiTaskTraceEvent } from '@/types'

/** 列表筛选条件（返回列表页保活）；空串表示不筛选 */
export interface AiTaskListFilters {
  type: string
  status: string
}

function emptyFilters(): AiTaskListFilters {
  return { type: '', status: '' }
}

/** 终态不再轮询（总册 3.6 生命周期：aborted / closed 为终态，proposals_ready 仍轮询） */
export function isTerminalTaskStatus(status: AiTaskStatus): boolean {
  return status === 'aborted' || status === 'closed'
}

const POLL_INTERVAL_MS = 2000
/** 顶栏指示器计数轮询间隔（交互 07 §5）：无需与详情同频，静默失败不打扰全局 */
const COUNT_POLL_INTERVAL_MS = 15000
/** 轨迹单页拉取上限（详设 3.6.4 pageSize 默认 200，上限 500） */
const EVENTS_PAGE_SIZE = 200

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
    /** 顶栏指示器进行中任务数 */
    runningCount: 0,
    countPollTimer: null as ReturnType<typeof setInterval> | null,
    countPollBusy: false,
    /** 轨迹事件（按最新一次执行归属，afterSeq 游标增量拉取；WS 合并预留） */
    events: [] as AiTaskTraceEvent[],
    eventsExecutionId: '',
    /** 下一次请求的 afterSeq 游标（0 起） */
    eventsNextSeq: 0,
    /** 上一页是否可能还有更多（返回条数打满单页上限） */
    eventsHasMore: false,
    eventsLoading: false,
    eventsError: false,
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
    /** 清空轨迹（换任务 / 再次执行后重新回看） */
    resetEvents() {
      this.events = []
      this.eventsExecutionId = ''
      this.eventsNextSeq = 0
      this.eventsHasMore = false
      this.eventsError = false
    },
    /**
     * 轨迹增量拉取（详设 3.6.4）：按 afterSeq 游标续拉，换执行实例自动重置；
     * 慢请求不叠加，实时通道接入前以本游标轮询兜底（交互 2.2 轨迹区）。
     */
    async loadEvents(taskId: string, executionId: string, reset = false): Promise<void> {
      if (this.eventsLoading) return
      if (reset || this.eventsExecutionId !== executionId) {
        this.events = []
        this.eventsExecutionId = executionId
        this.eventsNextSeq = 0
        this.eventsHasMore = false
        this.eventsError = false
      }
      const requestSeq = this.eventsNextSeq
      const requestExecution = executionId
      this.eventsLoading = true
      try {
        const resp = await fetchAiTaskEvents(taskId, executionId, {
          afterSeq: requestSeq,
          pageSize: EVENTS_PAGE_SIZE,
        })
        // 再次执行后旧执行的迟到响应不覆盖当前轨迹
        if (this.eventsExecutionId !== requestExecution) return
        this.events = [...this.events, ...resp.items]
        this.eventsNextSeq = resp.nextSeq ?? requestSeq
        this.eventsHasMore = resp.items.length >= EVENTS_PAGE_SIZE
        this.eventsError = false
      } catch {
        if (this.eventsExecutionId === requestExecution) this.eventsError = true
      } finally {
        if (this.eventsExecutionId === requestExecution) this.eventsLoading = false
      }
    },
    /** 顶栏指示器计数：静默失败（保留上次值），慢请求不叠加 */
    async refreshRunningCount(): Promise<void> {
      if (this.countPollBusy) return
      this.countPollBusy = true
      try {
        const { count } = await fetchAiRunningCount()
        this.runningCount = count
      } catch {
        // 计数失败不打扰全局：保留上次值等待下轮
      } finally {
        this.countPollBusy = false
      }
    },
    /** 随业务布局装载启停（交互 07 §5）；重复调用幂等 */
    startCountPolling() {
      if (this.countPollTimer !== null) return
      void this.refreshRunningCount()
      this.countPollTimer = setInterval(() => {
        void this.refreshRunningCount()
      }, COUNT_POLL_INTERVAL_MS)
    },
    stopCountPolling() {
      if (this.countPollTimer !== null) {
        clearInterval(this.countPollTimer)
        this.countPollTimer = null
      }
    },
  },
})
