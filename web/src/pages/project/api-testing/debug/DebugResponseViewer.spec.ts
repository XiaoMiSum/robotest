// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import DebugResponseViewer from './DebugResponseViewer.vue'
import type { ApiDebugExecuteResp } from '@/types'

let wrapper: VueWrapper | null = null

function mountViewer(response: ApiDebugExecuteResp | null): VueWrapper {
  wrapper = mount(DebugResponseViewer, {
    props: { response },
    global: {
      plugins: [ElementPlus],
      components: { ...ElementPlusIcons },
    },
  })
  return wrapper
}

function makeResponse(overrides?: Partial<ApiDebugExecuteResp>): ApiDebugExecuteResp {
  return {
    debugRecordId: 'rec-1',
    status: 'success',
    responseStatus: 200,
    responseHeaders: { 'Content-Type': 'application/json' },
    responseBody: { ok: true },
    durationMs: 120,
    size: 256,
    ...overrides,
  }
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('DebugResponseViewer 错误展示', () => {
  it('执行失败时在响应区渲染横幅并完整展示后端错误信息', () => {
    const wrapper = mountViewer(
      makeResponse({ status: 'error', errorMessage: '500 服务器内部错误' }),
    )
    const banner = wrapper.find('.resp-view__error-banner')
    expect(banner.exists()).toBe(true)
    expect(banner.attributes('role')).toBe('alert')
    expect(banner.text()).toContain('500 服务器内部错误')
    expect(wrapper.find('.resp-view__status-badge').text()).toBe('ERROR')
  })

  it('存在 errorMessage 时横幅展示，无论状态为 failed 还是 error', () => {
    const wrapper = mountViewer(makeResponse({ status: 'failed', errorMessage: '连接超时' }))
    expect(wrapper.find('.resp-view__error-banner').text()).toContain('连接超时')
  })

  it('无错误信息时不渲染横幅', () => {
    const wrapper = mountViewer(makeResponse())
    expect(wrapper.find('.resp-view__error-banner').exists()).toBe(false)
  })

  it('未执行时展示空态占位', () => {
    const wrapper = mountViewer(null)
    expect(wrapper.find('.resp-view__empty').exists()).toBe(true)
    expect(wrapper.find('.resp-view__error-banner').exists()).toBe(false)
  })
})
