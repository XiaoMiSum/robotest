import { describe, expect, it } from 'vitest'
import { ref, nextTick } from 'vue'
import type { ApiReportStepResult } from '@/types'
import { useReportProcessors } from './useReportProcessors'

function step(overrides?: Partial<ApiReportStepResult>): ApiReportStepResult {
  return { stepId: null, name: null, status: 'success', ...overrides }
}

describe('useReportProcessors', () => {
  describe('activeTab', () => {
    it('defaults to pre', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.activeTab.value).toBe('pre')
    })

    it('switches to post when pre is empty and post has items', async () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([step()])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      await nextTick()
      expect(s.activeTab.value).toBe('post')
    })

    it('switches to pre when post becomes empty and pre has items', async () => {
      const pre = ref<ApiReportStepResult[]>([step()])
      const post = ref<ApiReportStepResult[]>([step()])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      s.activeTab.value = 'post'
      expect(s.activeTab.value).toBe('post')
      post.value = []
      await nextTick()
      expect(s.activeTab.value).toBe('pre')
    })

    it('does not switch when both are empty', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.activeTab.value).toBe('pre')
    })

    it('does not switch when both have items', () => {
      const pre = ref<ApiReportStepResult[]>([step()])
      const post = ref<ApiReportStepResult[]>([step()])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.activeTab.value).toBe('pre')
    })

    it('manual override is respected', () => {
      const pre = ref<ApiReportStepResult[]>([step()])
      const post = ref<ApiReportStepResult[]>([step()])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      s.activeTab.value = 'post'
      expect(s.activeTab.value).toBe('post')
    })
  })

  describe('paneGroups', () => {
    it('returns empty array when both are empty', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneGroups.value).toEqual([])
    })

    it('includes only pre when post is empty', () => {
      const pre = ref<ApiReportStepResult[]>([step()])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneGroups.value).toEqual([{ tab: 'pre', list: pre.value }])
    })

    it('includes only post when pre is empty', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([step()])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneGroups.value).toEqual([{ tab: 'post', list: post.value }])
    })

    it('includes both when both have items', () => {
      const s1 = step({ stepId: 'a' })
      const s2 = step({ stepId: 'b' })
      const pre = ref<ApiReportStepResult[]>([s1])
      const post = ref<ApiReportStepResult[]>([s2])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneGroups.value).toEqual([
        { tab: 'pre', list: [s1] },
        { tab: 'post', list: [s2] },
      ])
    })
  })

  describe('collapsedKeys', () => {
    it('initializes with all keys from pre and post', () => {
      const s1 = step({ stepId: 'a' })
      const s2 = step({ stepId: 'b' })
      const pre = ref<ApiReportStepResult[]>([s1])
      const post = ref<ApiReportStepResult[]>([s2])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.collapsedKeys.value.has('a')).toBe(true)
      expect(s.collapsedKeys.value.has('b')).toBe(true)
    })

    it('recomputes when data changes', async () => {
      const pre = ref<ApiReportStepResult[]>([step({ stepId: 'a' })])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.collapsedKeys.value.has('a')).toBe(true)
      pre.value = [step({ stepId: 'c' })]
      await nextTick()
      expect(s.collapsedKeys.value.has('a')).toBe(false)
      expect(s.collapsedKeys.value.has('c')).toBe(true)
    })
  })

  describe('procKey', () => {
    it('returns stepId when present', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.procKey({ stepId: 'x', name: null, status: 'success' }, 'pre', 0)).toBe('x')
    })

    it('generates fallback key when stepId is null', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.procKey({ stepId: null, name: null, status: 'success' }, 'pre', 2)).toBe('task-pre-2')
    })

    it('generates fallback key for scene level', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'scene', () => pre.value, () => post.value)
      expect(s.procKey({ stepId: null, name: null, status: 'success' }, 'post', 1)).toBe('scene-post-1')
    })
  })

  describe('isCollapsed / toggleCollapse', () => {
    it('key is collapsed initially', () => {
      const pre = ref<ApiReportStepResult[]>([step({ stepId: 'k1' })])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.isCollapsed('k1')).toBe(true)
    })

    it('toggleCollapse expands a collapsed key', () => {
      const pre = ref<ApiReportStepResult[]>([step({ stepId: 'k1' })])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      s.toggleCollapse('k1')
      expect(s.isCollapsed('k1')).toBe(false)
    })

    it('toggleCollapse re-collapses an expanded key', () => {
      const pre = ref<ApiReportStepResult[]>([step({ stepId: 'k1' })])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      s.toggleCollapse('k1')
      s.toggleCollapse('k1')
      expect(s.isCollapsed('k1')).toBe(true)
    })

    it('unknown key is not collapsed', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.isCollapsed('nonexistent')).toBe(false)
    })

    it('toggle does not affect other keys', () => {
      const pre = ref<ApiReportStepResult[]>([step({ stepId: 'a' }), step({ stepId: 'b' })])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      s.toggleCollapse('a')
      expect(s.isCollapsed('a')).toBe(false)
      expect(s.isCollapsed('b')).toBe(true)
    })
  })

  describe('chain', () => {
    it('returns 5 items for task level', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.chain.value).toEqual([
        { label: '任务前置', key: 'task-pre' },
        { label: '场景前置', key: 'scene-pre' },
        { label: '步骤', key: 'steps' },
        { label: '场景后置', key: 'scene-post' },
        { label: '任务后置', key: 'task-post' },
      ])
    })

    it('returns 3 items for scene level', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'scene', () => pre.value, () => post.value)
      expect(s.chain.value).toEqual([
        { label: '场景前置', key: 'scene-pre' },
        { label: '步骤', key: 'steps' },
        { label: '场景后置', key: 'scene-post' },
      ])
    })
  })

  describe('chainHighlightKey', () => {
    it('task level + pre tab => task-pre', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      s.activeTab.value = 'pre'
      expect(s.chainHighlightKey.value).toBe('task-pre')
    })

    it('task level + post tab => task-post', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      s.activeTab.value = 'post'
      expect(s.chainHighlightKey.value).toBe('task-post')
    })

    it('scene level + pre tab => scene-pre', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'scene', () => pre.value, () => post.value)
      s.activeTab.value = 'pre'
      expect(s.chainHighlightKey.value).toBe('scene-pre')
    })

    it('scene level + post tab => scene-post', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'scene', () => pre.value, () => post.value)
      s.activeTab.value = 'post'
      expect(s.chainHighlightKey.value).toBe('scene-post')
    })
  })

  describe('statusLabel', () => {
    it('maps success', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.statusLabel('success')).toBe('成功')
    })

    it('maps passed', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.statusLabel('passed')).toBe('成功')
    })

    it('maps failed', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.statusLabel('failed')).toBe('失败')
    })

    it('maps skipped', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.statusLabel('skipped')).toBe('跳过')
    })

    it('maps error', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.statusLabel('error')).toBe('错误')
    })

    it('maps not_executed', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.statusLabel('not_executed')).toBe('未执行')
    })

    it('returns raw value for unknown status', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.statusLabel('custom')).toBe('custom')
    })

    it('returns - for null', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.statusLabel(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.statusLabel(undefined)).toBe('-')
    })
  })

  describe('assertionLabel', () => {
    it('maps passed to 通过', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.assertionLabel('passed')).toBe('通过')
    })

    it('maps success to 通过', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.assertionLabel('success')).toBe('通过')
    })

    it('maps failed to 失败', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.assertionLabel('failed')).toBe('失败')
    })

    it('maps skipped to 跳过', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.assertionLabel('skipped')).toBe('跳过')
    })

    it('returns raw value for unknown status', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.assertionLabel('other')).toBe('other')
    })

    it('returns - for null', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.assertionLabel(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.assertionLabel(undefined)).toBe('-')
    })
  })

  describe('badgeClass', () => {
    it('returns ok for success', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.badgeClass('success')).toBe('rr-badge--ok')
    })

    it('returns ok for passed', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.badgeClass('passed')).toBe('rr-badge--ok')
    })

    it('returns fail for failed', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.badgeClass('failed')).toBe('rr-badge--fail')
    })

    it('returns skip for other', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.badgeClass('skipped')).toBe('rr-badge--skip')
    })

    it('returns skip for null', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.badgeClass(null)).toBe('rr-badge--skip')
    })

    it('returns skip for undefined', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.badgeClass(undefined)).toBe('rr-badge--skip')
    })
  })

  describe('methodChipClass', () => {
    it('returns get for GET', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.methodChipClass('GET')).toBe('rr-chip--get')
    })

    it('returns get for lowercase get', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.methodChipClass('get')).toBe('rr-chip--get')
    })

    it('returns post for POST', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.methodChipClass('POST')).toBe('rr-chip--post')
    })

    it('returns put for PUT', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.methodChipClass('PUT')).toBe('rr-chip--put')
    })

    it('returns delete for DELETE', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.methodChipClass('DELETE')).toBe('rr-chip--delete')
    })

    it('returns empty for unknown method', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.methodChipClass('PATCH')).toBe('')
    })

    it('returns empty for null', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.methodChipClass(null)).toBe('')
    })

    it('returns empty for undefined', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.methodChipClass(undefined)).toBe('')
    })
  })

  describe('paneTone', () => {
    it('returns empty for empty list', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneTone([])).toBe('empty')
    })

    it('returns fail when any step is failed', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneTone([step({ status: 'success' }), step({ status: 'failed' })])).toBe('fail')
    })

    it('returns fail when any step is error', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneTone([step({ status: 'error' })])).toBe('fail')
    })

    it('returns ok when all steps are success', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneTone([step({ status: 'success' }), step({ status: 'success' })])).toBe('ok')
    })

    it('returns ok when all steps are passed', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneTone([step({ status: 'passed' })])).toBe('ok')
    })

    it('returns skip when mix of success and skipped', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneTone([step({ status: 'success' }), step({ status: 'skipped' })])).toBe('skip')
    })

    it('returns skip for only skipped steps', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.paneTone([step({ status: 'skipped' })])).toBe('skip')
    })
  })

  describe('pretty', () => {
    it('returns - for null', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.pretty(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.pretty(undefined)).toBe('-')
    })

    it('returns plain string when not valid JSON', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.pretty('hello')).toBe('hello')
    })

    it('pretty-prints valid JSON string', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.pretty('{"a":1}')).toBe('{\n  "a": 1\n}')
    })

    it('pretty-prints object value', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.pretty({ a: 1 })).toBe('{\n  "a": 1\n}')
    })

    it('pretty-prints array value', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.pretty([1, 2])).toBe('[\n  1,\n  2\n]')
    })
  })

  describe('displayValue', () => {
    it('returns - for null', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.displayValue(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.displayValue(undefined)).toBe('-')
    })

    it('returns string as-is', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.displayValue('text')).toBe('text')
    })

    it('stringifies object value', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.displayValue({ a: 1 })).toBe('{"a":1}')
    })

    it('stringifies number value', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.displayValue(42)).toBe('42')
    })
  })

  describe('procUrl', () => {
    it('returns empty when request is null', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.procUrl({ stepId: null, name: null, status: 'success', request: null })).toBe('')
    })

    it('returns empty when request is undefined', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.procUrl({ stepId: null, name: null, status: 'success' })).toBe('')
    })

    it('returns empty when url is null', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.procUrl({ stepId: null, name: null, status: 'success', request: { method: 'GET', url: null } })).toBe('')
    })

    it('returns url as string', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.procUrl({ stepId: null, name: null, status: 'success', request: { method: 'GET', url: 'https://example.com' } })).toBe('https://example.com')
    })

    it('stringifies non-string url', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.procUrl({ stepId: null, name: null, status: 'success', request: { method: 'GET', url: 123 } })).toBe('123')
    })
  })

  describe('headersEntries', () => {
    it('returns empty array for null headers', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.headersEntries(null)).toEqual([])
    })

    it('returns empty array for undefined headers', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.headersEntries(undefined)).toEqual([])
    })

    it('returns entries for valid headers', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.headersEntries({ 'Content-Type': 'application/json', Authorization: 'Bearer tok' })).toEqual([
        ['Content-Type', 'application/json'],
        ['Authorization', 'Bearer tok'],
      ])
    })

    it('returns empty array for empty headers', () => {
      const pre = ref<ApiReportStepResult[]>([])
      const post = ref<ApiReportStepResult[]>([])
      const s = useReportProcessors(() => 'task', () => pre.value, () => post.value)
      expect(s.headersEntries({})).toEqual([])
    })
  })
})
