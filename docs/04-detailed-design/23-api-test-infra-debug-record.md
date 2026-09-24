# 软件测试平台——调试记录

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 调试记录接口

### 1.1 查询调试记录列表

- **路径**：`GET /api/project/debug-records?pageNo=1&pageSize=20`
- **响应**：

```json
{
  "list": [
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

### 1.2 删除调试记录

- **路径**：`DELETE /api/project/debug-records/:id`
- **响应**：`{ "success": true }`


