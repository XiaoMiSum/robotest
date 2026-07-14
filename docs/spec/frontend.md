# 工程规范 — 前端

**文档版本**：V1.0
**日期**：2026-07-06
**状态**：已发布

---

## 1. 技术栈锁定

| 技术             | 版本   | 说明                                 |
| -------------- | ---- | ---------------------------------- |
| Vue            | 3.5+ | Composition API + `<script setup>` |
| TypeScript     | 6.x  | 严格模式                               |
| Vite           | 8.x  | 构建工具                               |
| Element Plus   | 最新   | UI 组件库                             |
| Pinia          | 最新   | 状态管理                               |
| Axios          | 最新   | HTTP 客户端                           |
| Vue Router     | 5.x  | 路由                                 |
| ESLint         | 9.x  | 代码检查                               |
| Prettier       | 最新   | 代码格式化                              |
| Vitest         | 最新   | 单元测试                               |
| Vue Test Utils | 最新   | 组件测试                               |

---

## 2. TypeScript 规范

**严格模式**：`tsconfig.json` 中 `strict: true`。

**基本规则**：

```typescript
// ✅ 正确：显式定义类型
const user: User = { id: 1, name: '张三' }

// ❌ 错误：滥用 any
const user: any = { id: 1, name: '张三' }

// ✅ 正确：明确返回值类型
function formatDate(date: Date): string { ... }

// ✅ 正确：自定义类型 + 泛型
type Status = 'active' | 'disabled'
type ApiResponse<T> = { code: number; message: string; data: T }
```

**禁止**：

- 禁止 `any`（确有必要时使用 `unknown` + 类型断言）
- 禁止 `// @ts-ignore`（只能用 `// @ts-expect-error` 并注明原因）
- 全局类型定义写在 `types/` 目录下，不可散落在组件中

---

## 3. 组件规范

### 3.1 文件结构顺序

```vue
<script setup lang="ts">
// 1. 类型导入
import type { PropType } from 'vue'
import type { User } from '@/types'

// 2. 工具导入
import { computed } from 'vue'
import { ElMessage } from 'element-plus'

// 3. 业务导入
import { useAuthStore } from '@/stores/auth'
import { fetchUsers } from '@/services/admin'

// 4. Props 定义（类型安全）
const props = defineProps({
  user: { type: Object as PropType<User>, required: true },
  editable: { type: Boolean, default: false }
})

// 5. Emits 定义
const emit = defineEmits<{
  (e: 'update', user: User): void
  (e: 'delete', id: number): void
}>()

// 6. 响应式状态
const loading = ref(false)

// 7. 计算属性
const displayName = computed(() => props.user.username)

// 8. 生命周期 & 业务方法
onMounted(() => { ... })
async function handleSave() { ... }
</script>

<template>
  <div class="user-card">
    <span>{{ displayName }}</span>
    <el-button @click="emit('delete', user.id)">删除</el-button>
  </div>
</template>

<style scoped lang="scss">
.user-card { display: flex; align-items: center; }
</style>
```

### 3.2 命名规则

| 要素        | 规范                 | 示例                      |
| --------- | ------------------ | ----------------------- |
| 组件名       | PascalCase，多词      | `UserList.vue`          |
| 组件目录      | kebab-case         | `admin/users/`          |
| Props     | camelCase          | `:userName`             |
| Emit 事件   | kebab-case         | `@update-user`          |
| CSS class | kebab-case BEM     | `.user-card__title`     |
| 模板 ref    | camelCase + Ref 后缀 | `const formRef = ref()` |

### 3.3 设计原则

- 每个组件只做一件事，职责单一。
- 页面组件（`pages/`）负责数据编排，纯 UI 组件（`components/`）不直接调用 API。
- 列表 + 表单是常见组合：`PageA.vue` 引用 `ATable.vue` + `AFormModal.vue`。
- 组件通信：Props 下传、Emit 上传；跨层级用 provide/inject；跨页面用 Pinia。

---

## 4. 路由规范

**集中管理**：

```typescript
// router/index.ts
const routes: RouteRecordRaw[] = [
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ['system'] },
    children: [
      { path: 'users', component: () => import('@/pages/admin/users/UserListPage.vue') }
    ]
  }
]
```

**meta 字段**：

```typescript
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    roles?: ('system' | 'business')[]
    workspaceRequired?: boolean
    projectRequired?: boolean
    title?: string
  }
}
```

**路由守卫流程**：

```
router.beforeEach
  ├── 未登录 && requiresAuth → 跳转登录页（保留回跳 URL）
  ├── 已登录 && 无系统角色 && 路径为 /admin/* → 403 提示
  ├── 已登录 && 无业务空间 && 路径为 /workspace/* → 提示页面
  ├── 已登录 && /workspace/projects/* && 无 activeProjectId → 重定向项目选择页
  └── 通过 → 继续
```

---

## 5. 状态管理规范

**Store 拆分**：

| Store       | 职责       | 数据                                           |
| ----------- | -------- | -------------------------------------------- |
| `auth`      | 认证与用户信息  | token, userInfo, roles                       |
| `workspace` | 工作空间上下文  | activeWorkspaceId, workspaceList             |
| `project`   | 项目工作区上下文 | activeProjectId, moduleTree, currentDocument |

**Pinia Composition API 写法**：

```typescript
export const useWorkspaceStore = defineStore('workspace', () => {
  const activeWorkspaceId = ref<number | null>(null)
  const workspaceList = ref<WorkspaceInfo[]>([])

  const currentWorkspace = computed(() =>
    workspaceList.value.find(w => w.id === activeWorkspaceId.value)
  )

  async function switchWorkspace(id: number) {
    activeWorkspaceId.value = id
    return await fetchGuide(id)
  }

  return { activeWorkspaceId, workspaceList, currentWorkspace, switchWorkspace }
})
```

**原则**：

- Store 只管理全局状态，页面局部状态用 `ref` / `reactive` 在组件内维护。
- API 请求在 `services/` 中调用，Store 不直接发请求。
- 避免跨 Store 循环引用。

---

## 6. API 请求层规范

```typescript
// services/request.ts
const service = axios.create({ baseURL: '/api', timeout: 15000 })

// 请求拦截：注入 Token + 上下文头
service.interceptors.request.use((config) => {
  config.headers.Authorization = `Bearer ${getToken()}`
  if (activeWorkspaceId.value) {
    config.headers['X-Active-Workspace'] = activeWorkspaceId.value
  }
  if (activeProjectId.value) {
    config.headers['X-Active-Project'] = activeProjectId.value
  }
  return config
})

// 响应拦截：统一错误处理
service.interceptors.response.use(
  (res) => res.data,
  (err) => {
    if (err.response?.status === 401) { /* 跳转登录 */ }
    return Promise.reject(err)
  }
)
```

**模块拆分**：每个模块一个 service 文件，职责单一。

```typescript
// services/admin.ts
export function fetchUsers(params: UserQueryParams): Promise<PageResult<UserInfo>> {
  return request.get('/admin/users', { params })
}
export function createUser(data: CreateUserDto): Promise<UserInfo> {
  return request.post('/admin/users', data)
}
```

---

## 7. 样式规范

- 使用 SCSS 预处理，全局变量在 `assets/styles/variables.scss` 中定义。
- 组件样式使用 `<style scoped lang="scss">`。
- 禁止使用 `!important`。
- 颜色、字体、间距使用 CSS 变量或 SCSS 变量，禁止硬编码。
- BEM 命名：`.block__element--modifier`。

---

**文档结束**
