# 软件测试平台——分享

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 分享机制

### 1.1 分享链接生成

分享不设全局开关，也没有「关闭分享」入口——重新生成即唯一覆盖方式，旧 token 随即失效。生成接口 `POST /api/project/reports/{id}/share` 要求 `api-report:view` 权限（`@PreAuthorize`）并经项目成员校验；前端 [分享] 按钮始终展示，越权由后端拒绝（HTTP 403，见 `docs/04-detailed-design/05-api-testing/05-api-testing-infra-report.md` 1.6）。有效期在生成时选择（前端提供 1 / 7 / 30 / 90 天，缺省 7 天），请求体仅 `{ "expiresInDays": 7 }`。

1. 生成唯一 `share_token`（UUID v4 去横线，32 字符十六进制）。
2. 设置过期时间 `share_expires_at = now + expiresInDays`（缺省 7 天）。
3. 记录分享者 `share_user_id = currentUser`：只写 `share_token` / `share_expires_at` / `share_user_id` 三列的部分更新，不整行覆盖（C11）。
4. 返回 `{shareUrl, expiresAt, shareBy}`：`shareUrl` 为站点相对路径 `/share/api-report/{id}?token={token}`，前端以 `window.location.origin` 拼成完整 URL；`expiresAt` 为 UTC 墙钟值，展示时按浏览器时区格式化；`shareBy` 为分享者名称（用户 name，无则 `null`）。

> **复用已有分享**：生成接口每次重新生成 token 并覆盖旧分享（旧链接立即失效）。前端在打开分享弹窗时优先复用**未过期**的现有分享——报告详情接口携带 `share`（`{shareUrl, expiresAt, shareBy}`，未过期才返回，否则 `null`），弹窗直接进入记录视图并提供 [复制]；仅在无分享 / 已过期时进入两步式生成视图。[重新生成] 只是切回生成视图，用户确认生成后才覆盖（分享者更新为当前用户），生成成功即把记录写回页面数据并切到记录视图。

生成失败（403 越权、报告不存在或不属于当前项目等）由后端拒绝，前端以错误消息提示。

### 1.2 分享访问校验（免登录）

接口 `GET /api/public/api-reports/{id}?token={token}`，路径在免登录白名单内，不接入 `@PreAuthorize`，鉴权完全依赖 token；`@RateLimit(limit = 60, window = 60)`，免登录口径为 **60 次 / 分钟**，超出返回框架 429（全局错误码）。上下文（workspaceId / projectId）经请求头传递（C4），URL 只含报告资源 ID 与分享令牌。

1. 按 `id + share_token` 定位报告（`uk_report_share_token` 部分唯一索引支撑）。
2. 校验 `share_expires_at > now`。
3. 逻辑删除过滤（`is_deleted`）。
4. 未匹配 / 已过期 / 已删除统一返回错误码 1000017312（`API_SHARE_EXPIRED`，消息「分享链接已过期或不存在」），不区分具体原因，避免枚举探测。

响应 `data` 字段：`id` / `reportType` / `name` / `environmentName` / `status` / `summary` / `result` / `createdAt`。`result` 为按 `reportType` 构建的结果数据集（场景 / 套件，见 `docs/04-detailed-design/05-api-testing/28-test-report-overview.md` 2.3），不含 Ryze 快照；对外**不返回** `share`（分享记录）、`executionMode`、`externalId`。

### 1.3 分享记录展示与复制

- 详情接口（`GET /api/project/reports/{id}`，需登录）响应在存在未过期分享时附带 `share`：`{shareUrl, expiresAt, shareBy}`。
- 分享弹窗打开时有未过期分享 → 直接展示记录（只读完整链接 + [复制]、有效期至、分享人）+ [重新生成]，不重复生成。
- [复制] 写入剪贴板的为带描述文本，包含报告名称、链接、有效期、分享者：

```
【测试报告分享】
报告名称：{报告名称}
分享链接：{完整 URL（站点前缀 + shareUrl）}
有效期至：{share_expires_at 本地时间}
分享人：{shareBy}
```

- 复制成功提示「已复制分享链接」，失败提示「复制失败，请手动复制」。
- 无未过期分享 → 弹窗两步式（选有效期 1 / 7 / 30 / 90 天，缺省 7 天 → [生成分享链接]，可 [取消]），生成成功即落盘（详情接口的 `share` 随刷新返回），失败以错误消息提示。

### 1.4 分享访问页（免登录页）

- 路由 `/share/api-report/:id`（name `ShareReport`，组件 `web/src/pages/project/api-testing/report/ShareReportPage.vue`），`meta.public: true`、`meta.title: '测试报告'`（标签页标题「测试报告 - RoboTest」）；token 走 query，与 `shareUrl` 一致：`/share/api-report/{id}?token={token}`。该页无权限码要求（token 校验 + 限流）。
- 守卫口径：公共路由只对未登录用户放行；已持有登录态的用户访问会被重定向到 `/`。
- 渲染：与报告详情页共用 `ReportResultView`（Hero → 统计卡 → 处理器页签 → 场景卡 → 步骤卡，见 `docs/04-detailed-design/05-api-testing/30-test-report-detail.md` 3），分享页不提供动作位（无 [分享] / [关闭]），也不展示分享记录本身；对外可见范围以 1.2 的响应字段为准。
- 状态分支：

| 状态 | 表现 |
| --- | --- |
| 加载中 | 内容区遮罩 |
| 缺少 `id` 或 `token` | 标题「分享链接无效」 |
| 接口返回 1000017312（消息含「分享」） | 标题「分享链接无效或已过期」 |
| 其他错误（含超出限流 429） | 展示后端错误消息，无消息时标题「加载失败」 |
| 以上错误态 | 统一副标题「请联系报告分享者重新生成链接」 |
| 成功 | 渲染结果视图 |

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-03 | 按实现对齐：补全生成接口权限与部分更新口径、索引名 `uk_report_share_token`、免登录接口路径/字段与 60 次/分限流、访问页路由与状态分支 |
