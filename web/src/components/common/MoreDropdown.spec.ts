// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import MoreDropdown from './MoreDropdown.vue'

let active: VueWrapper | null = null

function mountDropdown(): VueWrapper {
  const wrapper = mount(MoreDropdown, {
    attachTo: document.body,
    props: { trigger: 'click' },
    global: { plugins: [ElementPlus] },
    slots: {
      trigger: '<button class="more-trigger">更多</button>',
      dropdown: `
        <el-dropdown-menu>
          <el-dropdown-item class="more-dropdown-test-item">操作一</el-dropdown-item>
        </el-dropdown-menu>
      `,
    },
  })
  active = wrapper
  return wrapper
}

function isOpen(wrapper: VueWrapper): boolean {
  return wrapper.get('.more-dropdown__trigger').attributes('aria-expanded') === 'true'
}

async function openDropdown(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('.more-trigger').trigger('click')
  vi.runAllTimers()
  await nextTick()
}

function dispatchBodyMousedown(): void {
  document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  active?.unmount()
  active = null
  document.querySelectorAll('.el-popper').forEach((el) => el.remove())
  vi.useRealTimers()
})

describe('MoreDropdown', () => {
  it('点击触发按钮后菜单展开', async () => {
    const wrapper = mountDropdown()
    expect(isOpen(wrapper)).toBe(false)
    await openDropdown(wrapper)
    expect(isOpen(wrapper)).toBe(true)
  })

  it('点击空白处（mousedown）后菜单收起', async () => {
    const wrapper = mountDropdown()
    await openDropdown(wrapper)
    expect(isOpen(wrapper)).toBe(true)

    dispatchBodyMousedown()
    vi.runAllTimers()
    await nextTick()

    expect(isOpen(wrapper)).toBe(false)
  })

  it('点击菜单内部不收起', async () => {
    const wrapper = mountDropdown()
    await openDropdown(wrapper)
    expect(isOpen(wrapper)).toBe(true)

    const item = document.body.querySelector('.more-dropdown-test-item')
    expect(item).toBeTruthy()
    item?.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    vi.runAllTimers()
    await nextTick()

    expect(isOpen(wrapper)).toBe(true)
  })
})
