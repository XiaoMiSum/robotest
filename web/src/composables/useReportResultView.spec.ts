import { describe, expect, it } from 'vitest'
import { nextTick, ref } from 'vue'
import { useReportResultView } from './useReportResultView'
import type { ApiReportSceneResult, ApiReportSuiteResult, ApiReportStepResult } from '@/types'
import type { ReportViewReport } from './useReportResultView'

function makeStep(overrides?: Partial<ApiReportStepResult>): ApiReportStepResult {
  return {
    stepId: 'step-1',
    name: '步骤1',
    status: 'success',
    ...overrides,
  }
}

function makeSceneResult(overrides?: Partial<ApiReportSceneResult>): ApiReportSceneResult {
  return {
    sceneId: 'scene-1',
    sceneName: '场景1',
    environmentName: 'env-1',
    status: 'success',
    executedAt: '2025-01-01T10:00:00',
    summary: {
      total: 5,
      passed: 4,
      failed: 1,
      durationMs: 3000,
    },
    steps: [],
    ...overrides,
  }
}

function makeSuiteResult(overrides?: Partial<ApiReportSuiteResult>): ApiReportSuiteResult {
  return {
    taskId: 'task-1',
    taskName: '任务1',
    source: 'platform',
    triggeredAt: '2025-01-01T10:00:00',
    environmentName: 'env-suite',
    status: 'success',
    summary: {
      totalScenes: 3,
      passedScenes: 2,
      failedScenes: 1,
      durationMs: 10000,
    },
    scenes: [makeSceneResult({ sceneId: 's1' }), makeSceneResult({ sceneId: 's2' })],
    ...overrides,
  }
}

function makeReport(overrides?: Partial<ReportViewReport>): ReportViewReport {
  return {
    reportType: 'suite',
    name: '测试报告',
    status: 'success',
    environmentName: 'env-report',
    executionMode: null,
    summary: { durationMs: 5000 },
    result: makeSuiteResult(),
    createdAt: '2025-01-01T09:00:00',
    ...overrides,
  }
}

describe('useReportResultView', () => {
  describe('isSuite', () => {
    it('returns true when reportType is suite', () => {
      const report = ref(makeReport({ reportType: 'suite' }))
      const { isSuite } = useReportResultView(() => report.value, () => null)
      expect(isSuite.value).toBe(true)
    })

    it('returns false when reportType is scene', () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult() }))
      const { isSuite } = useReportResultView(() => report.value, () => null)
      expect(isSuite.value).toBe(false)
    })
  })

  describe('suiteResult', () => {
    it('returns result when isSuite', () => {
      const suite = makeSuiteResult()
      const report = ref(makeReport({ reportType: 'suite', result: suite }))
      const { suiteResult } = useReportResultView(() => report.value, () => null)
      expect(suiteResult.value).toEqual(suite)
    })

    it('returns null when not isSuite', () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult() }))
      const { suiteResult } = useReportResultView(() => report.value, () => null)
      expect(suiteResult.value).toBeNull()
    })
  })

  describe('sceneResult', () => {
    it('returns result when not isSuite', () => {
      const scene = makeSceneResult()
      const report = ref(makeReport({ reportType: 'scene', result: scene }))
      const { sceneResult } = useReportResultView(() => report.value, () => null)
      expect(sceneResult.value).toEqual(scene)
    })

    it('returns null when isSuite', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult() }))
      const { sceneResult } = useReportResultView(() => report.value, () => null)
      expect(sceneResult.value).toBeNull()
    })
  })

  describe('focused', () => {
    it('returns true when suite with focusSceneId and scenes', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult() }))
      const { focused } = useReportResultView(() => report.value, () => 's1')
      expect(focused.value).toBe(true)
    })

    it('returns false when not suite', () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult() }))
      const { focused } = useReportResultView(() => report.value, () => 's1')
      expect(focused.value).toBe(false)
    })

    it('returns false when focusSceneId is null', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult() }))
      const { focused } = useReportResultView(() => report.value, () => null)
      expect(focused.value).toBe(false)
    })

    it('returns false when focusSceneId is undefined', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult() }))
      const { focused } = useReportResultView(() => report.value, () => undefined)
      expect(focused.value).toBe(false)
    })

    it('returns true when suite has empty scenes array', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ scenes: [] }) }))
      const { focused } = useReportResultView(() => report.value, () => 's1')
      expect(focused.value).toBe(true)
    })
  })

  describe('focusedScene', () => {
    it('returns matched scene by focusSceneId', () => {
      const s1 = makeSceneResult({ sceneId: 's1' })
      const s2 = makeSceneResult({ sceneId: 's2' })
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ scenes: [s1, s2] }) }))
      const { focusedScene } = useReportResultView(() => report.value, () => 's2')
      expect(focusedScene.value).toEqual(s2)
    })

    it('returns null when no match', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ scenes: [makeSceneResult({ sceneId: 's1' })] }) }))
      const { focusedScene } = useReportResultView(() => report.value, () => 'nonexistent')
      expect(focusedScene.value).toBeNull()
    })

    it('returns null when not focused', () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult() }))
      const { focusedScene } = useReportResultView(() => report.value, () => 's1')
      expect(focusedScene.value).toBeNull()
    })
  })

  describe('scenes', () => {
    it('returns [focusedScene] when focused', () => {
      const s1 = makeSceneResult({ sceneId: 's1' })
      const s2 = makeSceneResult({ sceneId: 's2' })
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ scenes: [s1, s2] }) }))
      const { scenes } = useReportResultView(() => report.value, () => 's1')
      expect(scenes.value).toEqual([s1])
    })

    it('returns empty array when focused but no match', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ scenes: [makeSceneResult({ sceneId: 's1' })] }) }))
      const { scenes } = useReportResultView(() => report.value, () => 'nonexistent')
      expect(scenes.value).toEqual([])
    })

    it('returns all suite scenes when not focused', () => {
      const s1 = makeSceneResult({ sceneId: 's1' })
      const s2 = makeSceneResult({ sceneId: 's2' })
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ scenes: [s1, s2] }) }))
      const { scenes } = useReportResultView(() => report.value, () => null)
      expect(scenes.value).toEqual([s1, s2])
    })

    it('returns [sceneResult] for scene report', () => {
      const scene = makeSceneResult()
      const report = ref(makeReport({ reportType: 'scene', result: scene }))
      const { scenes } = useReportResultView(() => report.value, () => null)
      expect(scenes.value).toEqual([scene])
    })

    it('returns empty array for scene report with null result', () => {
      const report = ref(makeReport({ reportType: 'scene', result: null }))
      const { scenes } = useReportResultView(() => report.value, () => null)
      expect(scenes.value).toEqual([])
    })

    it('returns empty array for suite with null scenes', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ scenes: null as unknown as ApiReportSceneResult[] }) }))
      const { scenes } = useReportResultView(() => report.value, () => null)
      expect(scenes.value).toEqual([])
    })
  })

  describe('sceneKey', () => {
    it('returns sceneId when present', () => {
      const report = ref(makeReport())
      const { sceneKey } = useReportResultView(() => report.value, () => null)
      expect(sceneKey(makeSceneResult({ sceneId: 'sid-1' }), 0)).toBe('sid-1')
    })

    it('returns fallback when sceneId is null', () => {
      const report = ref(makeReport())
      const { sceneKey } = useReportResultView(() => report.value, () => null)
      expect(sceneKey(makeSceneResult({ sceneId: null }), 3)).toBe('scene-3')
    })

    it('returns fallback when sceneId is undefined', () => {
      const report = ref(makeReport())
      const { sceneKey } = useReportResultView(() => report.value, () => null)
      expect(sceneKey(makeSceneResult({ sceneId: undefined }), 5)).toBe('scene-5')
    })
  })

  describe('isSceneOpen / toggleScene', () => {
    it('isSceneOpen returns true for expanded scene', () => {
      const s1 = makeSceneResult({ sceneId: 's1' })
      const report = ref(makeReport({ reportType: 'scene', result: s1 }))
      const { isSceneOpen } = useReportResultView(() => report.value, () => null)
      expect(isSceneOpen(s1, 0)).toBe(true)
    })

    it('toggleScene removes scene from expanded set', () => {
      const s1 = makeSceneResult({ sceneId: 's1' })
      const report = ref(makeReport({ reportType: 'scene', result: s1 }))
      const { isSceneOpen, toggleScene } = useReportResultView(() => report.value, () => null)
      expect(isSceneOpen(s1, 0)).toBe(true)
      toggleScene(s1, 0)
      expect(isSceneOpen(s1, 0)).toBe(false)
    })

    it('toggleScene adds scene to expanded set', () => {
      const s1 = makeSceneResult({ sceneId: 's1' })
      const s2 = makeSceneResult({ sceneId: 's2' })
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ scenes: [s1, s2] }) }))
      const { isSceneOpen, toggleScene } = useReportResultView(() => report.value, () => null)
      expect(isSceneOpen(s2, 1)).toBe(false)
      toggleScene(s2, 1)
      expect(isSceneOpen(s2, 1)).toBe(true)
    })

    it('toggleScene creates a new Set instance', () => {
      const s1 = makeSceneResult({ sceneId: 's1' })
      const report = ref(makeReport({ reportType: 'scene', result: s1 }))
      const { isSceneOpen, toggleScene } = useReportResultView(() => report.value, () => null)
      const before = isSceneOpen(s1, 0)
      expect(before).toBe(true)
      toggleScene(s1, 0)
      expect(isSceneOpen(s1, 0)).toBe(false)
    })
  })

  describe('watch resets sceneExpanded', async () => {
    it('resets to default when report changes', async () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult({ sceneId: 's1' }) }))
      const { isSceneOpen, toggleScene } = useReportResultView(() => report.value, () => null)
      expect(isSceneOpen(makeSceneResult({ sceneId: 's1' }), 0)).toBe(true)
      toggleScene(makeSceneResult({ sceneId: 's1' }), 0)
      expect(isSceneOpen(makeSceneResult({ sceneId: 's1' }), 0)).toBe(false)

      report.value = makeReport({ reportType: 'scene', result: makeSceneResult({ sceneId: 's1' }) })
      await nextTick()
      await nextTick()
      expect(isSceneOpen(makeSceneResult({ sceneId: 's1' }), 0)).toBe(true)
    })

    it('resets to default when focusSceneId changes', async () => {
      const focusId = ref<string | null>(null)
      const report = ref(makeReport({ result: makeSuiteResult({ scenes: [makeSceneResult({ sceneId: 's1' })] }) }))
      const { isSceneOpen } = useReportResultView(() => report.value, () => focusId.value)

      expect(isSceneOpen(makeSceneResult({ sceneId: 's1' }), 0)).toBe(false)

      focusId.value = 's1'
      await nextTick()
      await nextTick()
      expect(isSceneOpen(makeSceneResult({ sceneId: 's1' }), 0)).toBe(true)
    })
  })

  describe('heroStatus', () => {
    it('returns ok for success', () => {
      const report = ref(makeReport({ status: 'success' }))
      const { heroStatus } = useReportResultView(() => report.value, () => null)
      expect(heroStatus.value).toEqual({ ok: true, label: '执行成功' })
    })

    it('returns ok for passed', () => {
      const report = ref(makeReport({ status: 'passed' }))
      const { heroStatus } = useReportResultView(() => report.value, () => null)
      expect(heroStatus.value).toEqual({ ok: true, label: '执行成功' })
    })

    it('returns failed for failed', () => {
      const report = ref(makeReport({ status: 'failed' }))
      const { heroStatus } = useReportResultView(() => report.value, () => null)
      expect(heroStatus.value).toEqual({ ok: false, label: '执行失败' })
    })

    it('returns partial for partial', () => {
      const report = ref(makeReport({ status: 'partial' }))
      const { heroStatus } = useReportResultView(() => report.value, () => null)
      expect(heroStatus.value).toEqual({ ok: false, label: '部分通过' })
    })

    it('falls back to status string for unknown status', () => {
      const report = ref(makeReport({ status: 'running' }))
      const { heroStatus } = useReportResultView(() => report.value, () => null)
      expect(heroStatus.value).toEqual({ ok: true, label: 'running' })
    })

    it('falls back to 执行成功 for empty status', () => {
      const report = ref(makeReport({ status: '' }))
      const { heroStatus } = useReportResultView(() => report.value, () => null)
      expect(heroStatus.value).toEqual({ ok: true, label: '执行成功' })
    })
  })

  describe('sourceLabel', () => {
    it('returns 定时任务 when source is schedule', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ source: 'schedule' }) }))
      const { sourceLabel } = useReportResultView(() => report.value, () => null)
      expect(sourceLabel.value).toBe('定时任务')
    })

    it('returns 平台内执行 for other sources', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ source: 'platform' }) }))
      const { sourceLabel } = useReportResultView(() => report.value, () => null)
      expect(sourceLabel.value).toBe('平台内执行')
    })

    it('returns 平台内执行 for null source', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ source: null as unknown as string }) }))
      const { sourceLabel } = useReportResultView(() => report.value, () => null)
      expect(sourceLabel.value).toBe('平台内执行')
    })
  })

  describe('heroEnv', () => {
    it('returns report environmentName when present', () => {
      const report = ref(makeReport({ environmentName: 'env-r' }))
      const { heroEnv } = useReportResultView(() => report.value, () => null)
      expect(heroEnv.value).toBe('env-r')
    })

    it('falls back to suiteResult environmentName', () => {
      const report = ref(makeReport({ environmentName: null, result: makeSuiteResult({ environmentName: 'env-s' }) }))
      const { heroEnv } = useReportResultView(() => report.value, () => null)
      expect(heroEnv.value).toBe('env-s')
    })

    it('falls back to sceneResult environmentName', () => {
      const report = ref(makeReport({ reportType: 'scene', environmentName: null, result: makeSceneResult({ environmentName: 'env-sc' }) }))
      const { heroEnv } = useReportResultView(() => report.value, () => null)
      expect(heroEnv.value).toBe('env-sc')
    })

    it('returns - when all are null', () => {
      const report = ref(makeReport({ environmentName: null, result: makeSuiteResult({ environmentName: null }) }))
      const { heroEnv } = useReportResultView(() => report.value, () => null)
      expect(heroEnv.value).toBe('-')
    })
  })

  describe('heroTime', () => {
    it('returns triggeredAt for suite', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ triggeredAt: '2025-06-01T10:00:00' }) }))
      const { heroTime } = useReportResultView(() => report.value, () => null)
      expect(heroTime.value).toBe('2025-06-01T10:00:00')
    })

    it('falls back to createdAt when triggeredAt is null', () => {
      const report = ref(makeReport({ createdAt: '2025-05-01T08:00:00', result: makeSuiteResult({ triggeredAt: null as unknown as string }) }))
      const { heroTime } = useReportResultView(() => report.value, () => null)
      expect(heroTime.value).toBe('2025-05-01T08:00:00')
    })

    it('returns executedAt for scene', () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult({ executedAt: '2025-06-01T11:00:00' }) }))
      const { heroTime } = useReportResultView(() => report.value, () => null)
      expect(heroTime.value).toBe('2025-06-01T11:00:00')
    })

    it('falls back to createdAt when executedAt is null', () => {
      const report = ref(makeReport({ reportType: 'scene', createdAt: '2025-05-01T07:00:00', result: makeSceneResult({ executedAt: null as unknown as string }) }))
      const { heroTime } = useReportResultView(() => report.value, () => null)
      expect(heroTime.value).toBe('2025-05-01T07:00:00')
    })
  })

  describe('heroTimeLabel', () => {
    it('returns 触发时间 for suite', () => {
      const report = ref(makeReport({ reportType: 'suite' }))
      const { heroTimeLabel } = useReportResultView(() => report.value, () => null)
      expect(heroTimeLabel.value).toBe('触发时间')
    })

    it('returns 执行时间 for scene', () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult() }))
      const { heroTimeLabel } = useReportResultView(() => report.value, () => null)
      expect(heroTimeLabel.value).toBe('执行时间')
    })
  })

  describe('heroDuration', () => {
    it('returns single scene duration when scenes length is 1', () => {
      const scene = makeSceneResult({ summary: { total: 1, passed: 1, failed: 0, durationMs: 2500 } })
      const report = ref(makeReport({ reportType: 'scene', result: scene }))
      const { heroDuration } = useReportResultView(() => report.value, () => null)
      expect(heroDuration.value).toBe(2500)
    })

    it('returns suite summary duration when multiple scenes', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ summary: { totalScenes: 2, passedScenes: 2, failedScenes: 0, durationMs: 8000 } }) }))
      const { heroDuration } = useReportResultView(() => report.value, () => null)
      expect(heroDuration.value).toBe(8000)
    })

    it('falls back to report summary durationMs', () => {
      const report = ref(makeReport({ reportType: 'scene', summary: { durationMs: 4000 }, result: makeSceneResult({ summary: undefined }) }))
      const { heroDuration } = useReportResultView(() => report.value, () => null)
      expect(heroDuration.value).toBe(4000)
    })

    it('returns null when no duration available', () => {
      const report = ref(makeReport({ reportType: 'scene', summary: null, result: makeSceneResult({ summary: undefined }) }))
      const { heroDuration } = useReportResultView(() => report.value, () => null)
      expect(heroDuration.value).toBeNull()
    })
  })

  describe('heroId', () => {
    it('returns Task ID for suite', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ taskId: 'task-42' }) }))
      const { heroId } = useReportResultView(() => report.value, () => null)
      expect(heroId.value).toEqual({ label: 'Task ID', value: 'task-42' })
    })

    it('returns null value when taskId is null', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ taskId: null }) }))
      const { heroId } = useReportResultView(() => report.value, () => null)
      expect(heroId.value).toEqual({ label: 'Task ID', value: null })
    })

    it('returns 场景 ID for scene', () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult({ sceneId: 'sc-99' }) }))
      const { heroId } = useReportResultView(() => report.value, () => null)
      expect(heroId.value).toEqual({ label: '场景 ID', value: 'sc-99' })
    })
  })

  describe('formatDuration', () => {
    it('returns - for null', () => {
      const report = ref(makeReport())
      const { formatDuration } = useReportResultView(() => report.value, () => null)
      expect(formatDuration(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const report = ref(makeReport())
      const { formatDuration } = useReportResultView(() => report.value, () => null)
      expect(formatDuration(undefined)).toBe('-')
    })

    it('formats milliseconds', () => {
      const report = ref(makeReport())
      const { formatDuration } = useReportResultView(() => report.value, () => null)
      expect(formatDuration(500)).toBe('500 ms')
    })

    it('formats seconds with two decimals', () => {
      const report = ref(makeReport())
      const { formatDuration } = useReportResultView(() => report.value, () => null)
      expect(formatDuration(1500)).toBe('1.50 s')
    })

    it('formats exact 1000ms', () => {
      const report = ref(makeReport())
      const { formatDuration } = useReportResultView(() => report.value, () => null)
      expect(formatDuration(1000)).toBe('1.00 s')
    })

    it('formats zero ms', () => {
      const report = ref(makeReport())
      const { formatDuration } = useReportResultView(() => report.value, () => null)
      expect(formatDuration(0)).toBe('0 ms')
    })
  })

  describe('stats', () => {
    it('returns suite-level stats when isSuite and not focused', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ summary: { totalScenes: 10, passedScenes: 7, failedScenes: 3, durationMs: 20000 } }) }))
      const { stats } = useReportResultView(() => report.value, () => null)
      expect(stats.value).toEqual([
        { label: '场景总数', value: '10', tone: 'b' },
        { label: '通过场景', value: '7', tone: 'g' },
        { label: '失败场景', value: '3', tone: 'r' },
        { label: '通过率', value: '70.0%', tone: 'g' },
        { label: '总耗时', value: '20.00 s', tone: 'b' },
      ])
    })

    it('returns scene-level stats for scene report', () => {
      const scene = makeSceneResult({ summary: { total: 8, passed: 6, failed: 2, durationMs: 4500 } })
      const report = ref(makeReport({ reportType: 'scene', result: scene }))
      const { stats } = useReportResultView(() => report.value, () => null)
      expect(stats.value).toEqual([
        { label: '总步骤', value: '8', tone: 'b' },
        { label: '通过', value: '6', tone: 'g' },
        { label: '失败', value: '2', tone: 'r' },
        { label: '通过率', value: '75.0%', tone: 'g' },
        { label: '总耗时', value: '4.50 s', tone: 'b' },
      ])
    })

    it('returns scene-level stats when focused', () => {
      const s1 = makeSceneResult({ sceneId: 's1', summary: { total: 3, passed: 1, failed: 2, durationMs: 1000 } })
      const s2 = makeSceneResult({ sceneId: 's2' })
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ scenes: [s1, s2] }) }))
      const { stats } = useReportResultView(() => report.value, () => 's1')
      expect(stats.value[0]).toEqual({ label: '总步骤', value: '3', tone: 'b' })
    })

    it('returns default stats for suite with no summary', () => {
      const report = ref(makeReport({ reportType: 'suite', result: makeSuiteResult({ summary: undefined }) }))
      const { stats } = useReportResultView(() => report.value, () => null)
      expect(stats.value[0]).toEqual({ label: '场景总数', value: '0', tone: 'b' })
    })

    it('returns default stats for scene with no summary', () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult({ summary: undefined }) }))
      const { stats } = useReportResultView(() => report.value, () => null)
      expect(stats.value[0]).toEqual({ label: '总步骤', value: '0', tone: 'b' })
    })
  })

  describe('envPresent', () => {
    it('returns true when suite has preprocessors', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ preprocessors: [makeStep()], postprocessors: [] }) }))
      const { envPresent } = useReportResultView(() => report.value, () => null)
      expect(envPresent.value).toBe(true)
    })

    it('returns true when suite has postprocessors', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ preprocessors: [], postprocessors: [makeStep()] }) }))
      const { envPresent } = useReportResultView(() => report.value, () => null)
      expect(envPresent.value).toBe(true)
    })

    it('returns false when suite has neither', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ preprocessors: [], postprocessors: [] }) }))
      const { envPresent } = useReportResultView(() => report.value, () => null)
      expect(envPresent.value).toBe(false)
    })

    it('returns false when focused', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ preprocessors: [makeStep()], postprocessors: [makeStep()], scenes: [makeSceneResult({ sceneId: 's1' })] }) }))
      const { envPresent } = useReportResultView(() => report.value, () => 's1')
      expect(envPresent.value).toBe(false)
    })

    it('returns false when not suite', () => {
      const report = ref(makeReport({ reportType: 'scene', result: makeSceneResult() }))
      const { envPresent } = useReportResultView(() => report.value, () => null)
      expect(envPresent.value).toBe(false)
    })

    it('returns false when preprocessors/postprocessors are null', () => {
      const report = ref(makeReport({ result: makeSuiteResult({ preprocessors: null as unknown as [], postprocessors: null as unknown as [] }) }))
      const { envPresent } = useReportResultView(() => report.value, () => null)
      expect(envPresent.value).toBe(false)
    })
  })

  describe('sceneMiniStat', () => {
    it('returns total count', () => {
      const report = ref(makeReport())
      const { sceneMiniStat } = useReportResultView(() => report.value, () => null)
      const scene = makeSceneResult({ summary: { total: 10, passed: 8, failed: 2, durationMs: 5000 } })
      expect(sceneMiniStat(scene, 'total')).toBe(10)
    })

    it('returns passed count', () => {
      const report = ref(makeReport())
      const { sceneMiniStat } = useReportResultView(() => report.value, () => null)
      const scene = makeSceneResult({ summary: { total: 10, passed: 8, failed: 2, durationMs: 5000 } })
      expect(sceneMiniStat(scene, 'passed')).toBe(8)
    })

    it('returns failed count', () => {
      const report = ref(makeReport())
      const { sceneMiniStat } = useReportResultView(() => report.value, () => null)
      const scene = makeSceneResult({ summary: { total: 10, passed: 8, failed: 2, durationMs: 5000 } })
      expect(sceneMiniStat(scene, 'failed')).toBe(2)
    })

    it('returns formatted duration', () => {
      const report = ref(makeReport())
      const { sceneMiniStat } = useReportResultView(() => report.value, () => null)
      const scene = makeSceneResult({ summary: { total: 10, passed: 8, failed: 2, durationMs: 5000 } })
      expect(sceneMiniStat(scene, 'durationMs')).toBe('5.00 s')
    })

    it('returns 0 when summary is null', () => {
      const report = ref(makeReport())
      const { sceneMiniStat } = useReportResultView(() => report.value, () => null)
      const scene = makeSceneResult({ summary: undefined })
      expect(sceneMiniStat(scene, 'total')).toBe(0)
    })

    it('returns - for durationMs when summary is null', () => {
      const report = ref(makeReport())
      const { sceneMiniStat } = useReportResultView(() => report.value, () => null)
      const scene = makeSceneResult({ summary: undefined })
      expect(sceneMiniStat(scene, 'durationMs')).toBe('-')
    })

    it('returns 0 when value is null in summary', () => {
      const report = ref(makeReport())
      const { sceneMiniStat } = useReportResultView(() => report.value, () => null)
      const scene = makeSceneResult({ summary: { total: 5, passed: null as unknown as number, failed: null as unknown as number, durationMs: 100 } })
      expect(sceneMiniStat(scene, 'passed')).toBe(0)
    })
  })

  describe('statusLabel', () => {
    it('maps success to 通过', () => {
      const report = ref(makeReport())
      const { statusLabel } = useReportResultView(() => report.value, () => null)
      expect(statusLabel('success')).toBe('通过')
    })

    it('maps passed to 通过', () => {
      const report = ref(makeReport())
      const { statusLabel } = useReportResultView(() => report.value, () => null)
      expect(statusLabel('passed')).toBe('通过')
    })

    it('maps failed to 失败', () => {
      const report = ref(makeReport())
      const { statusLabel } = useReportResultView(() => report.value, () => null)
      expect(statusLabel('failed')).toBe('失败')
    })

    it('maps partial to 部分通过', () => {
      const report = ref(makeReport())
      const { statusLabel } = useReportResultView(() => report.value, () => null)
      expect(statusLabel('partial')).toBe('部分通过')
    })

    it('maps skipped to 跳过', () => {
      const report = ref(makeReport())
      const { statusLabel } = useReportResultView(() => report.value, () => null)
      expect(statusLabel('skipped')).toBe('跳过')
    })

    it('returns raw status for unknown', () => {
      const report = ref(makeReport())
      const { statusLabel } = useReportResultView(() => report.value, () => null)
      expect(statusLabel('unknown')).toBe('unknown')
    })
  })

  describe('sceneStatusClass', () => {
    it('returns rr-badge--ok for success', () => {
      const report = ref(makeReport())
      const { sceneStatusClass } = useReportResultView(() => report.value, () => null)
      expect(sceneStatusClass('success')).toBe('rr-badge--ok')
    })

    it('returns rr-badge--ok for passed', () => {
      const report = ref(makeReport())
      const { sceneStatusClass } = useReportResultView(() => report.value, () => null)
      expect(sceneStatusClass('passed')).toBe('rr-badge--ok')
    })

    it('returns rr-badge--fail for failed', () => {
      const report = ref(makeReport())
      const { sceneStatusClass } = useReportResultView(() => report.value, () => null)
      expect(sceneStatusClass('failed')).toBe('rr-badge--fail')
    })

    it('returns rr-badge--skip for other statuses', () => {
      const report = ref(makeReport())
      const { sceneStatusClass } = useReportResultView(() => report.value, () => null)
      expect(sceneStatusClass('partial')).toBe('rr-badge--skip')
      expect(sceneStatusClass('skipped')).toBe('rr-badge--skip')
      expect(sceneStatusClass('unknown')).toBe('rr-badge--skip')
    })
  })
})
