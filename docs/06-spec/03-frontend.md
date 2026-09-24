# 前端工程规范

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 技术栈与版本来源

| 能力 | 技术 | 版本来源 |
| --- | --- | --- |
| 框架 | Vue 3.5 + Composition API | 依赖清单、锁文件 |
| 语言 | TypeScript strict | TypeScript 配置、锁文件 |
| 构建 | Vite | 依赖清单、锁文件 |
| UI | Element Plus | 依赖清单、锁文件 |
| 状态 | Pinia | 依赖清单、锁文件 |
| 请求 | Axios | 依赖清单、锁文件 |
| 测试 | Vitest、Vue Test Utils | 依赖清单、覆盖率配置 |
| 契约 | OpenAPI Typescript | 依赖清单、OpenAPI 基线 |

本文不重复维护“最新”版本。升级依赖时必须同步锁文件、类型检查、测试和 OpenAPI 生成结果。

## 2. TypeScript 规范

### 2.1 类型安全

- 禁止使用 `any`。
- 当前 TypeScript `strict` 不会自动禁止显式 `any`；应在 ESLint 中配置对应规则。配置完成前，C1 仍需人工审查和脚本补充检查。
- 不确定类型使用 `unknown`，再通过类型守卫或经过验证的断言缩小范围。
- 禁止用 `@ts-ignore` 隐藏错误；必须使用 `@ts-expect-error` 并说明原因。
- 跨端接口类型优先从 OpenAPI 生成类型中复用。
- 全局共享类型放在约定的共享类型目录，组件私有类型可以留在组件或相邻 composable。

```ts
const response: unknown = await request.get('/users')
const users = isUserList(response) ? response : []
```

### 2.2 命名和导入

- 组件、 composable、类型和工具分别遵循 PascalCase、camelCase、PascalCase 和动词/名词命名。
- 使用 `@/` 作为源码路径别名。
- 类型导入使用 `import type`，避免无意义的运行时依赖。
- 禁止通过循环导入解决分层问题。

## 3. 分层与依赖

依赖方向如下：

```text
router → pages → components → composables → services / types / utils
                    └──────────→ stores
pages             → stores / services / composables
stores            → services / types / utils
services          → types / utils / request infrastructure
```

### 3.1 页面

`pages/` 负责页面级数据编排、路由参数消费、权限状态和页面布局，可以调用 composables、stores 和 services。

### 3.2 组件

- 展示组件负责 Props、Emits、渲染和交互反馈。
- 组件不得直接依赖 `@/services`、`@/pages` 或其他页面。
- 组件需要 API 时，通过组件本地 composable 封装状态和调用。
- 组件不得把业务请求细节散落在模板事件中。

### 3.3 Composable

- 负责可复用的状态、副作用和 API 调用。
- 不得依赖 `pages/`。
- 不得包含与复用目标无关的页面布局逻辑。
- 返回值和副作用必须有明确命名，避免隐式全局状态。

### 3.4 Store

- Store 负责跨页面共享状态。
- 可以调用 `services/` 中的请求封装函数，但不得直接操作 Axios、组件或页面状态。
- 不得依赖 `components/`、`pages/` 或 `composables/`。
- 页面局部状态不得无理由提升为全局 Store。

### 3.5 Services

- Services 只负责 HTTP 请求和响应解包。
- 不得依赖 components、pages、composables 或 stores。
- 统一使用请求基础设施提供的请求实例和错误处理。
- 不在 service 中维护 loading、弹窗或路由状态。

上述边界的目标检查由 `web/eslint.config.mjs` 的 `no-restricted-imports` 和代码审查共同维护；当前 ESLint 规则只覆盖部分别名和目录，新增规则或迁移前必须补充对应门禁。

## 4. 组件规范

### 4.1 文件结构

建议顺序：

```vue
<script setup lang="ts">
import type { User } from '@/types'
import { computed, ref } from 'vue'
import { useUserList } from '@/composables/useUserList'

const props = defineProps<{ user: User }>()
const emit = defineEmits<{ delete: [id: string] }>()
const loading = ref(false)
const displayName = computed(() => props.user.name)
</script>

<template>
  <div class="user-card">
    <span>{{ displayName }}</span>
    <button :disabled="loading" @click="emit('delete', props.user.id)">
      删除
    </button>
  </div>
</template>
```

### 4.2 状态完整性

所有数据组件必须考虑：

- 加载中
- 空数据
- 请求失败
- 无权限
- 提交中
- 成功反馈
- 重试或刷新

禁止只实现正常路径。

### 4.3 命名

| 要素 | 规范 | 示例 |
| --- | --- | --- |
| 组件文件 | PascalCase，多词 | `UserList.vue` |
| 页面目录 | kebab-case | `pages/<feature>/` |
| Props | camelCase | `userName` |
| Emit | kebab-case | `@update-user` |
| CSS class | BEM | `.user-card__title` |
| 模板 ref | camelCase + `Ref` | `formRef` |

## 5. 路由规范

路由声明、导航守卫、参数校验、状态清理和错误处理应保持职责清晰，并共同保障认证、授权和资源校验。

### 5.1 路由声明

- 路由声明集中管理，页面组件按需加载；
- 每条路由应能声明访问所需的认证、授权、资源和展示信息；
- 路由元数据应使用稳定、可扩展的类型，并由类型检查覆盖；
- 路由路径和参数命名应保持语义清晰，不把敏感凭证放入 URL；
- 路由声明不得替代后端权限和资源归属校验。

### 5.2 导航守卫

路由守卫至少覆盖以下通用场景：

1. 未认证访问受保护页面；
2. 已认证但缺少必要权限；
3. 所需资源或作用域尚未加载、已失效或无权限；
4. 路由参数非法、资源不存在或加载失败；
5. 页面离开时存在未保存状态、需要确认或需要清理缓存。

守卫只负责导航体验、重定向和状态清理；后端必须独立执行 C2、C3、C4 和资源级权限校验。

### 5.3 参数和状态

- 路由参数只用于定位页面所需的资源，不直接作为授权依据；
- 参数进入页面或请求层前必须完成格式和语义校验；
- 作用域切换、用户退出或权限失效时，必须清理依赖旧作用域的页面状态和缓存；
- 页面不得通过路由参数绕过统一请求层或直接拼接敏感凭证。

### 5.4 加载和错误

- 路由级加载失败必须提供可恢复的错误状态；
- 重定向必须避免形成循环，并保留安全的返回目标；
- 懒加载、预取和缓存策略不得绕过认证和资源授权；
- 路由相关请求必须使用统一取消、重试和错误处理机制。

## 6. Pinia 规范

- 使用 Composition API 写法。
- 当前 `auth` Store 持有用户和活动作用域信息；不得再创建第二套作用域 Store。
- Store 名称表达业务域，不使用 `common`、`state` 等宽泛名称。
- 只持久化必要的会话和上下文信息。
- Token 使用统一认证服务管理，禁止在多个 Store 各自实现存储逻辑。
- 切换作用域时必须清理依赖旧上下文的页面状态。

## 7. API 请求层

### 7.1 统一契约

```ts
export interface Result<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
}
```

请求服务必须遵守 `05-api.md`：

- 分页参数使用 `pageNo/pageSize`。
- 分页数据读取 `list/total`。
- 认证失败和业务错误统一处理。
- Token 和作用域信息通过统一拦截器注入。

### 7.2 请求边界

- 不在组件中直接创建 Axios 实例。
- 不在 service 中处理 UI 消息、路由跳转等表现层行为。
- 上传、下载、SSE 和 WebSocket 使用各自明确的请求适配器。
- 生成类型与手写类型发生冲突时，先检查 OpenAPI 基线，不直接强行断言。

## 8. 样式与设计系统

- 使用 SCSS 和工程 CSS 变量。
- 组件样式默认使用 `<style scoped lang="scss">`。
- 颜色、字体、间距和层级使用设计令牌，禁止随意硬编码。
- 目标规范禁止使用 `!important`；现有第三方覆盖或历史代码需要例外时，必须限定作用域并登记原因，不得新增全局覆盖。
- 页面级布局、表格和滚动区域遵循 `13-scroll-container.md`。
- 不通过全局通配选择器修改单个页面的布局或滚动行为。

## 9. 时间处理

当前跨端时间契约由 `05-api.md` 定义。前端展示必须：

- 完整时间使用统一的时间格式化工具。
- 纯日期字段按日历日期展示，不做时区转换。
- 禁止直接 `new Date(后端无时区字符串)`。
- 禁止直接插值显示后端时间戳。
- 测试必须覆盖 UTC、带 offset、无时区和非法输入。

## 10. 可访问性与响应式

- 表单控件必须有可识别名称。
- 键盘可操作控件不得只依赖鼠标事件。
- 不得移除默认焦点样式而不提供替代焦点指示。
- 弹窗、抽屉和菜单必须支持关闭、焦点和滚动行为。
- 移动端不得仅通过缩小桌面布局实现适配。
- 需要内部滚动的区域必须提供边界、焦点或悬停提示。

## 11. 测试规范

- 纯函数、composable 和数据组装逻辑必须有单元测试。
- 组件测试至少覆盖主要渲染、用户交互、加载、空态和错误态。
- API service 测试验证请求参数、响应解包和错误分支。
- 跨端契约变更必须执行 OpenAPI 类型一致性检查。
- 变更覆盖率目标遵循 `07-quality.md`，不得通过删除测试或降低阈值绕过门禁。

## 12. 审查清单

- [ ] 无 `any`、无无理由的 `@ts-ignore`
- [ ] 页面、组件、composable、Store、Service 依赖方向正确
- [ ] 组件不直接依赖 services 或 pages
- [ ] 请求使用 `Result` 和统一错误处理
- [ ] 分页使用 `pageNo/pageSize` 和 `list`
- [ ] 加载、空态、错误和无权限状态完整
- [ ] 时间、可访问性和响应式要求已检查
- [ ] 相关单元测试和契约检查已执行

## 13. 参考

- API 契约：`docs/06-spec/05-api.md`
- 质量门禁：`docs/06-spec/07-quality.md`
- 滚动专项：`docs/06-spec/13-scroll-container.md`
- 前端约定：`web/AGENTS.md`

---

**文档结束**
