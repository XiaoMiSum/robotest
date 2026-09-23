# 软件测试平台——（分册：调试记录）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

> 本分册由《》按功能模块拆分而来。前言、引言、数据设计与公共约定见总览分册 `21-api-test-infra-overview.md`；原章节编号保持不变，分册-章节对照见总览分册。

---

### 3.3 调试记录接口

#### 3.3.1 查询调试记录列表

- **路径**：`GET /api/project/debug-records?page=1&pageSize=20`
- **响应**：

```json
{
  "records": [
    {
      "id": "018f...",
      "name": "登录接口调试",
      "method": "POST",
      "url": "/api/auth/login",
      "status": "success",
      "responseStatus": 200,
      "durationMs": 230,
      "executedAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 15
}
```

#### 3.3.2 删除调试记录

- **路径**：`DELETE /api/project/debug-records/:id`
- **响应**：`{ "success": true }`


