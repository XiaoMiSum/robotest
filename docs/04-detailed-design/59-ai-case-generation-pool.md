# 软件测试平台——轻量需求池

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 需求池接口（常规业务，不经 AI 网关、不受 AI 开关影响）

### 1.1 条目列表

- **路径**：`GET /api/project/requirements`
- **参数**：`keyword`（可选，标题模糊）、`status`（可选，active / archived，缺省返回全部）、`pageNo`、`pageSize`
- **响应**：`{ "list": [{ "id", "title", "sourceUrl", "status", "createdBy", "creatorName", "updatedAt" }], "total": 12 }`

### 1.2 条目详情

- **路径**：`GET /api/project/requirements/:id`
- **响应**：含 `content`（Markdown 全文）与 `status` 的完整对象。

### 1.3 创建条目

- **路径**：`POST /api/project/requirements`
- **请求体**：`{ "title": "登录模块需求", "content": "……", "sourceUrl": null }`
- **校验**：`title` ≤ 200；`content` 长度 ≤ `requirementContentMaxLength`（默认 20000 字符，定义见基础设施文档 2.2 settings 键清单；需求池不受 AI 开关影响，AI 配置记录不存在时取代码内置默认值），超限返回 1001 并明确提示；`sourceUrl` 仅格式校验，不访问。
- **状态**：创建即 `active`（不接受客户端传入状态）。
- **响应**：创建后的条目，201。

### 1.4 更新 / 删除条目

- **路径**：`PUT / DELETE /api/project/requirements/:id`
- **权限**：仅条目创建人或具备项目管理权限的成员，违规返回 2001。
- **归档态限制**：条目状态为 `archived` 时禁止更新，返回 2001（归档即封存，只读）；删除不受归档态限制。
- **删除处理**：逻辑删除条目，同事务解除其全部文档关联（requirement_document_rel 逻辑删除）；不影响已生成的用例。

### 1.5 归档 / 取消归档

- **路径**：`PUT /api/project/requirements/:id/archive`
- **请求体**：`{ "archived": true }`（false 为取消归档，恢复 active）
- **权限**：同 1.4（创建人或项目管理权限成员），违规返回 2001。
- **幂等**：重复归档/取消归档同一状态不报错；取消归档后条目恢复可编辑，重新参与 AI 消费。

### 1.6 文档关联查询 / 设置

- **路径**：`GET / PUT /api/project/documents/:docId/requirements`
- **PUT 请求体**：`{ "requirementIds": ["0198…", "0199…"] }`（全量设置，差量增删关联记录）
- **归属校验**：`:docId` 必须属于 `X-Active-Project` 对应项目（联表 test_case_module 校验），不一致返回 3001；`requirementIds` 中的条目须属于同一项目。
- **active 过滤**：GET 仅返回 `active` 条目的关联摘要（archived 条目过滤不展示，关联记录保留）；PUT 设置时 `requirementIds` 仅接受 active 条目（含 archived 返回 2001）。
- **说明**：文档编辑权限即可维护关联；GET 返回关联条目摘要列表，供脑图 AI 入口默认带入。

### 1.7 批量创建条目（US-AI-019）

- **路径**：`POST /api/project/requirements/batch`
- **请求体**：

```json
{
  "items": [
    {
      "title": "用户管理·新增用户",
      "content": "……",
      "sourceUrl": null,
      "aiGenerated": true
    }
  ]
}
```

- **校验**：`items` 非空且 ≤ 100 条；每项 `title` ≤ 200、`content` 长度 ≤ `requirementContentMaxLength`（同 1.3），超限返回 1001；`aiGenerated` 缺省 false（仅作展示标记，不影响业务规则，接受客户端透传）。
- **权限**：与 1.3 一致（创建人本人即可，项目管理权限成员亦可）；批量接口不做逐条创建人差异。
- **响应**：`{ "count": 5 }`（实际入库条数）。
- **说明**：AI 拆分预览勾选后的批量入库走此接口；标题前缀（模块名 · 需求点标题）由前端按预览分组拼接后提交，接口不感知模块概念。


