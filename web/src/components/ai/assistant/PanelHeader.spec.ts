// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import PanelHeader from './PanelHeader.vue'

interface HeaderProps {
  title?: string
  hasConversation?: boolean
  archived?: boolean
  listOpen?: boolean
}

function mountHeader(props: HeaderProps = {}) {
  return mount(PanelHeader, {
    props: {
      title: '登录评审',
      hasConversation: true,
      archived: false,
      listOpen: false,
      ...props,
    },
    global: { plugins: [ElementPlus] },
  })
}

describe('components/ai/assistant/PanelHeader', () => {
  it('渲染会话标题并按 listOpen 更新会话开关的展开态', () => {
    const wrapper = mountHeader({ title: '登录评审', listOpen: true })

    expect(wrapper.get('.ai-panel-header__title').text()).toBe('登录评审')
    expect(wrapper.get('[aria-label="历史会话"]').attributes('aria-expanded')).toBe('true')
  })

  it('会话开关点击触发 toggle-list', async () => {
    const wrapper = mountHeader()

    await wrapper.get('[aria-label="历史会话"]').trigger('click')

    expect(wrapper.emitted('toggle-list')).toHaveLength(1)
  })

  it('重命名 / 归档 / 删除 / 新会话各触发对应事件', async () => {
    const wrapper = mountHeader()

    await wrapper.get('[aria-label="重命名会话"]').trigger('click')
    await wrapper.get('[aria-label="归档会话"]').trigger('click')
    await wrapper.get('[aria-label="删除会话"]').trigger('click')
    await wrapper.get('[aria-label="新会话"]').trigger('click')

    expect(wrapper.emitted('rename')).toHaveLength(1)
    expect(wrapper.emitted('archive')).toHaveLength(1)
    expect(wrapper.emitted('delete')).toHaveLength(1)
    expect(wrapper.emitted('new-session')).toHaveLength(1)
  })

  it('收起与关闭按钮都触发 close', async () => {
    const wrapper = mountHeader()

    await wrapper.get('[aria-label="收起面板"]').trigger('click')
    await wrapper.get('[aria-label="关闭面板"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(2)
  })

  it('无会话时禁用重命名 / 归档 / 删除，新会话保持可用', () => {
    const wrapper = mountHeader({ hasConversation: false })

    expect(wrapper.get('[aria-label="重命名会话"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label="归档会话"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label="删除会话"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label="新会话"]').attributes('disabled')).not.toBeDefined()
  })

  it('归档会话只读：仅归档按钮禁用', () => {
    const wrapper = mountHeader({ archived: true })

    expect(wrapper.get('[aria-label="归档会话"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label="重命名会话"]').attributes('disabled')).not.toBeDefined()
    expect(wrapper.get('[aria-label="删除会话"]').attributes('disabled')).not.toBeDefined()
  })

  it('仅从头栏空白处发起拖动，按钮上的按下不触发', async () => {
    const wrapper = mountHeader()

    await wrapper.get('.ai-panel-header').trigger('mousedown')
    expect(wrapper.emitted('drag-start')).toHaveLength(1)

    await wrapper.get('[aria-label="新会话"]').trigger('mousedown')
    expect(wrapper.emitted('drag-start')).toHaveLength(1)
  })
})
