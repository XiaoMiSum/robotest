import { describe, expect, it } from 'vitest'
import type { AiMissingPoint, TestCaseModule } from '@/types'
import {
  buildMissingPointText,
  collectDocumentOptions,
  pickPreselectDocument,
} from './missingPoints'

const tree: TestCaseModule[] = [
  {
    id: 'd1',
    parentId: null,
    type: 'directory',
    name: '登录',
    sortOrder: 1,
    createdAt: '',
    children: [
      {
        id: 'd1-1',
        parentId: 'd1',
        type: 'document',
        name: '邮箱登录',
        sortOrder: 1,
        createdAt: '',
        children: [],
      },
      {
        id: 'd1-2',
        parentId: 'd1',
        type: 'directory',
        name: '第三方',
        sortOrder: 2,
        createdAt: '',
        children: [
          {
            id: 'd1-2-1',
            parentId: 'd1-2',
            type: 'document',
            name: '微信登录',
            sortOrder: 1,
            createdAt: '',
            children: [],
          },
        ],
      },
    ],
  },
  {
    id: 'd2',
    parentId: null,
    type: 'document',
    name: '找回密码',
    sortOrder: 2,
    createdAt: '',
    children: [],
  },
]

describe('buildMissingPointText 转用例生成文本拼接', () => {
  it('单条拼接为「序号. 标题 + 说明」', () => {
    const text = buildMissingPointText([
      { title: '验证码过期', description: '超时后提交应提示重发', suggestedModulePath: '登录/邮箱登录', relatedCaseTitles: [] },
    ])
    expect(text).toBe('1. 验证码过期\n说明：超时后提交应提示重发')
  })

  it('多条按序号递增并以空行分隔', () => {
    const text = buildMissingPointText([
      { title: 'A', description: 'a', suggestedModulePath: null, relatedCaseTitles: [] },
      { title: 'B', description: 'b', suggestedModulePath: null, relatedCaseTitles: [] },
    ])
    expect(text).toBe('1. A\n说明：a\n\n2. B\n说明：b')
  })
})

describe('collectDocumentOptions 模块树展开', () => {
  it('目录/文档扁平化为完整路径清单', () => {
    const options = collectDocumentOptions(tree)
    expect(options).toEqual([
      { id: 'd1-1', name: '邮箱登录', path: '登录/邮箱登录' },
      { id: 'd1-2-1', name: '微信登录', path: '登录/第三方/微信登录' },
      { id: 'd2', name: '找回密码', path: '找回密码' },
    ])
  })

  it('空树返回空数组', () => {
    expect(collectDocumentOptions([])).toEqual([])
  })
})

describe('pickPreselectDocument 目标文档预选', () => {
  const points: AiMissingPoint[] = [
    { title: 'P1', description: '', suggestedModulePath: '登录/邮箱登录', relatedCaseTitles: [] },
    { title: 'P2', description: '', suggestedModulePath: '登录/邮箱登录', relatedCaseTitles: [] },
    { title: 'P3', description: '', suggestedModulePath: '找回密码', relatedCaseTitles: [] },
  ]

  it('预选出现次数最多的建议模块对应文档', () => {
    expect(pickPreselectDocument(collectDocumentOptions(tree), points)).toBe('d1-1')
  })

  it('路径匹配不到现有文档的点不参与统计', () => {
    const partial = [
      { title: 'X', description: '', suggestedModulePath: '登录/邮箱登录', relatedCaseTitles: [] },
      { title: 'Y', description: '', suggestedModulePath: '不存在/路径', relatedCaseTitles: [] },
      { title: 'Z', description: '', suggestedModulePath: null, relatedCaseTitles: [] },
    ]
    expect(pickPreselectDocument(collectDocumentOptions(tree), partial)).toBe('d1-1')
  })

  it('无匹配路径时不预选（返回空串）', () => {
    const none = [
      { title: 'X', description: '', suggestedModulePath: '不存在/路径', relatedCaseTitles: [] },
    ]
    expect(pickPreselectDocument(collectDocumentOptions(tree), none)).toBe('')
  })
})
