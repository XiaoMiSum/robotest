import { defineStore } from 'pinia'
import { fetchAiArtifact, fetchAiTask, submitAiTask } from '@/services/ai'
import { fetchBugMetrics, fetchBugTrends } from '@/services/project'
import type {
  AiTaskDetail,
  BugAnalysisGroupBy,
  BugMetricsResp,
  BugSourceRef,
  BugTriageContent,
  BugTrendsResp,
} from '@/types'

/** 同步快路径等待上限（详设 3.6.2 waitSeconds ∈ [0,10]），超时转本地轮询 */
const SYNC_WAIT_SECONDS = 10
const POLL_INTERVAL_MS = 2000

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function isTerminal(status: string): boolean {
  return status === 'succeeded' || status === 'failed' || status === 'cancelled'
}

/** 范围指纹：摘要生成时记录，范围变化后据以判定快照过期 */
function rangeKeyOf(from: string, to: string, groupBy: string): string {
  return `${from}|${to}|${groupBy}`
}

function readStringList(raw: unknown): BugSourceRef[] {
  if (!Array.isArray(raw)) return []
  const refs: BugSourceRef[] = []
  for (const item of raw) {
    if (typeof item !== 'object' || item === null) continue
    const source = item as Record<string, unknown>
    refs.push({
      type: typeof source['type'] === 'string' ? source['type'] : '',
      id: typeof source['id'] === 'string' ? source['id'] : '',
      title: typeof source['title'] === 'string' ? source['title'] : '',
      quote: typeof source['quote'] === 'string' ? source['quote'] : undefined,
    })
  }
  return refs
}

// 请求序号：范围切换后的过期响应丢弃标记（模块级，随 store 生命周期无泄漏风险）
let trendsSeq = 0
let metricsSeq = 0

/**
 * 缺陷分析页状态（交互 5）：范围与分组条件、趋势/度量数据、摘要与分诊任务状态。
 * 范围切换取消上一请求（防竞态，以最新范围结果为准）；
 * 摘要与分诊复用 aiTask 详情缓存会互相挤占，故本地轮询 fetchAiTask。
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
    // ---------- AI 摘要（详设 3.4，只读建议不写入） ----------
    summaryTaskId: null as string | null,
    summaryStatus: '' as AiTaskDetail['status'] | '',
    summaryText: '',
    summaryCitations: [] as BugSourceRef[],
    summaryLoading: false,
    summaryError: '',
    summaryPollTimer: null as ReturnType<typeof setInterval> | null,
    /** 摘要生成时的范围指纹（空串表示尚未生成） */
    summaryRangeKey: '',
    // ---------- 分诊队列（详设 3.7，只读排序建议） ----------
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
    summaryRunning(state): boolean {
      return state.summaryStatus === 'pending' || state.summaryStatus === 'running'
    },
    /** 摘要基于上一次范围计算：范围变化后标记过期，提示重新生成（交互 2.1.2） */
    summaryStale(state): boolean {
      if (!state.summaryRangeKey) return false
      return state.summaryRangeKey !== rangeKeyOf(state.from, state.to, state.groupBy)
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

    // ==================== AI 摘要 ====================

    /** 生成 / 重新生成：按当前范围计算，同步快路径超时转轮询 */
    async generateSummary() {
      this.stopSummaryPolling()
      this.summaryLoading = true
      this.summaryError = ''
      this.summaryText = ''
      this.summaryCitations = []
      try {
        const task = await submitAiTask(
          'bug_trend_summary',
          { ...this.queryRange },
          SYNC_WAIT_SECONDS,
        )
        this.summaryRangeKey = rangeKeyOf(this.from, this.to, this.groupBy)
        this.summaryTaskId = task.taskId
        this.summaryStatus = task.status
        if (isTerminal(task.status)) {
          await this.resolveSummary(task.taskId, task.status)
        } else {
          this.startSummaryPolling(task.taskId)
        }
      } catch (error) {
        this.summaryError = errorMessage(error, '生成缺陷摘要失败')
        this.summaryStatus = 'failed'
      } finally {
        this.summaryLoading = false
      }
    },

    startSummaryPolling(taskId: string) {
      this.stopSummaryPolling()
      this.summaryPollTimer = setInterval(() => {
        void this.pollSummary(taskId)
      }, POLL_INTERVAL_MS)
    },

    stopSummaryPolling() {
      if (this.summaryPollTimer !== null) {
        clearInterval(this.summaryPollTimer)
        this.summaryPollTimer = null
      }
    },

    async pollSummary(taskId: string): Promise<void> {
      try {
        const detail = await fetchAiTask(taskId)
        this.summaryStatus = detail.status
        if (isTerminal(detail.status)) {
          this.stopSummaryPolling()
          await this.resolveSummary(taskId, detail.status)
        }
      } catch {
        // 轮询网络失败不终止轮询，下一轮继续；终态由下一轮判定
      }
    },

    /** 终态分流：成功取产物内容，失败落原因供卡片重试（交互 2.4） */
    async resolveSummary(taskId: string, status: AiTaskDetail['status']) {
      if (status !== 'succeeded') {
        this.summaryError = status === 'failed' ? '摘要生成失败，可重试' : ''
        return
      }
      try {
        const artifact = await fetchAiArtifact(taskId, 'summary-1')
        const content = (artifact['content'] ?? {}) as Record<string, unknown>
        this.summaryText = typeof content['text'] === 'string' ? content['text'] : ''
        this.summaryCitations = readStringList(content['citations'])
        if (!this.summaryText) {
          this.summaryError = '摘要产物为空，可重新生成'
        }
      } catch (error) {
        this.summaryError = errorMessage(error, '加载摘要失败')
      }
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
        if (isTerminal(task.status)) {
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
        if (isTerminal(detail.status)) {
          this.stopTriagePolling()
          await this.resolveTriage(taskId, detail.status)
        }
      } catch {
        // 同摘要轮询：网络失败不终止，下一轮继续
      }
    },

    async resolveTriage(taskId: string, status: AiTaskDetail['status']) {
      if (status !== 'succeeded') {
        this.triageError = status === 'failed' ? '分诊建议生成失败，可重新加载' : ''
        return
      }
      try {
        const artifact = await fetchAiArtifact(taskId, 'triage')
        const content = (artifact['content'] ?? {}) as Record<string, unknown>
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
      this.stopSummaryPolling()
      this.stopTriagePolling()
    },
  },
})

/** 导出给测试重置模块级序号 */
export function resetBugAnalysisSeq(): void {
  trendsSeq = 0
  metricsSeq = 0
}
