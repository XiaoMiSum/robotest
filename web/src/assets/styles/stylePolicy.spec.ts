import { describe, expect, it } from 'vitest'
// @ts-expect-error Vitest runs this policy test in Node, while the app tsconfig intentionally omits Node types.
import { readFileSync } from 'node:fs'
// @ts-expect-error Vitest runs this policy test in Node, while the app tsconfig intentionally omits Node types.
import { fileURLToPath } from 'node:url'

interface StyleSource {
  path: string
  content: string
}

const styleSources: StyleSource[] = Object.entries(
  import.meta.glob('/src/**/*.{vue,scss,css}', {
    eager: true,
    query: '?raw',
    import: 'default',
  }),
).map(([path, content]) => ({
  path: path.replace(/^\/+/, ''),
  content: String(content),
}))

const indexSource = String(
  Object.values(
    import.meta.glob('/index.html', {
      eager: true,
      query: '?raw',
      import: 'default',
    }),
  )[0] ?? '',
)

const variablesSource = readFileSync(
  fileURLToPath(new URL('./variables.scss', import.meta.url)),
  'utf8',
)

const globalStyleExceptions: Record<string, string> = {
  'src/App.vue': '应用入口 reset、选区/焦点基线和 Vue 过渡类',
  'src/components/project/functional-testing/case/CaseSelector.vue':
    'Element Plus Dialog Teleport 根节点',
  'src/components/project/functional-testing/case/CaseMindMap.vue':
    'Element Plus 下拉项多根 Fragment 不透传 scopeId，按 popper-class 锚定下拉样式',
  'src/components/project/functional-testing/case/AiCaseGenerationPanel.vue':
    'Element Plus Dialog Teleport 根节点固定高度布局',
}

const teleportSelectorExceptions = new Set([
  'src/components/project/functional-testing/review/ReviewMindMap.vue',
])

function hasUnscopedVueStyle(content: string): boolean {
  return /<style(?![^>]*\bscoped\b)[^>]*>/i.test(content)
}

// 减少动效兜底是无障碍强制要求（UI-DS-08 / §5.3），组件 scoped 过渡特异性高于普通全局规则，
// 必须以强制优先级覆盖；仅放行 variables.scss 中已登记的 prefers-reduced-motion 块，其余一律禁止。
const motionReduceBlock = /@media\s*\(prefers-reduced-motion:\s*reduce\)\s*\{[\s\S]*?\n\}/g

function hasUnsanctionedImportant(path: string, content: string): boolean {
  const withoutComments = content.replace(/\/\*[\s\S]*?\*\//g, '')
  const scoped =
    path === 'src/assets/styles/variables.scss'
      ? withoutComments.replace(motionReduceBlock, '')
      : withoutComments
  return /!\s*important\b/i.test(scoped)
}

describe('CODE-008 web style policy', () => {
  it('应用入口为全局桥接层提供明确作用域标记', () => {
    expect(indexSource).toContain('<body data-robotest-theme>')
  })

  it('Element Plus 桥接层限定作用域且不使用强制优先级', () => {
    expect(variablesSource).toContain('body[data-robotest-theme] {')
    const withoutReduceBlock = variablesSource.replace(motionReduceBlock, '')
    expect(withoutReduceBlock).not.toMatch(/!\s*important\b/i)
  })

  it('一方样式源码不重新引入强制优先级声明（减少动效兜底块除外）', () => {
    const violations = styleSources
      .filter(({ path, content }) => hasUnsanctionedImportant(path, content))
      .map(({ path }) => path)

    expect(violations).toEqual([])
  })

  it('减少动效兜底仅出现在已登记的全局媒体查询块内', () => {
    const reduceBlocks = variablesSource.match(motionReduceBlock) ?? []
    expect(reduceBlocks.length).toBeGreaterThan(0)
    expect(reduceBlocks.join('\n')).toContain('prefers-reduced-motion: reduce')
  })

  it('无 scoped 样式只允许出现在已登记的入口或 Teleport 例外中', () => {
    const violations = styleSources
      .filter(({ path, content }) => path.endsWith('.vue') && hasUnscopedVueStyle(content))
      .map(({ path }) => path)
      .filter((path) => !(path in globalStyleExceptions))

    expect(violations).toEqual([])
  })

  it('每个全局例外都记录 CODE-008 和规范来源', () => {
    for (const path of Object.keys(globalStyleExceptions)) {
      const source = styleSources.find((candidate) => candidate.path === path)
      expect(source, `缺少登记的样式文件：${path}`).toBeDefined()
      expect(source?.content).toContain('CODE-008')
      expect(source?.content).toContain('docs/00-spec/10-engineering/01-frontend.md')
    }
  })

  it('Teleport 的 :global 选择器限定在已登记抽屉类名', () => {
    const violations = styleSources
      .filter(({ content }) => /:global\s*\(/.test(content))
      .map(({ path }) => path)
      .filter((path) => !teleportSelectorExceptions.has(path))

    expect(violations).toEqual([])

    const source = styleSources.find(({ path }) => path === [...teleportSelectorExceptions][0])
    expect(source?.content).toContain('.comment-drawer .el-drawer__body')
  })
})
