// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { RequirementDetail } from '@/types'
import { useRequirementDetail } from './useRequirementDetail'

const mocks = vi.hoisted(() => ({
  getRequirement: vi.fn<() => Promise<RequirementDetail>>(),
  ElMessage: { error: vi.fn() },
}))

vi.mock('element-plus', () => ({ ElMessage: mocks.ElMessage }))
vi.mock('@/services/project', () => ({ getRequirement: mocks.getRequirement }))

function makeDetail(content: string): RequirementDetail {
  return {
    id: 'r1',
    title: '登录改版需求',
    content,
    sourceUrl: null,
    status: 'active',
    createdBy: 'u1',
    creatorName: null,
    updatedBy: 'u1',
    createdAt: '2026-07-30T00:00:00Z',
    updatedAt: '2026-07-30T00:00:00Z',
    aiGenerated: false,
  }
}

function deferred<T>(): { promise: Promise<T>; resolve: (v: T) => void; reject: (e: unknown) => void } {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((r, j) => {
    resolve = r
    reject = j
  })
  return { promise, resolve, reject }
}

function flush(): Promise<void> {
  return new Promise((r) => setTimeout(r, 0))
}

// 气泡的关闭手势挂在 window 上，每个用例收尾统一解绑，避免泄漏到下一个用例
let instances: { close: () => void }[] = []

function setup() {
  const api = useRequirementDetail()
  instances.push(api)
  return api
}

describe('useRequirementDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getRequirement.mockResolvedValue(makeDetail('# 正文'))
  })

  afterEach(() => {
    for (const api of instances) api.close()
    instances = []
    document.body.innerHTML = ''
  })

  describe('打开与切换', () => {
    it('首次点击 [明细] 打开并加载正文', async () => {
      const { detailId, content, toggle } = setup()
      toggle('r1')
      expect(detailId.value).toBe('r1')
      expect(mocks.getRequirement).toHaveBeenCalledWith('r1')
      await flush()
      expect(content.value).toBe('# 正文')
    })

    it('再次点击同一 [明细] 收起', async () => {
      const { detailId, toggle } = setup()
      toggle('r1')
      await flush()
      toggle('r1')
      expect(detailId.value).toBe('')
    })

    it('点击另一条切换内容', async () => {
      const { detailId, content, toggle } = setup()
      toggle('r1')
      await flush()
      mocks.getRequirement.mockResolvedValue(makeDetail('第二条正文'))
      toggle('r2')
      expect(detailId.value).toBe('r2')
      await flush()
      expect(content.value).toBe('第二条正文')
    })

    it('打开时清空上一条正文', async () => {
      const { content, toggle } = setup()
      toggle('r1')
      await flush()
      toggle('r1')
      mocks.getRequirement.mockReturnValue(new Promise(() => {}))
      toggle('r2')
      expect(content.value).toBe('')
    })

    it('迟到的响应被丢弃', async () => {
      const first = deferred<RequirementDetail>()
      const { detailId, content, toggle } = setup()
      mocks.getRequirement.mockReturnValueOnce(first.promise)
      toggle('r1')
      mocks.getRequirement.mockResolvedValue(makeDetail('第二条正文'))
      toggle('r2')
      await flush()
      first.resolve(makeDetail('第一条正文'))
      await flush()
      expect(detailId.value).toBe('r2')
      expect(content.value).toBe('第二条正文')
    })
  })

  describe('加载失败', () => {
    it('Error 异常提示原始消息', async () => {
      const { detailId, content, toggle } = setup()
      mocks.getRequirement.mockRejectedValue(new Error('接口 500'))
      toggle('r1')
      await flush()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('接口 500')
      expect(detailId.value).toBe('r1')
      expect(content.value).toBe('')
    })

    it('非 Error 异常提示通用消息', async () => {
      const { toggle } = setup()
      mocks.getRequirement.mockRejectedValue('boom')
      toggle('r1')
      await flush()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('明细加载失败')
    })

    it('切换后的失败不影响新条目展示', async () => {
      const first = deferred<RequirementDetail>()
      const { content, toggle } = setup()
      mocks.getRequirement.mockReturnValueOnce(first.promise)
      toggle('r1')
      mocks.getRequirement.mockResolvedValue(makeDetail('第二条正文'))
      toggle('r2')
      await flush()
      first.reject(new Error('已切走的请求失败'))
      await flush()
      expect(mocks.ElMessage.error).not.toHaveBeenCalled()
      expect(content.value).toBe('第二条正文')
    })
  })

  describe('关闭手势（52 §1.2 四种关闭方式）', () => {
    it('点击气泡外空白关闭', async () => {
      const { detailId, toggle } = setup()
      toggle('r1')
      await flush()
      const outside = document.createElement('div')
      document.body.append(outside)
      outside.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      expect(detailId.value).toBe('')
    })

    it('点击气泡内不关闭', async () => {
      const { detailId, toggle } = setup()
      toggle('r1')
      await flush()
      const pop = document.createElement('div')
      pop.className = 'req-detail-pop'
      document.body.append(pop)
      pop.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      expect(detailId.value).toBe('r1')
    })

    it('点击 [明细] 按钮不被外部关闭手势抢先', async () => {
      const { detailId, toggle } = setup()
      toggle('r1')
      await flush()
      const button = document.createElement('button')
      button.setAttribute('data-req-detail', 'true')
      document.body.append(button)
      // capture 关闭手势必须放行，否则按钮的切换会立即重新打开，表现为关不掉
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      expect(detailId.value).toBe('r1')
      toggle('r1')
      expect(detailId.value).toBe('')
    })

    it('ESC 关闭并阻断冒泡，避免连带关闭选取器', async () => {
      const { detailId, toggle } = setup()
      toggle('r1')
      await flush()
      const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
      const stop = vi.spyOn(event, 'stopPropagation')
      window.dispatchEvent(event)
      expect(detailId.value).toBe('')
      expect(stop).toHaveBeenCalled()
    })

    it('非 ESC 按键不关闭', async () => {
      const { detailId, toggle } = setup()
      toggle('r1')
      await flush()
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
      expect(detailId.value).toBe('r1')
    })

    it('未打开时不注册手势', () => {
      const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
      const stop = vi.spyOn(event, 'stopPropagation')
      window.dispatchEvent(event)
      expect(stop).not.toHaveBeenCalled()
    })
  })
})
