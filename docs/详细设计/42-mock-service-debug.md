# 软件测试平台——（分册：调试）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

> 本分册由《》按功能模块拆分而来。前言、引言、数据设计与公共约定见总览分册 `40-mock-service-overview.md`；原章节编号保持不变，分册-章节对照见总览分册。

---

### 3.2 Mock 调试

#### 3.2.1 执行 Mock 调试

- **路径**：`POST /api/project/mocks/:id/debug`
- **说明**：模拟请求命中该 Mock，返回配置的响应（不计入 hit_count）。
- **请求体**：

```json
{
  "headers": { "Content-Type": "application/json" },
  "body": { "username": "admin", "password": "123456" }
}
```

- **响应**：

```json
{
  "status": 200,
  "headers": { "Content-Type": "application/json" },
  "body": { "code": 200, "data": { "token": "mock-token-xxx" } },
  "durationMs": 5
}
```


