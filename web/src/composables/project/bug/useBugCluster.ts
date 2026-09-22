import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAiTask,
  fetchAiTask,
  fetchLatestBugClustering,
  startBugClustering,
  toBugClusterSnapshot,
  retryAiTask,
} from '@/services/ai'
import { useAiStore } from '@/stores/ai'
import type { AiBugClusterSnapshot, AiTask, BugSeverity } from '@/types'
import { buildModuleBars, buildSeveritySegments } from '@/components/project/bug/bugClusterChart'

const SEVERITY_DOT_MAX = 4
const STATUS_LABEL: Record<string, string> = {
  active: '活跃',
  resolved: '已解决',
  rejected: '已拒绝',
  closed: '已关闭',
}

export interface UseBugClusterOptions {
  modelValue: boolean
  emit: (event: 'update:modelValue', value: boolean) => void
}

export function useBugCluster(options: UseBugClusterOptions) {
  const { emit } = options
  const router = useRouter()
  const aiStore = useAiStore()

  const props = computed(() => ({
    modelValue: options.modelValue,
  }))

  const drawerVisible = computed({
    get: () => props.value.modelValue,
    set: (value: boolean) => emit('update:modelValue', value),
  })

  const task = ref<AiTask | null>(null)
  const starting = ref(false)
  const expandedCluster = ref<number | null>(0)
  const expandedCause = ref<number | null>(null)

  let pollTimer: ReturnType<typeof setInterval> | null = null
  let initialLoad: Promise<void> | null = null

  const snapshot = computed<AiBugClusterSnapshot | null>(() =>
    toBugClusterSnapshot(task.value?.result ?? null),
  )
  const running = computed(
    () => task.value?.status === 'pending' || task.value?.status === 'running',
  )
  const semanticDegraded = computed(() => aiStore.semanticDegraded)

  const summary = computed(() => ({
    bugs: snapshot.value?.bugCount ?? 0,
    clusters: snapshot.value?.clusters.length ?? 0,
    unclustered: snapshot.value?.unclustered.length ?? 0,
  }))

  const moduleBars = computed(() => buildModuleBars(snapshot.value?.clusters.flatMap((c) => c.moduleDist) ?? []))
  const severitySegments = computed(() =>
    buildSeveritySegments(
      snapshot.value?.clusters.reduce<Record<string, number>>((acc, c) => {
        for (const [key, count] of Object.entries(c.severityDist)) {
          acc[key] = (acc[key] ?? 0) + count
        }
        return acc
      }, {}) ?? { fatal: 0, serious: 0, general: 0, minor: 0 },
    ),
  )

  function severityDots(dist: Record<string, number>): { severity: BugSeverity; filled: boolean }[] {
    const order: BugSeverity[] = ['fatal', 'serious', 'general', 'minor']
    const dots: { severity: BugSeverity; filled: boolean }[] = []
    for (let i = 0; i < SEVERITY_DOT_MAX; i++) {
      dots.push({
        severity: order[i] ?? 'minor',
        filled: (dist[order[i] ?? 'minor'] ?? 0) > 0,
      })
    }
    return dots
  }

  function stopPolling(): void {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  async function loadLatest(): Promise<void> {
    try {
      const latest = await fetchLatestBugClustering()
      task.value = latest
      if (latest && (latest.status === 'pending' || latest.status === 'running')) {
        startPolling(latest.id)
      }
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载聚类结果失败')
    }
  }

  function startPolling(taskId: string): void {
    stopPolling()
    pollTimer = setInterval(async () => {
      try {
        const latest = await fetchAiTask(taskId)
        task.value = latest
        if (latest.status === 'success' || latest.status === 'failed' || latest.status === 'cancelled') {
          stopPolling()
        }
      } catch {
        stopPolling()
      }
    }, 2000)
  }

  async function start(): Promise<void> {
    if (task.value && running.value) {
      ElMessage.info('已有聚类任务在执行')
      startPolling(task.value.id)
      return
    }
    starting.value = true
    try {
      const { taskId } = await startBugClustering()
      task.value = {
        id: taskId,
        type: 'bug_clustering',
        targetId: null,
        status: 'pending',
        progress: 0,
        result: null,
        errorMessage: null,
        createdBy: '',
        createdAt: '',
        updatedAt: '',
      }
      expandedCluster.value = 0
      expandedCause.value = null
      startPolling(taskId)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '发起聚类失败')
    } finally {
      starting.value = false
    }
  }

  async function open(): Promise<void> {
    initialLoad = loadLatest()
    await initialLoad
    if (running.value) return
  }

  async function cancel(): Promise<void> {
    if (!task.value) return
    try {
      await ElMessageBox.confirm('取消后已产出的部分结果仍可查看，确定取消？', '取消任务', { type: 'warning' })
    } catch {
      return
    }
    try {
      await cancelAiTask(task.value.id)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '取消失败')
    }
  }

  async function retry(): Promise<void> {
    if (!task.value) return
    try {
      await retryAiTask(task.value.id)
      startPolling(task.value.id)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '重试失败')
    }
  }

  function goDetail(bugId: string): void {
    emit('update:modelValue', false)
    router.push(`/workspace/projects/bugs/${bugId}`)
  }

  watch(
    () => options.modelValue,
    (visible) => {
      if (visible) {
        void open()
      } else {
        stopPolling()
      }
    },
  )

  onBeforeUnmount(stopPolling)

  return {
    drawerVisible,
    task,
    starting,
    expandedCluster,
    expandedCause,
    snapshot,
    running,
    semanticDegraded,
    summary,
    moduleBars,
    severitySegments,
    severityDots,
    start,
    cancel,
    retry,
    goDetail,
    STATUS_LABEL,
  }
}
