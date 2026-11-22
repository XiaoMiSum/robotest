import { defineStore } from 'pinia'
import { fetchAiProposal, fetchAiTask, submitAiTask } from '@/services/ai'
import { fetchBugMetrics, fetchBugTrends } from '@/services/project'
import type {
  AiTaskDetail,
  BugAnalysisGroupBy,
  BugMetricsResp,
  BugTriageContent,
  BugTrendsResp,
} from '@/types'

/** 同步快路径等待上限（详设 3.6.2 waitSeconds ∈ [0,10]），超时转本地轮询 */
const SYNC_WAIT_SECONDS = 10
const POLL_INTERVAL_MS = 2000

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/** 定时获取任务明细：仅 running（进行中）继续轮询，非进行中即停止 */
function shouldStopTaskPolling(status: string): boolean {
  return status !== 'running'
}

// 请求序号：范围切换后的过期响应丢弃标记（模块级，随 store 生命周期无泄漏风险）
let trendsSeq = 0
let metricsSeq = 0

/**
 * 缺陷分析页状态（交互 5）：范围与分组条件、趋势/度量数据、分诊任务状态。
 * 范围切换取消上一请求（防竞态，以最新范围结果为准）；
 * 分诊轮询直接查 aiTask 详情，避免复用全局任务详情缓存造成干扰。
 */
export const useBugAnalysisStore = defineStore('bugAnalysis', {
  state: () => ({
    /** 日期为 yyyy-MM-dd 的 UTC 日历日；空串表示后端缺省（最近 30 天） */
    from: '',
    to: '',
    groupBy: 'none' as BugAnalysisGroupBy,
    trends: null as BugTrendsResp | null,
    trendsLoading: false,
    trendsError: '',
    metrics: null as BugMetricsResp | null,
    metricsLoading: false,
    metricsError: '',
    // ---------- 分诊队列（详设 3.5，只读排序建议） ----------
    triageItems: [] as BugTriageContent['items'],
    triageLoading: false,
    triageError: '',
    triagePollTimer: null as ReturnType<typeof setInterval> | null,
  }),
  getters: {
    /** 当前范围参数（空值不携带，交由后端缺省） */
    queryRange(state): { from?: string; to?: string; groupBy?: BugAnalysisGroupBy } {
      const query: { from?: string; to?: string; groupBy?: BugAnalysisGroupBy } = {}
      if (state.from) query.from = state.from
      if (state.to) query.to = state.to
      if (state.groupBy !== 'none') query.groupBy = state.groupBy
      return query
    },
  },
  actions: {
    // ==================== 趋势 / 度量 ====================

    async loadTrends() {
      const seq = ++trendsSeq
      this.trendsLoading = true
      this.trendsError = ''
      try {
        const resp = await fetchBugTrends(this.queryRange)
        // 范围切换后的过期响应直接丢弃，以最新范围结果为准（交互 5）
        if (seq === trendsSeq) this.trends = resp
      } catch (error) {
        if (seq === trendsSeq) {
          this.trendsError = errorMessage(error, '加载缺陷趋势失败')
        }
      } finally {
        if (seq === trendsSeq) this.trendsLoading = false
      }
    },

    async loadMetrics() {
      const seq = ++metricsSeq
      this.metricsLoading = true
      this.metricsError = ''
      try {
        const resp = await fetchBugMetrics(this.queryRange)
        if (seq === metricsSeq) this.metrics = resp
      } catch (error) {
        if (seq === metricsSeq) {
          this.metricsError = errorMessage(error, '加载质量度量失败')
        }
      } finally {
        if (seq === metricsSeq) this.metricsLoading = false
      }
    },

    /** 范围 / 分组切换：全页数据重取（交互 2.1.2） */
    async reload() {
      await Promise.all([this.loadTrends(), this.loadMetrics()])
    },

    // ==================== 分诊队列 ====================

    /** 分诊建议：同步快路径，超时转轮询；只读排序，不写入任何数据 */
    async loadTriage() {
      this.stopTriagePolling()
      this.triageLoading = true
      this.triageError = ''
      this.triageItems = []
      try {
        const task = await submitAiTask('bug_triage', {}, SYNC_WAIT_SECONDS)
        if (shouldStopTaskPolling(task.status)) {
          await this.resolveTriage(task.taskId, task.status)
        } else {
          this.startTriagePolling(task.taskId)
        }
      } catch (error) {
        // 空集（1000018283）等业务口径按提示降级，不阻塞看板其余区块
        this.triageError = errorMessage(error, '生成分诊建议失败')
      } finally {
        this.triageLoading = false
      }
    },

    startTriagePolling(taskId: string) {
      this.stopTriagePolling()
      this.triagePollTimer = setInterval(() => {
        void this.pollTriage(taskId)
      }, POLL_INTERVAL_MS)
    },

    stopTriagePolling() {
      if (this.triagePollTimer !== null) {
        clearInterval(this.triagePollTimer)
        this.triagePollTimer = null
      }
    },

    async pollTriage(taskId: string): Promise<void> {
      try {
        const detail = await fetchAiTask(taskId)
        if (shouldStopTaskPolling(detail.status)) {
          this.stopTriagePolling()
          await this.resolveTriage(taskId, detail.status)
        }
      } catch {
        // 轮询网络失败不终止，下一轮继续
      }
    },

    async resolveTriage(taskId: string, status: AiTaskDetail['status']) {
      if (status === 'aborted') {
        this.triageError = '分诊建议生成失败，可重新加载'
        return
      }
      if (status === 'running') return
      try {
        const proposal = await fetchAiProposal(taskId, 'triage')
        const content = (proposal.content ?? {}) as Record<string, unknown>
        const rawItems = Array.isArray(content['items']) ? content['items'] : []
        this.triageItems = rawItems.flatMap((item) => {
          if (typeof item !== 'object' || item === null) return []
          const source = item as Record<string, unknown>
          const bugId = source['bugId']
          if (typeof bugId !== 'string') return []
          return [{
            bugId,
            rank: typeof source['rank'] === 'number' ? source['rank'] : 0,
            reason: typeof source['reason'] === 'string' ? source['reason'] : '',
          }]
        })
        if (this.triageItems.length === 0) {
          this.triageError = '当前没有需要分诊的缺陷（激活未指派）'
        }
      } catch (error) {
        this.triageError = errorMessage(error, '加载分诊建议失败')
      }
    },

    // ==================== 生命周期 ====================

    /** 页面卸载：停掉本地轮询，避免计时器泄漏 */
    dispose() {
      this.stopTriagePolling()
    },
  },
})

/** 导出给测试重置模块级序号 */
export function resetBugAnalysisSeq(): void {
  trendsSeq = 0
  metricsSeq = 0
}
