// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import BugListPage from './BugListPage.vue'
import BugListToolbar from '@/components/project/bug/BugListToolbar.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import type { BugListItem } from '@/types'

const mocks = vi.hoisted(() => ({ fetchBugs: vi.fn(), push: vi.fn(), error: vi.fn() }))
vi.mock('@/services/project', () => ({ fetchBugs: mocks.fetchBugs }))
vi.mock('@/services/workspace', () => ({ fetchMembers: vi.fn() }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ user: { id: 'user-1' } }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { error: mocks.error },
}))

const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(BugListPage, {
    global: {
      stubs: {
        BugListToolbar: true,
        BugListTable: {
          props: ['bugs'],
          template:
            '<div><span v-for="bug in bugs" :key="bug.id">{{ bug.title }}</span><slot v-if="!bugs.length" name="empty" /></div>',
        },
        BugResolveDialog: true,
        ListPagination: true,
        ElDialog: true,
        ElAlert: {
          props: ['title', 'description'],
          template: '<div>{{ title }} {{ description }}</div>',
        },
        ElEmpty: { props: ['description'], template: '<div>{{ description }}<slot /></div>' },
        ElButton: { template: '<button><slot /></button>' },
      },
      directives: { loading: () => {} },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

function renderLayout() {
  const wrapper = mount(BugListPage, {
    global: {
      stubs: {
        BugListToolbar: {
          template: '<div class="toolbar-stub"><slot name="column-setting" /></div>',
        },
        BugListTable: {
          props: ['bugs'],
          template:
            '<div class="table-stub"><div class="action-header-stub"><slot name="column-setting" /></div><span v-for="bug in bugs" :key="bug.id">{{ bug.title }}</span></div>',
        },
        TableColumnSetting: { template: '<button aria-label="列设置" />' },
        BugResolveDialog: true,
        ListPagination: true,
        ElDialog: true,
        ElAlert: {
          props: ['title', 'description'],
          template: '<div>{{ title }} {{ description }}</div>',
        },
        ElEmpty: { props: ['description'], template: '<div>{{ description }}<slot /></div>' },
        ElButton: { template: '<button><slot /></button>' },
      },
      directives: { loading: () => {} },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('缺陷列表样板交互', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchBugs.mockResolvedValue({ list: [], total: 0 })
  })
  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('首次加载失败显示重试，恢复后显示正常空态与提交入口', async () => {
    mocks.fetchBugs.mockRejectedValueOnce(new Error('网络不可用'))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('网络不可用')
    expect(wrapper.text()).not.toContain('暂无缺陷')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无缺陷')
    await wrapper.get('button').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/workspace/projects/bugs/create')
  })

  it('列设置入口由工具栏移入表格操作列表头', async () => {
    const wrapper = renderLayout()
    await flushPromises()
    expect(wrapper.find('.toolbar-stub button[aria-label="列设置"]').exists()).toBe(false)
    expect(wrapper.find('.action-header-stub button[aria-label="列设置"]').exists()).toBe(true)
  })

  it('快捷过滤无结果可重置，并恢复默认查询与页码', async () => {
    const wrapper = render()
    await flushPromises()
    const toolbar = wrapper.getComponent(BugListToolbar)
    toolbar.vm.$emit('update:quickFilter', 'unresolved')
    await nextTick()
    toolbar.vm.$emit('search')
    await flushPromises()
    expect(mocks.fetchBugs.mock.lastCall?.[0]).toMatchObject({ status: 'active', pageNo: 1 })
    expect(wrapper.text()).toContain('没有符合条件的缺陷')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(mocks.fetchBugs.mock.lastCall?.[0]).toMatchObject({ status: undefined, pageNo: 1 })
    expect(wrapper.text()).toContain('暂无缺陷')
  })

  it('翻页失败保留数据并明确提示旧数据，重试使用当前页码', async () => {
    const bug = { id: 'bug-1', title: '已有缺陷' } as BugListItem
    mocks.fetchBugs.mockResolvedValueOnce({ list: [bug], total: 40 })
    const wrapper = render()
    await flushPromises()
    mocks.fetchBugs.mockRejectedValueOnce(new Error('翻页失败'))
    const pager = wrapper.getComponent(ListPagination)
    pager.vm.$emit('update:page-no', 2)
    await nextTick()
    pager.vm.$emit('page-change', { pageNo: 2, pageSize: 20 })
    await flushPromises()
    expect(wrapper.text()).toContain('已有缺陷')
    expect(wrapper.get('[role="alert"]').text()).toContain('当前显示上次加载的数据')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(mocks.fetchBugs.mock.lastCall?.[0]).toMatchObject({ pageNo: 2, pageSize: 20 })
  })
})
