# 软件测试平台——（分册：变量体系）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.4 步骤级变量管理

#### 3.4.1 查询步骤变量列表

- **路径**：`GET /api/project/scenes/:sceneId/steps/:stepId/variables`
- **响应**：

```json
[
  {
    "id": "018f...",
    "name": "username",
    "value": "${env:TEST_USER}",
    "source": "custom",
    "description": "用户名",
    "sortOrder": 0
  }
]
```

#### 3.4.2 批量更新步骤变量

- **路径**：`PUT /api/project/scenes/:sceneId/steps/:stepId/variables`
- **请求体**：

```json
{
  "variables": [
    { "name": "username", "value": "admin", "description": "用户名" },
    { "name": "password", "value": "${env:TEST_PASSWORD}", "description": "密码" }
  ]
}
```

- **说明**：手动更新的变量 `source` 设为 `custom`，全量覆盖。


### 3.5 场景变量管理

#### 3.5.1 批量更新场景变量

场景级变量**随场景整体聚合更新**：变量列表嵌入场景更新请求体（3.1.3 创建 / 3.1.4 更新）的 `variables` 字段，一次整体提交写入主表 `api_scene.variables` JSONB 列；场景详情接口（3.2.2）随场景配置返回完整变量列表。**不再提供独立的场景变量批量更新接口**，批量替换/导入/导出统一复用场景的创建/更新（3.1.3 / 3.1.4）能力。

`variables` 为全量覆盖语义：更新请求携带该字段时整体替换 JSONB 数组（缺省字段段保留不变）；创建请求携带时随场景一并落库。

请求体示例（场景更新 body 中的变量段）：

```json
{
  "variables": [
    { "name": "username", "value": "admin", "description": "测试用户名" },
    { "name": "password", "value": "${env:TEST_PASSWORD}", "description": "密码" }
  ]
}
```


### 4.1 变量引用与内置函数

变量引用语法与 Ryze 框架保持一致，使用 `${变量名}` 语法。

**变量解析优先级**（从低到高）：

```
内置函数 < 环境变量 < 场景变量 < 步骤级变量 < 步骤提取器变量 < 运行时覆盖
```

**内置函数**：

| 函数 | 说明 | 示例 |
| ---- | ---- | ---- |
| `${uuid()}` | 生成 UUID v7 | `${uuid()}` |
| `${timestamp()}` | 当前时间戳（毫秒） | `${timestamp()}` |
| `${timestamp_s()}` | 当前时间戳（秒） | `${timestamp_s()}` |
| `${date(format)}` | 当前日期（Java SimpleDateFormat） | `${date(yyyy-MM-dd)}` |
| `${random(min, max)}` | 随机整数 | `${random(1, 100)}` |
| `${randomString(length)}` | 随机字符串 | `${randomString(16)}` |
| `${env:VAR_NAME}` | 读取环境变量 | `${env:BASE_URL}` |
| `${var:VAR_NAME}` | 读取场景变量 | `${var:username}` |
| `${data:VAR_NAME}` | 读取数据源查询结果 | `${data:user_id}` |


### 6.1 Ryze 变量映射

平台场景变量与 Ryze variables 的映射为直接一对一（`name` → key，`value` → value）。内置函数在平台层解析后传入 Ryze 引擎，Ryze 引擎不感知平台内置函数。


