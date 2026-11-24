// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ReviewListPage from './ReviewListPage.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import ReviewCreateDialog from '@/components/project/functional-testing/review/ReviewCreateDialog.vue'
import ReviewEditDialog from '@/components/project/functional-testing/review/ReviewEditDialog.vue'
import type { TestReviewDetail, TestReviewListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetch: vi.fn(), create: vi.fn(), reopen: vi.fn(), delete: vi.fn(), detail: vi.fn(), update: vi.fn(),
  push: vi.fn(), error: vi.fn(), success: vi.fn(), warning: vi.fn(), confirm: vi.fn(), currentUserId: 'u1',
}))
vi.mock('@/services/project', () => ({
  fetchReviews: mocks.fetch,
  createReview: mocks.create,
  reopenReview: mocks.reopen,
  deleteReview: mocks.delete,
  getReviewDetail: mocks.detail,
  updateReview: mocks.update,
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ user: { id: mocks.currentUserId } }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('element-plus', async (original) => ({ ...await original<typeof import('element-plus')>(), ElMessage: { error: mocks.error, success: mocks.success }, ElMessageBox: { confirm: mocks.confirm } }))

const row: TestReviewListItem = { id: 'r1', title: '支付评审', status: 'rejected', initiator: { id: 'u1', name: '张明' }, participants: [], participantCount: 0, createdAt: '2026-11-15T08:00:00Z', totalAssociated: 10, reviewed: 8, passed: 6, progressPercent: 80, passRate: 60, requirementCount: 2 }
const activeRow: TestReviewListItem = { ...row, status: 'in_progress' }
const detail: TestReviewDetail = {
  id: 'r1', title: '支付评审', description: null, status: 'in_progress',
  initiator: { id: 'u1', name: '张明' }, participantIds: ['u1'],
  requirements: [], requirementIds: ['req-1'],
  createdAt: '2026-11-15T08:00:00Z',
}
const editPayload = {
  title: '支付评审（调整）',
  description: '',
  participantIds: ['u1'],
  requirementIds: ['req-2'],
}
const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(ReviewListPage, {
    global: {
      stubs: {
        ReviewCreateDialog: true,
        ReviewEditDialog: true,
        ListPagination: true,
        Plus: true,
        Search: true,
        TableColumnSetting: {
          props: { iconOnly: { type: Boolean, default: false } },
          template: '<button class="col-setting" :data-icon-only="String(iconOnly)" aria-label="列设置" />',
        },
      },
      directives: { loading: () => {} },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}
function button(wrapper: ReturnType<typeof mount>, text: string) {
  const found = wrapper.findAll('button').find((item) => item.text() === text)
  if (!found) throw new Error(`找不到按钮 ${text}`)
  return found
}
async function page(wrapper: ReturnType<typeof mount>, pageNo: number) {
  const pager = wrapper.getComponent(ListPagination)
  pager.vm.$emit('update:page-no', pageNo)
  await nextTick()
  pager.vm.$emit('change', { pageNo, pageSize: 20 })
  await flushPromises()
}
function deferred() {
  let resolve!: (value: { list: TestReviewListItem[]; total: number }) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<{ list: TestReviewListItem[]; total: number }>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.resetAllMocks()
  mocks.currentUserId = 'u1'
  mocks.fetch.mockResolvedValue({ list: [], total: 0 })
  mocks.detail.mockResolvedValue(detail)
  mocks.update.mockResolvedValue(detail)
  mocks.confirm.mockResolvedValue('confirm')
})
afterEach(() => { wrappers.splice(0).forEach((wrapper) => wrapper.unmount()) })

describe('评审列表样板', () => {
  it('列设置入口置于操作列表头右侧，工具栏不再展示列设置', async () => {
    const wrapper = render()
    await flushPromises()

    const settings = wrapper.findAll('.col-setting')
    expect(settings).toHaveLength(1)
    expect(settings[0].attributes('data-icon-only')).toBe('true')
    expect(wrapper.find('.review-table__action-header .col-setting').exists()).toBe(true)
  })

  it('首次加载不闪空态，失败可重试，恢复后空态可以发起评审', async () => {
    const request = deferred()
    mocks.fetch.mockReturnValueOnce(request.promise)
    const wrapper = render()
    await nextTick()
    expect(wrapper.text()).not.toContain('暂无评审')
    request.reject(new Error('网络中断'))
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('网络中断')
    expect(mocks.error).toHaveBeenCalledWith('网络中断')
    await button(wrapper, '刷新').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无评审')
    await wrapper.get('.el-empty button').trigger('click')
    expect(wrapper.getComponent(ReviewCreateDialog).props('modelValue')).toBe(true)
  })

  it('搜索回到首页，无结果可重置条件', async () => {
    const wrapper = render()
    await flushPromises()
    await page(wrapper, 3)
    await wrapper.get('input[aria-label="评审标题"]').setValue(' 支付 ')
    await button(wrapper, '查询').trigger('click')
    await flushPromises()
    expect(mocks.fetch.mock.lastCall?.[0]).toMatchObject({ keyword: '支付', pageNo: 1 })
    expect(wrapper.text()).toContain('没有符合条件的评审')
    await button(wrapper, '重置筛选').trigger('click')
    await flushPromises()
    expect(mocks.fetch.mock.lastCall?.[0]).toMatchObject({ keyword: undefined, status: undefined, pageNo: 1 })
  })

  it('翻页失败保留行与总数，重试使用当前条件和页码', async () => {
    mocks.fetch.mockResolvedValueOnce({ list: [row], total: 60 })
    const wrapper = render()
    await flushPromises()
    mocks.fetch.mockRejectedValueOnce(new Error('翻页失败'))
    await page(wrapper, 2)
    expect(wrapper.text()).toContain('支付评审')
    expect(wrapper.getComponent(ListPagination).props('total')).toBe(60)
    expect(wrapper.get('[role="alert"]').text()).toContain('上次加载的数据')
    await button(wrapper, '刷新').trigger('click')
    await flushPromises()
    expect(mocks.fetch.mock.lastCall?.[0]).toMatchObject({ pageNo: 2, pageSize: 20 })
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('旧成功响应不能覆盖较新的列表', async () => {
    const older = deferred()
    mocks.fetch.mockReturnValueOnce(older.promise)
    const wrapper = render()
    mocks.fetch.mockResolvedValueOnce({ list: [row], total: 1 })
    await button(wrapper, '查询').trigger('click')
    await flushPromises()
    older.resolve({ list: [], total: 0 })
    await flushPromises()
    expect(wrapper.text()).toContain('支付评审')
    expect(wrapper.getComponent(ListPagination).props('total')).toBe(1)
  })

  it('旧错误不结束新请求，卸载后响应不弹错误', async () => {
    const older = deferred(), latest = deferred()
    mocks.fetch.mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise)
    const wrapper = render()
    await button(wrapper, '查询').trigger('click')
    older.reject(new Error('过期错误'))
    await flushPromises()
    expect(wrapper.get('[aria-label="评审列表"]').attributes('aria-busy')).toBe('true')
    expect(mocks.error).not.toHaveBeenCalled()
    wrapper.unmount()
    latest.reject(new Error('离开后的错误'))
    await flushPromises()
    expect(mocks.error).not.toHaveBeenCalled()
  })

  it('行入口导航，重新发起及删除经确认后刷新，取消不提交', async () => {
    mocks.fetch.mockResolvedValue({ list: [row], total: 1 })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.list-title-link').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/workspace/projects/reviews/r1')
    await button(wrapper, '重新执行').trigger('click')
    await flushPromises()
    expect(mocks.reopen).toHaveBeenCalledWith('r1')
    expect(mocks.fetch).toHaveBeenCalledTimes(2)
    mocks.confirm.mockRejectedValueOnce('cancel')
    await button(wrapper, '删除').trigger('click')
    await flushPromises()
    expect(mocks.delete).not.toHaveBeenCalled()
    await button(wrapper, '删除').trigger('click')
    await flushPromises()
    expect(mocks.delete).toHaveBeenCalledWith('r1')
    expect(mocks.fetch).toHaveBeenCalledTimes(3)
  })

  async function openEdit(wrapper: ReturnType<typeof mount>) {
    await button(wrapper, '编辑').trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(ReviewEditDialog).props('modelValue')).toBe(true)
    return wrapper.getComponent(ReviewEditDialog)
  }

  it('仅发起人的活跃态评审显示编辑入口', async () => {
    mocks.fetch.mockResolvedValue({ list: [activeRow, { ...activeRow, id: 'r2', initiator: { id: 'u2', name: '他人' } }, { ...row, id: 'r3', status: 'completed' }], total: 3 })
    const wrapper = render()
    await flushPromises()
    const editButtons = wrapper.findAll('.review-table button').filter((item) => item.text() === '编辑')
    expect(editButtons).toHaveLength(1)
    await editButtons[0].trigger('click')
    await flushPromises()
    expect(mocks.detail).toHaveBeenCalledWith('r1')
  })

  it('关联需求变化确认后以 replan=true 保存并提示后台重新规划，不轮询不刷新', async () => {
    mocks.fetch.mockResolvedValue({ list: [activeRow], total: 1 })
    const wrapper = render()
    await flushPromises()
    const dialog = await openEdit(wrapper)
    dialog.vm.$emit('submit', editPayload)
    await flushPromises()
    expect(mocks.confirm).toHaveBeenCalledWith('检测到关联需求发生变化，是否重新规划测试用例？', '关联需求变化', { type: 'warning' })
    expect(mocks.update).toHaveBeenCalledWith('r1', { ...editPayload, replan: true })
    expect(mocks.success).toHaveBeenCalledWith('已保存，正在后台重新规划测试用例')
    expect(mocks.fetch).toHaveBeenCalledTimes(1)
    expect(wrapper.getComponent(ReviewEditDialog).props('modelValue')).toBe(false)
  })

  it('取消重新规划仅保存，提示已保存并刷新列表', async () => {
    mocks.fetch.mockResolvedValue({ list: [activeRow], total: 1 })
    mocks.confirm.mockRejectedValueOnce('cancel')
    const wrapper = render()
    await flushPromises()
    const dialog = await openEdit(wrapper)
    dialog.vm.$emit('submit', editPayload)
    await flushPromises()
    expect(mocks.update).toHaveBeenCalledWith('r1', { ...editPayload, replan: false })
    expect(mocks.success).toHaveBeenCalledWith('已保存')
    expect(mocks.success).not.toHaveBeenCalledWith('已保存，正在后台重新规划测试用例')
    expect(mocks.fetch).toHaveBeenCalledTimes(2)
  })

  it('仅调整关联需求顺序不弹确认且不重新规划', async () => {
    mocks.fetch.mockResolvedValue({ list: [activeRow], total: 1 })
    mocks.detail.mockResolvedValue({ ...detail, requirementIds: ['req-1', 'req-2'] })
    const wrapper = render()
    await flushPromises()
    const dialog = await openEdit(wrapper)
    dialog.vm.$emit('submit', { ...editPayload, requirementIds: ['req-2', 'req-1'] })
    await flushPromises()
    expect(mocks.confirm).not.toHaveBeenCalled()
    expect(mocks.update).toHaveBeenCalledWith('r1', expect.objectContaining({ replan: false }))
  })

  it('详情加载失败时提示且不打开编辑弹窗', async () => {
    mocks.fetch.mockResolvedValue({ list: [activeRow], total: 1 })
    mocks.detail.mockRejectedValueOnce(new Error('详情不可用'))
    const wrapper = render()
    await flushPromises()
    await button(wrapper, '编辑').trigger('click')
    await flushPromises()
    expect(mocks.error).toHaveBeenCalledWith('详情不可用')
    expect(wrapper.getComponent(ReviewEditDialog).props('modelValue')).toBe(false)
  })
})
