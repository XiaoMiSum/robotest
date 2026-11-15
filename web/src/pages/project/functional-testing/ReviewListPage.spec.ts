// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ReviewListPage from './ReviewListPage.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import ReviewCreateDialog from '@/components/project/functional-testing/review/ReviewCreateDialog.vue'
import type { TestReviewListItem } from '@/types'

const mocks = vi.hoisted(() => ({ fetch: vi.fn(), create: vi.fn(), reopen: vi.fn(), delete: vi.fn(), push: vi.fn(), error: vi.fn(), success: vi.fn(), confirm: vi.fn() }))
vi.mock('@/services/project', () => ({ fetchReviews: mocks.fetch, createReview: mocks.create, reopenReview: mocks.reopen, deleteReview: mocks.delete }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('element-plus', async (original) => ({ ...await original<typeof import('element-plus')>(), ElMessage: { error: mocks.error, success: mocks.success }, ElMessageBox: { confirm: mocks.confirm } }))

const row: TestReviewListItem = { id: 'r1', title: '支付评审', status: 'rejected', initiator: { id: 'u1', name: '张明' }, participants: [], participantCount: 0, createdAt: '2026-11-15T08:00:00Z', totalAssociated: 10, reviewed: 8, passed: 6, progressPercent: 80, passRate: 60, requirementCount: 2 }
const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(ReviewListPage, { global: { stubs: { ReviewCreateDialog: true, ListPagination: true, Plus: true, Search: true }, directives: { loading: () => {} } } })
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

beforeEach(() => { vi.resetAllMocks(); mocks.fetch.mockResolvedValue({ list: [], total: 0 }); mocks.confirm.mockResolvedValue('confirm') })
afterEach(() => { wrappers.splice(0).forEach((wrapper) => wrapper.unmount()) })

describe('评审列表样板', () => {
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
    await button(wrapper, '重新加载').trigger('click')
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
    await button(wrapper, '重新加载').trigger('click')
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
    await button(wrapper, '重新发起').trigger('click')
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
})
