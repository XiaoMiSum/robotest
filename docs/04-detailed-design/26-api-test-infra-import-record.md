# 软件测试平台——导入记录

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 导入记录接口

### 1.1 查询导入记录

- **路径**：`GET /api/project/import-records?pageNo=1&pageSize=20`
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "importType": "url_swagger",
      "sourceName": "petstore.yaml",
      "status": "success",
      "summary": { "created": 12, "updated": 3, "failed": 0, "skipped": 1 },
      "createdAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 8
}
```

---


