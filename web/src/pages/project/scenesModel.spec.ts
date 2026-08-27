import { describe, expect, it } from 'vitest'
import {
  methodTagType,
  stepMethod,
  stepPath,
  sortedSteps,
  emptyStepDraft,
  createValidator,
  serializeValidators,
  createExtractor,
  serializeExtractors,
  createStepVariable,
  parseRequestConfig,
  buildEmptyRequestConfig,
  createExecutionConfig,
  STEP_TYPE_OPTIONS,
  FAILURE_RULE_OPTIONS,
  VALIDATOR_TARGETS,
  VALIDATOR_CONDITIONS,
  EXTRACTOR_SOURCES,
  SOURCE_TYPE_LABELS,
} from './scenesModel'
import type { ApiSceneStepItem } from '@/types'

function step(overrides: Partial<ApiSceneStepItem> = {}): ApiSceneStepItem {
  return {
    id: 'step-1',
    name: '步骤 1',
    stepType: 'http',
    sourceType: 'custom',
    sortOrder: 1,
    enabled: true,
    requestConfig: { method: 'GET', url: '/api/test' },
    variables: [],
    processors: [],
    validators: [],
    extractors: [],
    ...overrides,
  }
}

describe('scenesModel', () => {
  describe('methodTagType', () => {
    it('maps HTTP methods to Element Plus tag types', () => {
      expect(methodTagType('GET')).toBe('success')
      expect(methodTagType('POST')).toBe('primary')
      expect(methodTagType('PUT')).toBe('warning')
      expect(methodTagType('PATCH')).toBe('warning')
      expect(methodTagType('DELETE')).toBe('danger')
      expect(methodTagType('OPTIONS')).toBe('info')
    })

    it('is case-insensitive', () => {
      expect(methodTagType('get')).toBe('success')
      expect(methodTagType('post')).toBe('primary')
    })
  })

  describe('stepMethod', () => {
    it('extracts method from requestConfig and uppercases', () => {
      const s = step({ requestConfig: { method: 'post', url: '/x' } })
      expect(stepMethod(s)).toBe('POST')
    })

    it('returns null when requestConfig is missing method', () => {
      expect(stepMethod(step({ requestConfig: { url: '/x' } }))).toBeNull()
    })

    it('returns null when requestConfig is null', () => {
      expect(stepMethod(step({ requestConfig: null as unknown as Record<string, unknown> }))).toBeNull()
    })
  })

  describe('stepPath', () => {
    it('extracts url from requestConfig', () => {
      const s = step({ requestConfig: { method: 'GET', url: '/api/users' } })
      expect(stepPath(s)).toBe('/api/users')
    })

    it('returns null when url is missing', () => {
      expect(stepPath(step({ requestConfig: {} }))).toBeNull()
    })
  })

  describe('sortedSteps', () => {
    it('returns a new array sorted by sortOrder ascending', () => {
      const a = step({ id: 'a', sortOrder: 3 })
      const b = step({ id: 'b', sortOrder: 1 })
      const c = step({ id: 'c', sortOrder: 2 })
      const sorted = sortedSteps([a, b, c])
      expect(sorted.map((s) => s.id)).toEqual(['b', 'c', 'a'])
    })

    it('does not mutate the input', () => {
      const arr = [step({ sortOrder: 2 }), step({ sortOrder: 1 })]
      sortedSteps(arr)
      expect(arr[0].sortOrder).toBe(2)
    })
  })

  describe('emptyStepDraft', () => {
    it('returns http step with empty name', () => {
      const draft = emptyStepDraft()
      expect(draft.stepType).toBe('http')
      expect(draft.name).toBe('')
      expect(draft.requestConfig.method).toBe('GET')
    })
  })

  describe('createValidator', () => {
    it('returns a validator with unique id and default target', () => {
      const v1 = createValidator()
      const v2 = createValidator()
      expect(v1.id).not.toBe(v2.id)
      expect(v1.enabled).toBe(true)
      expect(v1.target).toBe('status_code')
      expect(v1.condition).toBe('equals')
    })
  })

  describe('serializeValidators', () => {
    it('filters out validators without a name', () => {
      const items = [
        createValidator(),
        { ...createValidator(), name: '状态码校验' },
        { ...createValidator(), name: '  ' },
      ]
      const result = serializeValidators(items)
      expect(result).toHaveLength(1)
      expect(result[0].name).toBe('状态码校验')
    })

    it('returns a copy of each item', () => {
      const items = [{ ...createValidator(), name: 'test' }]
      const result = serializeValidators(items)
      expect(result[0]).toEqual(items[0])
      expect(result[0]).not.toBe(items[0])
    })
  })

  describe('createExtractor', () => {
    it('returns an extractor with unique id and default source', () => {
      const e = createExtractor()
      expect(e.source).toBe('json_field')
      expect(e.enabled).toBe(true)
    })
  })

  describe('serializeExtractors', () => {
    it('filters out extractors without both name and variableName', () => {
      const items = [
        createExtractor(),
        { ...createExtractor(), name: 'token', variableName: 'token_value' },
        { ...createExtractor(), name: 'missing_var', variableName: '' },
        { ...createExtractor(), name: '', variableName: 'orphan' },
      ]
      const result = serializeExtractors(items)
      expect(result).toHaveLength(1)
      expect(result[0].variableName).toBe('token_value')
    })
  })

  describe('createStepVariable', () => {
    it('returns a step variable with default source', () => {
      const v = createStepVariable()
      expect(v.source).toBe('custom')
      expect(v.name).toBe('')
    })
  })

  describe('parseRequestConfig', () => {
    it('returns empty object for null/undefined', () => {
      expect(parseRequestConfig(null)).toEqual({})
      expect(parseRequestConfig(undefined)).toEqual({})
    })

    it('passes through valid config', () => {
      const config = { method: 'POST', url: '/api' }
      expect(parseRequestConfig(config)).toBe(config)
    })

    it('returns empty for non-object', () => {
      expect(parseRequestConfig('string' as unknown as Record<string, unknown>)).toEqual({})
    })
  })

  describe('buildEmptyRequestConfig', () => {
    it('returns a complete empty config', () => {
      const config = buildEmptyRequestConfig()
      expect(config.method).toBe('GET')
      expect(config.headers).toEqual([])
      expect(config.params).toEqual([])
      expect(config.body).toEqual({ type: 'none', content: null })
      expect(config.timeout).toBe(30000)
    })
  })

  describe('createExecutionConfig', () => {
    it('returns defaults', () => {
      const c = createExecutionConfig()
      expect(c.timeout).toBe(30000)
      expect(c.retryCount).toBe(0)
      expect(c.conditionExpression).toBe('')
    })
  })

  describe('constants', () => {
    it('STEP_TYPE_OPTIONS has http and jdbc', () => {
      expect(STEP_TYPE_OPTIONS.map((o) => o.value)).toEqual(['http', 'jdbc'])
    })

    it('FAILURE_RULE_OPTIONS has all and continue', () => {
      expect(FAILURE_RULE_OPTIONS.map((o) => o.value)).toEqual(['all', 'continue'])
    })

    it('VALIDATOR_TARGETS covers expected targets', () => {
      const values = VALIDATOR_TARGETS.map((t) => t.value)
      expect(values).toContain('status_code')
      expect(values).toContain('json_field')
      expect(values).toContain('groovy')
    })

    it('VALIDATOR_CONDITIONS covers standard comparison operators', () => {
      const values = VALIDATOR_CONDITIONS.map((c) => c.value)
      expect(values).toContain('equals')
      expect(values).toContain('contains')
      expect(values).toContain('matches_regex')
    })

    it('EXTRACTOR_SOURCES covers standard sources', () => {
      const values = EXTRACTOR_SOURCES.map((s) => s.value)
      expect(values).toContain('json_field')
      expect(values).toContain('regex')
      expect(values).toContain('full_body')
    })

    it('SOURCE_TYPE_LABELS maps known source types', () => {
      expect(SOURCE_TYPE_LABELS.custom).toBe('自定义')
      expect(SOURCE_TYPE_LABELS.link).toBe('链接引用')
    })
  })
})
