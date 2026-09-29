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
      // 首列为启用勾选，Key 输入框位于第二列
      await wrapper.find('tbody .kv-table__row td:nth-child(2) input').setValue('timeout')
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
      await wrapper.find('tbody .kv-table__row td:nth-child(2) input').setValue('timeout')
      expect(entries).toHaveLength(1)
      expect(entries[0].key).toBe('timeout')
    })

    it('只读态隐藏新增入口', () => {
      const entries: ApiDebugKeyValue[] = []
      mountTable(entries, { headerAdd: true, disabled: true })
      expect(entries).toHaveLength(0)
    })
  })

  describe('emptyText 空态占位', () => {
    it('空表渲染占位行并横跨表头全部列', () => {
      const wrapper = mountTable([], { headerAdd: true, emptyText: '暂无变量，点击表头 [＋ 新增]' })
      const cell = wrapper.find('.kv-table__empty td')
      expect(cell.exists()).toBe(true)
      expect(cell.text()).toBe('暂无变量，点击表头 [＋ 新增]')
      expect(cell.attributes('colspan')).toBe(String(wrapper.findAll('thead th').length))
    })

    it('含描述列与启用列时 colspan 随表头列数变化', () => {
      const wrapper = mountTable([], {
        headerAdd: true, emptyText: '暂无变量', showDescription: true, showEnabled: true,
      })
      expect(wrapper.findAll('thead th')).toHaveLength(5)
      expect(wrapper.find('.kv-table__empty td').attributes('colspan')).toBe('5')
    })

    it('有数据时不渲染占位行', () => {
      const wrapper = mountTable([makeRow({ key: 'BASE_URL' })], { headerAdd: true, emptyText: '暂无变量' })
      expect(wrapper.find('.kv-table__empty').exists()).toBe(false)
    })

    it('未传空态文案时不渲染占位行', () => {
      const wrapper = mountTable([], { headerAdd: true })
      expect(wrapper.find('.kv-table__empty').exists()).toBe(false)
    })
  })

  describe('启用勾选列', () => {
    it('未传 showEnabled 时仍渲染启用列', () => {
      const wrapper = mountTable([makeRow({ key: 'X-Token' })], { showDescription: true })
      expect(wrapper.find('thead .kv-table__col-enable').exists()).toBe(true)
      expect(wrapper.find('tbody .kv-table__col-enable').exists()).toBe(true)
    })

    it('showEnabled=false 时隐藏启用列', () => {
      const wrapper = mountTable([makeRow({ key: 'BASE_URL' })], { showDescription: true, showEnabled: false })
      expect(wrapper.find('thead .kv-table__col-enable').exists()).toBe(false)
      expect(wrapper.find('tbody .kv-table__col-enable').exists()).toBe(false)
    })

    it('勾选变更回写行数据', async () => {
      const entries: ApiDebugKeyValue[] = [makeRow({ key: 'X-Token', enabled: true })]
      const wrapper = mountTable(entries, { showEnabled: true })
      await wrapper.find('tbody input[type="checkbox"]').setValue(false)
      expect(entries[0].enabled).toBe(false)
    })
  })

  describe('列序与表头', () => {
    it('启用列位于首列且无表头文字，值/说明为中文表头', () => {
      const wrapper = mountTable([makeRow({ key: 'X-Token' })], { showDescription: true })
      const heads = wrapper.findAll('thead th')
      expect(heads[0].classes()).toContain('kv-table__col-enable')
      expect(heads[0].text()).toBe('')
      expect(heads[1].text()).toBe('Key')
      expect(heads[2].text()).toBe('值')
      expect(heads[3].text()).toBe('说明')
      expect(heads[4].classes()).toContain('kv-table__col-op')
      // 数据行勾选框与表头对齐，同在首列
      expect(wrapper.find('tbody tr td').classes()).toContain('kv-table__col-enable')
    })

    it('showEnabled=false 时首列为 Key 列', () => {
      const wrapper = mountTable([makeRow({ key: 'BASE_URL' })], { showEnabled: false })
      const heads = wrapper.findAll('thead th')
      expect(heads[0].classes()).not.toContain('kv-table__col-enable')
      expect(heads[0].text()).toBe('Key')
    })
  })
})
