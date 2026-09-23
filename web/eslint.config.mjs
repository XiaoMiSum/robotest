import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'

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
  // 层级门禁：依赖方向 pages → components → composables → services/stores，详见 docs/spec/frontend.md 3.4
  {
    files: ['src/components/**/*.{vue,ts}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/services', '@/services/*', '@/services/**'],
              message: '组件不直接 import services：API 调用下沉到组件本地 composable（frontend.md 3.4）',
            },
            {
              group: ['@/pages', '@/pages/*', '@/pages/**'],
              message: '组件不得依赖 pages（frontend.md 3.4）',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/composables/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/pages', '@/pages/*', '@/pages/**'],
              message: 'composables 不得依赖 pages（frontend.md 3.4）',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/services/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: [
                '@/components',
                '@/components/*',
                '@/components/**',
                '@/pages',
                '@/pages/*',
                '@/pages/**',
                '@/composables',
                '@/composables/*',
                '@/composables/**',
                '@/stores',
                '@/stores/*',
                '@/stores/**',
              ],
              message: 'services 不得依赖上层模块（frontend.md 3.4）',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/stores/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: [
                '@/components',
                '@/components/*',
                '@/components/**',
                '@/pages',
                '@/pages/*',
                '@/pages/**',
                '@/composables',
                '@/composables/*',
                '@/composables/**',
              ],
              message: 'stores 不得依赖 components/pages/composables（frontend.md 3.4）',
            },
          ],
        },
      ],
    },
  },
  {
    ignores: ['dist', 'node_modules', '*.d.ts'],
  },
)
