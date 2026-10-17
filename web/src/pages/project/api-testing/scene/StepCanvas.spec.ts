// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import StepCanvas from './StepCanvas.vue'
import type { ApiSceneStepItem } from '@/types'

function makeStep(overrides: Partial<ApiSceneStepItem> = {}): ApiSceneStepItem {
  return {
    id: 's1',
    name: '健康检查',
    stepType: 'http',
    sortOrder: 1,
    enabled: true,
    sourceType: 'manual',
    sourceId: null,
    sourceRef: null,
    sourceStatus: null,
    requestConfig: { method: 'GET', url: '/api/health' },
    validators: [],
    extractors: [],
    ...overrides,
  } as ApiSceneStepItem
}

function mountCanvas(steps: ApiSceneStepItem[]) {
  return mount(StepCanvas, {
    props: { steps },
    global: { plugins: [ElementPlus] },
  })
}

describe('StepCanvas 空态', () => {
  it('无步骤时渲染引导插图与添加按钮，点击触发 add（与处理器页签同构）', async () => {
    const wrapper = mountCanvas([])
    expect(wrapper.find('.el-empty').exists()).toBe(true)
    expect(wrapper.find('.el-empty__description').text()).toBe('暂无步骤')

    const add = wrapper.find('.step-canvas__add')
    expect(add.exists()).toBe(true)
    await add.trigger('click')
    expect(wrapper.emitted('add')).toHaveLength(1)
  })

  it('有步骤时不渲染空态', () => {
    const wrapper = mountCanvas([makeStep()])
    expect(wrapper.find('.el-empty').exists()).toBe(false)
    expect(wrapper.find('.step-canvas__add').exists()).toBe(false)
    expect(wrapper.findAll('[data-test="step-card"]')).toHaveLength(1)
  })

  it('点击卡片触发 edit，点击启用勾选不触发', async () => {
    const wrapper = mountCanvas([makeStep()])
    await wrapper.find('[data-test="step-card"]').trigger('click')
    expect(wrapper.emitted('edit')).toHaveLength(1)

    await wrapper.find('.step-canvas__enabled input').trigger('click')
    expect(wrapper.emitted('edit')).toHaveLength(1)
  })
})
