// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ProcessorConfigEditor from './ProcessorConfigEditor.vue'

// jsdom 未实现 ResizeObserver，el-table 的尺寸监听依赖它
if (typeof ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
}

interface RefOption {
  name: string
  refName?: string
  isDefault?: boolean
}

function mountEditor(props: Record<string, unknown> = {}, slots: Record<string, string> = {}): VueWrapper {
  return mount(ProcessorConfigEditor, {
    props: { modelValue: { testclass: 'http', config: { path: '/a' } }, ...props },
    slots,
    global: { plugins: [ElementPlus] },
  })
}

function lastEmit(wrapper: VueWrapper): Record<string, unknown> | undefined {
  const emitted = wrapper.emitted('update:modelValue')
  return emitted?.[emitted.length - 1]?.[0] as Record<string, unknown> | undefined
}

function innerConfig(element: Record<string, unknown> | undefined): Record<string, unknown> {
  const config = element?.config
  return config && typeof config === 'object' && !Array.isArray(config) ? (config as Record<string, unknown>) : {}
}

describe('ProcessorConfigEditor', () => {
  it('header 插槽与类型切换同排渲染', () => {
    const wrapper = mountEditor({}, { header: '<span class="proc-head">名称占位</span>' })

    expect(wrapper.find('.processor-config__type-row .proc-head').text()).toBe('名称占位')
    expect(wrapper.findAll('.el-radio-button').map((item) => item.text())).toEqual(['HTTP', 'JDBC'])
  })

  it('隐藏类型切换且元素未配置类型时提示选择类型', () => {
    const wrapper = mountEditor({ modelValue: {}, showTypeSelect: false, showRefSelect: false })

    expect(wrapper.find('.processor-config__hint').text()).toBe('请在上方选择处理器类型')
    expect(wrapper.find('.processor-config__type-row').exists()).toBe(false)
  })

  it('未设置 showRefSelect=false 时渲染引用选择器，设置后不渲染', () => {
    expect(mountEditor().find('.processor-config__ref-select').exists()).toBe(true)
    expect(mountEditor({ showRefSelect: false }).find('.processor-config__ref-select').exists()).toBe(false)
  })

  it('默认 HTTP 引用在选项到达后补选并回写', async () => {
    const httpOptions: RefOption[] = [{ name: '默认', refName: 'h1', isDefault: true }]
    const wrapper = mountEditor({ httpOptions })
    await wrapper.vm.$nextTick()

    expect(innerConfig(lastEmit(wrapper)).ref).toBe('h1')
  })

  it('已有引用不被默认项覆盖', async () => {
    const wrapper = mountEditor({
      modelValue: { testclass: 'http', config: { ref: 'keep' } },
      httpOptions: [{ name: '默认', refName: 'h1', isDefault: true }],
    })
    await wrapper.vm.$nextTick()

    // 默认引用只在 ref 为空时补选：此处无任何回写即代表未被覆盖
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('切换到 JDBC 后重建 config 并补默认数据源', async () => {
    const wrapper = mountEditor({
      httpOptions: [],
      dsOptions: [{ name: 'MySQL', refName: 'd1', isDefault: true }],
    })

    await wrapper.findAll('.el-radio-button input')[1].setValue()
    await wrapper.vm.$nextTick()

    const emitted = lastEmit(wrapper)
    expect(emitted?.testclass).toBe('jdbc')
    expect(innerConfig(emitted).path).toBeUndefined()
    expect(innerConfig(emitted).datasource).toBe('d1')
    expect(wrapper.find('.processor-config__tabs').exists()).toBe(true)
  })

  it('引用选择器文案带引用名以区分同名配置', async () => {
    const wrapper = mountEditor({
      httpOptions: [{ name: '配置一', refName: 'h1' }],
    })

    await wrapper.find('.processor-config__ref-select').trigger('click')
    await wrapper.vm.$nextTick()
    // 下拉面板挂载在 body 上（teleport），且页面内多处下拉共存，故从 document 取全部选项文案
    const labels = Array.from(document.querySelectorAll('.el-select-dropdown__item'), (item) => item.textContent)
    expect(labels).toContain('配置一（h1）')
    wrapper.unmount()
  })

  it('import-extractors 事件透传给父级', async () => {
    const wrapper = mountEditor()

    await wrapper.findComponent({ name: 'RequestConfigEditor' }).vm.$emit('import-extractors')
    expect(wrapper.emitted('import-extractors')).toHaveLength(1)
  })
})
