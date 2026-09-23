# 软件测试平台——智能体

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 智能体接口（管理端）

### 1.1 获取智能体列表

- **路径**：`GET /api/admin/ai/agents`
- **响应**：

```json
[
  {
    "functionType": "case_generation",
    "name": "用例子树生成",
    "customized": false,
    "formatEditable": false,
    "updatedBy": "张三",
    "updatedAt": "2026-07-31T10:00:00Z"
  }
]
```

列表为全部有模板位的功能类型（见 2.3），`customized` 与 `formatEditable` 同源：均表示该功能记录是否被管理员解锁过格式约束段编辑（种子全部默认锁定为 false，即初始「0 已自定义」；保存时开启高级开关后为 true；恢复默认后回到 false）。

### 1.2 获取智能体详情

- **路径**：`GET /api/admin/ai/agents/:functionType`
- **响应**：

```json
{
  "functionType": "case_generation",
  "name": "用例子树生成",
  "customized": false,
  "formatEditable": false,
  "roleInstruction": "当前生效的角色指令段…",
  "formatConstraint": "当前生效的输出格式约束段…"
}
```

详情返回当前生效内容（全部来自数据库，无内置默认段）；`customized`/`formatEditable` 语义同 1.1。

### 1.3 保存自定义模板

- **路径**：`PUT /api/admin/ai/agents/:functionType`
- **请求体**：`{ "roleInstruction": "…", "formatEditable": false, "formatConstraint": null }`
- **校验**：
  - `roleInstruction` 必填，长度 ≤ 8000；
  - `formatConstraint` 仅当 `formatEditable = true` 时接受修改；`formatEditable = false` 时提交了与生效值不同的 `formatConstraint` 返回 6009；
  - `formatEditable` 从 false → true 属于高级开关开启，单独记审计。
- **处理**：模板记录由初始化种子全量落库且始终存在（恢复默认仅重置内容不删除），故仅更新，无插入分支；未命中视为配置缺失返回 6013；变更写 sys_audit_log。`formatEditable` 仅接受 false → true（开启即标记已自定义），保存时未开启开关则保持数据库原值，不允许通过保存置回 false（回默认只能走恢复默认）。

### 1.4 恢复默认

- **路径**：`DELETE /api/admin/ai/agents/:functionType`
- **处理**：将该功能记录的角色指令与格式约束重置为内置默认内容（资源文件仅作恢复数据源，与种子同源），`formatEditable` 重置为 false（格式约束段重新锁定，回到「默认」状态）；记录不存在时按默认内容重建，保证数据库始终有记录；写 sys_audit_log。


