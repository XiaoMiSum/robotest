// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { Delete as DeleteIcon } from '@element-plus/icons-vue'
import KeyValueTable from './KeyValueTable.vue'
import type { ApiDebugKeyValue } from '@/types'

function makeRow(overrides: Partial<ApiDebugKeyValue> = {}): ApiDebugKeyValue {
  return { key: '', value: '', enabled: true, ...overrides }
}

function mountTable(entries: ApiDebugKeyValue[], props: Record<string, unknown> = {}) {
  return mount(KeyValueTable, {
    props: { entries, ...props },
    global: { plugins: [ElementPlus], components: { Delete: DeleteIcon } },
  })
}

describe('KeyValueTable', () => {
  describe('默认自动补行', () => {
    it('空表加载后补出一行可编辑行', () => {
      const entries: ApiDebugKeyValue[] = []
      mountTable(entries)
      expect(entries).toHaveLength(1)
    })

    it('末行填入后自动追加新空行', async () => {
      const entries: ApiDebugKeyValue[] = [makeRow()]
      const wrapper = mountTable(entries)
      await wrapper.find('tbody input').setValue('timeout')
      expect(entries).toHaveLength(2)
      expect(entries[1].key).toBe('')
    })
  })

  describe('headerAdd 表头新增', () => {
    it('空表不自动补行', () => {
      const entries: ApiDebugKeyValue[] = []
      mountTable(entries, { headerAdd: true })
      expect(entries).toHaveLength(0)
    })

    it('表头渲染 [＋ 新增] 并在点击时追加一行', async () => {
      const entries: ApiDebugKeyValue[] = []
      const wrapper = mountTable(entries, { headerAdd: true })
      const button = wrapper.find('th button')
      expect(button.exists()).toBe(true)
      expect(button.text()).toContain('新增')
      await button.trigger('click')
      expect(entries).toHaveLength(1)
      expect(wrapper.emitted('change')).toHaveLength(1)
    })

    it('输入时不再自动追加空行', async () => {
      const entries: ApiDebugKeyValue[] = [makeRow()]
      const wrapper = mountTable(entries, { headerAdd: true })
      await wrapper.find('tbody input').setValue('timeout')
      expect(entries).toHaveLength(1)
      expect(entries[0].key).toBe('timeout')
    })

    it('只读态隐藏新增入口', () => {
      const entries: ApiDebugKeyValue[] = []
      mountTable(entries, { headerAdd: true, disabled: true })
      expect(entries).toHaveLength(0)
    })
  })
})
