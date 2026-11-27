// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import PlanListPage from './PlanListPage.vue'
import ListPagination from '@/components/common/ListPagination.vue'
import PlanCreateDialog from '@/components/project/functional-testing/plan/PlanCreateDialog.vue'
import PlanEditDialog from '@/components/project/functional-testing/plan/PlanEditDialog.vue'
import type { TestPlanDetail, TestPlanListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetch: vi.fn(),
  create: vi.fn(),
  complete: vi.fn(),
  resume: vi.fn(),
  delete: vi.fn(),
  detail: vi.fn(),
  update: vi.fn(),
  push: vi.fn(),
  error: vi.fn(),
  success: vi.fn(),
  confirm: vi.fn(),
}))
vi.mock('@/services/project', () => ({
  fetchPlans: mocks.fetch,
  createPlan: mocks.create,
  completePlan: mocks.complete,
  resumePlan: mocks.resume,
  deletePlan: mocks.delete,
  getPlanDetail: mocks.detail,
  updatePlan: mocks.update,
}))
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
const detail: TestPlanDetail = {
  id: 'p1', name: '支付计划', description: null, status: 'in_progress',
  environment: 'staging', startTime: null, endTime: null,
  executor: { id: 'u1', name: '张明' },
  requirements: [], requirementIds: ['req-1'],
  createdAt: '2026-11-15T08:00:00Z',
}
const editPayload = {
  name: '支付计划（调整）',
  description: '',
  executorId: 'u1',
  startTime: null,
  endTime: null,
  environment: 'staging',
  requirementIds: ['req-2'],
}
const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(PlanListPage, {
    global: {
      stubs: {
        PlanCreateDialog: true,
        PlanEditDialog: true,
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
async function openMore(wrapper: ReturnType<typeof mount>, index = 0) {
  const triggers = wrapper.findAll('.row-actions__more')
  const trigger = triggers[index]
  if (!trigger) throw new Error(`找不到第 ${index + 1} 个更多按钮`)
  await trigger.trigger('click')
  await flushPromises()
}
function menuItem(text: string) {
  const found = Array.from(document.querySelectorAll('.el-dropdown-menu__item')).find(
    (item) => item.textContent?.trim() === text,
  )
  if (!found) throw new Error(`找不到菜单项 ${text}`)
  return found as HTMLElement
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
  mocks.detail.mockResolvedValue(detail)
  mocks.update.mockResolvedValue(detail)
  mocks.confirm.mockResolvedValue('confirm')
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  document.querySelectorAll('.el-popper').forEach((el) => el.remove())
})

describe('计划列表样板', () => {
  it('列设置入口置于操作列表头右侧，工具栏不再展示列设置', async () => {
    const wrapper = render()
    await flushPromises()

    const settings = wrapper.findAll('.col-setting')
    expect(settings).toHaveLength(1)
    expect(settings[0].attributes('data-icon-only')).toBe('true')
    expect(wrapper.find('.plan-table__action-header .col-setting').exists()).toBe(true)
  })

  it('首次失败可重试，普通空态可以新建计划', async () => {
    mocks.fetch.mockRejectedValueOnce(new Error('网络中断'))
    const wrapper = render()
    await nextTick()
    expect(wrapper.text()).not.toContain('暂无计划')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('网络中断')
    expect(mocks.error).toHaveBeenCalledWith('网络中断')
    await button(wrapper, '刷新').trigger('click')
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
    await button(wrapper, '刷新').trigger('click')
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
    ['new', true, false],
    ['in_progress', true, false],
    ['blocked', false, true],
    ['completed', false, false],
    ['closed', false, false],
  ] as const)('%s 状态约束完成与恢复操作', async (status, complete, resume) => {
    mocks.fetch.mockResolvedValueOnce({ list: [{ ...row, status }], total: 1 })
    const wrapper = render()
    await flushPromises()
    const labels = wrapper.findAll('.plan-table button').map((item) => item.text())
    expect(labels.includes('完成')).toBe(complete)
    expect(labels.includes('恢复')).toBe(resume)
    if (!labels.includes('删除')) {
      await openMore(wrapper)
      expect(
        Array.from(document.querySelectorAll('.el-dropdown-menu__item'), (item) => item.textContent?.trim()),
      ).toContain('删除')
    }
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
    await openMore(wrapper)
    menuItem('删除').click()
    await flushPromises()
    expect(mocks.delete).not.toHaveBeenCalled()
    await openMore(wrapper)
    menuItem('删除').click()
    await flushPromises()
    expect(mocks.delete).toHaveBeenCalledWith('p1')
    expect(mocks.fetch).toHaveBeenCalledTimes(4)
  })

  it('编辑入口仅随计划状态显隐', async () => {
    mocks.fetch.mockResolvedValue({ list: [{ ...row, status: 'new' }], total: 1 })
    const active = render()
    await flushPromises()
    expect(active.findAll('.plan-table button').some((item) => item.text() === '编辑')).toBe(true)

    mocks.fetch.mockResolvedValue({ list: [{ ...row, status: 'completed' }], total: 1 })
    const finished = render()
    await flushPromises()
    expect(finished.findAll('.plan-table button').some((item) => item.text() === '编辑')).toBe(false)
  })

  async function openEdit(wrapper: ReturnType<typeof mount>) {
    await button(wrapper, '编辑').trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(PlanEditDialog).props('modelValue')).toBe(true)
    return wrapper.getComponent(PlanEditDialog)
  }

  it('关联需求变化确认后以 replan=true 保存并提示后台重新规划，不轮询不刷新', async () => {
    mocks.fetch.mockResolvedValue({ list: [row], total: 1 })
    const wrapper = render()
    await flushPromises()
    const dialog = await openEdit(wrapper)
    dialog.vm.$emit('submit', editPayload)
    await flushPromises()
    expect(mocks.confirm).toHaveBeenCalledWith('检测到关联需求发生变化，是否重新规划测试用例？', '关联需求变化', { type: 'warning' })
    expect(mocks.update).toHaveBeenCalledWith('p1', { ...editPayload, replan: true })
    expect(mocks.success).toHaveBeenCalledWith('已保存，正在后台重新规划测试用例')
    expect(mocks.fetch).toHaveBeenCalledTimes(1)
    expect(wrapper.getComponent(PlanEditDialog).props('modelValue')).toBe(false)
  })

  it('取消重新规划仅保存，提示已保存并刷新列表', async () => {
    mocks.fetch.mockResolvedValue({ list: [row], total: 1 })
    mocks.confirm.mockRejectedValueOnce('cancel')
    const wrapper = render()
    await flushPromises()
    const dialog = await openEdit(wrapper)
    dialog.vm.$emit('submit', editPayload)
    await flushPromises()
    expect(mocks.update).toHaveBeenCalledWith('p1', { ...editPayload, replan: false })
    expect(mocks.success).toHaveBeenCalledWith('已保存')
    expect(mocks.success).not.toHaveBeenCalledWith('已保存，正在后台重新规划测试用例')
    expect(mocks.fetch).toHaveBeenCalledTimes(2)
  })

  it('仅调整关联需求顺序不弹确认且不重新规划', async () => {
    mocks.fetch.mockResolvedValue({ list: [row], total: 1 })
    mocks.detail.mockResolvedValue({ ...detail, requirementIds: ['req-1', 'req-2'] })
    const wrapper = render()
    await flushPromises()
    const dialog = await openEdit(wrapper)
    dialog.vm.$emit('submit', { ...editPayload, requirementIds: ['req-2', 'req-1'] })
    await flushPromises()
    expect(mocks.confirm).not.toHaveBeenCalled()
    expect(mocks.update).toHaveBeenCalledWith('p1', expect.objectContaining({ replan: false }))
  })
})
