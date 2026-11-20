import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCaseRequirementLink } from './useCaseRequirementLink'
import type { Minder } from '@/minder/types'

const mocks = vi.hoisted(() => ({ getRequirement: vi.fn(), error: vi.fn() }))
vi.mock('@/services/project', () => ({ getRequirement: mocks.getRequirement }))
vi.mock('element-plus', () => ({ ElMessage: { error: mocks.error } }))

interface FakeNode {
  data: Record<string, unknown>
  setData: (key: string, value: unknown) => FakeNode
}

function makeNode(id: string, data: Record<string, unknown> = {}): FakeNode {
  const node: FakeNode = {
    data: { id, ...data },
    setData(key: string, value: unknown) {
      node.data[key] = value
      return node
    },
  }
  return node
}

function setup(nodes: FakeNode[], selectedRef: { value: string }) {
  const root = {
    traverse(fn: (node: unknown) => void) {
      nodes.forEach((node) => fn(node))
    },
  }
  const refresh = vi.fn()
  const fire = vi.fn()
  const flush = vi.fn()
  const minder = { getRoot: () => root, refresh, fire } as unknown as Minder
  const link = useCaseRequirementLink({
    caseId: () => selectedRef.value,
    getMinder: () => minder,
    flushPersistence: flush,
  })
  return { link, refresh, fire, flush, minder }
}

describe('useCaseRequirementLink', () => {
  beforeEach(() => vi.resetAllMocks())

  it('保存打开时的用例，避免选择变化后覆盖另一个用例', async () => {
    const caseA = makeNode('case-a', { requirementId: 'r1' })
    const caseB = makeNode('case-b', { requirementId: 'r2' })
    const selected = { value: 'case-a' }
    const { link, flush } = setup([caseA, caseB], selected)
    mocks.getRequirement.mockImplementation((id: string) =>
      Promise.resolve({ id, code: `${id}-code`, title: '需求' }),
    )

    await link.open()
    expect(mocks.getRequirement).toHaveBeenCalledWith('r1')
    expect(link.picks()).toHaveLength(1)

    selected.value = 'case-b'
    link.confirm([])
    expect(caseA.data['requirementId']).toBeNull()
    expect(caseB.data['requirementId']).toBe('r2')
    expect(flush).toHaveBeenCalledTimes(1)
    expect(link.visible.value).toBe(false)
  })

  it('无关联时直接打开，确认后写入节点并触发持久化', () => {
    const caseA = makeNode('case-a')
    const { link, refresh, fire, flush } = setup([caseA], { value: 'case-a' })

    link.open()
    expect(link.visible.value).toBe(true)
    expect(mocks.getRequirement).not.toHaveBeenCalled()

    link.confirm(['r1'])
    expect(caseA.data['requirementId']).toBe('r1')
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(fire).toHaveBeenCalledWith('contentchange')
    expect(flush).toHaveBeenCalledTimes(1)
    expect(link.visible.value).toBe(false)
    expect(link.saving.value).toBe(false)
  })

  it('关闭后忽略未完成的加载', async () => {
    let resolve!: (value: { id: string; code: string; title: string }) => void
    mocks.getRequirement.mockReturnValue(new Promise((done) => { resolve = done }))
    const { link } = setup([makeNode('case-a', { requirementId: 'r1' })], { value: 'case-a' })

    const pending = link.open()
    link.close()
    resolve({ id: 'r1', code: 'REQ-1', title: '需求' })
    await pending
    expect(link.visible.value).toBe(false)
    expect(link.picks()).toHaveLength(0)
  })

  it('选中变化后忽略过期的加载结果', async () => {
    let resolve!: (value: { id: string; code: string; title: string }) => void
    mocks.getRequirement.mockReturnValue(new Promise((done) => { resolve = done }))
    const selected = { value: 'case-a' }
    const { link } = setup([
      makeNode('case-a', { requirementId: 'r1' }),
      makeNode('case-b', { requirementId: 'r2' }),
    ], selected)

    const pending = link.open()
    selected.value = 'case-b'
    resolve({ id: 'r1', code: 'REQ-1', title: '需求' })
    await pending
    expect(link.picks()).toHaveLength(0)
  })

  it('加载失败显示错误且不打开弹窗', async () => {
    mocks.getRequirement.mockRejectedValue(new Error('加载失败'))
    const { link } = setup([makeNode('case-a', { requirementId: 'r1' })], { value: 'case-a' })

    await link.open()
    expect(mocks.error).toHaveBeenCalledWith('加载失败')
    expect(link.visible.value).toBe(false)
  })
})
