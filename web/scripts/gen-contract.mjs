// 契约生成脚本（08 §4.2 / 07 §4.1）：从后端 springdoc 拉取 OpenAPI JSON，
// 落地为基线 openapi/contract.json，再用 openapi-typescript 生成只读前端类型 src/types/generated/contract.d.ts。
// 基线由后端运行环境产出（默认 http://localhost:58080/v3/api-docs），CI 中比对基线与生成产物是否漂移。
import { execSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const openapiUrl = process.env.OPENAPI_URL ?? 'http://localhost:58080/v3/api-docs'
const baselineDir = path.join(root, 'openapi')
const baselinePath = path.join(baselineDir, 'contract.json')
const outPath = path.join(root, 'src', 'types', 'generated', 'contract.d.ts')

if (!process.env.OPENAPI_URL && !process.env.CI) {
  // 本地缺后端时允许用既有基线重生成类型，避免拉取失败阻塞类型开发
  if (!(await exists(baselinePath))) {
    console.warn(`[contract] 未找到基线 ${baselinePath}，跳过类型生成；请先运行后端后执行 pnpm contract:gen`)
    process.exit(0)
  }
  console.warn(`[contract] 无后端环境，基于既有基线 ${baselinePath} 重新生成类型`)
  await generate()
  process.exit(0)
}

console.error(`[contract] fetching ${openapiUrl} ...`)
const res = await fetch(openapiUrl)
if (!res.ok) {
  throw new Error(`OPENAPI 拉取失败 HTTP ${res.status}（后端需运行于 ${openapiUrl}）`)
}
const doc = await res.text()
mkdirSync(baselineDir, { recursive: true })
writeFileSync(baselinePath, doc, 'utf8')
console.error(`[contract] 基线已写入 ${baselinePath}`)
await generate()

async function generate() {
  mkdirSync(path.dirname(outPath), { recursive: true })
  execSync(`npx openapi-typescript "${baselinePath}" -o "${outPath}"`, {
    cwd: root,
    stdio: 'inherit',
  })
  console.error(`[contract] 类型已生成 ${outPath}`)
}

async function exists(p) {
  try {
    await import('node:fs/promises').then((m) => m.access(p))
    return true
  } catch {
    return false
  }
}