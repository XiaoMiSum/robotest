// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import StreamIndicator from './StreamIndicator.vue'
import type { AiAssistantSseError } from '@/types'

function mountIndicator(props: {
  streaming?: boolean
  recovering?: boolean
  error?: AiAssistantSseError | null
} = {}) {
  return mount(StreamIndicator, {
    props: { streaming: false, recovering: false, error: null, ...props },
    global: { plugins: [ElementPlus] },
  })
}

describe('components/ai/assistant/StreamIndicator', () => {
  it('空闲时不渲染任何指示', () => {
    expect(mountIndicator().find('.ai-stream-indicator').exists()).toBe(false)
  })

  it('流式生成中展示打字动画', () => {
    const wrapper = mountIndicator({ streaming: true })

    expect(wrapper.find('.ai-stream-indicator__typing').exists()).toBe(true)
    expect(wrapper.findAll('.ai-stream-indicator__typing span')).toHaveLength(3)
  })

  it('断线恢复中展示「恢复中…」', () => {
    const wrapper = mountIndicator({ streaming: true, recovering: true })

    expect(wrapper.text()).toContain('恢复中…')
    expect(wrapper.find('.ai-stream-indicator__typing').exists()).toBe(false)
  })

  it('发送失败展示错误码与原因，优先级最高', () => {
    const wrapper = mountIndicator({
      streaming: false,
      recovering: true,
      error: { code: 1000018260, msg: '消息内容不合法' },
    })

    expect(wrapper.text()).toContain('发送失败（1000018260）：消息内容不合法')
    expect(wrapper.text()).not.toContain('恢复中…')
  })
})
