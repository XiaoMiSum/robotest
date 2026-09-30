// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import AiMasterSwitch from './AiMasterSwitch.vue'

// defineModel() 默认模型名为 modelValue，enabled 仅为本地 ref 名
function mountSwitch(
  modelValue: boolean,
  beforeChange: () => Promise<boolean> | boolean,
) {
  return mount(AiMasterSwitch, {
    props: { modelValue, loading: false, beforeChange },
    global: { plugins: [ElementPlus] },
  })
}

describe('AiMasterSwitch', () => {
  it('beforeChange 放行时点击切换 modelValue', async () => {
    const beforeChange = vi.fn(() => true)
    const wrapper = mountSwitch(false, beforeChange)

    await wrapper.find('.el-switch').trigger('click')
    await nextTick()

    expect(beforeChange).toHaveBeenCalledOnce()
    const events = wrapper.emitted('update:modelValue')
    expect(events).toHaveLength(1)
    expect(events![0][0]).toBe(true)
  })

  it('beforeChange 拒绝时不切换 modelValue', async () => {
    const beforeChange = vi.fn(() => false)
    const wrapper = mountSwitch(false, beforeChange)

    await wrapper.find('.el-switch').trigger('click')
    await nextTick()

    expect(beforeChange).toHaveBeenCalledOnce()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('beforeChange 返回 Promise<boolean> 时按结果决定是否切换', async () => {
    const beforeChange = vi.fn(async () => true)
    const wrapper = mountSwitch(false, beforeChange)

    await wrapper.find('.el-switch').trigger('click')
    // ElSwitch 内部 then 回调需一轮微任务落定
    await nextTick()
    await nextTick()

    expect(beforeChange).toHaveBeenCalledOnce()
    const events = wrapper.emitted('update:modelValue')
    expect(events).toHaveLength(1)
    expect(events![0][0]).toBe(true)
  })
})
