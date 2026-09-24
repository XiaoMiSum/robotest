# web/AGENTS.md — 前端约定

> 本文件适用于 `web/` 目录。总则见根目录 `AGENTS.md`，全局共享约定以总则为准。

---

## 技术栈

- **框架**：Vue 3.5 + TypeScript（strict，版本以 `package.json` 和锁文件为准）
- **构建**：Vite（版本以 `package.json` 和锁文件为准）
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

分层：**路由 → 页面 → 组件 → 组合式 → 服务/状态**（页面可直连 services/stores；当前 ESLint 门禁只覆盖部分跨层导入，需结合代码审查）

- `router/`：懒加载 + meta 守卫（`admin` / `workspace` / `project` / `none`）
- `services/`：Axios 实例，拦截器注入 Token / 上下文头（`X-Active-Workspace`）
- `pages/`：编排数据，调用 services 与 stores
- `components/`：纯展示 + emit 事件；不直接 import services（状态与 API 调用下沉到本地 composable）
- `stores/`（Pinia）：全局 + 模块状态
- `composables/`：可复用组合式逻辑；组件本地 composable 封装状态与 services 调用
- 脑图：自研 SVG/Canvas + Yjs CRDT 协同（详见 `docs/00-spec/20-contracts/01-api.md` 第 9 节）

> 详细分层职责参见 `docs/00-spec/10-engineering/01-frontend.md`；页面分区滚动与滚动条视觉隐藏参见 `docs/00-spec/50-ui/03-scroll-container.md`。

## 核心约定

| 编号  | 规则                                                    | 检查方式      |
| --- | ----------------------------------------------------- | --------- |
| C1  | 禁止 `any`，必须使用 `unknown` + 类型断言或类型守卫                 | ESLint、TypeScript、代码审查     |
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
- 上下文标识（如 workspaceId）仅通过请求头 `X-Active-Workspace` / `X-Active-Project` 传递（C4），不出现在活动上下文 URL 或请求体中；资源自身 ID 按 API 规范处理
- 后端返回的普通时间字段为 UTC+0 无时区标识字符串，展示必须走 `utils/format.ts` 的 `formatDateTime` / `formatDate` 转本地时区，禁止直接 `new Date()` 或直接插值（详见 `docs/00-spec/10-engineering/01-frontend.md` 第 9 节）
- 邀请链接 `expiresAt` 按统一时间格式化工具展示；创建时使用无时区字符串提交
- 避免新增外部依赖，确有必要时需经团队讨论
