// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ContextPicker from './ContextPicker.vue'
import type { AiAssistantEntityRef } from '@/types'

const mocks = vi.hoisted(() => ({ load: vi.fn() }))

vi.mock('@/composables/project/trace/useTraceNodePicker', async () => {
  const { ref } = await import('vue')
  return {
    useTraceNodePicker: () => ({
      options: ref([
        { id: 'r1', label: '登录需求' },
        { id: 'r2', label: '订单需求' },
      ]),
      loading: ref(false),
      load: mocks.load,
    }),
  }
})

function mountPicker(selected: AiAssistantEntityRef[] = [], disabled = false) {
  return mount(ContextPicker, {
    props: { selected, disabled },
    global: { plugins: [ElementPlus] },
  })
}

async function openPicker(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('[aria-label="添加上下文附件"]').trigger('click')
  await flushPromises()
}

function isPickerOpen(wrapper: VueWrapper): boolean {
  return wrapper
    .get('[aria-label="添加上下文附件"]')
    .classes()
    .includes('ai-context-picker__trigger--open')
}

describe('components/ai/assistant/ContextPicker', () => {
  let activeWrapper: VueWrapper | null = null

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = null
    document.body.innerHTML = ''
  })

  it('打开弹层按默认类型拉取候选', async () => {
    activeWrapper = mountPicker()

    await openPicker(activeWrapper)

    expect(isPickerOpen(activeWrapper)).toBe(true)
    expect(mocks.load).toHaveBeenCalledWith('requirement', '')
    expect(document.body.textContent).toContain('登录需求')
  })

  it('切换类型立即按新类型重新拉取', async () => {
    activeWrapper = mountPicker()

    await openPicker(activeWrapper)
    const buttons = document.body.querySelectorAll('.ai-context-picker__types .el-radio-button__inner')
    ;(buttons[2] as HTMLElement).click()
    await flushPromises()

    expect(mocks.load).toHaveBeenLastCalledWith('module', '')
  })

  it('关键词防抖后携带关键词重新拉取', async () => {
    activeWrapper = mountPicker()

    await openPicker(activeWrapper)
    const input = document.body.querySelector('.ai-context-picker__keyword input')
    expect(input).not.toBeNull()
    const nativeInput = input as HTMLInputElement
    nativeInput.value = '登录'
    nativeInput.dispatchEvent(new Event('input', { bubbles: true }))
    await flushPromises()
    expect(mocks.load).toHaveBeenCalledTimes(1)

    await new Promise((resolve) => setTimeout(resolve, 350))
    await flushPromises()

    expect(mocks.load).toHaveBeenLastCalledWith('requirement', '登录')
    expect(mocks.load).toHaveBeenCalledTimes(2)
  })

  it('点击候选回传附件引用并收起弹层', async () => {
    activeWrapper = mountPicker()

    await openPicker(activeWrapper)
    const option = document.body.querySelector('.ai-context-picker__option') as HTMLButtonElement
    option.click()
    await flushPromises()

    expect(activeWrapper.emitted('add')?.[0]?.[0]).toEqual({
      entityType: 'requirement',
      entityId: 'r1',
      entityTitle: '登录需求',
    })
    expect(isPickerOpen(activeWrapper)).toBe(false)
  })

  it('已选候选重复点击不回传，仅收起弹层', async () => {
    activeWrapper = mountPicker([
      { entityType: 'requirement', entityId: 'r1', entityTitle: '登录需求' },
    ])

    await openPicker(activeWrapper)
    const selected = document.body.querySelector('.ai-context-picker__option--selected')
    expect(selected).not.toBeNull()
    ;(selected as HTMLButtonElement).click()
    await flushPromises()

    expect(activeWrapper.emitted('add')).toBeUndefined()
    expect(isPickerOpen(activeWrapper)).toBe(false)
  })

  it('弹层外点击收起，点击候选区保持展开', async () => {
    activeWrapper = mountPicker()

    await openPicker(activeWrapper)
    const content = document.body.querySelector('.ai-context-picker') as HTMLElement
    content.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await flushPromises()
    expect(isPickerOpen(activeWrapper)).toBe(true)

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await flushPromises()
    expect(isPickerOpen(activeWrapper)).toBe(false)
  })

  it('禁用态下触发按钮不可点击', async () => {
    activeWrapper = mountPicker([], true)

    const trigger = activeWrapper.get('[aria-label="添加上下文附件"]')
    expect(trigger.attributes('disabled')).toBeDefined()
    await trigger.trigger('click')

    expect(mocks.load).not.toHaveBeenCalled()
  })
})
