import { describe, expect, it } from 'vitest'
import type { ApiReportStepResult } from '@/types'
import { nextTick, ref } from 'vue'
import { useReportStepCard } from './useReportStepCard'

function makeStep(overrides?: Partial<ApiReportStepResult>): ApiReportStepResult {
  return {
    stepId: 'step-1',
    name: '步骤1',
    status: 'success',
    ...overrides,
  }
}

function makeEvent(open: boolean): Event {
  return { target: { open } } as unknown as Event
}

describe('useReportStepCard', () => {
  describe('stepKey', () => {
    it('returns stepId when present', () => {
      const steps = () => [makeStep({ stepId: 'sid-1' })]
      const { stepKey } = useReportStepCard(steps)
      expect(stepKey(makeStep({ stepId: 'sid-1' }), 0)).toBe('sid-1')
    })

    it('returns fallback when stepId is null', () => {
      const steps = () => [makeStep({ stepId: null })]
      const { stepKey } = useReportStepCard(steps)
      expect(stepKey(makeStep({ stepId: null }), 3)).toBe('step-3')
    })
  })

  describe('openKeys initial state', () => {
    it('includes failed and error steps', () => {
      const steps = () => [
        makeStep({ stepId: 's1', status: 'success' }),
        makeStep({ stepId: 's2', status: 'failed' }),
        makeStep({ stepId: 's3', status: 'error' }),
        makeStep({ stepId: 's4', status: 'passed' }),
      ]
      const { openKeys } = useReportStepCard(steps)
      expect(openKeys.value.has('s2')).toBe(true)
      expect(openKeys.value.has('s3')).toBe(true)
      expect(openKeys.value.has('s1')).toBe(false)
    })

    it('empty steps produces empty set', () => {
      const steps = () => []
      const { openKeys } = useReportStepCard(steps)
      expect(openKeys.value.size).toBe(0)
    })
  })

  describe('watch on steps().length', () => {
    it('resets openKeys when steps length changes', async () => {
      const stepsData = ref<ApiReportStepResult[]>([makeStep({ stepId: 's1', status: 'success' })])
      const steps = () => stepsData.value
      const { openKeys } = useReportStepCard(steps)
      expect(openKeys.value.size).toBe(0)
      stepsData.value = [
        makeStep({ stepId: 's1', status: 'failed' }),
        makeStep({ stepId: 's2', status: 'success' }),
      ]
      await nextTick()
      await nextTick()
      expect(openKeys.value.has('s1')).toBe(true)
      expect(openKeys.value.size).toBe(1)
    })
  })

  describe('isOpen', () => {
    it('returns true for keys in openKeys', () => {
      const steps = () => [makeStep({ stepId: 's1', status: 'failed' })]
      const { isOpen } = useReportStepCard(steps)
      expect(isOpen('s1')).toBe(true)
      expect(isOpen('s2')).toBe(false)
    })
  })

  describe('onToggle', () => {
    it('adds key when opening', () => {
      const steps = () => [makeStep({ stepId: 's1', status: 'success' })]
      const { openKeys, onToggle } = useReportStepCard(steps)
      expect(openKeys.value.has('s1')).toBe(false)
      onToggle('s1', makeEvent(true))
      expect(openKeys.value.has('s1')).toBe(true)
    })

    it('removes key when closing', () => {
      const steps = () => [makeStep({ stepId: 's1', status: 'failed' })]
      const { openKeys, onToggle } = useReportStepCard(steps)
      expect(openKeys.value.has('s1')).toBe(true)
      onToggle('s1', makeEvent(false))
      expect(openKeys.value.has('s1')).toBe(false)
    })

    it('does not mutate original set', () => {
      const steps = () => [makeStep({ stepId: 's1', status: 'success' })]
      const { openKeys, onToggle } = useReportStepCard(steps)
      const original = openKeys.value
      onToggle('s1', makeEvent(true))
      expect(original.has('s1')).toBe(false)
      expect(openKeys.value.has('s1')).toBe(true)
    })
  })

  describe('allOpen and toggleAll', () => {
    it('allOpen is false initially', () => {
      const steps = () => [makeStep()]
      const { allOpen } = useReportStepCard(steps)
      expect(allOpen.value).toBe(false)
    })

    it('toggleAll opens all steps and sets allOpen to true', () => {
      const steps = () => [
        makeStep({ stepId: 's1' }),
        makeStep({ stepId: 's2' }),
        makeStep({ stepId: 's3' }),
      ]
      const { openKeys, allOpen, toggleAll } = useReportStepCard(steps)
      toggleAll()
      expect(allOpen.value).toBe(true)
      expect(openKeys.value.size).toBe(3)
      expect(openKeys.value.has('s1')).toBe(true)
      expect(openKeys.value.has('s2')).toBe(true)
      expect(openKeys.value.has('s3')).toBe(true)
    })

    it('toggleAll again clears all and sets allOpen to false', () => {
      const steps = () => [
        makeStep({ stepId: 's1' }),
        makeStep({ stepId: 's2' }),
      ]
      const { openKeys, allOpen, toggleAll } = useReportStepCard(steps)
      toggleAll()
      toggleAll()
      expect(allOpen.value).toBe(false)
      expect(openKeys.value.size).toBe(0)
    })

    it('toggleAll uses stepKey fallback when stepId is null', () => {
      const steps = () => [
        makeStep({ stepId: null }),
        makeStep({ stepId: 's2' }),
      ]
      const { openKeys, toggleAll } = useReportStepCard(steps)
      toggleAll()
      expect(openKeys.value.has('step-0')).toBe(true)
      expect(openKeys.value.has('s2')).toBe(true)
    })
  })

  describe('statusLabel', () => {
    it('maps success to 成功', () => {
      const { statusLabel } = useReportStepCard(() => [])
      expect(statusLabel('success')).toBe('成功')
    })

    it('maps passed to 成功', () => {
      const { statusLabel } = useReportStepCard(() => [])
      expect(statusLabel('passed')).toBe('成功')
    })

    it('maps failed to 失败', () => {
      const { statusLabel } = useReportStepCard(() => [])
      expect(statusLabel('failed')).toBe('失败')
    })

    it('maps skipped to 跳过', () => {
      const { statusLabel } = useReportStepCard(() => [])
      expect(statusLabel('skipped')).toBe('跳过')
    })

    it('maps error to 错误', () => {
      const { statusLabel } = useReportStepCard(() => [])
      expect(statusLabel('error')).toBe('错误')
    })

    it('maps not_executed to 未执行', () => {
      const { statusLabel } = useReportStepCard(() => [])
      expect(statusLabel('not_executed')).toBe('未执行')
    })

    it('returns raw status for unknown status', () => {
      const { statusLabel } = useReportStepCard(() => [])
      expect(statusLabel('unknown')).toBe('unknown')
    })

    it('returns - for null', () => {
      const { statusLabel } = useReportStepCard(() => [])
      expect(statusLabel(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const { statusLabel } = useReportStepCard(() => [])
      expect(statusLabel(undefined)).toBe('-')
    })
  })

  describe('assertionLabel', () => {
    it('maps passed to 通过', () => {
      const { assertionLabel } = useReportStepCard(() => [])
      expect(assertionLabel('passed')).toBe('通过')
    })

    it('maps success to 通过', () => {
      const { assertionLabel } = useReportStepCard(() => [])
      expect(assertionLabel('success')).toBe('通过')
    })

    it('maps failed to 失败', () => {
      const { assertionLabel } = useReportStepCard(() => [])
      expect(assertionLabel('failed')).toBe('失败')
    })

    it('maps skipped to 跳过', () => {
      const { assertionLabel } = useReportStepCard(() => [])
      expect(assertionLabel('skipped')).toBe('跳过')
    })

    it('returns raw status for unknown value', () => {
      const { assertionLabel } = useReportStepCard(() => [])
      expect(assertionLabel('unknown')).toBe('unknown')
    })

    it('returns - for null', () => {
      const { assertionLabel } = useReportStepCard(() => [])
      expect(assertionLabel(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const { assertionLabel } = useReportStepCard(() => [])
      expect(assertionLabel(undefined)).toBe('-')
    })
  })

  describe('badgeClass', () => {
    it('returns rsc-badge--ok for success', () => {
      const { badgeClass } = useReportStepCard(() => [])
      expect(badgeClass('success')).toBe('rsc-badge--ok')
    })

    it('returns rsc-badge--ok for passed', () => {
      const { badgeClass } = useReportStepCard(() => [])
      expect(badgeClass('passed')).toBe('rsc-badge--ok')
    })

    it('returns rsc-badge--fail for failed', () => {
      const { badgeClass } = useReportStepCard(() => [])
      expect(badgeClass('failed')).toBe('rsc-badge--fail')
    })

    it('returns rsc-badge--skip for error', () => {
      const { badgeClass } = useReportStepCard(() => [])
      expect(badgeClass('error')).toBe('rsc-badge--skip')
    })

    it('returns rsc-badge--skip for null', () => {
      const { badgeClass } = useReportStepCard(() => [])
      expect(badgeClass(null)).toBe('rsc-badge--skip')
    })

    it('returns rsc-badge--skip for undefined', () => {
      const { badgeClass } = useReportStepCard(() => [])
      expect(badgeClass(undefined)).toBe('rsc-badge--skip')
    })
  })

  describe('methodChipClass', () => {
    it('returns rsc-chip--get for GET', () => {
      const { methodChipClass } = useReportStepCard(() => [])
      expect(methodChipClass('GET')).toBe('rsc-chip--get')
    })

    it('normalizes lowercase get', () => {
      const { methodChipClass } = useReportStepCard(() => [])
      expect(methodChipClass('get')).toBe('rsc-chip--get')
    })

    it('returns rsc-chip--post for POST', () => {
      const { methodChipClass } = useReportStepCard(() => [])
      expect(methodChipClass('POST')).toBe('rsc-chip--post')
    })

    it('returns rsc-chip--put for PUT', () => {
      const { methodChipClass } = useReportStepCard(() => [])
      expect(methodChipClass('PUT')).toBe('rsc-chip--put')
    })

    it('returns rsc-chip--delete for DELETE', () => {
      const { methodChipClass } = useReportStepCard(() => [])
      expect(methodChipClass('DELETE')).toBe('rsc-chip--delete')
    })

    it('returns empty string for unknown method', () => {
      const { methodChipClass } = useReportStepCard(() => [])
      expect(methodChipClass('PATCH')).toBe('')
    })

    it('returns empty string for null', () => {
      const { methodChipClass } = useReportStepCard(() => [])
      expect(methodChipClass(null)).toBe('')
    })

    it('returns empty string for undefined', () => {
      const { methodChipClass } = useReportStepCard(() => [])
      expect(methodChipClass(undefined)).toBe('')
    })
  })

  describe('pretty', () => {
    it('returns - for null', () => {
      const { pretty } = useReportStepCard(() => [])
      expect(pretty(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const { pretty } = useReportStepCard(() => [])
      expect(pretty(undefined)).toBe('-')
    })

    it('returns raw string when not JSON', () => {
      const { pretty } = useReportStepCard(() => [])
      expect(pretty('hello')).toBe('hello')
    })

    it('formats valid JSON string', () => {
      const { pretty } = useReportStepCard(() => [])
      expect(pretty('{"a":1}')).toBe(JSON.stringify({ a: 1 }, null, 2))
    })

    it('returns raw string when JSON.parse fails', () => {
      const { pretty } = useReportStepCard(() => [])
      expect(pretty('{invalid')).toBe('{invalid')
    })

    it('formats object as JSON', () => {
      const { pretty } = useReportStepCard(() => [])
      expect(pretty({ x: 1 })).toBe(JSON.stringify({ x: 1 }, null, 2))
    })

    it('formats number as JSON', () => {
      const { pretty } = useReportStepCard(() => [])
      expect(pretty(42)).toBe('42')
    })

    it('formats boolean as JSON', () => {
      const { pretty } = useReportStepCard(() => [])
      expect(pretty(true)).toBe('true')
    })

    it('formats array as JSON', () => {
      const { pretty } = useReportStepCard(() => [])
      expect(pretty([1, 2])).toBe('[\n  1,\n  2\n]')
    })
  })

  describe('displayValue', () => {
    it('returns - for null', () => {
      const { displayValue } = useReportStepCard(() => [])
      expect(displayValue(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const { displayValue } = useReportStepCard(() => [])
      expect(displayValue(undefined)).toBe('-')
    })

    it('returns string as-is', () => {
      const { displayValue } = useReportStepCard(() => [])
      expect(displayValue('text')).toBe('text')
    })

    it('returns stringified object', () => {
      const { displayValue } = useReportStepCard(() => [])
      expect(displayValue({ a: 1 })).toBe('{"a":1}')
    })

    it('returns stringified array', () => {
      const { displayValue } = useReportStepCard(() => [])
      expect(displayValue([1, 2])).toBe('[1,2]')
    })

    it('returns stringified number', () => {
      const { displayValue } = useReportStepCard(() => [])
      expect(displayValue(42)).toBe('42')
    })
  })

  describe('formatDuration', () => {
    it('returns - for null', () => {
      const { formatDuration } = useReportStepCard(() => [])
      expect(formatDuration(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const { formatDuration } = useReportStepCard(() => [])
      expect(formatDuration(undefined)).toBe('-')
    })

    it('formats milliseconds', () => {
      const { formatDuration } = useReportStepCard(() => [])
      expect(formatDuration(500)).toBe('500 ms')
    })

    it('formats seconds with one decimal', () => {
      const { formatDuration } = useReportStepCard(() => [])
      expect(formatDuration(1500)).toBe('1.5 s')
    })

    it('formats exact 1000ms', () => {
      const { formatDuration } = useReportStepCard(() => [])
      expect(formatDuration(1000)).toBe('1.0 s')
    })

    it('formats zero ms', () => {
      const { formatDuration } = useReportStepCard(() => [])
      expect(formatDuration(0)).toBe('0 ms')
    })
  })

  describe('headersEntries', () => {
    it('returns entries for non-null headers', () => {
      const { headersEntries } = useReportStepCard(() => [])
      const result = headersEntries({ 'content-type': 'application/json', 'x-token': 'abc' })
      expect(result).toEqual([['content-type', 'application/json'], ['x-token', 'abc']])
    })

    it('returns empty array for null', () => {
      const { headersEntries } = useReportStepCard(() => [])
      expect(headersEntries(null)).toEqual([])
    })

    it('returns empty array for undefined', () => {
      const { headersEntries } = useReportStepCard(() => [])
      expect(headersEntries(undefined)).toEqual([])
    })

    it('returns empty array for empty object', () => {
      const { headersEntries } = useReportStepCard(() => [])
      expect(headersEntries({})).toEqual([])
    })
  })

  describe('headerValue', () => {
    it('returns value for matching key', () => {
      const { headerValue } = useReportStepCard(() => [])
      expect(headerValue({ 'content-type': 'text/html' }, 'content-type')).toBe('text/html')
    })

    it('is case-insensitive', () => {
      const { headerValue } = useReportStepCard(() => [])
      expect(headerValue({ 'Content-Type': 'text/html' }, 'content-type')).toBe('text/html')
    })

    it('returns null for missing key', () => {
      const { headerValue } = useReportStepCard(() => [])
      expect(headerValue({ 'x-token': 'abc' }, 'content-type')).toBeNull()
    })

    it('returns null for null headers', () => {
      const { headerValue } = useReportStepCard(() => [])
      expect(headerValue(null, 'content-type')).toBeNull()
    })

    it('returns null for undefined headers', () => {
      const { headerValue } = useReportStepCard(() => [])
      expect(headerValue(undefined, 'content-type')).toBeNull()
    })

    it('stringifies non-string values', () => {
      const { headerValue } = useReportStepCard(() => [])
      expect(headerValue({ 'x-count': 42 }, 'x-count')).toBe('42')
    })

    it('returns null for null header value', () => {
      const { headerValue } = useReportStepCard(() => [])
      expect(headerValue({ 'x-null': null }, 'x-null')).toBeNull()
    })
  })

  describe('responseMeta', () => {
    it('returns HTTP/1.1 · content-type', () => {
      const { responseMeta } = useReportStepCard(() => [])
      const step = makeStep({
        response: { headers: { 'content-type': 'application/json' } },
      })
      expect(responseMeta(step)).toBe('HTTP/1.1 · application/json')
    })

    it('returns HTTP/1.1 when no content-type', () => {
      const { responseMeta } = useReportStepCard(() => [])
      const step = makeStep({ response: { headers: {} } })
      expect(responseMeta(step)).toBe('HTTP/1.1')
    })

    it('returns HTTP/1.1 when response is null', () => {
      const { responseMeta } = useReportStepCard(() => [])
      const step = makeStep({ response: null })
      expect(responseMeta(step)).toBe('HTTP/1.1')
    })

    it('returns HTTP/1.1 when response headers is null', () => {
      const { responseMeta } = useReportStepCard(() => [])
      const step = makeStep({ response: { headers: null } })
      expect(responseMeta(step)).toBe('HTTP/1.1')
    })

    it('returns HTTP/1.1 when no response field', () => {
      const { responseMeta } = useReportStepCard(() => [])
      const step = makeStep({ response: undefined })
      expect(responseMeta(step)).toBe('HTTP/1.1')
    })
  })

  describe('edge cases', () => {
    it('openKeys immutability: onToggle creates new Set', () => {
      const steps = () => [makeStep({ stepId: 's1', status: 'success' })]
      const { openKeys, onToggle } = useReportStepCard(steps)
      const before = openKeys.value
      onToggle('s1', makeEvent(true))
      expect(openKeys.value).not.toBe(before)
    })

    it('toggleAll creates new Set each call', () => {
      const steps = () => [makeStep({ stepId: 's1' })]
      const { openKeys, toggleAll } = useReportStepCard(steps)
      const before = openKeys.value
      toggleAll()
      expect(openKeys.value).not.toBe(before)
      const during = openKeys.value
      toggleAll()
      expect(openKeys.value).not.toBe(during)
    })

    it('defaultOpenKeys with mixed statuses', () => {
      const steps = () => [
        makeStep({ stepId: 's1', status: 'success' }),
        makeStep({ stepId: 's2', status: 'failed' }),
        makeStep({ stepId: 's3', status: 'passed' }),
        makeStep({ stepId: 's4', status: 'error' }),
        makeStep({ stepId: 's5', status: 'skipped' }),
        makeStep({ stepId: 's6', status: 'not_executed' }),
      ]
      const { openKeys } = useReportStepCard(steps)
      expect(openKeys.value.size).toBe(2)
      expect(openKeys.value.has('s2')).toBe(true)
      expect(openKeys.value.has('s4')).toBe(true)
    })

    it('pretty handles nested JSON string', () => {
      const { pretty } = useReportStepCard(() => [])
      const input = JSON.stringify({ a: { b: [1, 2] } })
      const result = pretty(input)
      expect(result).toBe(JSON.stringify({ a: { b: [1, 2] } }, null, 2))
    })

    it('headerValue finds key with different casing in both', () => {
      const { headerValue } = useReportStepCard(() => [])
      expect(headerValue({ 'X-Custom-Header': 'value' }, 'x-custom-header')).toBe('value')
    })
  })
})
