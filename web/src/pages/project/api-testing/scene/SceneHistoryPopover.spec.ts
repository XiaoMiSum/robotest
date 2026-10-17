// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import SceneHistoryPopover from './SceneHistoryPopover.vue'
import type { ApiExecutionHistoryItem } from '@/types'

function makeItem(overrides: Partial<ApiExecutionHistoryItem> = {}): ApiExecutionHistoryItem {
  return {
    id: 'exec-1',
    status: 'success',
    executionMode: 'scene',
    triggerType: 'manual',
    executedAt: '2026-10-17T02:20:30Z',
    durationMs: 1200,
    reportId: 'rpt-1',
    ...overrides,
  }
}

function mountPopover(props: Partial<Record<string, unknown>> = {}) {
  return mount(SceneHistoryPopover, {
    props: {
      history: [],
      total: 0,
      page: 1,
      loading: false,
      error: null,
      ...props,
    },
    global: { plugins: [ElementPlus] },
  })
}

/** 弹层内容经 el-popover teleport 到 body：点开引用按钮后再从 body 取面板 */
async function mountAndOpen(props: Partial<Record<string, unknown>> = {}) {
  const wrapper = mountPopover(props)
  await wrapper.find('button').trigger('click')
  await flushPromises()
  const panel = document.body.querySelector('.shp-panel')
  expect(panel).not.toBeNull()
  return { wrapper, panel: panel as HTMLElement }
}

describe('SceneHistoryPopover', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('引用按钮为「执行历史」且空历史给出「暂无执行记录」', async () => {
    const { wrapper, panel } = await mountAndOpen()
    expect(wrapper.find('button').text()).toContain('执行历史')
    expect(panel.querySelector('.shp-empty')?.textContent).toBe('暂无执行记录')
    expect(panel.querySelectorAll('.shp-row')).toHaveLength(0)
  })

  it('渲染历史行并把报告点击透传为 view-report', async () => {
    const { wrapper, panel } = await mountAndOpen({
      history: [makeItem(), makeItem({ id: 'exec-2', reportId: null })],
    })
    const rows = panel.querySelectorAll('.shp-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].textContent).toContain('success')

    const reportBtn = rows[0].querySelector('.shp-report') as HTMLElement
    expect(reportBtn).not.toBeNull()
    reportBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('view-report')?.[0]).toEqual(['rpt-1'])
    expect(rows[1].querySelector('.shp-report')).toBeNull()
  })

  it('出错时展示原因并以重试触发 load', async () => {
    const { wrapper, panel } = await mountAndOpen({ error: '网络异常' })
    expect(panel.querySelector('.shp-error')?.textContent).toContain('网络异常')
    const retry = panel.querySelector('.shp-error button') as HTMLElement
    retry.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('load')).toBeTruthy()
  })

  it('有历史总数时渲染分页', async () => {
    const { panel } = await mountAndOpen({ history: [makeItem()], total: 40 })
    expect(panel.querySelector('.shp-pager')).not.toBeNull()
  })
})
