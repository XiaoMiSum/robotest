// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import PlanListPage from './PlanListPage.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import PlanCreateDialog from '@/components/project/functional-testing/plan/PlanCreateDialog.vue'
import type { TestPlanListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetch: vi.fn(),
  create: vi.fn(),
  complete: vi.fn(),
  resume: vi.fn(),
  delete: vi.fn(),
  push: vi.fn(),
  error: vi.fn(),
  success: vi.fn(),
  confirm: vi.fn(),
  hasPermission: vi.fn(),
}))
vi.mock('@/services/project', () => ({
  fetchPlans: mocks.fetch,
  createPlan: mocks.create,
  completePlan: mocks.complete,
  resumePlan: mocks.resume,
  deletePlan: mocks.delete,
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ hasPermission: mocks.hasPermission }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: { error: mocks.error, success: mocks.success },
  ElMessageBox: { confirm: mocks.confirm },
}))

const row: TestPlanListItem = {
  id: 'p1',
  name: '支付计划',
  status: 'in_progress',
  executor: { id: 'u1', name: '张明' },
  environment: 'staging',
  startTime: null,
  endTime: null,
  createdAt: '2026-11-15T08:00:00Z',
  totalAssociated: 10,
  passed: 6,
  progressPercent: 80,
  passRate: 60,
  requirementCount: 2,
}
const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(PlanListPage, {
    global: {
      stubs: { PlanCreateDialog: true, ListPagination: true, Plus: true, Search: true },
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
  let resolve!: (value: { list: TestPlanListItem[]; total: number }) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<{ list: TestPlanListItem[]; total: number }>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
beforeEach(() => {
  vi.resetAllMocks()
  mocks.fetch.mockResolvedValue({ list: [], total: 0 })
  mocks.confirm.mockResolvedValue('confirm')
  mocks.hasPermission.mockReturnValue(true)
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
})

describe('计划列表样板', () => {
  it('首次失败可重试，普通空态可以新建计划', async () => {
    mocks.fetch.mockRejectedValueOnce(new Error('网络中断'))
    const wrapper = render()
    await nextTick()
    expect(wrapper.text()).not.toContain('暂无计划')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('网络中断')
    expect(mocks.error).toHaveBeenCalledWith('网络中断')
    await button(wrapper, '重新加载').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无计划')
    await wrapper.get('.el-empty button').trigger('click')
    expect(wrapper.getComponent(PlanCreateDialog).props('modelValue')).toBe(true)
  })

  it('筛选从首页查询，无结果可重置', async () => {
    const wrapper = render()
    await flushPromises()
    await page(wrapper, 3)
    await wrapper.get('input[aria-label="计划名称"]').setValue(' 支付 ')
    await button(wrapper, '查询').trigger('click')
    await flushPromises()
    expect(mocks.fetch.mock.lastCall?.[0]).toMatchObject({ keyword: '支付', pageNo: 1 })
    expect(wrapper.text()).toContain('没有符合条件的计划')
    await button(wrapper, '重置筛选').trigger('click')
    await flushPromises()
    expect(mocks.fetch.mock.lastCall?.[0]).toMatchObject({
      keyword: undefined,
      status: undefined,
      pageNo: 1,
    })
  })

  it('刷新失败保留行与总数，按当前页重试', async () => {
    mocks.fetch.mockResolvedValueOnce({ list: [row], total: 60 })
    const wrapper = render()
    await flushPromises()
    mocks.fetch.mockRejectedValueOnce(new Error('翻页失败'))
    await page(wrapper, 2)
    expect(wrapper.text()).toContain('支付计划')
    expect(wrapper.getComponent(ListPagination).props('total')).toBe(60)
    expect(wrapper.get('[role="alert"]').text()).toContain('上次加载的数据')
    await button(wrapper, '重新加载').trigger('click')
    await flushPromises()
    expect(mocks.fetch.mock.lastCall?.[0]).toMatchObject({ pageNo: 2, pageSize: 20 })
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
    expect(wrapper.text()).toContain('支付计划')
    expect(wrapper.getComponent(ListPagination).props('total')).toBe(1)
  })

  it('旧错误不结束新请求，卸载后不弹错误', async () => {
    const older = deferred(),
      latest = deferred()
    mocks.fetch.mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise)
    const wrapper = render()
    await button(wrapper, '查询').trigger('click')
    older.reject(new Error('过期错误'))
    await flushPromises()
    expect(wrapper.get('[aria-label="计划列表"]').attributes('aria-busy')).toBe('true')
    expect(mocks.error).not.toHaveBeenCalled()
    wrapper.unmount()
    latest.reject(new Error('离开后的错误'))
    await flushPromises()
    expect(mocks.error).not.toHaveBeenCalled()
  })

  it.each([
    ['new', 'plan:execute', true, false],
    ['in_progress', 'plan:execute', true, false],
    ['blocked', 'plan:block', false, true],
    ['blocked', 'plan:execute', false, false],
    ['in_progress', 'plan:block', false, false],
    ['completed', 'plan:execute', false, false],
    ['closed', 'plan:block', false, false],
    ['new', '', false, false],
  ] as const)('%s 状态与 %s 权限共同约束操作', async (status, permission, complete, resume) => {
    mocks.hasPermission.mockImplementation((value: string) => value === permission)
    mocks.fetch.mockResolvedValueOnce({ list: [{ ...row, status }], total: 1 })
    const wrapper = render()
    await flushPromises()
    const labels = wrapper.findAll('.plan-table button').map((item) => item.text())
    expect(labels.includes('完成')).toBe(complete)
    expect(labels.includes('恢复')).toBe(resume)
    expect(labels).toContain('删除')
  })

  it('完成、恢复和删除经确认后调用正确服务并刷新，取消不提交', async () => {
    mocks.fetch.mockResolvedValue({
      list: [row, { ...row, id: 'p2', status: 'blocked' }],
      total: 2,
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.list-title-link').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/workspace/projects/plans/p1')
    await button(wrapper, '完成').trigger('click')
    await flushPromises()
    expect(mocks.complete).toHaveBeenCalledWith('p1')
    await button(wrapper, '恢复').trigger('click')
    await flushPromises()
    expect(mocks.resume).toHaveBeenCalledWith('p2')
    mocks.confirm.mockRejectedValueOnce('cancel')
    await button(wrapper, '删除').trigger('click')
    await flushPromises()
    expect(mocks.delete).not.toHaveBeenCalled()
    await button(wrapper, '删除').trigger('click')
    await flushPromises()
    expect(mocks.delete).toHaveBeenCalledWith('p1')
    expect(mocks.fetch).toHaveBeenCalledTimes(4)
  })
})
