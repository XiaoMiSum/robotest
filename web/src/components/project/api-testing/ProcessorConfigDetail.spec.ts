// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ProcessorConfigDetail from './ProcessorConfigDetail.vue'

// jsdom 未实现 ResizeObserver，el-table 的尺寸监听依赖它
if (typeof ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
}

function mountDetail(element: Record<string, unknown>): VueWrapper {
  return mount(ProcessorConfigDetail, {
    props: { element },
    global: { plugins: [ElementPlus] },
  })
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

describe('ProcessorConfigDetail', () => {
  it('http 元素渲染文本行、键值对行与请求体代码块', () => {
    const wrapper = mountDetail({
      testclass: 'http',
      config: {
        method: 'POST',
        ref: '默认环境',
        path: '/api/login',
        headers: { 'Content-Type': 'application/json' },
        body: { user: 'a' },
      },
      extractors: [],
    })

    const labels = wrapper.findAll('.processor-detail__k').map((item) => item.text())
    expect(labels).toContain('请求方法')
    expect(labels).toContain('请求头')
    expect(wrapper.find('.processor-detail__kv-k').text()).toBe('Content-Type')
    expect(wrapper.find('.processor-detail__code').text()).toContain('"user": "a"')
    expect(wrapper.find('.processor-detail__extractors').exists()).toBe(false)
  })

  it('jdbc 元素 SQL 走代码块，参数以逗号串联', () => {
    const wrapper = mountDetail({
      testclass: 'jdbc',
      config: { datasource: '订单库', sql: 'select 1 from dual', args: ['a', 'b'] },
      extractors: [],
    })

    const rows = wrapper.findAll('.processor-detail__row')
    expect(rows[0].text()).toContain('JDBC')
    expect(rows[1].text()).toContain('订单库')
    expect(wrapper.find('.processor-detail__code').text()).toBe('select 1 from dual')
    expect(rows.at(-1)?.text()).toBe('参数a, b')
  })

  it('提取器非空时渲染只读表格，来源与空字段已映射为展示文案', async () => {
    const wrapper = mountDetail({
      testclass: 'http',
      config: {},
      extractors: [{ source: 'json_field', expression: '$.token', variableName: '', description: '' }],
    })
    // el-table 的列宽计算有 50ms 防抖，列上屏需等待其完成
    await delay(150)

    const headers = wrapper.findAll('.el-table__header th').map((item) => item.text().trim())
    expect(headers).toEqual(['来源', '表达式', '目标变量名', '描述'])
    const body = wrapper.find('.el-table__body').text()
    expect(body).toContain('JSON 字段')
    expect(body).toContain('$.token')
    expect(body).toContain('—')
  })

  it('缺省 element 时回落 HTTP 默认行', () => {
    const wrapper = mount(ProcessorConfigDetail, { global: { plugins: [ElementPlus] } })

    expect(wrapper.findAll('.processor-detail__k').map((item) => item.text())).toEqual([
      '处理器类型',
      '环境 HTTP 配置',
      '请求方法',
      '路径',
      '请求体类型',
    ])
  })
})
