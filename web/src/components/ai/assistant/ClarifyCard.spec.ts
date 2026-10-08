// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ClarifyCard from './ClarifyCard.vue'

function mountCard(options: string[] = ['选项一', '选项二']) {
  return mount(ClarifyCard, {
    props: { question: '要建哪个模块的评审？', options },
  })
}

describe('components/ai/assistant/ClarifyCard', () => {
  it('渲染反问文案与快捷选项', () => {
    const wrapper = mountCard()

    expect(wrapper.text()).toContain('要建哪个模块的评审？')
    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(2)
    expect(buttons[0].text()).toBe('选项一')
    expect(buttons[1].text()).toBe('选项二')
  })

  it('点击选项触发 pick 并携带选项文案', async () => {
    const wrapper = mountCard()

    await wrapper.findAll('button')[1].trigger('click')

    expect(wrapper.emitted('pick')?.[0]).toEqual(['选项二'])
  })

  it('无选项时不渲染选项区', () => {
    const wrapper = mountCard([])

    expect(wrapper.text()).toContain('要建哪个模块的评审？')
    expect(wrapper.findAll('button')).toHaveLength(0)
  })
})
