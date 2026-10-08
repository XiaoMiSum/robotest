// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ComposerInput from './ComposerInput.vue'
import type { AiAssistantEntityRef } from '@/types'

vi.mock('@/composables/project/trace/useTraceNodePicker', async () => {
  const { ref } = await import('vue')
  return {
    useTraceNodePicker: () => ({
      options: ref([{ id: 'r1', label: '登录需求' }]),
      loading: ref(false),
      load: vi.fn(),
    }),
  }
})

const ATTACHMENT: AiAssistantEntityRef = {
  entityType: 'requirement',
  entityId: 'r1',
  entityTitle: '登录需求',
}

function mountComposer(
  props: Partial<{
    modelValue: string
    disabled: boolean
    attachments: AiAssistantEntityRef[]
  }> = {},
) {
  return mount(ComposerInput, {
    props: {
      modelValue: '',
      disabled: false,
      attachments: [],
      ...props,
    },
    global: { plugins: [ElementPlus] },
  })
}

async function pressEnter(wrapper: VueWrapper, options: Record<string, unknown> = {}): Promise<void> {
  const textarea = wrapper.get('textarea')
  textarea.element.dispatchEvent(
    new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, ...options }),
  )
  await flushPromises()
}

describe('components/ai/assistant/ComposerInput', () => {
  let activeWrapper: VueWrapper | null = null

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = null
    document.body.innerHTML = ''
  })

  it('Enter 发送非空白内容，Shift+Enter 仅换行', async () => {
    activeWrapper = mountComposer({ modelValue: '  问题  ' })

    await pressEnter(activeWrapper, { shiftKey: true })
    expect(activeWrapper.emitted('send')).toBeUndefined()

    await pressEnter(activeWrapper)
    expect(activeWrapper.emitted('send')?.[0]).toEqual(['问题'])
  })

  it('输入法组词确认的 Enter 不触发发送', async () => {
    activeWrapper = mountComposer({ modelValue: '评审' })

    await pressEnter(activeWrapper, { isComposing: true })

    expect(activeWrapper.emitted('send')).toBeUndefined()
  })

  it('空白内容时发送按钮禁用且 Enter 不发送', async () => {
    activeWrapper = mountComposer({ modelValue: '   ' })

    expect(activeWrapper.get('[aria-label="发送"]').attributes('disabled')).toBeDefined()
    await pressEnter(activeWrapper)
    expect(activeWrapper.emitted('send')).toBeUndefined()
  })

  it('禁用态下输入框与发送按钮均禁用', () => {
    activeWrapper = mountComposer({ modelValue: '问题', disabled: true })

    expect(activeWrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(activeWrapper.get('[aria-label="发送"]').attributes('disabled')).toBeDefined()
  })

  it('输入变化回传给父级，点击发送携带去空白内容', async () => {
    activeWrapper = mountComposer()

    await activeWrapper.get('textarea').setValue('新问题')
    await flushPromises()
    expect(activeWrapper.emitted('update:modelValue')?.[0]).toEqual(['新问题'])

    await activeWrapper.get('[aria-label="发送"]').trigger('click')
    expect(activeWrapper.emitted('send')).toBeUndefined()

    await activeWrapper.setProps({ modelValue: '新问题' })
    await activeWrapper.get('[aria-label="发送"]').trigger('click')
    expect(activeWrapper.emitted('send')?.[0]).toEqual(['新问题'])
  })

  it('附件标签渲染与移除回传附件 ID', async () => {
    activeWrapper = mountComposer({
      modelValue: '问题',
      attachments: [ATTACHMENT],
    })

    const chips = activeWrapper.findAll('.ai-composer__attachment')
    expect(chips).toHaveLength(1)
    expect(chips[0].text()).toContain('登录需求')

    await activeWrapper.get('[aria-label="移除附件：登录需求"]').trigger('click')
    expect(activeWrapper.emitted('remove-attachment')?.[0]).toEqual(['r1'])
  })

  it('附件选择器回传的引用上抛为 add-attachment', async () => {
    activeWrapper = mountComposer()

    await activeWrapper.get('[aria-label="添加上下文附件"]').trigger('click')
    await flushPromises()
    const option = document.body.querySelector('.ai-context-picker__option') as HTMLButtonElement
    option.click()
    await flushPromises()

    expect(activeWrapper.emitted('add-attachment')?.[0]?.[0]).toEqual({
      entityType: 'requirement',
      entityId: 'r1',
      entityTitle: '登录需求',
    })
  })
})
