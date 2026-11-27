// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import MoreDropdown, { type MoreAction } from './MoreDropdown.vue'

const actions: MoreAction[] = [
  { key: 'edit', label: '编辑' },
  { key: 'archive', label: '归档', tone: 'warning' },
  { key: 'delete', label: '删除', tone: 'danger', divided: true },
]

const mounted: VueWrapper[] = []

function mountDropdown(): VueWrapper {
  const wrapper = mount(MoreDropdown, {
    attachTo: document.body,
    props: { actions },
    global: { plugins: [ElementPlus] },
    slots: { trigger: '<button class="more-trigger">更多</button>' },
  })
  mounted.push(wrapper)
  return wrapper
}

function isOpen(wrapper: VueWrapper): boolean {
  return wrapper.get('.more-dropdown__trigger').attributes('aria-expanded') === 'true'
}

async function flush(): Promise<void> {
  vi.runAllTimers()
  await nextTick()
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  document.querySelectorAll('.el-popper').forEach((el) => el.remove())
  vi.useRealTimers()
})

describe('MoreDropdown', () => {
  it('点击触发按钮展开菜单并渲染动作项', async () => {
    const wrapper = mountDropdown()
    expect(isOpen(wrapper)).toBe(false)

    await wrapper.get('.more-trigger').trigger('click')
    await flush()

    expect(isOpen(wrapper)).toBe(true)
    const items = document.body.querySelectorAll('.el-dropdown-menu__item')
    expect(Array.from(items, (item) => item.textContent?.trim())).toEqual(['编辑', '归档', '删除'])
  })

  it('再次点击触发按钮收起菜单', async () => {
    const wrapper = mountDropdown()
    await wrapper.get('.more-trigger').trigger('click')
    await flush()
    expect(isOpen(wrapper)).toBe(true)

    await wrapper.get('.more-trigger').trigger('click')
    await flush()
    expect(isOpen(wrapper)).toBe(false)
  })

  it('点击菜单与触发器之外区域收起菜单', async () => {
    const wrapper = mountDropdown()
    await wrapper.get('.more-trigger').trigger('click')
    await flush()
    expect(isOpen(wrapper)).toBe(true)

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await flush()
    expect(isOpen(wrapper)).toBe(false)
  })

  it('菜单内按下不收起菜单', async () => {
    const wrapper = mountDropdown()
    await wrapper.get('.more-trigger').trigger('click')
    await flush()

    const item = document.body.querySelector('.el-dropdown-menu__item')
    expect(item).toBeTruthy()
    item?.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await flush()
    expect(isOpen(wrapper)).toBe(true)
  })

  it('Escape 收起菜单', async () => {
    const wrapper = mountDropdown()
    await wrapper.get('.more-trigger').trigger('click')
    await flush()
    expect(isOpen(wrapper)).toBe(true)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flush()
    expect(isOpen(wrapper)).toBe(false)
  })

  it('展开另一个菜单时收起此前展开的菜单', async () => {
    const first = mountDropdown()
    const second = mountDropdown()
    await first.get('.more-trigger').trigger('click')
    await flush()
    expect(isOpen(first)).toBe(true)

    await second.get('.more-trigger').trigger('click')
    await flush()
    expect(isOpen(first)).toBe(false)
    expect(isOpen(second)).toBe(true)
  })

  it('点击动作项透传 command 并收起菜单', async () => {
    const wrapper = mountDropdown()
    await wrapper.get('.more-trigger').trigger('click')
    await flush()

    const items = document.body.querySelectorAll('.el-dropdown-menu__item')
    ;(items[1] as HTMLElement).click()
    await flush()

    expect(wrapper.emitted('command')?.[0]).toEqual(['archive'])
    expect(isOpen(wrapper)).toBe(false)
  })

  it('按动作配置渲染警告色、危险色与分隔线', async () => {
    const wrapper = mountDropdown()
    await wrapper.get('.more-trigger').trigger('click')
    await flush()

    const items = document.body.querySelectorAll('.el-dropdown-menu__item')
    expect(items[1].getAttribute('style')).toContain('--el-color-warning')
    expect(items[2].getAttribute('style')).toContain('--el-color-danger')
    expect(document.body.querySelector('.el-dropdown-menu__item--divided')).toBeTruthy()
  })
})
