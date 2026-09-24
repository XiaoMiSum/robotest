import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { Linter } from 'eslint'
import { describe, expect, it } from 'vitest'
import rule from './no-restricted-layer-imports.mjs'
import { layerImportBaseline } from './layer-import-baseline.mjs'

const projectRoot = fileURLToPath(new URL('..', import.meta.url))
const linter = new Linter()

function verify(code, filename, baseline = []) {
  return linter.verify(
    code,
    [
      {
        files: ['**/*.{ts,tsx,vue}'],
        languageOptions: {
          ecmaVersion: 2022,
          sourceType: 'module',
        },
        plugins: {
          architecture: {
            rules: {
              'no-restricted-layer-imports': rule,
            },
          },
        },
        rules: {
          'architecture/no-restricted-layer-imports': ['error', { baseline }],
        },
      },
    ],
    { filename: path.resolve(projectRoot, filename) },
  )
}

function architectureErrors(messages) {
  return messages.filter((message) => message.ruleId === 'architecture/no-restricted-layer-imports')
}

describe('no-restricted-layer-imports', () => {
  it.each([
    ["import service from '@/services/workspace'", 'src/components/WorkspaceCard.vue'],
    [
      "import page from '@/pages/workspace/ProjectListPage.vue'",
      'src/components/WorkspaceCard.vue',
    ],
    [
      "import component from '../../components/workspace/ProjectCard.vue'",
      'src/composables/workspace/useProjectCard.ts',
    ],
    [
      "import page from '../../pages/workspace/ProjectListPage.vue'",
      'src/composables/workspace/useProjectPage.ts',
    ],
    ["import store from '@/stores/auth'", 'src/services/workspace.ts'],
    ["import composable from '@/composables/workspace/useProjectListPage'", 'src/stores/nav.ts'],
    ["import service from '@/services/workspace'", 'src/utils/workspaceRole.ts'],
  ])('拒绝不合规层级导入：%s', (code, filename) => {
    expect(architectureErrors(verify(code, filename))).toHaveLength(1)
  })

  it.each([
    [
      "import component from '@/components/common/MarkdownView.vue'",
      'src/pages/workspace/WorkspaceInfoPage.vue',
    ],
    [
      "import composable from '@/composables/workspace/useWorkspaceListPage'",
      'src/components/workspace/WorkspaceListCard.vue',
    ],
    [
      "import service from '@/services/workspace'",
      'src/composables/workspace/useWorkspaceListPage.ts',
    ],
    ["import service from './admin'", 'src/services/workspace.ts'],
    ["import store from './nav'", 'src/stores/nav.ts'],
    ["import type from '@/types/workspace'", 'src/utils/workspaceRole.ts'],
    ["import request from 'axios'", 'src/services/index.ts'],
  ])('允许同层或向下依赖：%s', (code, filename) => {
    expect(architectureErrors(verify(code, filename))).toEqual([])
  })

  it('检查 re-export 与静态动态 import', () => {
    expect(
      architectureErrors(verify("export { value } from '@/stores/auth'", 'src/services/auth.ts')),
    ).toHaveLength(1)
    expect(
      architectureErrors(
        verify("void import('@/pages/admin/DashboardPage.vue')", 'src/components/Dashboard.vue'),
      ),
    ).toHaveLength(1)
  })

  it('基线只放行精确的文件和导入源', () => {
    const [entry] = layerImportBaseline
    const baselineCode = `import '${entry.importSource}'`

    expect(architectureErrors(verify(baselineCode, entry.file))).toHaveLength(1)
    expect(architectureErrors(verify(baselineCode, entry.file, layerImportBaseline))).toEqual([])
    expect(
      architectureErrors(
        verify("import '@/components/another/Unexpected'", entry.file, layerImportBaseline),
      ),
    ).toHaveLength(1)
  })

  it('过期基线不再放行', () => {
    const [entry] = layerImportBaseline
    const expiredEntry = { ...entry, expiresOn: '2000-01-01' }

    expect(
      architectureErrors(verify(`import '${entry.importSource}'`, entry.file, [expiredEntry])),
    ).toHaveLength(1)
  })
})

describe('CODE-007 层级迁移基线', () => {
  it('每条基线都记录范围、责任人、批准、失效日期和整改计划', () => {
    const keys = layerImportBaseline.map((entry) => `${entry.file}\0${entry.importSource}`)

    expect(new Set(keys).size).toBe(layerImportBaseline.length)
    for (const entry of layerImportBaseline) {
      expect(entry.file).toMatch(/^src\/composables\/.+\.ts$/)
      expect(entry.importSource).toMatch(/^@\/components\/.+/)
      expect(entry.reason).toBeTruthy()
      expect(entry.owner).toBeTruthy()
      expect(entry.approvedBy).toBeTruthy()
      expect(entry.reviewedAt).toBeTruthy()
      expect(entry.remediation).toBeTruthy()
      expect(Date.parse(`${entry.expiresOn}T23:59:59.999Z`)).toBeGreaterThan(Date.now())
    }
  })

  it('只记录当前仍存在且确实违反目标层级的精确导入', () => {
    for (const entry of layerImportBaseline) {
      const absolutePath = path.resolve(projectRoot, entry.file)
      expect(fs.existsSync(absolutePath)).toBe(true)
      expect(fs.readFileSync(absolutePath, 'utf8')).toContain(entry.importSource)
      expect(architectureErrors(verify(`import '${entry.importSource}'`, entry.file))).toHaveLength(
        1,
      )
    }
  })
})
