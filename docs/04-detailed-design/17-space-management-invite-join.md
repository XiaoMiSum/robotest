# 软件测试平台——公开邀请

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 公开接口（无需认证）

### 1.1 验证邀请令牌

- **路径**：`GET /api/workspace/invitations/verify`
- **参数**：`token`（必填）
- **响应**：`{ "valid": true, "workspaceName": "电商平台测试", "expiresAt": "..." }`
- **校验**：token 存在且 status='active'，未过期，未达最大使用次数。

### 1.2 通过邀请链接加入并登录

- **路径**：`POST /api/workspace/invitations/join`

- **请求体**：
  
  ```json
  {
  "token": "a1b2c3d4e5f6...",
  "email": "newuser@example.com",
  "password": "Abc@123456",
  "name": "张三"
  }
  ```

- **处理流程**（两步流程）：

  1. **第一步 — 检查邮箱**：前端调用 `POST /api/workspace/invitations/check-email`，传入 `email` 和 `token`。
     - 校验 token 有效性。
     - 查询该邮箱是否已在系统中注册。
     - 返回结果告知前端是"已有用户"还是"新用户"。

  2. **第二步 — 加入并登录**：前端根据第一步结果展示对应表单，提交至 `POST /api/workspace/invitations/join`。

     **已有用户**：用户填写密码进行验证。
     - 校验 token 有效性。
     - 验证密码是否正确。
     - 检查用户是否已在工作空间中。
     - 将用户加入工作空间，workspaceRole 引用预置 `workspace_member` 角色 ID。
     - 更新 use_count + 1。
     - 生成 JWT Token，设置该工作空间为活跃工作空间。

     **新用户**：用户填写姓名和密码创建账号。
     - 校验 token 有效性。
     - 使用传入的 `name`、`email`、`password` 创建新用户（`name` 必填）。
     - 检查用户是否已在工作空间中（按 email 查重）。
     - 将用户加入工作空间，workspaceRole 引用预置 `workspace_member` 角色 ID。
     - 更新 use_count + 1。
     - 生成 JWT Token，设置该工作空间为活跃工作空间。

- **补充说明**：`check-email` 接口（`POST /api/workspace/invitations/check-email`）与 `verify`、`join` 接口一样，属于无需登录即可访问的公开接口，需加入 Spring Security 免登录白名单。

- **响应**：
  
  ```json
  {
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "accessExpiry": "2026-07-20T07:44:00",
  "refreshExpiry": "2026-07-26T19:44:00",
  "user": { "id": 5, "username": "newuser", "email": "newuser@example.com" },
  "activeWorkspace": { "id": 1, "name": "电商平台测试", "workspaceRole": "member" },
  "isNewUser": true
  }
  ```
  
  > 说明：JWT Token 通过框架 `JwtTokenProvider` 直接生成，有效期由配置 `migoo.security.jwt.access-token-expires` 和 `refresh-token-expires` 控制。

---


### 1.2.1 公开邀请上下文边界

邀请验证、邮箱检查和加入接口不读取 `X-Active-Workspace`，也不把 `workspaceId` 作为请求体上下文。邀请 `token` 是服务端生成并绑定目标工作空间的资源凭证；加入成功响应中的 `activeWorkspace` 只表示本次登录初始化结果，客户端后续请求仍按工作空间域规则传递活动上下文。

### 1.3 邀请加入页面

**路由**：`/join?token=xxx`，无需登录。

**页面布局**：

```
┌──────────────────────────────────────────────────┐
│                                                  │
│            🔗 加入工作空间                        │
│                                                  │
│        您将被加入「电商平台测试」工作空间           │
│                                                  │
│       邮箱: [________________]                   │
│       密码: [________________]                   │
│       (8-64字符，含大小写、数字、特殊字符)         │
│                                                  │
│              [加入并登录]                         │
│                                                  │
│         已有账号？输入密码验证后直接加入            │
│         没有账号？将自动创建并加入                  │
└──────────────────────────────────────────────────┘
```

**交互说明**：

| 操作   | 触发方式      | 反馈                                                                                                            |
| ---- | --------- | ------------------------------------------------------------------------------------------------------------- |
| 验证链接 | 页面加载      | 调用 `GET /api/workspace/invitations/verify?token=xxx` → 无效则展示错误提示及原因                                           |
| 提交   | 点击[加入并登录] | 前端校验 → 调用 `POST /api/workspace/invitations/join` → 成功：存储Token，设置活跃空间，isNewUser=true时展示欢迎提示，跳转至项目列表页；失败：显示具体错误 |
| 密码校验 | 实时        | 显示密码强度指示条（弱/中/强）                                                                                              |
| 防重复  | 提交后       | 按钮置灰显示loading                                                                                                 |


