# web/AGENTS.md — 前端约定

> 本文件适用于 `web/` 目录。总则见根目录 `AGENTS.md`，全局共享约定以总则为准。

---

## 技术栈

- **框架**：Vue 3.5 + TypeScript 6.x（strict）
- **构建**：Vite 8
- **UI**：Element Plus
- **状态管理**：Pinia
- **协作**：Yjs CRDT（WebSocket 实时协同）

## 环境命令

```bash
# 开发（端口 5173）
pnpm install && pnpm run dev

# 构建 → dist/
pnpm run build

# 质量门禁
pnpm run lint && pnpm run typecheck && pnpm run test:unit -- --coverage
```

## 架构

分层：**路由 → API → 页面 → 组件 → 状态**

- `router/`：懒加载 + meta 守卫（`admin` / `business`）
- `services/`：Axios 实例，拦截器注入 Token / 上下文头（`X-Active-Workspace`）
- `pages/`：编排数据，调用 services 与 stores
- `components/`：纯展示 + emit 事件
- `stores/`（Pinia）：全局 + 模块状态
- `composables/`：可复用组合式逻辑
- 脑图：自研 SVG/Canvas + Yjs CRDT 协同（详见 `docs/spec/api.md#4`）

> 详细分层职责参见 `docs/spec/frontend.md`。

## 核心约定

| 编号  | 规则                                                    | 检查方式      |
| --- | ----------------------------------------------------- | --------- |
| C1  | 禁止 `any`，必须使用 `unknown` + 类型断言或类型守卫                 | `tsc`     |
| C6  | 注释只写 **why**，不写 **what**；无意义的冗余注释禁止添加               | 代码审查      |
| C8  | 关键模块覆盖（覆盖率 ≥ 70%）                                    | CI        |

### 编码示例

**C1 — 类型安全**
```typescript
// ❌ 禁止
const data: any = await api.getUsers()
// ✅ 推荐
const data: unknown = await api.getUsers()
const users = data as User[]
```

**C6 — 注释**
```typescript
// ❌ 禁止（what）
count++  // 计数器加一
// ✅ 推荐（why）
count++  // 跳过过期 token，防止脏数据进入报表
```

## 边界

- 只修改 `web/` 目录下的文件，不碰 `server/` 代码
- 上下文标识（如 workspaceId）仅通过请求头 `X-Active-Workspace` 传递（C4），不出现在 URL 或请求体中
- 避免新增外部依赖，确有必要时需经团队讨论
