import { describe, expect, it } from 'vitest'
import {
  defaultComponentConfig,
  defaultProcessorConfig,
  extractorsFromComponents,
  isProcessorComponentType,
  parseComponentConfig,
} from './processorFormModel'
import type { ApiComponentListItem } from '@/types'

function extractorComponent(partial: Partial<ApiComponentListItem>): ApiComponentListItem {
  return {
    id: 'comp-1',
    scope: 'project',
    type: 'extractor',
    name: '提取 Token',
    description: null,
    config: null,
    enabled: true,
    updatedAt: '2026-08-17 10:30:00',
    ...partial,
  }
}

describe('processorFormModel', () => {
  describe('isProcessorComponentType', () => {
    it('marks processor types only', () => {
      expect(isProcessorComponentType('preprocessor')).toBe(true)
      expect(isProcessorComponentType('postprocessor')).toBe(true)
      expect(isProcessorComponentType('validator')).toBe(false)
      expect(isProcessorComponentType('extractor')).toBe(false)
    })
  })

  describe('defaultProcessorConfig', () => {
    it('seeds enabled and sortOrder defaults', () => {
      expect(defaultProcessorConfig()).toEqual({ enabled: true, sortOrder: 0 })
    })
  })

  describe('defaultComponentConfig', () => {
    it('adds sortOrder for processor types only, enabled for all types', () => {
      expect(defaultComponentConfig('preprocessor')).toEqual({ enabled: true, sortOrder: 0 })
      expect(defaultComponentConfig('postprocessor')).toEqual({ enabled: true, sortOrder: 0 })
      expect(defaultComponentConfig('validator')).toEqual({ enabled: true })
      expect(defaultComponentConfig('extractor')).toEqual({ enabled: true })
    })
  })

  describe('parseComponentConfig', () => {
    it('parses a valid JSON object config', () => {
      expect(parseComponentConfig('{"source":"header","variableName":"token"}')).toEqual({
        source: 'header',
        variableName: 'token',
      })
    })

    it('returns empty object for null / invalid / non-object config', () => {
      expect(parseComponentConfig(null)).toEqual({})
      expect(parseComponentConfig('not json')).toEqual({})
      expect(parseComponentConfig('"plain"')).toEqual({})
      expect(parseComponentConfig('[1,2]')).toEqual({})
    })
  })

  describe('extractorsFromComponents', () => {
    it('converts each selected component config into an independent extractor row', () => {
      const items = [
        extractorComponent({
          config: '{"enabled":true,"source":"header","expression":"X-Token","variableName":"token","description":"取 Token 头"}',
        }),
        extractorComponent({
          id: 'comp-2',
          config: '{"source":"body","expression":"$.data.id","variableName":"userId"}',
        }),
      ]
      expect(extractorsFromComponents(items)).toEqual([
        {
          enabled: true,
          source: 'header',
          expression: 'X-Token',
          variableName: 'token',
          description: '取 Token 头',
        },
        {
          enabled: true,
          source: 'body',
          expression: '$.data.id',
          variableName: 'userId',
          description: '',
        },
      ])
    })

    it('falls back to defaults for missing or broken fields', () => {
      const items = [
        extractorComponent({ config: '{"enabled":false,"variableName":"v"}' }),
        extractorComponent({ id: 'comp-2', config: null }),
      ]
      expect(extractorsFromComponents(items)).toEqual([
        { enabled: false, source: '', expression: '', variableName: 'v', description: '' },
        { enabled: true, source: '', expression: '', variableName: '', description: '' },
      ])
    })
  })
})