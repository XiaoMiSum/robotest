import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:58080',
        changeOrigin: true,
        configure: (proxy) => {
          // SSE 响应禁用代理缓冲：http-proxy 对 text/event-stream 默认缓冲，
          // 导致前端 fetch 的 ReadableStream 永远等不到流结束（vitejs/vite#10851）
          proxy.on('proxyRes', (proxyRes) => {
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              proxyRes.headers['x-accel-buffering'] = 'no'
            }
          })
        },
      },
      '/ws': {
        target: 'ws://localhost:58080',
        ws: true,
      },
    },
  },
  test: {
    server: {
      deps: {
        // element-plus 经 unplugin-vue-components 注入组件与样式导入；测试环境 css 关闭，
        // 但外部化交由 Node ESM 加载会因 .css 扩展名直接失败，必须走 vite 管线
        inline: ['element-plus'],
      },
    },
    // C8 覆盖率门禁（docs/00-spec/30-quality-delivery/01-quality.md §4）：
    // 核心范围任一指标低于 70% 即失败。范围与排除项理由随阈值一并锁定，
    // 扩大范围或新增排除须先更新规范 §4 再改此处，防止绕过门禁。
    coverage: {
      provider: 'v8',
      // text 供控制台门禁判定，html 供人工查阅，clover/json 供 CI 产物归档
      reporter: ['text', 'html', 'clover', 'json'],
      reportsDirectory: 'coverage',
      include: ['src/**'],
      exclude: [
        // 应用引导入口（创建 app 并挂载），无业务分支，由 E2E 与发布验收覆盖
        'src/main.ts',
        // 纯类型声明（无任何运行时导出），编译期即擦除
        'src/types/**',
        // 测试文件自身不计入覆盖率
        '**/*.spec.ts',
      ],
      thresholds: { lines: 70, functions: 70, branches: 70, statements: 70 },
    },
  },
})
