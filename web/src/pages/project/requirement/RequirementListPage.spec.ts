// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import RequirementListPage from './RequirementListPage.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  useRequirementList: vi.fn(),
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/composables/project/requirement/useRequirementList', () => ({
  useRequirementList: mocks.useRequirementList,
}))
function makeListState(overrides: Record<string, unknown> = {}) {
  return {
    loading: ref(false),
    hasLoaded: ref(true),
    loadError: ref(''),
    rows: ref([]),
    total: ref(0),
    pageNo: ref(1),
    pageSize: ref(20),
    filters: ref({ status: [], moduleIds: [], ownerId: '', systemVersion: '', keyword: '' }),
    filterCount: ref(0),
    moduleTree: ref([]),
    memberOptions: ref([]),
    versionOptions: ref([]),
    canCreate: ref(true),
    canConfirm: ref(false),
    canEdit: ref(false),
    aiAvailable: ref(true),
    retry: vi.fn(),
    search: vi.fn(),
    resetFilters: vi.fn(),
    changePage: vi.fn(),
    changePageSize: vi.fn(),
    handleKeywordBlur: vi.fn(),
    handleConfirm: vi.fn(),
    handleArchive: vi.fn(),
    handleUnarchive: vi.fn(),
    ...overrides,
  }
}

const wrappers: ReturnType<typeof mount>[] = []

function render() {
  const wrapper = mount(RequirementListPage, {
    global: {
      stubs: {
        RequirementImportDialog: true,
        ListPagination: true,
        ListTitleLink: true,
        ListLoadFeedback: true,
        TableColumnSetting: {
          props: ['iconOnly', 'columns', 'visibleKeys'],
          template: '<button class="col-setting" :data-icon-only="String(iconOnly)" aria-label="列设置" />',
        },
        ElCard: { template: '<div class="card"><slot /></div>' },
        ElSkeleton: true,
        ElTable: { template: '<div class="table"><slot /></div>' },
        ElTableColumn: {
          props: ['label'],
          template:
            '<div class="col" :data-label="label"><div class="col-header"><slot name="header">{{ label }}</slot></div></div>',
        },
        ElButton: { template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
        ElBadge: { template: '<div><slot /></div>' },
        ElInput: true,
        ElSelect: true,
        ElOption: true,
        ElTreeSelect: true,
        ElTag: true,
        ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
        ElDropdownMenu: { template: '<div><slot /></div>' },
        ElDropdownItem: { template: '<div><slot /></div>' },
        ElEmpty: { template: '<div><slot /></div>' },
        ElIcon: { template: '<i><slot /></i>' },
      },
      directives: { loading: () => {} },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('RequirementListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useRequirementList.mockReturnValue(makeListState())
  })

  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('将列设置放入操作列表头，工具栏不再展示列设置', async () => {
    const wrapper = render()
    await flushPromises()

    const settings = wrapper.findAll('.col-setting')
    expect(settings).toHaveLength(1)
    expect(wrapper.find('.requirement-list__toolbar .col-setting').exists()).toBe(false)
    expect(wrapper.find('.requirement-list__action-header .col-setting').exists()).toBe(true)
  })

  it('操作列常驻，列设置不受行操作权限影响', async () => {
    mocks.useRequirementList.mockReturnValue(makeListState({ canConfirm: ref(false), canEdit: ref(false) }))
    const wrapper = render()
    await flushPromises()

    expect(wrapper.find('[data-label="操作"]').exists()).toBe(true)
    expect(wrapper.find('.requirement-list__action-header .col-setting').exists()).toBe(true)
  })

  it('导入历史入口直接进入历史页，不再查询最近一次任务', async () => {
    const wrapper = render()
    await flushPromises()

    const btn = wrapper.findAll('button').find((b) => b.text().includes('导入历史'))
    expect(btn).toBeDefined()
    await btn!.trigger('click')
    await flushPromises()

    expect(mocks.push).toHaveBeenCalledWith('/workspace/projects/requirements/import-history')
  })

  it('AI 不可用时隐藏导入历史与导入需求入口', async () => {
    mocks.useRequirementList.mockReturnValue(makeListState({ aiAvailable: ref(false) }))
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).not.toContain('导入历史')
    expect(wrapper.text()).not.toContain('导入需求')
  })
})
