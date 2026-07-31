import { describe, expect, it } from 'vitest'
import type { AiGeneratedNode } from '@/types'
import type { Minder } from '../types'
import {
  buildPreviewTree,
  filterCheckedTree,
  findNodeById,
  appendGeneratedTree,
  mountGeneratedNodes,
  type MountTargetSource,
  type MinderLike,
} from './aiMount'

const sampleNodes: AiGeneratedNode[] = [
  {
    type: 'case',
    title: '邮箱登录成功',
    priority: 'P1',
    children: [
      { type: 'precondition', title: '用户已注册', children: [] },
      { type: 'step', title: '输入邮箱密码并提交', children: [] },
      { type: 'expected', title: '跳转首页', children: [] },
    ],
  },
  { type: 'case', title: '密码错误提示', priority: 'P2', children: [] },
]

describe('buildPreviewTree 预览键分配', () => {
  it('按路径分配稳定 key 并映射字段', () => {
    const preview = buildPreviewTree(sampleNodes)
    expect(preview[0].key).toBe('ai-0')
    expect(preview[0].children[1].key).toBe('ai-0-1')
    expect(preview[0].priority).toBe('P1')
    expect(preview[1].children).toEqual([])
  })

  it('children 缺省视为空数组', () => {
    const preview = buildPreviewTree([{ type: 'normal', title: '分组' }])
    expect(preview[0].children).toEqual([])
  })
})

describe('filterCheckedTree 勾选过滤（4.2 取舍规则）', () => {
  const preview = buildPreviewTree(sampleNodes)
  const allKeys = ['ai-0', 'ai-0-0', 'ai-0-1', 'ai-0-2', 'ai-1']

  it('全选保留完整树', () => {
    const result = filterCheckedTree(preview, new Set(allKeys))
    expect(result).toHaveLength(2)
    expect(result[0].children).toHaveLength(3)
  })

  it('取消父节点则子孙一并排除（即使子孙 key 仍在集合中）', () => {
    const keys = new Set(allKeys.filter((k) => k !== 'ai-0'))
    const result = filterCheckedTree(preview, keys)
    expect(result).toHaveLength(1)
    expect(result[0].title).toBe('密码错误提示')
  })

  it('取消 case 的单个子节点仅排除该节点', () => {
    const keys = new Set(allKeys.filter((k) => k !== 'ai-0-1'))
    const result = filterCheckedTree(preview, keys)
    expect(result[0].children?.map((n) => n.type)).toEqual(['precondition', 'expected'])
  })

  it('空勾选返回空数组', () => {
    expect(filterCheckedTree(preview, new Set())).toEqual([])
  })
})

// 结构化伪造节点：与 kity 节点同构（data + getChildren）
function fakeNode(data: Record<string, unknown>, children: MountTargetSource[] = []): MountTargetSource {
  return { data, getChildren: () => children }
}

describe('findNodeById 目标存在性校验', () => {
  const grandchild = fakeNode({ id: 'c1' })
  const root = fakeNode({ id: 'root' }, [fakeNode({ id: 'a' }, [grandchild])])

  it('命中深层节点', () => {
    expect(findNodeById(root, 'c1')).toBe(grandchild)
  })

  it('未命中返回 null', () => {
    expect(findNodeById(root, 'missing')).toBeNull()
    expect(findNodeById(null, 'root')).toBeNull()
  })
})

describe('appendGeneratedTree 挂载写入', () => {
  function fakeMinder(created: Record<string, unknown>[]): MinderLike {
    return {
      createNode(data) {
        created.push(data)
        return fakeNode(data)
      },
    }
  }

  it('所有新节点写入 aiGenerated=true 且保留类型/优先级', () => {
    const created: Record<string, unknown>[] = []
    const count = appendGeneratedTree(fakeMinder(created), fakeNode({ id: 't' }), sampleNodes)
    expect(count).toBe(5)
    expect(created).toHaveLength(5)
    expect(created.every((d) => d.aiGenerated === true)).toBe(true)
    expect(created[0]).toMatchObject({ text: '邮箱登录成功', type: 'case', priority: 'P1' })
    expect(created[1]).toMatchObject({ text: '用户已注册', type: 'precondition' })
    expect(created[1].priority).toBeUndefined()
  })

  it('case 缺省优先级补 P2（与手工标记联动规则一致）', () => {
    const created: Record<string, unknown>[] = []
    appendGeneratedTree(fakeMinder(created), fakeNode({ id: 't' }), [
      { type: 'case', title: '无优先级用例', children: [] },
    ])
    expect(created[0].priority).toBe('P2')
  })
})

describe('mountGeneratedNodes 挂载入口', () => {
  function fakeFullMinder(rootId: string) {
    const events: string[] = []
    const created: Record<string, unknown>[] = []
    const root = fakeNode({ id: rootId })
    const minder = {
      getRoot: () => root,
      createNode(data: Record<string, unknown>) {
        created.push(data)
        return fakeNode(data)
      },
      select: () => minder,
      refresh: () => minder,
      fire: (type: string) => {
        events.push(type)
        return minder
      },
    }
    return { minder: minder as unknown as Minder, events, created }
  }

  it('目标存在：创建节点并补发一次 contentchange（单撤销组）', () => {
    const { minder, events, created } = fakeFullMinder('target')
    const count = mountGeneratedNodes(minder, 'target', sampleNodes)
    expect(count).toBe(5)
    expect(created).toHaveLength(5)
    expect(events.filter((e) => e === 'contentchange')).toHaveLength(1)
  })

  it('目标节点已被协同删除：返回 null 且不写入任何节点', () => {
    const { minder, events, created } = fakeFullMinder('other')
    expect(mountGeneratedNodes(minder, 'target', sampleNodes)).toBeNull()
    expect(created).toHaveLength(0)
    expect(events).toHaveLength(0)
  })
})
