# 工程规范 — API 设计

**文档版本**：V1.0
**日期**：2026-07-06
**状态**：已发布

---

## 1. URL 规范

| 模块   | 基础路径             | 请求头                                    |
| ---- | ---------------- | -------------------------------------- |
| 系统管理 | `/api/admin`     | `Authorization`                        |
| 空间管理 | `/api/workspace` | `Authorization` + `X-Active-Workspace` |
| 功能测试 | `/api/project`   | `Authorization` + `X-Active-Project`   |

**设计规则**：

- 资源名使用复数名词：`/users`、`/workspaces`、`/modules`
- 嵌套资源通过路径层级表达：`/workspaces/:id/members`
- 操作型接口使用动词路径：`/reviews/:id/sync`、`/plans/:id/close`
- 上下文信息（workspaceId / projectId）通过请求头传递，不在 URL 中暴露

---

## 2. 请求方法规范

| 方法     | 用途          | 幂等  | 安全  |
| ------ | ----------- | --- | --- |
| GET    | 查询列表 / 详情   | ✅   | ✅   |
| POST   | 创建资源 / 执行操作 | ❌   | ❌   |
| PUT    | 全量更新        | ✅   | ❌   |
| PATCH  | 部分更新        | ✅   | ❌   |
| DELETE | 删除资源        | ✅   | ❌   |

---

## 3. 分页规范

**请求**：`GET /api/admin/users?page=1&pageSize=20`

- `page`：页码，从 1 开始
- `pageSize`：每页条数，默认 20，最大 100

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 45
  }
}
```

---

## 4. 错误码规范

| 范围        | 模块      | 示例               |
| --------- | ------- | ---------------- |
| 1001-1099 | 通用参数/校验 | 1001 参数校验失败      |
| 2001-2099 | 权限      | 2001 无权限         |
| 3001-3099 | 资源不存在   | 3001 用户不存在       |
| 4001-4099 | 冲突      | 4001 工作空间有项目无法解散 |
| 5000      | 服务器错误   | 5000 服务器内部错误     |

各模块详细错误码见对应详细设计说明书中的「错误码补充」章节。

---

## 5. WebSocket 规范

**连接端点**：`/ws/documents/{docId}`

**认证**：查询参数传递 Token

```
ws://example.com/ws/documents/10?token=eyJhbGciOi...
```

**消息格式**：

```json
{
  "type": "op",
  "payload": {
    "op": "add_node",
    "data": { "parentId": null, "title": "新节点" }
  },
  "version": 1
}
```

**操作类型**：

| op              | 说明     |
| --------------- | ------ |
| `add_node`      | 新增节点   |
| `move_node`     | 移动节点   |
| `update_attrs`  | 更新节点属性 |
| `delete_node`   | 删除节点   |
| `update_layout` | 更新布局   |

---

**文档结束**
