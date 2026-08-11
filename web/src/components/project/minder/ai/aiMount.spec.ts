import { describe, expect, it } from 'vitest'
import type { AiGeneratedNode } from '@/types'
import type { Minder } from '../types'
import {
  buildPreviewTree,
  buildDocumentPreviewTree,
  filterCheckedTree,
  findNodeById,
  appendGeneratedTree,
  mountGeneratedNodes,
  type AiPreviewNode,
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
  it('按路径分配稳定 key 并映射字段（生成节点 aiGenerated=true）', () => {
    const preview = buildPreviewTree(sampleNodes)
    expect(preview[0].key).toBe('ai-0')
    expect(preview[0].children[1].key).toBe('ai-0-1')
    expect(preview[0].priority).toBe('P1')
    expect(preview[0].aiGenerated).toBe(true)
    expect(preview[1].children).toEqual([])
  })

  it('children 缺省视为空数组', () => {
    const preview = buildPreviewTree([{ type: 'normal', title: '分组' }])
    expect(preview[0].children).toEqual([])
  })
})

describe('buildDocumentPreviewTree 完整文档树预览（交互设计 2.2 纯预览约束）', () => {
  const docRoot = fakeNode({ id: 'root', text: '登录模块', type: 'normal' }, [
    fakeNode({ id: 'target', text: '密码登录', type: 'case', priority: 'P1' }, [
      fakeNode({ id: 'old-step', text: '输入密码', type: 'step' }),
    ]),
    fakeNode({ id: 'other', text: '其他模块', type: 'normal' }),
  ])

  it('生成节点以 AI 徽标树插入目标节点下，既有节点只读', () => {
    const tree = buildDocumentPreviewTree(docRoot, 'target', sampleNodes)
    expect(tree).not.toBeNull()
    const target = tree?.[0].children[0]
    expect(target?.key).toBe('target')
    expect(target?.aiGenerated).toBe(false)
    // 既有子节点保留 + 生成节点追加为末尾新子节点
    expect(target?.children[0]).toMatchObject({ key: 'old-step', aiGenerated: false })
    expect(target?.children[1]).toMatchObject({ key: 'ai-0', aiGenerated: true, priority: 'P1' })
    expect(target?.children[1].children[0]).toMatchObject({ key: 'ai-0-0', aiGenerated: true })
    expect(target?.children[2]).toMatchObject({ key: 'ai-1', aiGenerated: true })
    // 无关分支保持既有节点
    expect(tree?.[0].children[1]).toMatchObject({ key: 'other', aiGenerated: false })
  })

  it('目标节点已被协同删除：返回 null（调用方回退仅展示生成节点树）', () => {
    const tree = buildDocumentPreviewTree(docRoot, 'deleted-target', sampleNodes)
    expect(tree).toBeNull()
  })

  it('空根返回 null', () => {
    expect(buildDocumentPreviewTree(null, 'target', sampleNodes)).toBeNull()
  })
})

describe('filterCheckedTree 勾选过滤（4.2 取舍规则）', () => {
  const preview = buildPreviewTree(sampleNodes)

  /** 按 key 将 AI 节点置为未勾选（模拟脑图点击级联后的状态），返回新树不污染共享 fixture */
  function unselect(nodes: AiPreviewNode[], key: string): AiPreviewNode[] {
    return nodes.map((node) => {
      const next: AiPreviewNode = {
        ...node,
        aiSelected: node.key === key ? false : node.aiSelected,
      }
      next.children = unselect(node.children, key)
      return next
    })
  }

  it('全选保留完整树', () => {
    const result = filterCheckedTree(preview)
    expect(result).toHaveLength(2)
    expect(result[0].children).toHaveLength(3)
  })

  it('取消父节点则子孙一并排除（aiSelected 级联后整组剔除）', () => {
    const result = filterCheckedTree(unselect(preview, 'ai-0'))
    expect(result).toHaveLength(1)
    expect(result[0].title).toBe('密码错误提示')
  })

  it('取消 case 的单个子节点仅排除该节点', () => {
    const result = filterCheckedTree(unselect(preview, 'ai-0-1'))
    expect(result[0].children?.map((n) => n.type)).toEqual(['precondition', 'expected'])
  })

  it('空勾选返回空数组', () => {
    const allUnselected = preview.map((node) => {
      node.aiSelected = false
      node.children = unselect(node.children, '')
      return node
    })
    expect(filterCheckedTree(allUnselected)).toEqual([])
  })

  it('完整文档树预览：仅提取勾选的生成节点，既有节点不参与挂载', () => {
    const docRoot = fakeNode({ id: 'root', text: '登录模块' }, [
      fakeNode({ id: 'target', text: '密码登录', type: 'case' }),
    ])
    const fullTree = buildDocumentPreviewTree(docRoot, 'target', sampleNodes)
    expect(fullTree).not.toBeNull()
    // 既有节点（target）恒不输出，仅 AI 节点参与挂载
    const result = filterCheckedTree(fullTree ?? [])
    expect(result).toHaveLength(2)
    expect(result[0]).toMatchObject({ type: 'case', title: '邮箱登录成功', priority: 'P1' })
    expect(result[0].children).toHaveLength(3)
    // 取消父节点（ai-0）则其 AI 子孙一并排除
    const partial = filterCheckedTree(fullTree ? unselect(fullTree, 'ai-0') : [])
    expect(partial).toHaveLength(1)
    expect(partial[0].title).toBe('密码错误提示')
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
