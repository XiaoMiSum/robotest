import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'
import noRestrictedLayerImports from './eslint-rules/no-restricted-layer-imports.mjs'
import { layerImportBaseline } from './eslint-rules/layer-import-baseline.mjs'

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  prettier,
  {
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
  },
  {
    files: ['src/**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
    },
  },
  {
    rules: {
      'vue/multi-word-component-names': 'off',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      // 共享表单对象依赖引用不变的深层原地编辑（既定契约），仍禁 prop 整体重赋值
      'vue/no-mutating-props': ['error', { shallowOnly: true }],
    },
  },
  {
    files: ['**/*.{ts,tsx,vue}'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/ban-ts-comment': [
        'error',
        {
          'ts-expect-error': 'allow-with-description',
          'ts-ignore': true,
          'ts-nocheck': true,
        },
      ],
    },
  },
  // 自定义规则同时解析别名和相对路径；基线仅按“文件 + 导入源”精确放行并设置失效日期
  {
    files: ['src/**/*.{ts,tsx,vue}'],
    plugins: {
      architecture: {
        rules: {
          'no-restricted-layer-imports': noRestrictedLayerImports,
        },
      },
    },
    rules: {
      'architecture/no-restricted-layer-imports': ['error', { baseline: layerImportBaseline }],
    },
  },
  {
    // 这两个声明文件由 Vite 插件生成且会被重写；其余 .d.ts 继续纳入 C1 检查
    ignores: ['dist/**', 'node_modules/**', 'src/auto-imports.d.ts', 'src/components.d.ts'],
  },
)
