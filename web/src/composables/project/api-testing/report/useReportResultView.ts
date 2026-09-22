import { ref, computed, watch } from 'vue'
import type { ApiReportSceneResult, ApiReportSummary, ApiReportSuiteResult } from '@/types'

interface ReportViewReport {
  reportType: string
  name: string
  status: string
  environmentName: string | null
  executionMode?: string | null
  summary?: ApiReportSummary | null
  result: ApiReportSceneResult | ApiReportSuiteResult | null
  createdAt: string
}

export function useReportResultView(
  report: () => ReportViewReport,
  focusSceneId: () => string | null | undefined,
) {
  const isSuite = computed(() => report().reportType === 'suite')

  const suiteResult = computed<ApiReportSuiteResult | null>(() =>
    isSuite.value ? (report().result as ApiReportSuiteResult | null) : null,
  )

  const sceneResult = computed<ApiReportSceneResult | null>(() =>
    !isSuite.value ? (report().result as ApiReportSceneResult | null) : null,
  )

  const focused = computed(() => isSuite.value && !!focusSceneId() && !!suiteResult.value?.scenes)

  const focusedScene = computed<ApiReportSceneResult | null>(() => {
    if (!focused.value || !focusSceneId()) return null
    return suiteResult.value?.scenes.find((scene) => scene.sceneId === focusSceneId()) ?? null
  })

  const scenes = computed<ApiReportSceneResult[]>(() => {
    if (focused.value) return focusedScene.value ? [focusedScene.value] : []
    if (isSuite.value) return (suiteResult.value?.scenes ?? []) as ApiReportSceneResult[]
    return sceneResult.value ? [sceneResult.value] : []
  })

  function sceneKey(scene: ApiReportSceneResult, index: number): string {
    return scene.sceneId ?? `scene-${index}`
  }

  function defaultSceneExpanded(): Set<string> {
    const v = new Set<string>()
    if (!isSuite.value || focused.value) {
      scenes.value.forEach((scene, index) => v.add(sceneKey(scene, index)))
    }
    return v
  }

  const sceneExpanded = ref<Set<string>>(new Set(defaultSceneExpanded()))

  function isSceneOpen(scene: ApiReportSceneResult, index: number): boolean {
    return sceneExpanded.value.has(sceneKey(scene, index))
  }

  function toggleScene(scene: ApiReportSceneResult, index: number) {
    const key = sceneKey(scene, index)
    const set = new Set(sceneExpanded.value)
    if (set.has(key)) set.delete(key)
    else set.add(key)
    sceneExpanded.value = set
  }

  watch(
    () => [report(), focusSceneId()] as const,
    () => {
      sceneExpanded.value = defaultSceneExpanded()
    },
  )

  const heroStatus = computed<{ ok: boolean; label: string }>(() => {
    const status = report().status
    if (status === 'success' || status === 'passed') return { ok: true, label: '执行成功' }
    if (status === 'failed') return { ok: false, label: '执行失败' }
    if (status === 'partial') return { ok: false, label: '部分通过' }
    return { ok: true, label: status || '执行成功' }
  })

  const sourceLabel = computed(() => {
    if (suiteResult.value?.source === 'schedule') return '定时任务'
    return '平台内执行'
  })

  const heroEnv = computed(() => report().environmentName ?? suiteResult.value?.environmentName ?? sceneResult.value?.environmentName ?? '-')

  const heroTime = computed(() => {
    if (isSuite.value) return suiteResult.value?.triggeredAt ?? report().createdAt
    return sceneResult.value?.executedAt ?? report().createdAt
  })

  const heroTimeLabel = computed(() => (isSuite.value ? '触发时间' : '执行时间'))

  const heroDuration = computed<number | null>(() => {
    const ms = scenes.value.length === 1 ? scenes.value[0]?.summary?.durationMs : suiteResult.value?.summary?.durationMs
    return ms ?? report().summary?.durationMs ?? null
  })

  const heroId = computed<{ label: string; value: string | null }>(() => {
    if (isSuite.value) return { label: 'Task ID', value: suiteResult.value?.taskId ?? null }
    return { label: '场景 ID', value: sceneResult.value?.sceneId ?? null }
  })

  function percent(num: number | undefined, denom: number | undefined): string {
    if (!denom) return '-'
    return `${((num ?? 0) / denom * 100).toFixed(1)}%`
  }

  function formatDuration(ms: number | null | undefined): string {
    if (ms == null) return '-'
    if (ms < 1000) return `${ms} ms`
    return `${(ms / 1000).toFixed(2)} s`
  }

  const stats = computed<Array<{ label: string; value: string; tone: 'g' | 'r' | 'b' | 'n' }>>(() => {
    if (isSuite.value && !focused.value) {
      const s = suiteResult.value?.summary
      return [
        { label: '场景总数', value: String(s?.totalScenes ?? 0), tone: 'b' },
        { label: '通过场景', value: String(s?.passedScenes ?? 0), tone: 'g' },
        { label: '失败场景', value: String(s?.failedScenes ?? 0), tone: 'r' },
        { label: '通过率', value: percent(s?.passedScenes, s?.totalScenes), tone: 'g' },
        { label: '总耗时', value: formatDuration(s?.durationMs ?? null), tone: 'b' },
      ]
    }
    const s = focused.value ? focusedScene.value?.summary : sceneResult.value?.summary
    return [
      { label: '总步骤', value: String(s?.total ?? 0), tone: 'b' },
      { label: '通过', value: String(s?.passed ?? 0), tone: 'g' },
      { label: '失败', value: String(s?.failed ?? 0), tone: 'r' },
      { label: '通过率', value: percent(s?.passed, s?.total), tone: 'g' },
      { label: '总耗时', value: formatDuration(s?.durationMs ?? null), tone: 'b' },
    ]
  })

  const envPresent = computed(() => {
    if (!isSuite.value || focused.value) return false
    return (suiteResult.value?.preprocessors?.length ?? 0) > 0 || (suiteResult.value?.postprocessors?.length ?? 0) > 0
  })

  function sceneMiniStat(scene: ApiReportSceneResult, key: 'total' | 'passed' | 'failed' | 'durationMs'): number | string {
    const s = scene.summary
    if (key === 'durationMs') return formatDuration(s?.durationMs ?? null)
    return s && s[key] != null ? s[key]! : 0
  }

  function statusLabel(status: string): string {
    const map: Record<string, string> = {
      success: '通过',
      passed: '通过',
      failed: '失败',
      partial: '部分通过',
      skipped: '跳过',
    }
    return map[status] ?? status
  }

  function sceneStatusClass(status: string): string {
    if (status === 'success' || status === 'passed') return 'rr-badge--ok'
    if (status === 'failed') return 'rr-badge--fail'
    return 'rr-badge--skip'
  }

  return {
    isSuite,
    suiteResult,
    sceneResult,
    focused,
    focusedScene,
    scenes,
    sceneKey,
    isSceneOpen,
    toggleScene,
    heroStatus,
    sourceLabel,
    heroEnv,
    heroTime,
    heroTimeLabel,
    heroDuration,
    heroId,
    formatDuration,
    stats,
    envPresent,
    sceneMiniStat,
    statusLabel,
    sceneStatusClass,
  }
}

export type { ReportViewReport }
