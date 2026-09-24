import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { ESLint } from 'eslint'
import { describe, expect, it } from 'vitest'

const projectRoot = fileURLToPath(new URL('..', import.meta.url))
const eslint = new ESLint({
  cwd: projectRoot,
  overrideConfigFile: path.join(projectRoot, 'eslint.config.mjs'),
})

async function lintFixture(code, filePath) {
  const [result] = await eslint.lintText(code, {
    filePath: path.join(projectRoot, filePath),
    warnIgnored: true,
  })
  return result.messages
}

function hasRule(messages, ruleId) {
  return messages.some((message) => message.ruleId === ruleId)
}

describe('CODE-007 C1 ESLint 门禁', () => {
  it.each([
    ['src/Code007GateFixture.ts', 'export const value: any = 1'],
    [
      'src/components/Code007GateFixture.vue',
      '<script setup lang="ts">const value: any = 1</script>',
    ],
  ])('阻断 %s 中的 explicit any', async (filePath, code) => {
    expect(hasRule(await lintFixture(code, filePath), '@typescript-eslint/no-explicit-any')).toBe(
      true,
    )
  })

  it('阻断 @ts-ignore 与无说明的 @ts-expect-error', async () => {
    const messages = await lintFixture(
      '// @ts-ignore\nconst value = 1',
      'src/Code007GateFixture.ts',
    )
    expect(hasRule(messages, '@typescript-eslint/ban-ts-comment')).toBe(true)

    const undocumented = await lintFixture(
      '// @ts-expect-error\nconst value = 1',
      'src/Code007GateFixture.ts',
    )
    expect(hasRule(undocumented, '@typescript-eslint/ban-ts-comment')).toBe(true)
  })

  it('允许带有效说明的 @ts-expect-error', async () => {
    const messages = await lintFixture(
      '// @ts-expect-error 第三方声明缺少精确类型，等待上游修复\nconst value = 1',
      'src/Code007GateFixture.ts',
    )
    expect(hasRule(messages, '@typescript-eslint/ban-ts-comment')).toBe(false)
  })

  it('只豁免生成声明文件，不放过手写 .d.ts', async () => {
    const generated = await lintFixture(
      '// @ts-nocheck\nconst value: any = 1',
      'src/auto-imports.d.ts',
    )
    expect(hasRule(generated, '@typescript-eslint/ban-ts-comment')).toBe(false)
    expect(hasRule(generated, '@typescript-eslint/no-explicit-any')).toBe(false)

    const authored = await lintFixture('// @ts-ignore\nconst value: any = 1', 'env.d.ts')
    expect(hasRule(authored, '@typescript-eslint/ban-ts-comment')).toBe(true)
    expect(hasRule(authored, '@typescript-eslint/no-explicit-any')).toBe(true)
  })

  it('通过正常 lint 阻断组件反向依赖 services', async () => {
    const messages = await lintFixture(
      "import { get } from '@/services'",
      'src/components/Code007GateFixture.ts',
    )
    expect(hasRule(messages, 'architecture/no-restricted-layer-imports')).toBe(true)
  })
})
