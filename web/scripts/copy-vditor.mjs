// 把 vditor 的运行资源同步到 public/vditor，供 options.cdn 本地取用：
// 内网部署无公网 CDN，编辑器的表情、内容/代码主题与按需渲染器都从 cdn 拉取
import { cpSync, existsSync, rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const src = resolve(webRoot, 'node_modules/vditor/dist')
const dest = resolve(webRoot, 'public/vditor/dist')

if (!existsSync(src)) {
  console.error('[copy-vditor] 未找到 node_modules/vditor/dist，请先执行 pnpm install')
  process.exit(1)
}

rmSync(resolve(webRoot, 'public/vditor'), { recursive: true, force: true })
// ts/types 是源码与类型声明，运行时不会加载，不进静态资源
cpSync(src, dest, {
  recursive: true,
  filter: (from) => !/[\\/](ts|types)([\\/]|$)/.test(from) && !from.endsWith('.d.ts'),
})

console.log('[copy-vditor] 已同步 vditor 运行资源到 public/vditor/dist')
